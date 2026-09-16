package com.musicplayer.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("documentUri", unique = true)],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
