package io.github.parthibdebnath.openplayer.data.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.parthibdebnath.openplayer.data.model.SongSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.random.Random

// A corrupt file (a write cut off by a crash or a full disk) is replaced with defaults rather than
// throwing on every read, which would otherwise crash the app on each launch until it was
// reinstalled.
private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
    const val GLASS_THEMING = false
    /** Used only where there's no phone setting to follow; see [SettingsRepository.snapshot]. */
    const val TWENTY_FOUR_HOUR_CLOCK = false
    const val SHUFFLE_SEED_LOCKED = false
    const val CROSSFADE_MS = 0
    const val BACKGROUND_PLAYBACK = true
    const val BACKGROUND_COLOR_HEX = "#000000"
    const val DAILY_RANDOM_BACKGROUND = false

    /** On = icons sit bare, with no circle behind them. Glass theming overrides it while it's on. */
    const val HIDE_ICON_BACKGROUNDS = true

    /** On = a title too long for its space scrolls; off = it stays put and is cut off. */
    const val SCROLL_LONG_TITLES = true

    /** On = the song you left off on starts again from 0:00 when the app is reopened. */
    const val RESTART_SONG_ON_OPEN = false

    /** Lowering rather than pausing, so a game or a notification doesn't stop the music. */
    val OTHER_APP_SOUND = OtherAppSound.LOWER_VOLUME
}

/** Every setting at once, as the UI reads them. */
data class SettingsSnapshot(
    val notificationMediaControl: Boolean = SettingsDefaults.NOTIFICATION_MEDIA_CONTROL,
    val requireAuthToOpen: Boolean = SettingsDefaults.REQUIRE_AUTH_TO_OPEN,
    val requireAuthToDelete: Boolean = SettingsDefaults.REQUIRE_AUTH_TO_DELETE,
    val twoFingerBrightnessGesture: Boolean = SettingsDefaults.TWO_FINGER_BRIGHTNESS_GESTURE,
    val threeFingerVolumeGesture: Boolean = SettingsDefaults.THREE_FINGER_VOLUME_GESTURE,
    val glassTheming: Boolean = SettingsDefaults.GLASS_THEMING,
    val twentyFourHourClock: Boolean = SettingsDefaults.TWENTY_FOUR_HOUR_CLOCK,
    val shuffleSeed: String = "",
    val shuffleSeedLocked: Boolean = SettingsDefaults.SHUFFLE_SEED_LOCKED,
    val crossfadeMs: Int = SettingsDefaults.CROSSFADE_MS,
    val backgroundPlayback: Boolean = SettingsDefaults.BACKGROUND_PLAYBACK,
    val backgroundColorHex: String = SettingsDefaults.BACKGROUND_COLOR_HEX,
    val dailyRandomBackground: Boolean = SettingsDefaults.DAILY_RANDOM_BACKGROUND,
    val hideIconBackgrounds: Boolean = SettingsDefaults.HIDE_ICON_BACKGROUNDS,
    val scrollLongTitles: Boolean = SettingsDefaults.SCROLL_LONG_TITLES,
    val restartSongOnOpen: Boolean = SettingsDefaults.RESTART_SONG_ON_OPEN,
    val songSort: SongSort = SongSort.NAME,
    val otherAppSound: OtherAppSound = SettingsDefaults.OTHER_APP_SOUND,
)

