@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.parthibdebnath.openplayer.MainActivity
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.core.app.ServiceCompat
import io.github.parthibdebnath.openplayer.data.repository.PlaylistRepository
import io.github.parthibdebnath.openplayer.data.settings.SettingsRepository
import io.github.parthibdebnath.openplayer.util.runSuspendCatching
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wraps the [PlaybackEngine]'s current player in a MediaSession purely for system integration: the
 * notification media controls, lock-screen controls, and Bluetooth/headset media buttons. The
 * Compose UI does not go through this service or a MediaController - it talks to [PlaybackEngine]
 * directly since everything runs in the same process.
 *
 * The engine owns two players so crossfades can overlap, and which one is live changes each time a
 * fade completes, so the session is repointed at whichever is currently active.
 *
 * Media3 owns the notification and the service's foreground state entirely. An earlier version
 * posted its own placeholder notification from `onCreate` to beat the foreground-service start
 * deadline; that left the service holding two notification ids and fighting Media3 for foreground
 * state, and when that manual `startForeground` failed the session came up broken with playback
 * stopped. It's started by [PlaybackServiceStarter] whenever something plays - or by Media3's
 * MediaButtonReceiver for a headset button - and Media3 moves it to the foreground as soon as
 * its player plays.
 *
 * Started from cold by a headset button, nothing has read the saved queue back yet, so this does
 * it; the engine holds the button's play until the queue is there.
 *
 * Media3 keeps the service in the foreground for a while after playback pauses (ten minutes by
 * default). On 1.4 it dropped out of the foreground the instant playback paused, and Android then
 * stopped the idle service about a minute later - taking the session and the notification with it,
 * so a song paused from the notification simply vanished from the shade. The same churn, a
 * foreground stop and restart with the notification re-posted, happened on every interruption
 * from another app's sounds.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        try {
            val engine = PlaybackEngine.get(applicationContext)
            val session = MediaSession.Builder(this, EngineSessionPlayer(engine.activePlayer.value, engine))
                // Without this, tapping the notification does nothing.
                .setSessionActivity(openAppIntent())
                .build()
            mediaSession = session

            // Register the session with the service explicitly.
            //
            // This is the reason no media notification ever appeared. A MediaSessionService only
            // posts notifications for sessions it has been told about, and the one returned from
            // onGetSession is added automatically *when a MediaController connects*. This app has
            // no controller - the UI drives PlaybackEngine directly, in-process - so onGetSession
            // was never called, the session was never added, and the service had nothing to
            // build a notification from.
            if (!isSessionAdded(session)) {
                addSession(session)
                Log.i("PlaybackService", "Media session registered with the service")
            }
            serviceScope.launch {
                runSuspendCatching {
                    engine.restoreIfNeeded(
                        lookup = { ids -> PlaylistRepository.get(applicationContext).getSongsByIds(ids) },
                        // A headset button isn't opening the app, so "Restart song on open" stays out of it.
                        restartFromBeginning = false,
                    )
                }.onFailure { Log.w("PlaybackService", "Could not restore the queue", it) }
            }
            watchForStop(engine)
            serviceScope.launch {
                engine.activePlayer.collect { player ->
                    // Only on a real swap: activePlayer is a StateFlow, so it replays the current
                    // value straight away, and reassigning for nothing makes Media3 tear the
                    // session's player down and re-attach it.
                    val current = mediaSession?.player
                    if (current is EngineSessionPlayer && current.wrappedPlayer === player) return@collect
                    runCatching { mediaSession?.player = EngineSessionPlayer(player, engine) }
                        .onFailure { Log.e("PlaybackService", "Could not repoint session player", it) }
                }
            }
        } catch (t: Throwable) {
            Log.e("PlaybackService", "onCreate failed", t)
        }
    }

    /**
     * Ends the service when there's nothing for it to do: no song loaded, or - with "Notification
     * media controls" switched off - a pause the listener chose. That setting can't mean "no
     * notification" any more, since a media service in the foreground has to show one while it
     * plays; it now means the notification lasts only as long as the music, gone as soon as it's
     * paused. A pause forced by a call or another app's sound doesn't count: the music comes back
     * by itself, and it can only do that from the foreground. Headset buttons keep working after
     * a stop, through Media3's MediaButtonReceiver, which starts the service again.
     */
    private fun watchForStop(engine: PlaybackEngine) {
        val keepWhenPaused = SettingsRepository(applicationContext).notificationMediaControl
        serviceScope.launch {
            combine(engine.uiState, keepWhenPaused) { state, keep ->
                val idle = state.currentSong == null && engine.restoreDone
                val pausedForGood = !keep && state.currentSong != null && !state.isPlaying && !engine.resumesByItself
                idle || pausedForGood
            }
                .distinctUntilChanged()
                .collectLatest { shouldStop ->
                    if (!shouldStop) return@collectLatest
                    // A moment's grace: a pause that's immediately undone, or a song changing over,
                    // shouldn't take the notification down and put it straight back up.
                    delay(STOP_GRACE_MS)
                    Log.i("PlaybackService", "Nothing left to carry - stopping")
                    ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
        }
    }

    private fun openAppIntent(): PendingIntent {
        val launch = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Media3 posts and updates the media notification through here. Wrapped so a failure is logged
     * rather than silently leaving an empty shade with no indication why.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (t: Throwable) {
            Log.e("PlaybackService", "Could not post the media notification", t)
        }
    }

    override fun onDestroy() {
        _running.value = false
        serviceScope.cancel()
        mediaSession?.let { session ->
            runCatching { removeSession(session) }
            session.release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val STOP_GRACE_MS = 1_500L

        private val _running = MutableStateFlow(false)

        /**
         * Whether the service actually exists. The system can stop it on its own - after a long
         * pause in the background, or under memory pressure - so whoever starts it has to check
         * this rather than remember having started it.
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
