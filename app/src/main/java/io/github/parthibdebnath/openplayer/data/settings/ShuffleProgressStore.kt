package io.github.parthibdebnath.openplayer.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.parthibdebnath.openplayer.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// A store of its own rather than a key in playback_state: that one is rewritten every couple of
// seconds with the position, and every write rewrites the whole file - these shuffles with it.
private val Context.shuffleProgressDataStore by preferencesDataStore(
    name = "shuffle_progress",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * A shuffle put aside when something else started playing, so its list can pick it up again
 * where it was left: the same order, the same song, the same songs already played.
 *
 * Ids, like [SavedQueue]'s, re-resolved against the list as it is when the shuffle is picked up.
 */
@Serializable
data class SavedShuffle(
    /** Which list this is the shuffle of; see [io.github.parthibdebnath.openplayer.audio.listSourceKey]. */
    val sourceKey: String,
    val playedSongIds: List<Long> = emptyList(),
    val currentSongId: Long? = null,
    val upcomingSongIds: List<Long> = emptyList(),
)

/**
 * Every list's set-aside shuffle, most recently left last, capped at [MAX_SAVED_SHUFFLES].
 *
 * Changes are made in the order they're asked for, behind one lock: the saved ones are read the
 * first time any is needed, and a shuffle set aside before that read finished would otherwise
 * have been overwritten by it - or written out alone, wiping every other list's.
 */
class ShuffleProgressStore(private val context: Context, private val scope: CoroutineScope) {
    private val key = stringPreferencesKey("saved_shuffles")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedShuffle.serializer())
    private val mutex = Mutex()

    /** Null until read. Insertion order is age: the oldest goes first when there are too many. */
    private var entries: LinkedHashMap<String, SavedShuffle>? = null

    /** Sets [shuffle] aside for its list, replacing whatever that list had. */
    fun put(shuffle: SavedShuffle) {
        edit { map ->
            map.remove(shuffle.sourceKey)
            map[shuffle.sourceKey] = shuffle
            while (map.size > MAX_SAVED_SHUFFLES) map.remove(map.keys.first())
            true
        }
    }

    fun remove(sourceKey: String) {
        edit { map -> map.remove(sourceKey) != null }
    }

    fun clear() {
        edit { map -> map.isNotEmpty().also { map.clear() } }
    }

    /**
     * Hands back [sourceKey]'s shuffle and forgets it: it's about to become the queue, which is
     * saved on its own, and it's set aside again when something else replaces it.
     */
    suspend fun take(sourceKey: String): SavedShuffle? = mutex.withLock {
        val map = loaded()
        map.remove(sourceKey)?.also { write(map) }
    }

    /** [change] returns whether it changed anything, so an unchanged store isn't written. */
    private fun edit(change: (LinkedHashMap<String, SavedShuffle>) -> Boolean) {
        scope.launch {
            mutex.withLock {
                val map = loaded()
                if (change(map)) write(map)
            }
        }
    }

    private suspend fun loaded(): LinkedHashMap<String, SavedShuffle> = entries ?: run {
        val raw = runSuspendCatching { context.shuffleProgressDataStore.data.first()[key] }.getOrNull()
        val saved = if (raw == null) {
            emptyList()
        } else {
            // Off the main thread: with a few big playlists this is a lot of numbers.
            withContext(Dispatchers.Default) { runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList()) }
        }
        LinkedHashMap<String, SavedShuffle>().apply { saved.forEach { put(it.sourceKey, it) } }.also { entries = it }
    }

    private suspend fun write(map: LinkedHashMap<String, SavedShuffle>) {
        val snapshot = map.values.toList()
        runSuspendCatching {
            val encoded = withContext(Dispatchers.Default) { json.encodeToString(serializer, snapshot) }
            context.shuffleProgressDataStore.edit { it[key] = encoded }
        }.onFailure { Log.w("ShuffleProgressStore", "Could not save shuffles", it) }
    }

    private companion object {
        /** Enough for every list someone moves between; old ones, and deleted lists', age out. */
        const val MAX_SAVED_SHUFFLES = 30
    }
}
