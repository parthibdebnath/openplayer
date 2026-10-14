package io.github.parthibdebnath.openplayer.data.repository

import android.content.Context
import android.net.Uri
import java.util.concurrent.atomic.AtomicInteger
import androidx.documentfile.provider.DocumentFile
import io.github.parthibdebnath.openplayer.data.db.AppDatabase
import io.github.parthibdebnath.openplayer.data.db.PlaylistEntity
import io.github.parthibdebnath.openplayer.data.db.SongEntity
import io.github.parthibdebnath.openplayer.data.db.chunkedQuery
import io.github.parthibdebnath.openplayer.data.model.Playlist
import io.github.parthibdebnath.openplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val scanner = PlaylistScanner(context, db)

    // flowOn matters here: without it the entity-to-model mapping runs on whichever dispatcher
    // collects, which is the main thread. For a playlist of a few thousand songs that's a visible
    // stall every time the screen showing it is composed.
    //
    // The counts come from one grouped query and are only re-published when one actually changes:
    // filling in artists and durations after an import touches every row, but no count.
    fun observePlaylists(): Flow<List<Playlist>> =
        combine(playlistDao.observeAll(), songDao.observeCounts()) { entities, counts ->
            val byPlaylist = counts.associate { it.playlistId to it.songCount }
            entities.map { it.toModel(songCount = byPlaylist[it.id] ?: 0) }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // Room re-runs this whenever any song row changes, including during another playlist's scan,
    // so unchanged results are dropped before they reach sorting and the screen.
    fun observeSongs(playlistId: Long): Flow<List<Song>> =
        songDao.observeByPlaylist(playlistId)
            .map { list -> list.map { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Songs across multiple playlists (playlist id order, then each playlist's own song order) - used for Multi-Playlist. */
    suspend fun getSongsForPlaylists(playlistIds: List<Long>): List<Song> =
        withContext(Dispatchers.IO) {
            songDao.getByPlaylists(playlistIds).map { it.toModel() }
        }

    /** Resolves saved song ids back to songs when restoring a queue; missing ids are simply absent. */
    suspend fun getSongsByIds(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        chunkedQuery(ids) { songDao.getByIds(it) }.map { it.toModel() }
    }

    /** Every song id that currently exists, for pruning the play queue after a rescan. */
    suspend fun getAllSongIds(): Set<Long> = withContext(Dispatchers.IO) {
        songDao.getAllIds().toHashSet()
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
        // Shown as scanning, so the new playlist reads "Scanning..." rather than "0 songs".
        whileScanning { scanner.rescan(id, folderUri.toString()) }
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

    private val activeScans = AtomicInteger(0)
    private val _scanning = MutableStateFlow(false)

    /** True while a folder is being indexed, so the UI can say so instead of showing an empty list. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // Counted rather than a plain flag: scans overlap - the launch rescan and a folder imported
    // meanwhile - and the first to finish used to clear the flag while the other was still going.
    private suspend fun <T> whileScanning(block: suspend () -> T): T {
        synchronized(activeScans) { _scanning.value = activeScans.incrementAndGet() > 0 }
        return try {
            block()
        } finally {
            synchronized(activeScans) { _scanning.value = activeScans.decrementAndGet() > 0 }
        }
    }

    suspend fun rescanAll() = withContext(Dispatchers.IO) {
        whileScanning {
            playlistDao.getAll().forEach { scanner.rescan(it.id, it.folderUri) }
        }
    }

    suspend fun rescanPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext
        whileScanning { scanner.rescan(playlistId, entity.folderUri) }
    }

    suspend fun renameSong(songId: Long, newDisplayName: String) = withContext(Dispatchers.IO) {
        songDao.updateDisplayName(songId, newDisplayName)
    }

    /**
     * Copies user-picked files into the playlist's folder, then rescans once to index them all.
     *
     * @return how many were copied.
     */
    suspend fun addSongsFromDocuments(playlistId: Long, sourceUris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(playlistId) ?: return@withContext 0
        val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.folderUri)) ?: return@withContext 0
        val copied = sourceUris.count { SafFileOperations.copyInto(context, it, root) != null }
        if (copied > 0) whileScanning { scanner.rescan(playlistId, entity.folderUri) }
        copied
    }

    /** Moves a song's file into "<playlist folder>/removedSongs/" and rescans to drop it from the index. */
    suspend fun removeSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(song.playlistId) ?: return@withContext false
        val root = DocumentFile.fromTreeUri(context, Uri.parse(entity.folderUri)) ?: return@withContext false
        val removedFolder = SafFileOperations.getOrCreateRemovedSongsFolder(context, root) ?: return@withContext false
        // The stored uri already identifies the file; searching the folder for it by name cost a
        // storage round trip per song in the folder.
        val sourceDoc = DocumentFile.fromSingleUri(context, Uri.parse(song.documentUri))
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
