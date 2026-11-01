package io.github.parthibdebnath.openplayer.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.parthibdebnath.openplayer.data.settings.OtherAppSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.media.AudioAttributes as SystemAudioAttributes

/** What asking for audio focus came to; see [AudioFocusController.requestFocus]. */
enum class FocusResult {
    /** Held: go ahead and play. */
    GRANTED,

    /** Not yet - a call, usually. Playback starts by itself when focus comes (see onDelayedGain). */
    DELAYED,

    /** Refused: don't play. */
    DENIED,
}

/**
 * Owns one audio focus request for the whole engine, and decides what other apps' sounds do to
 * the music.
 *
 * [PlaybackEngine] runs two players so tracks can overlap during a crossfade, and ExoPlayer's
 * built-in focus handling is per-player: the incoming player requesting focus would revoke the
 * outgoing one's and pause it halfway through the fade. So both players are built with their own
 * handling switched off and focus is held here, once, for as long as anything is playing.
 *
 * Holding real focus also matters for its own sake - several OEM audio policies mute or tear down
 * a stream that plays without ever having asked for it.
 *
 * Short interruptions follow the user's [mode]. Losing focus for good is handled more carefully
 * than "pause and never come back", which is what every game that asks for permanent focus to
 * play a sound effect used to do to the music - see [startTakeoverCheck].
 */
