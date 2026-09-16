package com.musicplayer.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.musicplayer.app.data.db.AppDatabase
import com.musicplayer.app.data.db.PlaylistEntity
import com.musicplayer.app.data.db.SongEntity
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Owns playlist CRUD and the SAF file operations backing it. "Delete playlist" only forgets
 * the playlist within the app (removes its DB rows and releases the persisted folder
 * permission) - it never touches the user's actual files on disk.
 */
class PlaylistRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val playlistDao = db.playlistDao()
    private val songDao = db.songDao()
    private val scanner = PlaylistScanner(context, songDao)

    // flowOn matters here: without it the entity-to-model mapping runs on whichever dispatcher
    // collects, which is the main thread. For a playlist of a few thousand songs that's a visible
    // stall every time the screen showing it is composed.
    fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll()
            .map { entities -> entities.map { it.toModel(songCount = 0) } }
            .flowOn(Dispatchers.IO)

    fun observeSongs(playlistId: Long): Flow<List<Song>> =
        songDao.observeByPlaylist(playlistId)
            .map { list -> list.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    fun observeSongCount(playlistId: Long): Flow<Int> = songDao.observeCountForPlaylist(playlistId)

    /** Songs across multiple playlists (playlist id order, then each playlist's own song order) - used for Multi-Playlist. */
    suspend fun getSongsForPlaylists(playlistIds: List<Long>): List<com.musicplayer.app.data.model.Song> =
        withContext(Dispatchers.IO) {
            songDao.getByPlaylists(playlistIds).map { it.toModel() }
        }

    /** Resolves saved song ids back to songs when restoring a queue; missing ids are simply absent. */
    suspend fun getSongsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else songDao.getByIds(ids).map { it.toModel() }
    }

    /**
     * Creates a playlist for [folderUri], or returns the id of the existing one if that folder has
     * already been imported. Importing the same folder twice produced a second playlist that
     * always showed zero songs, because each song row belongs to exactly one playlist and the
     * scan re-homed them to whichever playlist scanned last.
     */
    suspend fun createPlaylist(folderUri: Uri, displayName: String): Long = withContext(Dispatchers.IO) {
        playlistDao.getAll().firstOrNull { it.folderUri == folderUri.toString() }?.let {
            return@withContext it.id
        }
        // Best-effort: some providers don't grant persistable permission on the returned tree
        // Uri. Don't let that abort playlist creation - it just means a future rescan (e.g.
        // after a reboot) may fail to re-read this folder until the user re-picks it.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val nextNumber = playlistDao.getMaxNumber() + 1
        val id = playlistDao.insert(
            PlaylistEntity(
                displayName = displayName,
                folderUri = folderUri.toString(),
                number = nextNumber,
                createdAt = System.currentTimeMillis(),
            ),
        )
        scanner.rescan(id, folderUri.toString())
        id
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext
        playlistDao.update(entity.copy(displayName = newName))
    }

    /** Removes the playlist from the app only; the folder and its songs on disk are untouched. */
    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(entity.folderUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        playlistDao.delete(entity)
    }

    suspend fun rescanAll() = withContext(Dispatchers.IO) {
        playlistDao.getAll().forEach { scanner.rescan(it.id, it.folderUri) }
    }

    suspend fun rescanPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext
        scanner.rescan(playlistId, entity.folderUri)
    }

    suspend fun renameSong(songId: Long, newDisplayName: String) = withContext(Dispatchers.IO) {
        val song = songDao.getById(songId) ?: return@withContext
        songDao.update(song.copy(displayName = newDisplayName))
    }

    /** Copies a user-picked file into the playlist's folder, then rescans to index it. */
    suspend fun addSongFromDocument(playlistId: Long, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext false
        val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.folderUri)) ?: return@withContext false
        val copied = SafFileOperations.copyInto(context, sourceUri, root) ?: return@withContext false
        scanner.rescan(playlistId, entity.folderUri)
        true
    }

    /** Moves a song's file into "<playlist folder>/removedSongs/" and rescans to drop it from the index. */
    suspend fun removeSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(song.playlistId) ?: return@withContext false
        val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.folderUri)) ?: return@withContext false
        val removedFolder = SafFileOperations.getOrCreateRemovedSongsFolder(context, root) ?: return@withContext false
        val sourceDoc = root.findFile(song.fileName)?.takeIf { it.uri.toString() == song.documentUri }
            ?: DocumentFile.fromSingleUri(context, Uri.parse(song.documentUri))
            ?: return@withContext false
        val moved = SafFileOperations.moveInto(context, sourceDoc, root, removedFolder)
        if (moved) scanner.rescan(song.playlistId, entity.folderUri)
        moved
    }

    companion object {
        @Volatile private var instance: PlaylistRepository? = null
        fun get(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }
}

private fun PlaylistEntity.toModel(songCount: Int) = Playlist(
    id = id,
    displayName = displayName,
    folderUri = folderUri,
    number = number,
    createdAt = createdAt,
    songCount = songCount,
)

private fun SongEntity.toModel() = Song(
    id = id,
    playlistId = playlistId,
    documentUri = documentUri,
    fileName = fileName,
    displayName = displayName,
    artist = artist,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    sortIndex = sortIndex,
)
