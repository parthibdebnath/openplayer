package io.github.parthibdebnath.openplayer.audio

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val playLastSongToEnd: Boolean = true,
)

/**
 * Double-tap/long-press-on-clock sleep timer. Ticks down in real time; when it reaches zero,
 * either stops playback immediately or - if "play last song to end" is on - lets
 * [PlaybackEngine] finish the current song first via [consumeStopAfterCurrentSong].
 */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val onFireImmediateStop: () -> Unit,
    /**
     * Fired when the timer expires but the current song is to be played out. The engine normally
     * rolls into the next song without the current one ever reaching STATE_ENDED - either
     * gaplessly from a preloaded item, or by crossfading - so it has to be told to stop doing that
     * or the end of this song would never be noticed.
     */
    private val onArmStopAfterCurrentSong: () -> Unit = {},
    /**
     * Fired when a pending stop-after-this-song is called off, so the engine can go back to
     * preloading the next song and looping under repeat-one, both of which arming switched off.
     */
    private val onDisarmStopAfterCurrentSong: () -> Unit = {},
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /**
     * When the timer ends, on the elapsed-since-boot clock. Wall-clock time moved the deadline
     * whenever the phone's clock did - a time zone change while travelling, a network time
     * correction - so a 30-minute timer could fire hours early or late.
     */
    private var endTimestampMs: Long = 0L
    private var tickJob: Job? = null
    private var stopAfterCurrentSongPending = false

    /** hr/min to prefill the overlay with when reopened while a timer is already running, rounded to the nearest minute. */
    fun remainingHoursMinutesForPrefill(): Pair<Int, Int> {
        val remaining = (endTimestampMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        val totalMinutes = Math.round(remaining / 60000.0).toInt()
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    /**
     * Confirms the overlay's values. If they match the currently-running timer's original
     * hours/minutes, the existing countdown resumes untouched (exact second preserved);
     * otherwise the old timer is discarded and a new one starts from now.
     */
    fun confirm(hours: Int, minutes: Int, playLastSongToEnd: Boolean) {
        val clampedHours = hours.coerceIn(0, MAX_HOURS)
        val clampedMinutes = minutes.coerceIn(0, MAX_MINUTES)

        if (clampedHours == 0 && clampedMinutes == 0) {
            cancel()
            return
        }

        val unchanged = _state.value.isActive && clampedHours == lastConfirmedHours && clampedMinutes == lastConfirmedMinutes
        if (!unchanged) {
            lastConfirmedHours = clampedHours
            lastConfirmedMinutes = clampedMinutes
            endTimestampMs = SystemClock.elapsedRealtime() + (clampedHours * 3600_000L) + (clampedMinutes * 60_000L)
            startTicking()
        }
        _state.value = _state.value.copy(isActive = true, playLastSongToEnd = playLastSongToEnd)
    }

    fun cancel() {
        val wasArmed = stopAfterCurrentSongPending
        reset()
        if (wasArmed) onDisarmStopAfterCurrentSong()
    }

    private fun reset() {
        tickJob?.cancel()
        tickJob = null
        stopAfterCurrentSongPending = false
        _state.value = SleepTimerState()
    }

    /** Time left on a running timer; meaningless when none is running. */
    fun remainingMs(): Long = (endTimestampMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /** Whether the engine should let the current song finish and then stop, rather than advancing. */
    fun isStopAfterCurrentSongPending(): Boolean = stopAfterCurrentSongPending

    /** PlaybackEngine calls this on natural track completion; returns true if it should stop instead of advancing. */
    fun consumeStopAfterCurrentSong(): Boolean {
        if (stopAfterCurrentSongPending) {
            // No disarm callback here: the engine is about to stop, and restores normal playback
            // itself once it has - preloading the next song into a player that's still set to play
            // would start it.
            reset()
            return true
        }
        return false
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val remaining = endTimestampMs - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    onTimerFired()
                    break
                }
                _state.value = _state.value.copy(isActive = true, remainingMs = remaining)
                delay(1000)
            }
        }
    }

    private fun onTimerFired() {
        if (_state.value.playLastSongToEnd) {
            stopAfterCurrentSongPending = true
            _state.value = _state.value.copy(remainingMs = 0)
            onArmStopAfterCurrentSong()
        } else {
            cancel()
            onFireImmediateStop()
        }
    }

    private var lastConfirmedHours = 0
    private var lastConfirmedMinutes = 0

    companion object {
        const val MAX_HOURS = 9
        const val MAX_MINUTES = 60
    }
}
