package io.github.parthibdebnath.openplayer.data.repository

import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The library and the app's setup, as saved to a file: by Settings - Export, and automatically
 * into each playlist folder (see [FOLDER_BACKUP_NAME]). Each playlist's folder and settings, the
 * names given to songs in the app, and - from format 2 - every setting and sound effect.
 * Versioned, so a later format can still read an older file.
 */
@Serializable
data class LibraryExport(
    val format: Int = 2,
    val playlists: List<ExportedPlaylist> = emptyList(),
    /** Settings by their stored name. Null in files from before settings were included. */
    val settings: Map<String, JsonPrimitive>? = null,
    val effects: EffectsState? = null,
    /**
     * Which install wrote it. A backup found in a folder from a different install - the app
     * reinstalled, or installed on a new phone - is offered for restoring; one this install wrote
     * itself is just kept up to date.
     */
    val installId: String? = null,
    /** When it was written, in milliseconds since 1970. */
    val savedAt: Long = 0L,
)

@Serializable
data class ExportedPlaylist(
    val name: String,
    val folderUri: String,
    val includeSubfolders: Boolean = false,
    /** Songs renamed in the app: file name to the name shown. */
    val renamedSongs: Map<String, String> = emptyMap(),
)

/**
 * The backup kept in every playlist folder. App data is deleted with the app, and folder access
 * with it, but the music folders stay - so after a reinstall, even one signed differently that
 * Android treats as a new app, adding any one of the old folders finds everything again.
 * Hidden (a leading dot), so file managers and other players leave it out of sight.
 */
const val FOLDER_BACKUP_NAME = ".openplayer-backup.json"

/**
 * Far above any real library's export, which is a few hundred kilobytes at most. Exports and
 * folder backups come from files anyone can put there, and reading a huge one whole would run
 * the app out of memory.
 */
private const val MAX_EXPORT_BYTES = 8 * 1024 * 1024

/** The stream's text, or null if it's too big to be an export; see [MAX_EXPORT_BYTES]. */
fun InputStream.readExportText(): String? {
    val bytes = readNBytesCompat(MAX_EXPORT_BYTES + 1)
    return if (bytes.size > MAX_EXPORT_BYTES) null else bytes.decodeToString()
}

// InputStream.readNBytes is Java 11 (Android 13); minSdk is 26.
private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
