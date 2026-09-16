package com.musicplayer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    fun observeByPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    suspend fun getByPlaylist(playlistId: Long): List<SongEntity>

    @Query("SELECT * FROM songs WHERE playlistId IN (:playlistIds) ORDER BY playlistId, sortIndex ASC")
    suspend fun getByPlaylists(playlistIds: List<Long>): List<SongEntity>

    @Query("SELECT documentUri FROM songs WHERE playlistId = :playlistId")
    suspend fun getDocumentUrisForPlaylist(playlistId: Long): List<String>

    @Query("SELECT COUNT(*) FROM songs WHERE playlistId = :playlistId")
    fun observeCountForPlaylist(playlistId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs WHERE documentUri IN (:documentUris)")
    suspend fun deleteByDocumentUris(documentUris: List<String>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>
}
