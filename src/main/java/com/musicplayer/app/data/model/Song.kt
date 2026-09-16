package com.musicplayer.app.data.model

/** A single audio file within a playlist folder. Backed by a SAF document URI. */
data class Song(
    val id: Long,
    val playlistId: Long,
    val documentUri: String,
    val fileName: String,
    val displayName: String,
    val artist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    val sortIndex: Long,
)