/**
 * The app's settings, kept in a preferences DataStore. Every key is stored under a fixed name and
 * read with a default, so settings carry over unchanged through restarts and app updates, and a
 * setting added in a later version simply starts at its default.
 */
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
        val SCROLL_LONG_TITLES = booleanPreferencesKey("scroll_long_titles")
        val RESTART_SONG_ON_OPEN = booleanPreferencesKey("restart_song_on_open")
        val SONG_SORT = stringPreferencesKey("song_sort")
        val OTHER_APP_SOUND = stringPreferencesKey("other_app_sound")

        /** Settings that no longer exist, cleared out by [prepare]. */
        val RETIRED = listOf(booleanPreferencesKey("invert_contrast"))
    }

    // A read failure (as opposed to a corrupt file, handled above) falls back to the defaults for
    // that read instead of crashing whatever is collecting.
    private val prefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    private fun <T> setting(read: (Preferences) -> T): Flow<T> = prefs.map(read).distinctUntilChanged()

    val snapshot: Flow<SettingsSnapshot> = setting { p ->
        SettingsSnapshot(
            notificationMediaControl = p[Keys.NOTIFICATION_MEDIA_CONTROL] ?: SettingsDefaults.NOTIFICATION_MEDIA_CONTROL,
            requireAuthToOpen = p[Keys.REQUIRE_AUTH_TO_OPEN] ?: SettingsDefaults.REQUIRE_AUTH_TO_OPEN,
            requireAuthToDelete = p[Keys.REQUIRE_AUTH_TO_DELETE] ?: SettingsDefaults.REQUIRE_AUTH_TO_DELETE,
            twoFingerBrightnessGesture = p[Keys.TWO_FINGER_BRIGHTNESS_GESTURE] ?: SettingsDefaults.TWO_FINGER_BRIGHTNESS_GESTURE,
            threeFingerVolumeGesture = p[Keys.THREE_FINGER_VOLUME_GESTURE] ?: SettingsDefaults.THREE_FINGER_VOLUME_GESTURE,
            glassTheming = p[Keys.GLASS_THEMING] ?: SettingsDefaults.GLASS_THEMING,
            // Until it's been set in the app, the clock matches the phone's own 12/24-hour choice
            // rather than always starting in 12-hour, which looked wrong on 24-hour phones.
            twentyFourHourClock = p[Keys.TWENTY_FOUR_HOUR_CLOCK] ?: DateFormat.is24HourFormat(context),
            shuffleSeed = p[Keys.SHUFFLE_SEED] ?: "",
            shuffleSeedLocked = p[Keys.SHUFFLE_SEED_LOCKED] ?: SettingsDefaults.SHUFFLE_SEED_LOCKED,
            crossfadeMs = crossfadeOf(p),
            backgroundPlayback = p[Keys.BACKGROUND_PLAYBACK] ?: SettingsDefaults.BACKGROUND_PLAYBACK,
            backgroundColorHex = p[Keys.BACKGROUND_COLOR_HEX] ?: SettingsDefaults.BACKGROUND_COLOR_HEX,
            dailyRandomBackground = p[Keys.DAILY_RANDOM_BACKGROUND] ?: SettingsDefaults.DAILY_RANDOM_BACKGROUND,
            hideIconBackgrounds = p[Keys.HIDE_ICON_BACKGROUNDS] ?: SettingsDefaults.HIDE_ICON_BACKGROUNDS,
            scrollLongTitles = p[Keys.SCROLL_LONG_TITLES] ?: SettingsDefaults.SCROLL_LONG_TITLES,
            restartSongOnOpen = p[Keys.RESTART_SONG_ON_OPEN] ?: SettingsDefaults.RESTART_SONG_ON_OPEN,
            songSort = SongSort.fromName(p[Keys.SONG_SORT]),
            otherAppSound = OtherAppSound.fromName(p[Keys.OTHER_APP_SOUND]),
        )
    }

    // The few settings the playback side reacts to on their own, so it isn't woken by every
    // unrelated change.
    val notificationMediaControl: Flow<Boolean> =
        setting { it[Keys.NOTIFICATION_MEDIA_CONTROL] ?: SettingsDefaults.NOTIFICATION_MEDIA_CONTROL }
    val requireAuthToOpen: Flow<Boolean> =
        setting { it[Keys.REQUIRE_AUTH_TO_OPEN] ?: SettingsDefaults.REQUIRE_AUTH_TO_OPEN }
    val restartSongOnOpen: Flow<Boolean> =
        setting { it[Keys.RESTART_SONG_ON_OPEN] ?: SettingsDefaults.RESTART_SONG_ON_OPEN }
    val crossfadeMs: Flow<Int> = setting(::crossfadeOf)
    val songSort: Flow<SongSort> = setting { SongSort.fromName(it[Keys.SONG_SORT]) }
    val otherAppSound: Flow<OtherAppSound> = setting { OtherAppSound.fromName(it[Keys.OTHER_APP_SOUND]) }

    /**
     * Empty only until [prepare] has run once. Falling back to a freshly generated seed here
     * instead meant every read of an unset seed produced a different one, so the shuffle order
     * changed under the player each time any other setting was touched.
     */
    val shuffleSeed: Flow<String> = setting { it[Keys.SHUFFLE_SEED] ?: "" }

    private fun crossfadeOf(p: Preferences): Int =
        (p[Keys.CROSSFADE_MS] ?: SettingsDefaults.CROSSFADE_MS).coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)

    suspend fun setNotificationMediaControl(value: Boolean) = set(Keys.NOTIFICATION_MEDIA_CONTROL, value)
    suspend fun setRequireAuthToOpen(value: Boolean) = set(Keys.REQUIRE_AUTH_TO_OPEN, value)
    suspend fun setRequireAuthToDelete(value: Boolean) = set(Keys.REQUIRE_AUTH_TO_DELETE, value)
    suspend fun setTwoFingerBrightnessGesture(value: Boolean) = set(Keys.TWO_FINGER_BRIGHTNESS_GESTURE, value)
    suspend fun setThreeFingerVolumeGesture(value: Boolean) = set(Keys.THREE_FINGER_VOLUME_GESTURE, value)
    suspend fun setGlassTheming(value: Boolean) = set(Keys.GLASS_THEMING, value)
    suspend fun setTwentyFourHourClock(value: Boolean) = set(Keys.TWENTY_FOUR_HOUR_CLOCK, value)
    suspend fun setShuffleSeedLocked(value: Boolean) = set(Keys.SHUFFLE_SEED_LOCKED, value)
    suspend fun setBackgroundPlayback(value: Boolean) = set(Keys.BACKGROUND_PLAYBACK, value)
    suspend fun setBackgroundColorHex(value: String) = set(Keys.BACKGROUND_COLOR_HEX, value)
    suspend fun setDailyRandomBackground(value: Boolean) = set(Keys.DAILY_RANDOM_BACKGROUND, value)
    suspend fun setHideIconBackgrounds(value: Boolean) = set(Keys.HIDE_ICON_BACKGROUNDS, value)
    suspend fun setScrollLongTitles(value: Boolean) = set(Keys.SCROLL_LONG_TITLES, value)
    suspend fun setRestartSongOnOpen(value: Boolean) = set(Keys.RESTART_SONG_ON_OPEN, value)
    suspend fun setShuffleSeed(value: String) = set(Keys.SHUFFLE_SEED, value)
    suspend fun setSongSort(value: SongSort) = set(Keys.SONG_SORT, value.name)
    suspend fun setOtherAppSound(value: OtherAppSound) = set(Keys.OTHER_APP_SOUND, value.name)

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
        set(Keys.CROSSFADE_MS, snapped)
    }

    /**
     * Run once at startup: stores a shuffle seed on first launch, so there is one stable seed from
     * then on, and clears out settings that no longer exist.
     */
    suspend fun prepare() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.SHUFFLE_SEED].isNullOrEmpty()) prefs[Keys.SHUFFLE_SEED] = generateShuffleSeed()
            Keys.RETIRED.forEach { prefs.remove(it) }
        }
    }

    /** Regenerates the seed. Callers must check the seed lock first; a locked seed cannot be regenerated. */
    suspend fun regenerateShuffleSeed(): String {
        val newSeed = generateShuffleSeed()
        setShuffleSeed(newSeed)
        return newSeed
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
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
            return String.format(java.util.Locale.US, "#%06X", rgb and 0xFFFFFF)
        }
    }
}
