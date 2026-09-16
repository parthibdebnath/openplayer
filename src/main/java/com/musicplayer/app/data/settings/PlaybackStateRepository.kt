package com.musicplayer.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

/**
 * What's needed to put the user back exactly where they left off. Songs are stored as ids and
 * re-resolved against the database on restore, so a song deleted in the meantime simply drops out
 * of the restored queue rather than resurrecting a dead file reference.
 */
@Serializable
data class SavedPlaybackState(
    val currentSongId: Long? = null,
    val playlistLabel: String = "",
    val positionMs: Long = 0L,
    val queuedSongIds: List<Long> = emptyList(),
    val upcomingSongIds: List<Long> = emptyList(),
    val poolSongIds: List<Long> = emptyList(),
    val repeatMode: String = "OFF",
    val shuffled: Boolean = false,
)

class PlaybackStateRepository(private val context: Context) {
    private val key = stringPreferencesKey("saved_playback_state")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): SavedPlaybackState? {
        val raw = context.playbackStateDataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString(SavedPlaybackState.serializer(), raw) }.getOrNull()
    }

    suspend fun save(state: SavedPlaybackState) {
        context.playbackStateDataStore.edit {
            it[key] = json.encodeToString(SavedPlaybackState.serializer(), state)
        }
    }
}
