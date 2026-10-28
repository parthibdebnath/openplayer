package io.github.parthibdebnath.openplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A tag with the number of songs that carry it. */
data class TagWithCount(val id: Long, val name: String, val songCount: Int)

/** Which tags one song has, for a playlist's export: file name and tag name. */
data class SongTagName(val fileName: String, val tagName: String)

@Dao
interface TagDao {
    /** Every tag some existing song carries, with its song count, in name order. */
    @Query(
        "SELECT t.id AS id, t.name AS name, COUNT(*) AS songCount FROM tags t " +
            "JOIN song_tags st ON st.tagId = t.id JOIN songs s ON s.id = st.songId " +
            "GROUP BY t.id ORDER BY t.name COLLATE NOCASE",
    )
    fun observeTags(): Flow<List<TagWithCount>>

    /** The songs carrying a tag. */
    @Query("SELECT s.* FROM songs s JOIN song_tags st ON st.songId = s.id WHERE st.tagId = :tagId")
    fun observeSongsWithTag(tagId: Long): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s JOIN song_tags st ON st.songId = s.id WHERE st.tagId = :tagId")
    suspend fun getSongsWithTag(tagId: Long): List<SongEntity>

    /** One song's tags, in name order. */
    @Query(
        "SELECT t.* FROM tags t JOIN song_tags st ON st.tagId = t.id WHERE st.songId = :songId " +
            "ORDER BY t.name COLLATE NOCASE",
    )
    fun observeTagsForSong(songId: Long): Flow<List<TagEntity>>

    /** Matches ignoring case, through the column's collation. */
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongTag(link: SongTagEntity)

    @Query("DELETE FROM song_tags WHERE songId = :songId AND tagId = :tagId")
    suspend fun deleteSongTag(songId: Long, tagId: Long)

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    /** Removes the tag from every song; the songs and their files are untouched. */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    /** Moves every song from one tag onto another, for a rename onto an existing name. */
    @Query("INSERT OR IGNORE INTO song_tags (songId, tagId) SELECT songId, :intoId FROM song_tags WHERE tagId = :fromId")
    suspend fun copyLinks(fromId: Long, intoId: Long)

    @Query("DELETE FROM song_tags WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteOrphanLinks()

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM song_tags)")
    suspend fun deleteUnusedTags()

    /** Tags of a playlist's songs, by file name, for exports and folder backups. */
    @Query(
        "SELECT s.fileName AS fileName, t.name AS tagName FROM songs s " +
            "JOIN song_tags st ON st.songId = s.id JOIN tags t ON t.id = st.tagId WHERE s.playlistId = :playlistId",
    )
    suspend fun getTagNamesForPlaylist(playlistId: Long): List<SongTagName>

    @Query("SELECT id FROM songs WHERE playlistId = :playlistId AND fileName = :fileName")
    suspend fun songIdsByFileName(playlistId: Long, fileName: String): List<Long>

    /**
     * Drops links to songs that no longer exist, then tags no song carries. A tag is a playlist
     * only while some song has it, so the last one going takes the tag with it.
     */
    @Transaction
    suspend fun pruneOrphans() {
        deleteOrphanLinks()
        deleteUnusedTags()
    }

    /** Puts [name] on [songId], creating the tag if it's new. Returns the tag's id. */
    @Transaction
    suspend fun addTag(songId: Long, name: String): Long {
        val tagId = findByName(name)?.id ?: insertTag(TagEntity(name = name))
        insertSongTag(SongTagEntity(songId = songId, tagId = tagId))
        return tagId
    }

    /** Takes a tag off a song, and drops the tag if that was its last song. */
    @Transaction
    suspend fun removeTag(songId: Long, tagId: Long) {
        deleteSongTag(songId, tagId)
        deleteUnusedTags()
    }

    /**
     * Renames a tag. Onto another tag's name (ignoring case) the two are merged, since names are
     * unique; a change of case alone is just a rename.
     */
    @Transaction
    suspend fun renameOrMerge(id: Long, name: String) {
        val existing = findByName(name)
        if (existing == null || existing.id == id) {
            renameTag(id, name)
        } else {
            copyLinks(fromId = id, intoId = existing.id)
            deleteTag(id)
        }
    }
}
