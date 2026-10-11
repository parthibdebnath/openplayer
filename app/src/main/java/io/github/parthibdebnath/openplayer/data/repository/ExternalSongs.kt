package io.github.parthibdebnath.openplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.parthibdebnath.openplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Playlist id given to songs opened from another app, which belong to no playlist. */
const val EXTERNAL_PLAYLIST_ID = -1L

/** Queue label shown while a song opened from another app is playing. */
const val EXTERNAL_LABEL = "Opened file"

private const val UNKNOWN_ARTIST = "Unknown Artist"

val Song.isExternal: Boolean get() = playlistId == EXTERNAL_PLAYLIST_ID

/**
 * Songs opened from another app ("Open with"), which aren't part of any playlist and so aren't in
 * the database. They get negative ids, which database rows never have.
 */
object ExternalSongs {

    fun idFor(uri: Uri): Long = -((uri.toString().hashCode().toLong() and 0x7FFF_FFFFL) + 1L)

    /**
     * Reads what's needed to play and show [uri], or null if it can't be read. Asks to keep access
     * past this session, which only works when the opening app offers that.
     */
    suspend fun resolve(context: Context, uri: Uri): Song? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        try {
            var name: String? = null
            var size = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                    }
                }
            val fileName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
            val (artist, durationMs) = readMetadata(context, uri)
            Song(
                id = idFor(uri),
                playlistId = EXTERNAL_PLAYLIST_ID,
                documentUri = uri.toString(),
                fileName = fileName,
                displayName = fileName.substringBeforeLast('.').ifBlank { fileName },
                artist = artist,
                durationMs = durationMs,
                sizeBytes = size,
                lastModified = 0L,
                sortIndex = 0L,
            )
        } catch (t: Throwable) {
            Log.w("ExternalSongs", "Could not open $uri", t)
            null
        }
    }

    /**
     * Whether [uri] can still be read after the app restarts. Most apps only lend a file for as
     * long as the screen that opened it, in which case there's no point saving it to resume later.
     */
    fun survivesRestart(context: Context, uri: Uri): Boolean =
        uri.scheme != ContentResolver.SCHEME_CONTENT ||
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun readMetadata(context: Context, uri: Uri): Pair<String, Long> {
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
            runCatching { retriever.release() }
        }
    }
}