class AudioFocusController(
    context: Context,
    private val scope: CoroutineScope,
    /** Pauses if something was actually playing; returns whether it did. */
    private val onPauseForFocusLoss: () -> Boolean,
    private val onResumeAfterFocusLoss: () -> Unit,
    private val onDuckingChanged: (Boolean) -> Unit,
    /** Whether any of our players is playing right now. */
    private val isPlaying: () -> Boolean,
    /** How many of our own players are producing sound, to tell them apart from other apps'. */
    private val ownActivePlayers: () -> Int,
    /** Another app has taken the audio over for good, and playback has been paused for it. */
    private val onTakenOver: () -> Unit,
    /** Focus asked for earlier and put off has now been granted: start what was asked for. */
    private val onDelayedGain: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var holdsFocus = false
    private var pausedForTransientLoss = false
    private var request: AudioFocusRequest? = null

    // Two separate reasons to be ducked, so one ending doesn't lift the other.
    private var duckedForTransient = false
    private var duckedForTakeoverCheck = false

    private var takeoverJob: Job? = null
    private var pausedForTakeoverCheck = false

    /** Play was pressed while focus could only be promised for later; see [FocusResult.DELAYED]. */
    private var startWhenGranted = false

    /**
     * Focus is lent out for a while - a call, an alarm, a voice search. Still ours on paper, so
     * [holdsFocus] stays true, but not ours to play over: play pressed now has to ask again.
     */
    private var lentOut = false

    /** What a short interruption from another app does; see [OtherAppSound]. */
    var mode: OtherAppSound = OtherAppSound.LOWER_VOLUME
        set(value) {
            field = value
            // Switching to "keep playing" mid-interruption shouldn't leave the music quiet until
            // the other app happens to finish.
            if (value == OtherAppSound.KEEP_PLAYING) setTransientDuck(false)
        }

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                holdsFocus = false
                lentOut = false
                pausedForTransientLoss = false
                setTransientDuck(false)
                startTakeoverCheck()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                lentOut = true
                onTransientLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (mode != OtherAppSound.KEEP_PLAYING) setTransientDuck(true)

            AudioManager.AUDIOFOCUS_GAIN -> {
                holdsFocus = true
                lentOut = false
                setTransientDuck(false)
                when {
                    startWhenGranted -> {
                        startWhenGranted = false
                        pausedForTransientLoss = false
                        onDelayedGain()
                    }
                    pausedForTransientLoss -> {
                        pausedForTransientLoss = false
                        onResumeAfterFocusLoss()
                    }
                }
            }
        }
    }

    /**
     * Only arms the resume if this actually interrupted playback. Focus is still held while
     * paused, so a voice search or an alarm hands it back afterwards - and resuming on that would
     * start music the user had deliberately stopped.
     */
    private fun onTransientLoss() {
        when {
            // A call is never something to talk over or merely dip under, whatever the setting
            // says: "Keep playing" played music into phone calls.
            isCommunicating() -> {
                setTransientDuck(false)
                pausedForTransientLoss = onPauseForFocusLoss() || pausedForTransientLoss
            }
            mode == OtherAppSound.PAUSE -> pausedForTransientLoss = onPauseForFocusLoss()
            mode == OtherAppSound.LOWER_VOLUME -> setTransientDuck(true)
            else -> Unit
        }
    }

    /**
     * Permanent focus loss means "another app is the audio app now" - but a lot of apps that ask
     * for it don't mean it. Games in particular request permanent focus for each sound effect and
     * let it go a moment later; obeying that literally stopped the music for good on the first
     * move of a chess game.
     *
     * So rather than stopping straight away, playback holds (dipped, paused or untouched, per
     * [mode]) while it watches whether anything else is actually playing. If the other app's sound
     * is over within moments it was an interruption, and the music comes back and takes focus again.
     * If something keeps playing - a video, another music app - it's a real takeover, and playback
     * pauses as it always did, saying why.
     */
    private fun startTakeoverCheck() {
        cancelTakeoverCheck()
        if (!isPlaying()) return
        when (mode) {
            OtherAppSound.PAUSE -> pausedForTakeoverCheck = onPauseForFocusLoss()
            OtherAppSound.LOWER_VOLUME -> setTakeoverDuck(true)
            OtherAppSound.KEEP_PLAYING -> Unit
        }
        takeoverJob = scope.launch {
            // Give the other app a moment to actually start making its sound.
            delay(TAKEOVER_SETTLE_MS)
            val deadline = SystemClock.elapsedRealtime() + TAKEOVER_WINDOW_MS
            var quietChecks = 0
            while (true) {
                quietChecks = if (otherActivePlayers() == 0) quietChecks + 1 else 0
                if (quietChecks >= QUIET_CHECKS_TO_RESUME) {
                    endTakeoverCheck(takenOver = false)
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    endTakeoverCheck(takenOver = true)
                    return@launch
                }
                delay(TAKEOVER_POLL_MS)
            }
        }
    }

    private fun endTakeoverCheck(takenOver: Boolean) {
        takeoverJob = null
        val wasPausedForCheck = pausedForTakeoverCheck
        pausedForTakeoverCheck = false
        setTakeoverDuck(false)
        if (takenOver) {
            if (onPauseForFocusLoss() || wasPausedForCheck) onTakenOver()
        } else {
            Log.i("AudioFocusController", "Focus loss was only a short sound - carrying on")
            // Back in the focus stack, so the next interruption is heard about too. Not a request
            // to start anything later, though: that's decided right here.
            if (requestFocus() == FocusResult.DELAYED) startWhenGranted = false
            if (wasPausedForCheck) onResumeAfterFocusLoss()
        }
    }

    private fun cancelTakeoverCheck() {
        takeoverJob?.cancel()
        takeoverJob = null
        pausedForTakeoverCheck = false
        setTakeoverDuck(false)
    }

    /**
     * Players that are producing sound and aren't ours. For an app without system privileges the
     * platform only lists players that are actually playing (paused and idle ones are left out of
     * the anonymised list it hands back), so the count is the answer - minus our own. A failure to
     * read it counts as "something is playing", which errs towards the old, safe pause.
     */
    /**
     * Whether a phone or video call, a ringing phone or an assistant has the audio. The mode says
     * so for calls; an app's own calling or an assistant shows up as a player with that usage.
     */
    private fun isCommunicating(): Boolean {
        val callMode = runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)
        if (callMode in CALL_MODES) return true
        val configs = runCatching { audioManager.activePlaybackConfigurations }.getOrNull() ?: return false
        return configs.any { it.audioAttributes.usage in COMMUNICATION_USAGES }
    }

    /** Whether playback was stopped by another app and will start again by itself. */
    val resumePending: Boolean
        get() = pausedForTransientLoss || pausedForTakeoverCheck || startWhenGranted

    private fun otherActivePlayers(): Int {
        val active = runCatching { audioManager.activePlaybackConfigurations.size }.getOrNull() ?: return Int.MAX_VALUE
        return (active - ownActivePlayers()).coerceAtLeast(0)
    }

    private fun setTransientDuck(ducked: Boolean) {
        duckedForTransient = ducked
        onDuckingChanged(duckedForTransient || duckedForTakeoverCheck)
    }

    private fun setTakeoverDuck(ducked: Boolean) {
        duckedForTakeoverCheck = ducked
        onDuckingChanged(duckedForTransient || duckedForTakeoverCheck)
    }

    /**
     * Called when playback is stopped deliberately - by the user, the sleep timer, or an output
     * change. Cancels any pending auto-resume, so a deliberate pause during an interruption isn't
     * undone when focus comes back.
     */
    fun onPlaybackPausedDeliberately() {
        pausedForTransientLoss = false
        startWhenGranted = false
        cancelTakeoverCheck()
    }

    /**
     * Asks for focus if we don't already hold it, and says whether playback may start.
     *
     * A refusal is obeyed: it means a call is in progress, and playing anyway put music on top of
     * it. A delayed grant - also a call, on most phones - is remembered, and playback starts by
     * itself through [onDelayedGain] once the call is over, unless it's paused in the meantime.
     */
    fun requestFocus(): FocusResult {
        // Pressing play settles any takeover check in progress: the user has decided.
        if (takeoverJob != null) cancelTakeoverCheck()
        if (holdsFocus && !lentOut) return FocusResult.GRANTED
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = request ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    SystemAudioAttributes.Builder()
                        .setUsage(SystemAudioAttributes.USAGE_MEDIA)
                        .setContentType(SystemAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                // Without this the system ducks us itself on Android 8+ and never says so, which
                // made "Keep playing" dip anyway. Declaring that we handle it means we're told.
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
                .also { request = it }
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (holdsFocus) lentOut = false
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                startWhenGranted = false
                FocusResult.GRANTED
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.i("AudioFocusController", "Audio focus delayed - starting once it's granted")
                startWhenGranted = true
                FocusResult.DELAYED
            }
            else -> {
                Log.w("AudioFocusController", "Audio focus refused (result=$result)")
                startWhenGranted = false
                FocusResult.DENIED
            }
        }
    }

    fun abandonFocus() {
        cancelTakeoverCheck()
        // A pending delayed request is abandoned too, so it can't start playback later.
        val pending = startWhenGranted
        startWhenGranted = false
        if (!holdsFocus && !pending) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        holdsFocus = false
        lentOut = false
        pausedForTransientLoss = false
        setTransientDuck(false)
    }

    private companion object {
        val CALL_MODES = buildSet {
            add(AudioManager.MODE_IN_CALL)
            add(AudioManager.MODE_IN_COMMUNICATION)
            add(AudioManager.MODE_RINGTONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(AudioManager.MODE_CALL_SCREENING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(AudioManager.MODE_CALL_REDIRECT)
                add(AudioManager.MODE_COMMUNICATION_REDIRECT)
            }
        }

        val COMMUNICATION_USAGES = setOf(
            SystemAudioAttributes.USAGE_VOICE_COMMUNICATION,
            SystemAudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
            SystemAudioAttributes.USAGE_ASSISTANT,
        )

        /** How long the other app gets to start its sound before we look. */
        const val TAKEOVER_SETTLE_MS = 400L
        const val TAKEOVER_POLL_MS = 250L

        /** Quiet checks in a row that count as "the other sound is over" - half a second. */
        const val QUIET_CHECKS_TO_RESUME = 2

        /** Something still playing after this long is a takeover, not an interruption. */
        const val TAKEOVER_WINDOW_MS = 4_000L
    }
}
