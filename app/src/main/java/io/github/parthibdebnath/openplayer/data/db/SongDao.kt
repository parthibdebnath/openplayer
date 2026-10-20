package io.github.parthibdebnath.openplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistSongCount(val playlistId: Long, val songCount: Int)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    fun observeByPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    suspend fun getByPlaylist(playlistId: Long): List<SongEntity>

    @Query("SELECT * FROM songs WHERE playlistId IN (:playlistIds) ORDER BY playlistId, sortIndex ASC")
    suspend fun getByPlaylists(playlistIds: List<Long>): List<SongEntity>

    /** Every playlist's song count in one grouped query, for the playlist list. */
    @Query("SELECT playlistId, COUNT(*) AS songCount FROM songs GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistSongCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    /**
     * Touches only the two metadata columns. Writing back a whole row would overwrite anything
     * changed since it was read - a rename made while a large folder was still being indexed got
     * silently reverted that way.
     */
    @Query("UPDATE songs SET artist = :artist, durationMs = :durationMs WHERE id = :id")
    suspend fun updateMetadata(id: Long, artist: String, durationMs: Long)

    /** Renames without rewriting the rest of the row, which metadata loading may be updating. */
    @Query("UPDATE songs SET displayName = :displayName WHERE id = :id")
    suspend fun updateDisplayName(id: Long, displayName: String)

    @Query("UPDATE songs SET resumePositionMs = :positionMs WHERE id = :id")
    suspend fun updateResumePosition(id: Long, positionMs: Long)

    /** Puts a saved name back on an imported playlist's song, matched by file name. */
    @Query("UPDATE songs SET displayName = :displayName WHERE playlistId = :playlistId AND fileName = :fileName")
    suspend fun renameByFileName(playlistId: Long, fileName: String, displayName: String)

    /**
     * Songs in every playlist whose name or artist contains the pattern (already wrapped in %
     * and escaped). A plain scan: tens of thousands of rows take a few milliseconds, and a
     * full-text index would mean another table kept in step with every write.
     */
    @Query(
        "SELECT * FROM songs WHERE displayName LIKE :pattern ESCAPE '\\' OR artist LIKE :pattern ESCAPE '\\' " +
            "ORDER BY displayName COLLATE NOCASE LIMIT :limit",
    )
    suspend fun search(pattern: String, limit: Int): List<SongEntity>

    @Query("SELECT id FROM songs")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM songs WHERE documentUri IN (:documentUris)")
    suspend fun deleteByDocumentUris(documentUris: List<String>)

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>
}
