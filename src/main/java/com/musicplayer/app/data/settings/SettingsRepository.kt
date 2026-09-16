package com.musicplayer.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Crossfade duration bounds, in milliseconds, and the granularity the slider steps in. Zero is off. */
const val CROSSFADE_MIN_MS = 0
const val CROSSFADE_MAX_MS = 15000
const val CROSSFADE_STEP_MS = 500

/** Default values are the state a user sees the very first time they open the app. */
object SettingsDefaults {
    const val NOTIFICATION_MEDIA_CONTROL = true
    const val REQUIRE_AUTH_TO_OPEN = false
    const val REQUIRE_AUTH_TO_DELETE = false
    const val TWO_FINGER_BRIGHTNESS_GESTURE = true
    const val THREE_FINGER_VOLUME_GESTURE = true
    const val GLASS_THEMING = true
    const val TWENTY_FOUR_HOUR_CLOCK = false
    const val SHUFFLE_SEED_LOCKED = false
    const val CROSSFADE_MS = 0
    const val BACKGROUND_PLAYBACK = true
    const val BACKGROUND_COLOR_HEX = "#000000"
    const val DAILY_RANDOM_BACKGROUND = false
    const val INVERT_CONTRAST = false

    /** On = icons sit bare, with no circle behind them. Glass theming overrides it while it's on. */
    const val HIDE_ICON_BACKGROUNDS = true
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val NOTIFICATION_MEDIA_CONTROL = booleanPreferencesKey("notification_media_control")
        val REQUIRE_AUTH_TO_OPEN = booleanPreferencesKey("require_auth_to_open")
        val REQUIRE_AUTH_TO_DELETE = booleanPreferencesKey("require_auth_to_delete")
        val TWO_FINGER_BRIGHTNESS_GESTURE = booleanPreferencesKey("two_finger_brightness_gesture")
        val THREE_FINGER_VOLUME_GESTURE = booleanPreferencesKey("three_finger_volume_gesture")
        val GLASS_THEMING = booleanPreferencesKey("glass_theming")
        val TWENTY_FOUR_HOUR_CLOCK = booleanPreferencesKey("twenty_four_hour_clock")
        val SHUFFLE_SEED = stringPreferencesKey("shuffle_seed")
        val SHUFFLE_SEED_LOCKED = booleanPreferencesKey("shuffle_seed_locked")
        val CROSSFADE_MS = intPreferencesKey("crossfade_ms")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val BACKGROUND_COLOR_HEX = stringPreferencesKey("background_color_hex")
        val DAILY_RANDOM_BACKGROUND = booleanPreferencesKey("daily_random_background")
        val BACKGROUND_COLOR_DATE = stringPreferencesKey("background_color_date")
        val HIDE_ICON_BACKGROUNDS = booleanPreferencesKey("hide_icon_backgrounds")
        val INVERT_CONTRAST = booleanPreferencesKey("invert_contrast")
    }

    val notificationMediaControl: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.NOTIFICATION_MEDIA_CONTROL] ?: SettingsDefaults.NOTIFICATION_MEDIA_CONTROL }
    val requireAuthToOpen: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.REQUIRE_AUTH_TO_OPEN] ?: SettingsDefaults.REQUIRE_AUTH_TO_OPEN }
    val requireAuthToDelete: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.REQUIRE_AUTH_TO_DELETE] ?: SettingsDefaults.REQUIRE_AUTH_TO_DELETE }
    val twoFingerBrightnessGesture: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.TWO_FINGER_BRIGHTNESS_GESTURE] ?: SettingsDefaults.TWO_FINGER_BRIGHTNESS_GESTURE }
    val threeFingerVolumeGesture: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.THREE_FINGER_VOLUME_GESTURE] ?: SettingsDefaults.THREE_FINGER_VOLUME_GESTURE }
    val glassTheming: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.GLASS_THEMING] ?: SettingsDefaults.GLASS_THEMING }
    val twentyFourHourClock: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.TWENTY_FOUR_HOUR_CLOCK] ?: SettingsDefaults.TWENTY_FOUR_HOUR_CLOCK }
    val backgroundPlayback: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.BACKGROUND_PLAYBACK] ?: SettingsDefaults.BACKGROUND_PLAYBACK }
    val backgroundColorHex: Flow<String> = context.settingsDataStore.data
        .map { it[Keys.BACKGROUND_COLOR_HEX] ?: SettingsDefaults.BACKGROUND_COLOR_HEX }
    val dailyRandomBackground: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.DAILY_RANDOM_BACKGROUND] ?: SettingsDefaults.DAILY_RANDOM_BACKGROUND }
    val hideIconBackgrounds: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.HIDE_ICON_BACKGROUNDS] ?: SettingsDefaults.HIDE_ICON_BACKGROUNDS }
    val invertContrast: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.INVERT_CONTRAST] ?: SettingsDefaults.INVERT_CONTRAST }
    val crossfadeMs: Flow<Int> = context.settingsDataStore.data
        .map { (it[Keys.CROSSFADE_MS] ?: SettingsDefaults.CROSSFADE_MS).coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS) }
    val shuffleSeed: Flow<String> = context.settingsDataStore.data
        .map { it[Keys.SHUFFLE_SEED] ?: generateShuffleSeed() }
    val shuffleSeedLocked: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.SHUFFLE_SEED_LOCKED] ?: SettingsDefaults.SHUFFLE_SEED_LOCKED }

    suspend fun setNotificationMediaControl(value: Boolean) = setBoolean(Keys.NOTIFICATION_MEDIA_CONTROL, value)
    suspend fun setRequireAuthToOpen(value: Boolean) = setBoolean(Keys.REQUIRE_AUTH_TO_OPEN, value)
    suspend fun setRequireAuthToDelete(value: Boolean) = setBoolean(Keys.REQUIRE_AUTH_TO_DELETE, value)
    suspend fun setTwoFingerBrightnessGesture(value: Boolean) = setBoolean(Keys.TWO_FINGER_BRIGHTNESS_GESTURE, value)
    suspend fun setThreeFingerVolumeGesture(value: Boolean) = setBoolean(Keys.THREE_FINGER_VOLUME_GESTURE, value)
    suspend fun setGlassTheming(value: Boolean) = setBoolean(Keys.GLASS_THEMING, value)
    suspend fun setTwentyFourHourClock(value: Boolean) = setBoolean(Keys.TWENTY_FOUR_HOUR_CLOCK, value)
    suspend fun setShuffleSeedLocked(value: Boolean) = setBoolean(Keys.SHUFFLE_SEED_LOCKED, value)
    suspend fun setBackgroundPlayback(value: Boolean) = setBoolean(Keys.BACKGROUND_PLAYBACK, value)

    suspend fun setBackgroundColorHex(value: String) {
        context.settingsDataStore.edit { it[Keys.BACKGROUND_COLOR_HEX] = value }
    }

    suspend fun setDailyRandomBackground(value: Boolean) = setBoolean(Keys.DAILY_RANDOM_BACKGROUND, value)
    suspend fun setHideIconBackgrounds(value: Boolean) = setBoolean(Keys.HIDE_ICON_BACKGROUNDS, value)
    suspend fun setInvertContrast(value: Boolean) = setBoolean(Keys.INVERT_CONTRAST, value)

    /**
     * Rolls a new background colour if [today] isn't the day the current one was picked. Both the
     * colour and the date are written together so a restart on the same day keeps the same colour.
     *
     * @return true if a new colour was applied.
     */
    suspend fun applyDailyRandomColorIfDue(today: String): Boolean {
        var applied = false
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.BACKGROUND_COLOR_DATE] == today) return@edit
            prefs[Keys.BACKGROUND_COLOR_DATE] = today
            prefs[Keys.BACKGROUND_COLOR_HEX] = randomPleasantColorHex()
            applied = true
        }
        return applied
    }

    suspend fun setCrossfadeMs(value: Int) {
        val snapped = (Math.round(value / CROSSFADE_STEP_MS.toFloat()) * CROSSFADE_STEP_MS)
            .coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)
        context.settingsDataStore.edit { it[Keys.CROSSFADE_MS] = snapped }
    }

    suspend fun setShuffleSeed(value: String) {
        context.settingsDataStore.edit { it[Keys.SHUFFLE_SEED] = value }
    }

    /** Regenerates the seed. Callers must check [shuffleSeedLocked] first; a locked seed cannot be regenerated. */
    suspend fun regenerateShuffleSeed(): String {
        val newSeed = generateShuffleSeed()
        setShuffleSeed(newSeed)
        return newSeed
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    companion object {
        private const val SEED_LENGTH = 15
        private const val SEED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        fun generateShuffleSeed(): String =
            (1..SEED_LENGTH).map { SEED_CHARS[Random.nextInt(SEED_CHARS.length)] }.joinToString("")

        /**
         * A random colour that's still pleasant to stare at: full random RGB lands on muddy
         * mid-greys most of the time, so this picks a random hue at a fixed, fairly deep
         * saturation and brightness instead.
         */
        fun randomPleasantColorHex(): String {
            val hue = Random.nextInt(360).toFloat()
            val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.35f))
            return String.format("#%06X", rgb and 0xFFFFFF)
        }
    }
}
