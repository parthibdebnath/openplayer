package io.github.parthibdebnath.openplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tag the user has put on songs. Each tag is also a playlist of the songs carrying it. Names are
 * unique ignoring case, so "Chill" and "chill" are one tag rather than two lists side by side.
 */
@Entity(
    tableName = "tags",
    indices = [Index("name", unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

/**
 * One song carrying one tag.
 *
 * Deliberately no foreign key to `songs`: the folder scan rewrites a changed song's row with
 * REPLACE, which SQLite carries out as a delete and an insert, and a cascading key would quietly
 * strip the song's tags every time its file changed. Song ids survive rescans, so the link stays
 * valid; rows left behind by deleted songs are pruned ([TagDao.pruneOrphans]) and every read joins
 * through `songs`, so they never show in the meantime. Deleting a tag does cascade.
 */
@Entity(
    tableName = "song_tags",
    primaryKeys = ["songId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class SongTagEntity(
    val songId: Long,
    val tagId: Long,
)
