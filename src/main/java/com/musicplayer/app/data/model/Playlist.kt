package com.musicplayer.app.data.model

/** A user playlist, backed by a folder on-device selected via the Storage Access Framework. */
data class Playlist(
    val id: Long,
    val displayName: String,
    val folderUri: String,
    val number: Int,
    val createdAt: Long,
    val songCount: Int = 0,
)
