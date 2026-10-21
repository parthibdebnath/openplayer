package io.github.parthibdebnath.openplayer.data.repository

import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

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
