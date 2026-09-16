package com.musicplayer.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.musicplayer.app.data.db.SongDao
import com.musicplayer.app.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Rescans a playlist's backing folder (SAF tree) on every app open, per spec.
 *
 * Only the folder's direct children are indexed as songs (nested folders other than
 * [REMOVED_SONGS_FOLDER_NAME] are not recursed into) so that "import a folder as a
 * playlist" has predictable, non-surprising contents. Metadata (artist/duration) is
 * extracted only for newly-discovered files, with several files processed concurrently and
 * each inserted into the DB as soon as it's ready (rather than all-at-once at the end) so the
 * song list fills in progressively instead of appearing to hang on a large import.
 */
class PlaylistScanner(
    private val context: Context,
    private val songDao: SongDao,
) {
    suspend fun rescan(playlistId: Long, folderUri: String) = withContext(Dispatchers.IO) {
        try {
            rescanInternal(playlistId, folderUri)
        } catch (_: SecurityException) {
            // Folder permission was revoked (e.g. externally, or after a reboot without persist);
            // leave the existing cached song index untouched rather than crashing.
        }
    }

    private suspend fun rescanInternal(playlistId: Long, folderUri: String) = coroutineScope {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return@coroutineScope
        val children = root.listFiles()

        val foundFiles = children.filter { doc ->
            doc.isFile && doc.name != null && isAudioFile(doc.name!!, doc.type)
        }
        val foundUris = foundFiles.map { it.uri.toString() }.toSet()

        val existing = songDao.getByPlaylist(playlistId).associateBy { it.documentUri }

        val staleUris = existing.keys - foundUris
        if (staleUris.isNotEmpty()) {
            songDao.deleteByDocumentUris(staleUris.toList())
        }

        val toProcess = foundFiles.filter { doc ->
            val current = existing[doc.uri.toString()]
            !(current != null && current.lastModified == doc.lastModified() && current.sizeBytes == doc.length())
        }

        val concurrency = Semaphore(8)
        toProcess.map { doc ->
            async {
                concurrency.withPermit {
                    val uriString = doc.uri.toString()
                    val current = existing[uriString]
                    val (artist, durationMs) = extractMetadata(doc.uri)
                    val fileName = doc.name.orEmpty()
                    val entity = SongEntity(
                        id = current?.id ?: 0,
                        playlistId = playlistId,
                        documentUri = uriString,
                        fileName = fileName,
                        displayName = current?.displayName ?: fileName.substringBeforeLast('.'),
                        artist = current?.artist?.takeIf { it.isNotBlank() && artist == "Unknown Artist" } ?: artist,
                        durationMs = durationMs,
                        sizeBytes = doc.length(),
                        lastModified = doc.lastModified(),
                        sortIndex = current?.sortIndex ?: fileName.lowercase().hashCode().toLong(),
                    )
                    songDao.insert(entity)
                }
            }
        }.awaitAll()
    }

    private fun extractMetadata(uri: Uri): Pair<String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown Artist"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            artist to duration
        } catch (_: Exception) {
            "Unknown Artist" to 0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
    }
}
