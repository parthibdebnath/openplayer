package com.musicplayer.app.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.media.AudioAttributes as SystemAudioAttributes

/**
 * Owns one audio focus request for the whole engine.
 *
 * [PlaybackEngine] runs two players so tracks can overlap during a crossfade, and ExoPlayer's
 * built-in focus handling is per-player: the incoming player requesting focus would revoke the
 * outgoing one's and pause it halfway through the fade. So both players are built with their own
 * handling switched off and focus is held here, once, for as long as anything is playing.
 *
 * Holding real focus also matters for its own sake - several OEM audio policies mute or tear down
 * a stream that plays without ever having asked for it.
 */
class AudioFocusController(
    context: Context,
    /** Pauses if something was actually playing; returns whether it did. */
    private val onPauseForFocusLoss: () -> Boolean,
    private val onResumeAfterFocusLoss: () -> Unit,
    private val onDuckingChanged: (Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var holdsFocus = false
    private var pausedForTransientLoss = false
    private var request: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                holdsFocus = false
                pausedForTransientLoss = false
                onDuckingChanged(false)
                onPauseForFocusLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onDuckingChanged(false)
                // Only arm the resume if this actually interrupted playback. Focus is still held
                // while paused, so a voice search or an alarm hands it back afterwards - and
                // resuming on that would start music the user had deliberately stopped.
                pausedForTransientLoss = onPauseForFocusLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuckingChanged(true)

            AudioManager.AUDIOFOCUS_GAIN -> {
                holdsFocus = true
                onDuckingChanged(false)
                if (pausedForTransientLoss) {
                    pausedForTransientLoss = false
                    onResumeAfterFocusLoss()
                }
            }
        }
    }

    /**
     * Called when playback is stopped deliberately - by the user, the sleep timer, or an output
     * change. Cancels any pending auto-resume, so a deliberate pause during an interruption isn't
     * undone when focus comes back.
     */
    fun onPlaybackPausedDeliberately() {
        pausedForTransientLoss = false
    }

    /**
     * Asks for focus if we don't already hold it. Playback proceeds either way - a denial is
     * logged rather than used to block the user's own explicit request to play.
     */
    fun requestFocus() {
        if (holdsFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    SystemAudioAttributes.Builder()
                        .setUsage(SystemAudioAttributes.USAGE_MEDIA)
                        .setContentType(SystemAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!holdsFocus) Log.w("AudioFocusController", "Audio focus not granted (result=$result)")
    }

    fun abandonFocus() {
        if (!holdsFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        holdsFocus = false
        pausedForTransientLoss = false
    }
}
