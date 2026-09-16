package com.musicplayer.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.effectsDataStore by preferencesDataStore(name = "effects")

/**
 * Persists SFX screen state (all effect parameters + individual/global toggles) as a single
 * JSON blob. Changes apply instantly to playback and are saved indefinitely, per spec.
 */
class EffectsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("effects_state_json")

    val effectsState: Flow<EffectsState> = context.effectsDataStore.data.map { prefs ->
        prefs[key]?.let {
            runCatching { json.decodeFromString(EffectsState.serializer(), it) }.getOrNull()
        } ?: EffectsState()
    }

    suspend fun update(transform: (EffectsState) -> EffectsState) {
        context.effectsDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(EffectsState.serializer(), it) }.getOrNull()
            } ?: EffectsState()
            prefs[key] = json.encodeToString(transform(current))
        }
    }
}
