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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
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

    /** Off: a song plays exactly as recorded, silences included. */
    const val SKIP_SILENCE = false

    /** Off: every song plays at the level it was mastered at. */
    const val NORMALIZE_VOLUME = false

    /** On = tracks of 20 minutes or more pick up where they were left off. */
    const val REMEMBER_LONG_TRACKS = true

    val HAPTIC_STRENGTH = HapticStrength.LIGHT

    /** Off: the screen times out as usual. On only matters while the Play screen is up and playing. */
    const val KEEP_SCREEN_ON = false

    /** On = a hidden backup of the library and settings is kept in each playlist folder. */
    const val BACKUP_TO_FOLDERS = true
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
    val skipSilence: Boolean = SettingsDefaults.SKIP_SILENCE,
    val normalizeVolume: Boolean = SettingsDefaults.NORMALIZE_VOLUME,
    val rememberLongTracks: Boolean = SettingsDefaults.REMEMBER_LONG_TRACKS,
    val hapticStrength: HapticStrength = SettingsDefaults.HAPTIC_STRENGTH,
    val keepScreenOn: Boolean = SettingsDefaults.KEEP_SCREEN_ON,
    val backupToFolders: Boolean = SettingsDefaults.BACKUP_TO_FOLDERS,
)

/**
 * The app's settings, kept in a preferences DataStore. Every key is stored under a fixed name and
 * read with a default, so settings carry over unchanged through restarts and app updates, and a
 * setting added in a later version simply starts at its default.
 */
class SettingsRepository(private val context: Context) {
    private object Keys {
        /**
         * Each setting's stored type, by name. Declared first, so the keys below can register
         * themselves as they're created. An import uses it to turn away a value of the wrong
         * type: stored under a known name, it would throw on every read and crash each launch.
         */
        val types = HashMap<String, StoredType>()

        private fun bool(name: String) = booleanPreferencesKey(name).also { types[name] = StoredType.BOOLEAN }
        private fun int(name: String) = intPreferencesKey(name).also { types[name] = StoredType.INT }
        private fun text(name: String) = stringPreferencesKey(name).also { types[name] = StoredType.STRING }

        val NOTIFICATION_MEDIA_CONTROL = bool("notification_media_control")
        val REQUIRE_AUTH_TO_OPEN = bool("require_auth_to_open")
        val REQUIRE_AUTH_TO_DELETE = bool("require_auth_to_delete")
        val TWO_FINGER_BRIGHTNESS_GESTURE = bool("two_finger_brightness_gesture")
        val THREE_FINGER_VOLUME_GESTURE = bool("three_finger_volume_gesture")
        val GLASS_THEMING = bool("glass_theming")
        val TWENTY_FOUR_HOUR_CLOCK = bool("twenty_four_hour_clock")
        val SHUFFLE_SEED = text("shuffle_seed")
        val SHUFFLE_SEED_LOCKED = bool("shuffle_seed_locked")
        val CROSSFADE_MS = int("crossfade_ms")
        val BACKGROUND_PLAYBACK = bool("background_playback")
        val BACKGROUND_COLOR_HEX = text("background_color_hex")
        val DAILY_RANDOM_BACKGROUND = bool("daily_random_background")
        val BACKGROUND_COLOR_DATE = text("background_color_date")
        val HIDE_ICON_BACKGROUNDS = bool("hide_icon_backgrounds")
        val SCROLL_LONG_TITLES = bool("scroll_long_titles")
        val RESTART_SONG_ON_OPEN = bool("restart_song_on_open")
        val SONG_SORT = text("song_sort")
        val OTHER_APP_SOUND = text("other_app_sound")
        val SKIP_SILENCE = bool("skip_silence")
        val NORMALIZE_VOLUME = bool("normalize_volume")
        val REMEMBER_LONG_TRACKS = bool("remember_long_tracks")
        val HAPTIC_STRENGTH = text("haptic_strength")
        val KEEP_SCREEN_ON = bool("keep_screen_on")
        val BACKUP_TO_FOLDERS = bool("backup_to_folders")
        val UPCOMING_EXPANDED = bool("upcoming_expanded")

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
            skipSilence = p[Keys.SKIP_SILENCE] ?: SettingsDefaults.SKIP_SILENCE,
            normalizeVolume = p[Keys.NORMALIZE_VOLUME] ?: SettingsDefaults.NORMALIZE_VOLUME,
            rememberLongTracks = p[Keys.REMEMBER_LONG_TRACKS] ?: SettingsDefaults.REMEMBER_LONG_TRACKS,
            hapticStrength = HapticStrength.fromName(p[Keys.HAPTIC_STRENGTH]),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: SettingsDefaults.KEEP_SCREEN_ON,
            backupToFolders = p[Keys.BACKUP_TO_FOLDERS] ?: SettingsDefaults.BACKUP_TO_FOLDERS,
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
    val skipSilence: Flow<Boolean> = setting { it[Keys.SKIP_SILENCE] ?: SettingsDefaults.SKIP_SILENCE }
    val normalizeVolume: Flow<Boolean> = setting { it[Keys.NORMALIZE_VOLUME] ?: SettingsDefaults.NORMALIZE_VOLUME }
    val rememberLongTracks: Flow<Boolean> = setting { it[Keys.REMEMBER_LONG_TRACKS] ?: SettingsDefaults.REMEMBER_LONG_TRACKS }

