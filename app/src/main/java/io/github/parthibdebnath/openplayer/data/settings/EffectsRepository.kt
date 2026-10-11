package io.github.parthibdebnath.openplayer.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.effectsDataStore by preferencesDataStore(
    name = "effects",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Persists SFX screen state (all effect parameters + individual/global toggles) as a single
 * JSON blob. Changes apply instantly to playback and are saved indefinitely, per spec.
 *
 * The format is chosen to carry settings through app updates intact:
 *  - every value is written out, defaults included, so a later version changing a default doesn't
 *    quietly change what someone had set;
 *  - fields added later read as their defaults, fields removed later are ignored, and a value that
 *    no longer fits its field falls back to that field's default instead of failing the whole read
 *    and resetting every effect.
 */
class EffectsRepository(private val context: Context) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val key = stringPreferencesKey("effects_state_json")

    val effectsState: Flow<EffectsState> = context.effectsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (EffectsState) -> EffectsState) {
        context.effectsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(EffectsState.serializer(), transform(decode(prefs)))
        }
    }

    private fun decode(prefs: Preferences): EffectsState {
        val raw = prefs[key] ?: return EffectsState()
        return runCatching { json.decodeFromString(EffectsState.serializer(), raw) }
            .onFailure { Log.w("EffectsRepository", "Saved effects couldn't be read; using defaults", it) }
            .getOrDefault(EffectsState())
    }
}
