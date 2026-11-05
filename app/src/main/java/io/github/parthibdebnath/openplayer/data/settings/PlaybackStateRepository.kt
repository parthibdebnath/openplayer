package io.github.parthibdebnath.openplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.playbackStateDataStore by preferencesDataStore(
    name = "playback_state",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.playbackPositionDataStore by preferencesDataStore(
    name = "playback_position",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The shape of the queue: what was playing, what comes next and in what order (so a shuffle
 * resumes mid-way through the same order rather than reshuffling), and what has already played.
 *
 * Songs are stored as ids and re-resolved against the database on restore, so a song deleted in
 * the meantime simply drops out rather than resurrecting a dead file reference.
 */
@Serializable
data class SavedQueue(
    val currentSongId: Long? = null,
    val playlistLabel: String = "",
    val poolSongIds: List<Long> = emptyList(),
    val queuedSongIds: List<Long> = emptyList(),
    val upcomingSongIds: List<Long> = emptyList(),
    val historySongIds: List<Long> = emptyList(),
    val repeatMode: String = "OFF",
    val shuffled: Boolean = false,
    /**
     * Which list this queue is, so after a restart its shuffle can still be set aside when
     * something else is played, and its Shuffle button carries on with it rather than starting over.
     */
    val sourceKey: String? = null,
    /** Songs opened from another app, which have no database row to resolve their ids against. */
    val externalSongs: List<SavedExternalSong> = emptyList(),
    /** The swap arrow presses that a swipe on the row can still undo, oldest first. */
    val slotSwaps: List<SavedSlotSwap> = emptyList(),
)

/** A [io.github.parthibdebnath.openplayer.audio.SlotSwap], as saved. */
@Serializable
data class SavedSlotSwap(
    val incomingId: Long,
    val displacedId: Long,
    val incomingWasQueued: Boolean = false,
    val displacedAppended: Boolean = true,
)

/** Everything needed to rebuild a song that was opened from another app. */
@Serializable
data class SavedExternalSong(
    val id: Long,
    val uri: String,
    val fileName: String,
    val displayName: String,
    val artist: String,
    val durationMs: Long,
)

/** How far into [songId] playback had got. */
@Serializable
data class SavedPosition(
    val songId: Long,
    val positionMs: Long,
)

/**
 * Persists where the listener left off.
 *
 * Queue and position live in separate files because they change at very different rates: the
 * position every couple of seconds, the queue only when something is skipped, queued or
 * reordered. A DataStore rewrites its whole file on every change, so with both in one file each
 * position save re-wrote a queue that can hold thousands of ids. Earlier versions did exactly
 * that; their saved position is moved across the first time either is read (see [migrate]).
 */
class PlaybackStateRepository(
    private val queueStore: DataStore<Preferences>,
    private val positionStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.playbackStateDataStore, context.playbackPositionDataStore)

    private val json = Json { ignoreUnknownKeys = true }
    private val migrateLock = Mutex()
    private var migrated = false

    suspend fun loadQueue(): SavedQueue? {
        migrate()
        val raw = queueStore.data.first()[QUEUE_KEY] ?: return null
        return runCatching { json.decodeFromString(SavedQueue.serializer(), raw) }.getOrNull()
    }

    suspend fun loadPosition(): SavedPosition? {
        migrate()
        val raw = positionStore.data.first()[POSITION_KEY] ?: return null
        return runCatching { json.decodeFromString(SavedPosition.serializer(), raw) }.getOrNull()
    }

    suspend fun saveQueue(queue: SavedQueue) {
        migrate()
        val capped = if (queue.historySongIds.size > MAX_SAVED_HISTORY) {
            queue.copy(historySongIds = queue.historySongIds.takeLast(MAX_SAVED_HISTORY))
        } else {
            queue
        }
        queueStore.edit { it[QUEUE_KEY] = json.encodeToString(SavedQueue.serializer(), capped) }
    }

    suspend fun savePosition(position: SavedPosition) {
        migrate()
        positionStore.edit { it[POSITION_KEY] = json.encodeToString(SavedPosition.serializer(), position) }
    }

    /**
     * Moves a position saved by an earlier version, alongside the queue, into its own file - once,
     * before anything is read or written, so nobody loses their place in the update and a new
     * position is never overwritten by the old one. A position already in the new file wins.
     */
    private suspend fun migrate() = migrateLock.withLock {
        if (migrated) return@withLock
        val legacy = queueStore.data.first()[POSITION_KEY]
        if (legacy != null) {
            positionStore.edit { if (it[POSITION_KEY] == null) it[POSITION_KEY] = legacy }
            queueStore.edit { it.remove(POSITION_KEY) }
        }
        migrated = true
    }

    companion object {
        private val QUEUE_KEY = stringPreferencesKey("saved_queue")

        // The same name in both files: it's how the old copy is found.
        private val POSITION_KEY = stringPreferencesKey("saved_position")

        /**
         * Songs already played that are kept for Previous across a restart. A long session could
         * otherwise save tens of thousands, rewritten with every skip; past this many, the
         * oldest are simply forgotten - Previous doesn't reach back that far, and a shuffle
         * rebuilt after a restart may bring one of those back round sooner.
         */
        const val MAX_SAVED_HISTORY = 1_000
    }
}