    /** Whether the Play screen's list of upcoming songs is open, so it's the same next time. */
    val upcomingExpanded: Flow<Boolean> = setting { it[Keys.UPCOMING_EXPANDED] ?: false }

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
    suspend fun setSkipSilence(value: Boolean) = set(Keys.SKIP_SILENCE, value)
    suspend fun setNormalizeVolume(value: Boolean) = set(Keys.NORMALIZE_VOLUME, value)
    suspend fun setRememberLongTracks(value: Boolean) = set(Keys.REMEMBER_LONG_TRACKS, value)
    suspend fun setHapticStrength(value: HapticStrength) = set(Keys.HAPTIC_STRENGTH, value.name)
    suspend fun setKeepScreenOn(value: Boolean) = set(Keys.KEEP_SCREEN_ON, value)
    suspend fun setBackupToFolders(value: Boolean) = set(Keys.BACKUP_TO_FOLDERS, value)
    suspend fun setUpcomingExpanded(value: Boolean) = set(Keys.UPCOMING_EXPANDED, value)

    /**
     * Every stored setting by its stored name, for a backup. Read generically rather than field
     * by field, so a setting added later is backed up without anyone remembering to list it.
     */
    suspend fun exportAll(): Map<String, JsonPrimitive> {
        val p = prefs.first()
        return p.asMap().mapNotNull { (key, value) ->
            val primitive = when (value) {
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                else -> null
            }
            primitive?.let { key.name to it }
        }.toMap()
    }

    /**
     * Puts back settings from a backup. Each value is stored under the type it was saved with -
     * every setting here is a flag, a whole number or text - and names this version doesn't know
     * are still kept, so a backup from a newer version loses nothing on its way through.
     *
     * Backups are read from shared music folders that other apps can write to, so they're not
     * trusted: a known setting only takes a value of its own type, and a backup can switch the
     * app lock on but never off.
     */
    suspend fun importAll(values: Map<String, JsonPrimitive>) {
        context.settingsDataStore.edit { prefs ->
            values.forEach { (name, value) ->
                val type = when {
                    value.isString -> StoredType.STRING
                    value.booleanOrNull != null -> StoredType.BOOLEAN
                    value.intOrNull != null -> StoredType.INT
                    else -> return@forEach
                }
                val expected = Keys.types[name]
                if (expected != null && expected != type) return@forEach
                if (name in LOCK_SETTINGS && value.booleanOrNull == false) return@forEach
                when (type) {
                    StoredType.STRING -> prefs[stringPreferencesKey(name)] = value.content
                    StoredType.BOOLEAN -> prefs[booleanPreferencesKey(name)] = value.boolean
                    StoredType.INT -> prefs[intPreferencesKey(name)] = value.int
                }
            }
            Keys.RETIRED.forEach { prefs.remove(it) }
        }
    }

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

    private enum class StoredType { BOOLEAN, INT, STRING }

    companion object {
        /** Settings an import may turn on but never off; see [importAll]. */
        private val LOCK_SETTINGS = setOf("require_auth_to_open", "require_auth_to_delete")

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
