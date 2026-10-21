@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Headset and media buttons for when there's no media notification - "Notification media controls"
 * switched off, or notifications blocked for the app.
 *
 * Buttons reach an app through its media session, and the only session used to be the one inside
 * [PlaybackService], which exists to post the notification. So turning the notification off took
 * the buttons with it: play/pause on a headset did nothing. This is a session of its own, with no
 * service and so no notification, held for as long as a song is loaded. Kept at process level
 * rather than in the UI, so the buttons still work once the app is in the background.
 *
 * Only ever one of the two is live: while the service runs, its session handles the buttons and
 * this one is released, so the system never has two sessions from the app to choose between.
 */
object MediaButtonSession {
    private var session: MediaSession? = null
    private var follow: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Main thread only, like the engine it wraps. */
    fun start(context: Context) {
        if (session != null) return
        try {
            val engine = PlaybackEngine.get(context.applicationContext)
            val created = MediaSession.Builder(context.applicationContext, EngineSessionPlayer(engine.activePlayer.value, engine))
                // Distinct from the service's session, which may be starting up as this one is
                // released - two sessions with one id in a process is an error.
                .setId(SESSION_ID)
                .build()
            session = created
            follow = scope.launch {
                // Repointed whenever a crossfade hands playback to the other player, as the
                // service's session is.
                engine.activePlayer.collect { player ->
                    val current = session?.player
                    if (current is EngineSessionPlayer && current.wrappedPlayer === player) return@collect
                    runCatching { session?.player = EngineSessionPlayer(player, engine) }
                        .onFailure { Log.w("MediaButtonSession", "Could not repoint the session", it) }
                }
            }
        } catch (t: Throwable) {
            Log.e("MediaButtonSession", "Could not create the media button session", t)
            session = null
        }
    }

    fun stop() {
        follow?.cancel()
        follow = null
        session?.let { runCatching { it.release() } }
        session = null
    }

    private const val SESSION_ID = "media_buttons"
}
