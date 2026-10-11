package io.github.parthibdebnath.openplayer.data.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.playbackStateDataStore by preferencesDataStore(
    name = "playback_state",
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
 * Queue and position are stored separately because they change at very different rates: the
 * position every second or two, the queue only when something is skipped, queued or reordered.
 * Keeping them apart means the frequent writes stay tiny instead of re-serialising a queue that
 * can hold thousands of ids.
 */
class PlaybackStateRepository(private val context: Context) {
    private val queueKey = stringPreferencesKey("saved_queue")
    private val positionKey = stringPreferencesKey("saved_position")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadQueue(): SavedQueue? {
        val raw = context.playbackStateDataStore.data.first()[queueKey] ?: return null
        return runCatching { json.decodeFromString(SavedQueue.serializer(), raw) }.getOrNull()
    }

    suspend fun loadPosition(): SavedPosition? {
        val raw = context.playbackStateDataStore.data.first()[positionKey] ?: return null
        return runCatching { json.decodeFromString(SavedPosition.serializer(), raw) }.getOrNull()
    }

    suspend fun saveQueue(queue: SavedQueue) {
        context.playbackStateDataStore.edit {
            it[queueKey] = json.encodeToString(SavedQueue.serializer(), queue)
        }
    }

    suspend fun savePosition(position: SavedPosition) {
        context.playbackStateDataStore.edit {
            it[positionKey] = json.encodeToString(SavedPosition.serializer(), position)
        }
    }
}
