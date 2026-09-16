package com.musicplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.app.MainActivity
import com.musicplayer.app.audio.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * stopped. The service is now started only while the app is in the foreground with a song already
 * loaded, so Media3 promotes it itself and no deadline applies.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
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
        serviceScope.cancel()
        mediaSession?.let { session ->
            runCatching { removeSession(session) }
            session.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
