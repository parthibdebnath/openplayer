package io.github.parthibdebnath.openplayer.render

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.parthibdebnath.openplayer.MainActivity
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine
import io.github.parthibdebnath.openplayer.audio.PlaybackUiState
import io.github.parthibdebnath.openplayer.data.db.AppDatabase
import io.github.parthibdebnath.openplayer.data.db.PlaylistEntity
import io.github.parthibdebnath.openplayer.data.db.SongEntity
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.settings.SettingsRepository
import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone

/**
 * Not a test in the usual sense: renders the real app - MainActivity, the real database and
 * settings - under Robolectric's native graphics, in every theme, and writes the frames out as
 * PNGs for review. Skipped unless an output directory is given:
 *
 *   ./gradlew testDebugUnitTest --tests '*ThemeRenderTest*' -PthemeRenderOut=/tmp/frames
 *
 * -PthemeRenderOnly=AQUA,LUNA limits it to some themes; -PthemeRenderVideo=true also records a
 * walkthrough of each theme, frame by frame at 30 fps, for stitching into a video.
 *
 * Playback can't run here (there's no audio decoding), so the engine's published state is set
 * directly to a song part-way through; everything else goes through the app's own code.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-port-420dpi", application = Application::class)
class ThemeRenderTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val outRoot = System.getProperty("themeRender.out").orEmpty()
    private val only = System.getProperty("themeRender.only").orEmpty()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private val video = System.getProperty("themeRender.video") == "true"

    private lateinit var songs: List<Song>
    private lateinit var playlistIds: List<Long>

    @Test
    fun renderThemes() {
        assumeTrue("no output directory given", outRoot.isNotEmpty())
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        setBattery(app, 82)
        io.github.parthibdebnath.openplayer.util.BatteryMonitor.start(app)
        seedLibrary(app)

        val themes = UiTheme.entries.filter { only.isEmpty() || it.name in only }
        // The first theme is set before launch, so the first frame is already themed.
        runBlocking { SettingsRepository(app).setUiTheme(themes.first()) }

        ActivityScenario.launch(MainActivity::class.java).use { launched ->
            scenario = launched
            compose.mainClock.autoAdvance = false
            settle(1500)
            for (theme in themes) {
                runBlocking { SettingsRepository(app).setUiTheme(theme) }
                settle(800)
                renderStills(theme)
                if (video) recordWalkthrough(theme)
            }
        }
    }

    // --- scenes -------------------------------------------------------------------------------

    private fun renderStills(theme: UiTheme) {
        val dir = File(outRoot, theme.name.lowercase()).apply { mkdirs() }
        goToTab("Play")
        publishPlayback(expandedHint = false)
        settle(900)
        shot(dir, "1_play")

        tapIfPresent("upcoming:toggle")
        settle(700)
        shot(dir, "2_play_upcoming")
        tapIfPresent("upcoming:toggle")
        settle(700)

        goToTab("File")
        settle(900)
        shot(dir, "3_playlists")

        openPlaylist("Late Night Drive")
        settle(2500)
        shot(dir, "4_songs")

        compose.onAllNodes(hasText("Glass Hearts", substring = true)).onFirstOrNull()?.let { longPress(it) }
        settle(700)
        println("THEMERENDER ${theme.name} menu items found: " + compose.onAllNodes(hasText("Play next")).fetchSemanticsNodes().size)
        shot(dir, "5_song_menu")
        // A confirmation: removing a song asks first.
        compose.onAllNodes(hasText("Remove")).onFirstOrNull()?.performClick()
        settle(800)
        shot(dir, "5b_confirm")
        tapIfPresent("dialog:dismiss")
        settle(500)

        goToTab("SFX")
        settle(900)
        shot(dir, "6_sfx_speed")
        compose.onAllNodes(hasText("Reverb")).onFirstOrNull()?.performClick()
        settle(900)
        shot(dir, "7_sfx_reverb")
        compose.onAllNodes(hasText("Speed")).onFirstOrNull()?.performClick()
        settle(400)

        goToTab("File")
        settle(400)
        tapIfPresent("nav:settings")
        settle(900)
        shot(dir, "8_settings")

        // The frame's own menu (a Start menu, an Apple menu...), where the skin has one.
        if (compose.onAllNodes(hasTestTag("frame:menu")).fetchSemanticsNodes().isNotEmpty()) {
            tapIfPresent("frame:menu")
            settle(700)
            shot(dir, "9_frame_menu")
            pressBack()
            settle(500)
        }
        // Back to the playlists for the next theme.
        tapIfPresent("nav:settings")
        settle(400)
        goToTab("Play")
        settle(600)
    }

    /**
     * A walkthrough: Play, the upcoming list opening, a song skipped, the tabs in turn, a
     * playlist opened and scrolled, the effects. Every frame is 1/30 s of the app's own clock.
     */
    private fun recordWalkthrough(theme: UiTheme) {
        val dir = File(outRoot, "video_" + theme.name.lowercase()).apply { mkdirs() }
        var frame = 0
        fun run(ms: Long) {
            val frames = (ms / FRAME_MS).toInt().coerceAtLeast(1)
            repeat(frames) {
                compose.mainClock.advanceTimeBy(FRAME_MS)
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(FRAME_MS))
                capture(File(dir, "f%05d.png".format(frame++)))
            }
        }
        goToTab("Play")
        publishPlayback(expandedHint = false)
        run(1200)
        tapIfPresent("upcoming:toggle"); run(1300)
        tapIfPresent("upcoming:toggle"); run(900)
        // Playback moving along a few seconds.
        repeat(20) { i -> publishPlayback(positionOffsetMs = i * 250L); run(66) }
        goToTab("File"); run(1300)
        openPlaylist("Late Night Drive")
        run(1200)
        compose.onAllNodes(hasTestTag("songs:list")).onFirstOrNull()?.performTouchInput {
            swipe(Offset(centerX, bottom - 80f), Offset(centerX, top + 80f), 500)
        }
        run(1200)
        goToTab("SFX"); run(1200)
        compose.onAllNodes(hasText("Reverb")).onFirstOrNull()?.performClick(); run(1200)
        compose.onAllNodes(hasText("Pitch")).onFirstOrNull()?.performClick(); run(900)
        compose.onAllNodes(hasText("Speed")).onFirstOrNull()?.performClick(); run(500)
        goToTab("File"); run(500)
        goToTab("Play"); run(1500)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun goToTab(name: String) {
        val node = compose.onAllNodes(hasTestTag("tab:$name")).onFirstOrNull()
            ?: compose.onAllNodes(hasText(name)).onFirstOrNull()
        node?.performClick()
    }

    /** Held long enough for a long press, on the app's own (paused) clock. */
    private fun longPress(node: SemanticsNodeInteraction) {
        node.performTouchInput { down(center) }
        settle(900)
        node.performTouchInput { up() }
    }

    private fun openPlaylist(name: String) {
        compose.onAllNodes(hasTestTag("playlist:$name")).onFirstOrNull()?.performClick()
    }

    private fun tapIfPresent(tag: String) {
        compose.onAllNodes(hasTestTag(tag)).onFirstOrNull()?.performClick()
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirstOrNull(): SemanticsNodeInteraction? =
        if (fetchSemanticsNodes().isNotEmpty()) this[0] else null

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            compose.mainClock.advanceTimeBy(FRAME_MS)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(FRAME_MS))
            // Database reads run on real background threads; give them real time to land.
            Thread.sleep(4)
            left -= FRAME_MS
        }
    }

    private fun shot(dir: File, name: String) = capture(File(dir, "$name.png"))

    private fun capture(file: File) {
        shadowOf(Looper.getMainLooper()).idle()
        // Every window - the activity's, and any popup or dialog over it - composited in place.
        com.github.takahirom.roborazzi.captureScreenRoboImage(file.absolutePath)
    }

    private fun setBattery(app: Application, percent: Int) {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, percent)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
        @Suppress("DEPRECATION") app.sendStickyBroadcast(intent)
    }

    /** Six playlists of made-up songs, written straight into the app's own database. */
    private fun seedLibrary(app: Application) = runBlocking {
        val db = AppDatabase.get(app)
        val ids = mutableListOf<Long>()
        val all = mutableListOf<SongEntity>()
        DemoLibrary.playlists.forEachIndexed { index, (name, tracks) ->
            val id = db.playlistDao().insert(
                PlaylistEntity(displayName = name, folderUri = "content://demo/tree/$index", number = index + 1, createdAt = index.toLong()),
            )
            ids += id
            val rows = tracks.mapIndexed { i, (title, artist, seconds) ->
                SongEntity(
                    playlistId = id,
                    documentUri = "content://demo/doc/$index/$i",
                    fileName = "$title.mp3",
                    displayName = title,
                    artist = artist,
                    durationMs = seconds * 1000L,
                    sizeBytes = 4_000_000L + i,
                    lastModified = 1L,
                    sortIndex = i.toLong(),
                    isFavourite = (index == 0 && i % 4 == 1) || (index == 2 && i % 5 == 0),
                )
            }
            db.songDao().insertAll(rows)
        }
        playlistIds = ids
        val stored = db.songDao().getByPlaylist(ids.first())
        songs = stored.map {
            Song(it.id, it.playlistId, it.documentUri, it.fileName, it.displayName, it.artist, it.durationMs, it.sizeBytes, it.lastModified, it.sortIndex)
        }
        // A tag, so the playlists screen shows one.
        stored.take(6).forEach { db.tagDao().addTag(it.id, "Summer 2008") }
    }

    private fun publishPlayback(expandedHint: Boolean = false, positionOffsetMs: Long = 0L) {
        val engine = PlaybackEngine.get(ApplicationProvider.getApplicationContext())
        val field = PlaybackEngine::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(engine) as MutableStateFlow<PlaybackUiState>
        val current = songs[3]
        flow.value = PlaybackUiState(
            currentSong = current,
            playlistLabel = "Late Night Drive",
            isPlaying = true,
            positionMs = 102_000L + positionOffsetMs,
            durationMs = current.durationMs,
            shuffleOn = true,
            upcoming = songs.drop(4).take(5),
        )
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}

