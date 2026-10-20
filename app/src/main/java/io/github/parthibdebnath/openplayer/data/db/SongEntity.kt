package io.github.parthibdebnath.openplayer.data.db

import androidx.room.ColumnInfo
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
    /** Where a long track was left off, to pick up from next time; zero means from the start. */
    @ColumnInfo(defaultValue = "0") val resumePositionMs: Long = 0L,
)
