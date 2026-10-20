package io.github.parthibdebnath.openplayer.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.room.withTransaction
import io.github.parthibdebnath.openplayer.data.db.AppDatabase
import io.github.parthibdebnath.openplayer.data.db.SongEntity
import io.github.parthibdebnath.openplayer.data.db.chunkedExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val UNKNOWN_ARTIST = "Unknown Artist"

/** How many files are read for metadata at once. */
private const val METADATA_CONCURRENCY = 6

/** Metadata results are written in batches, so a large import doesn't fire thousands of UI updates. */
private const val METADATA_BATCH_SIZE = 24

/**
 * Stored as a song's duration once its file has been read and turned out not to report one, so it
 * isn't read again on every launch. Zero means "not read yet".
 */
private const val DURATION_UNAVAILABLE = -1L

/** How deep into subfolders a scan goes, and how many files it takes at most. */
private const val MAX_DEPTH = 8
private const val MAX_FILES = 50_000

private class ExtractedMetadata(val songId: Long, val artist: String, val durationMs: Long)

/** One row of the folder listing, read in a single bulk query rather than file by file. */
private data class ScannedFile(
    val documentUri: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * Rescans a playlist's backing folder (SAF tree) on every app open, per spec.
 *
 * By default only the folder's direct children are indexed as songs, so that "import a folder as a
 * playlist" has predictable contents. A playlist can opt in to its subfolders as well - a library
 * laid out artist/album/track - in which case the whole tree is walked, except
 * [REMOVED_SONGS_FOLDER_NAME].
 *
 * The scan runs in two phases, which is what makes a large folder usable:
 *
 *  1. One bulk query lists the folder and writes every song row at once, so the list is on screen
 *     almost immediately with names intact.
 *  2. Artist and duration - which need each file to actually be opened and parsed - are filled in
 *     afterwards, in batches, only for files that are new or have changed.
 */
class PlaylistScanner(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val songDao = db.songDao()

    suspend fun rescan(playlistId: Long, folderUri: String, includeSubfolders: Boolean) = withContext(Dispatchers.IO) {
        try {
            rescanInternal(playlistId, folderUri, includeSubfolders)
        } catch (_: SecurityException) {
            // Folder permission was revoked (e.g. externally, or after a reboot without persist);
            // leave the existing cached song index untouched rather than crashing.
        }
    }

    private suspend fun rescanInternal(playlistId: Long, folderUri: String, includeSubfolders: Boolean) = coroutineScope {
        val treeUri = Uri.parse(folderUri)
        // Null - as opposed to empty - means the folder couldn't be read at all: permission
        // revoked, an SD card or USB drive removed, the folder renamed. That has to leave the
        // index alone. Treating it as "the folder is now empty" marked every song as deleted and
        // wiped the whole playlist, renames and all, until the folder came back.
        val found = listAudioFiles(treeUri, includeSubfolders) ?: return@coroutineScope
        val existing = songDao.getByPlaylist(playlistId).associateBy { it.documentUri }
        if (found.isEmpty() && existing.isEmpty()) return@coroutineScope

        val foundUris = found.mapTo(HashSet(found.size)) { it.documentUri }
        val staleUris = (existing.keys - foundUris).toList()
        if (staleUris.isNotEmpty()) {
            chunkedExecute(staleUris) { songDao.deleteByDocumentUris(it) }
        }

        // Phase 1 - everything the folder listing already told us, written in one transaction.
        // Only new and changed files are written: rewriting every unchanged row on every launch
        // was thousands of pointless writes for a large playlist, each one waking the song list.
        // A changed file gets a zero duration, which is what marks it for phase 2.
        val rows = found.mapNotNull { file ->
            val current = existing[file.documentUri]
            val unchanged = current != null &&
                current.lastModified == file.lastModified &&
                current.sizeBytes == file.sizeBytes
            if (unchanged) return@mapNotNull null
            SongEntity(
                // Preserved, never regenerated: the conflict strategy is REPLACE, and letting a
                // new id be assigned would break saved queues pointing at it.
                id = current?.id ?: 0,
                playlistId = playlistId,
                documentUri = file.documentUri,
                fileName = file.name,
                displayName = current?.displayName ?: file.name.substringBeforeLast('.'),
                artist = UNKNOWN_ARTIST,
                durationMs = 0L,
                sizeBytes = file.sizeBytes,
                lastModified = file.lastModified,
                sortIndex = current?.sortIndex ?: file.name.lowercase().hashCode().toLong(),
            )
        }
        if (rows.isNotEmpty()) songDao.insertAll(rows)

        // Phase 2 - re-read so the freshly generated ids are known, then fill in what's missing.
        // Zero means "not read yet"; it also picks up a scan that was cut short last time.
        val needingMetadata = songDao.getByPlaylist(playlistId).filter { it.durationMs == 0L }
        if (needingMetadata.isNotEmpty()) fillInMetadata(needingMetadata)
    }

    /**
     * Lists the folder in a single query.
     *
     * DocumentFile is deceptively expensive here: `listFiles()` hands back lazy objects whose
     * name, type, size and timestamp are each a separate IPC round trip to the storage provider.
     * Reading five fields from a thousand files is several thousand round trips, which is what
     * made a large playlist sit empty for the best part of a minute. Querying the children
     * collection directly returns every column for every file in one go.
     */
    private fun listAudioFiles(treeUri: Uri, includeSubfolders: Boolean): List<ScannedFile>? {
        val files = ArrayList<ScannedFile>()
        // The root has to be readable; a subfolder that can't be is skipped, not fatal.
        val pending = ArrayDeque<Pair<String, Int>>()
        val rootFolders = listFolder(treeUri, DocumentsContract.getTreeDocumentId(treeUri), files) ?: return null
        if (includeSubfolders) rootFolders.forEach { pending.addLast(it to 1) }
        while (pending.isNotEmpty() && files.size < MAX_FILES) {
            val (folderId, depth) = pending.removeFirst()
            val subfolders = listFolder(treeUri, folderId, files) ?: continue
            // Bounded, so a deep or looping tree (some providers expose links) can't run away.
            if (depth < MAX_DEPTH) subfolders.forEach { pending.addLast(it to depth + 1) }
        }
        return files
    }

    /**
     * Adds one folder's audio files to [into] and returns its subfolders' ids, or null if the
     * folder couldn't be read.
     */
    private fun listFolder(treeUri: Uri, folderId: String, into: MutableList<ScannedFile>): List<String>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val folders = ArrayList<String>()
        try {
            // A null cursor is the provider saying it couldn't answer - not that the folder is empty.
            val queried = context.contentResolver.query(childrenUri, projection, null, null, null)
                ?: return null
            queried.use { cursor ->
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(2)
                    val name = cursor.getString(1) ?: continue
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (name != REMOVED_SONGS_FOLDER_NAME) cursor.getString(0)?.let(folders::add)
                        continue
                    }
                    if (!isAudioFile(name, mimeType)) continue
                    val documentId = cursor.getString(0) ?: continue
                    into += ScannedFile(
                        // Matches what DocumentFile would have produced for the same child, so
                        // rows already stored against that uri still line up.
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                        name = name,
                        sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e("PlaylistScanner", "Could not list $folderId in $treeUri", t)
            return null
        }
        return folders
    }

    /**
     * Opens each file to read its artist and duration, writing results back in batches.
     *
     * Batched because every write wakes the observing query: updating a thousand songs one at a
     * time would push a thousand fresh lists at the UI.
     */
    private suspend fun fillInMetadata(songs: List<SongEntity>) = coroutineScope {
        val permits = Semaphore(METADATA_CONCURRENCY)
        val pending = ArrayList<ExtractedMetadata>(METADATA_BATCH_SIZE)
        val pendingLock = Mutex()

        suspend fun flush(force: Boolean) {
            val batch = pendingLock.withLock {
                if (pending.size < METADATA_BATCH_SIZE && !force) return
                pending.toList().also { pending.clear() }
            }
            if (batch.isEmpty()) return
            // One transaction per batch, so the observing song list hears about it once.
            db.withTransaction {
                batch.forEach { songDao.updateMetadata(it.songId, it.artist, it.durationMs) }
            }
        }

        songs.map { song ->
            async {
                permits.withPermit {
                    val (artist, durationMs) = extractMetadata(Uri.parse(song.documentUri))
                    pendingLock.withLock {
                        pending += ExtractedMetadata(
                            songId = song.id,
                            artist = artist,
                            // A file that reports no duration is marked as checked rather than
                            // left at zero, which would have it re-read on every single launch.
                            durationMs = if (durationMs > 0L) durationMs else DURATION_UNAVAILABLE,
                        )
                    }
                    flush(force = false)
                }
            }
        }.awaitAll()

        flush(force = true)
    }

    private fun extractMetadata(uri: Uri): Pair<String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: UNKNOWN_ARTIST
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            artist to duration
        } catch (_: Exception) {
            UNKNOWN_ARTIST to 0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
    }
}
