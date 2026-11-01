@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine
import io.github.parthibdebnath.openplayer.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts [PlaybackService] whenever there's playback for it to carry, for the life of the process.
 *
 * The service used to be started only while the app was on screen and could post notifications,
 * from the ViewModel. Music that went on playing in the background, or was started there - a call
 * ending, focus coming back - then played with no foreground service at all, and the system was
 * free to freeze or kill it. A media notification doesn't need the notification permission
 * (Android 13 exempts media sessions), so nothing stands in the way of always having one.
 *
 * Stopping is the service's own business; see [PlaybackService.watchForStop]. A headset button
 * pressed while it isn't running reaches Media3's MediaButtonReceiver, which starts it itself.
 */
object PlaybackServiceStarter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false
    private val appVisible = MutableStateFlow(false)

    /** Whether the app is on screen, where the service may be started without a foreground start. */
    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
    }

    /** Main thread only. Safe to call more than once. */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        val engine = PlaybackEngine.get(app)
        val keepWhenPaused = SettingsRepository(app).notificationMediaControl
        scope.launch {
            combine(
                engine.uiState.map { Playback(it.currentSong != null, it.isPlaying) }.distinctUntilChanged(),
                PlaybackService.running,
                appVisible,
                keepWhenPaused,
            ) { playback, running, visible, keep -> Inputs(playback, running, visible, keep) }
                .distinctUntilChanged()
                .collect { start(app, it) }
        }
    }

    private data class Playback(val hasSong: Boolean, val playing: Boolean)

    private data class Inputs(val playback: Playback, val running: Boolean, val visible: Boolean, val keepWhenPaused: Boolean)

    private fun start(app: Context, inputs: Inputs) {
        if (inputs.running || !inputs.playback.hasSong) return
        val playing = inputs.playback.playing
        // Paused with the app open, the session is still wanted for the lock screen and headset -
        // unless the notification is set to go when paused, which would only stop it again.
        if (!playing && !(inputs.visible && inputs.keepWhenPaused)) return
        val intent = Intent(app, PlaybackService::class.java)
        runCatching {
            if (inputs.visible) {
                // On screen: an ordinary start, and Media3 moves it to the foreground once it
                // plays - no five-second deadline to meet.
                app.startService(intent)
            } else {
                // Playing in the background with no service: this is the case that matters.
                // Media3 puts it in the foreground as soon as its session sees the player playing.
                ContextCompat.startForegroundService(app, intent)
            }
            Log.i("PlaybackServiceStarter", "Playback service started (visible=${inputs.visible}, playing=$playing)")
        }.onFailure { Log.w("PlaybackServiceStarter", "Could not start the playback service", it) }
    }
}
