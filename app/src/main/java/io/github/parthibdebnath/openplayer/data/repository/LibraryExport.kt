package io.github.parthibdebnath.openplayer.data.repository

import kotlinx.serialization.Serializable

/**
 * The library as saved to a file by Settings - Export playlists: each playlist's folder and
 * settings, and the names given to songs in the app. Versioned, so a later format can still read
 * an older file.
 */
@Serializable
data class LibraryExport(
    val format: Int = 1,
    val playlists: List<ExportedPlaylist> = emptyList(),
)

@Serializable
data class ExportedPlaylist(
    val name: String,
    val folderUri: String,
    val includeSubfolders: Boolean = false,
    /** Songs renamed in the app: file name to the name shown. */
    val renamedSongs: Map<String, String> = emptyMap(),
)
