@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.parthibdebnath.openplayer.audio.mix.MixChannelAudioProcessor
import io.github.parthibdebnath.openplayer.audio.mix.MixPlan
import io.github.parthibdebnath.openplayer.audio.mix.MixPlanner
import io.github.parthibdebnath.openplayer.audio.mix.MixStyle
import io.github.parthibdebnath.openplayer.audio.mix.TempoAudioProcessor
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.repository.TrackAnalysisRepository
import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import io.github.parthibdebnath.openplayer.data.settings.OtherAppSound
import io.github.parthibdebnath.openplayer.data.settings.PlaybackStateRepository
import io.github.parthibdebnath.openplayer.data.settings.SavedPosition
import io.github.parthibdebnath.openplayer.data.settings.SavedExternalSong
import io.github.parthibdebnath.openplayer.data.settings.SavedQueue
import io.github.parthibdebnath.openplayer.data.settings.SavedShuffle
import io.github.parthibdebnath.openplayer.data.settings.SettingsDefaults
import io.github.parthibdebnath.openplayer.data.settings.SettingsRepository
import io.github.parthibdebnath.openplayer.data.settings.ShuffleProgressStore
import io.github.parthibdebnath.openplayer.data.repository.EXTERNAL_PLAYLIST_ID
import io.github.parthibdebnath.openplayer.data.repository.ExternalSongs
import io.github.parthibdebnath.openplayer.data.repository.isExternal
import io.github.parthibdebnath.openplayer.util.runSuspendCatching
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlaybackUiState(
    val currentSong: Song? = null,
    val playlistLabel: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleOn: Boolean = false,
    val upcoming: List<Song> = emptyList(),
    /** Why the current song couldn't be played, worded for the listener; null when all is well. */
    val error: String? = null,
    /** Why playback stopped when the listener didn't stop it themselves; null otherwise. */
    val notice: String? = null,
)

/**
 * One player plus the DSP chain feeding it. Audio processors hold per-stream buffers and are
 * configured and flushed by the sink that owns them, so the two players cannot share a set.
 */
private class PlayerSlot(context: Context, audioAttributes: AudioAttributes) {
    val normalization = NormalizationAudioProcessor()
    val reverb = ReverbAudioProcessor()
    val threeD = ThreeDAudioProcessor()

    /** The DJ mix's level and filter for this deck. */
    val mixChannel = MixChannelAudioProcessor()

    /** The DJ mix's tempo for this deck. */
    val tempo = TempoAudioProcessor()

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        CustomRenderersFactory(context, normalization, reverb, threeD, mixChannel, tempo),
        audioMediaSourceFactory(context),
    )
        // Focus is handled centrally by AudioFocusController instead of per-player - see there.
        .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
        .setHandleAudioBecomingNoisy(true)
        // Keeps the CPU awake while - and only while - this player is playing. Without it some
        // phones let the processor sleep with the screen off and background playback stalls.
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    /** The crossfade's own gain for this slot, kept separate from the global ducking gain. */
    var fadeGain: Float = 1f

    // The tempo easing back to normal after a DJ mix: from glideFrom, over glideTotalMs of playing
    // time. Not gliding while glideTotalMs is 0.
    var glideFrom: Double = 1.0
    var glideTotalMs: Double = 0.0
    var glideElapsedMs: Double = 0.0

    /** Back to plain playback: normal tempo, full level, no filter. */
    fun resetMix() {
        tempo.rate = 1f
        tempo.holdAtStart(0L)
        glideTotalMs = 0.0
        mixChannel.gain = 1f
        mixChannel.cutoffHz = MixChannelAudioProcessor.FLOOR_HZ
    }
}

/**
 * Application-scoped singleton owning playback: two ExoPlayer instances, the custom reverb/3D
 * processors feeding each, and the [PlaybackQueueManager].
 *
 * Two players rather than one so a crossfade can genuinely overlap - the outgoing track keeps
 * playing while the incoming one starts underneath it. Exactly one is "active" at a time; the
 * other is idle except during a fade. [activePlayer] publishes which, so
 * [io.github.parthibdebnath.openplayer.playback.PlaybackService] can point its MediaSession at the right one.
 *
 * With crossfade off, the active player carries a two-item window instead, letting ExoPlayer roll
 * between songs gaplessly without the pipeline ever stopping.
 */
class PlaybackEngine private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val slots = listOf(
        PlayerSlot(appContext, mediaAudioAttributes),
        PlayerSlot(appContext, mediaAudioAttributes),
    )
    private var activeIndex = 0
    private val active: PlayerSlot get() = slots[activeIndex]
    private val standby: PlayerSlot get() = slots[1 - activeIndex]

    /** The player currently driving playback. Swaps when a crossfade completes. */
    private val _activePlayer = MutableStateFlow(slots[0].player)
    val activePlayer: StateFlow<ExoPlayer> = _activePlayer.asStateFlow()

    private val player: ExoPlayer get() = active.player

    val queueManager = PlaybackQueueManager()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sleepTimerManager = SleepTimerManager(
        scope = engineScope,
        onFireImmediateStop = { pause() },
        onArmStopAfterCurrentSong = {
            // Stop the current song rolling into the next one, so it reaches STATE_ENDED and the
            // pending stop is actually noticed. Without this the preloaded item (or a crossfade)
            // carried straight on and the timer never took effect - and under repeat-one the song
            // looped indefinitely, since a looping player never ends at all.
            cancelCrossfade()
            dropPreloadedNext()
            applyRepeatModeToPlayer()
        },
        onDisarmStopAfterCurrentSong = {
            applyRepeatModeToPlayer()
            syncUpcomingWindow()
        },
    )

    private val focusController = AudioFocusController(
        context = appContext,
        scope = engineScope,
        onPauseForFocusLoss = {
            // Reports whether this actually interrupted playback, which is what decides if the
            // interruption should resume us afterwards.
            val wasPlaying = slots.any { it.player.isPlaying }
            if (wasPlaying) pausePlayers()
            wasPlaying
        },
        // Through play() rather than the player directly, so both halves of an interrupted
        // crossfade come back together.
        onResumeAfterFocusLoss = { play() },
        onDuckingChanged = { ducking ->
            duckGain = if (ducking) DUCKED_GAIN else 1f
            applyVolumes()
        },
        isPlaying = { slots.any { it.player.isPlaying } },
        ownActivePlayers = { slots.count { it.player.isPlaying } },
        onTakenOver = {
            // Said on the Play screen, so music that stopped on its own isn't a mystery.
            notice = "Paused because another app started playing"
            syncState()
        },
        onDelayedGain = { play() },
    )

    /** What other apps' short sounds do to the music; see [io.github.parthibdebnath.openplayer.data.settings.OtherAppSound]. */
    var otherAppSound: OtherAppSound
        get() = focusController.mode
        set(value) {
            focusController.mode = value
        }

    val outputDeviceManager = OutputDeviceManager(
        context = appContext,
        onExternalOutputsChanged = {
            Log.i("PlaybackEngine", "External audio outputs changed - pausing playback")
            pause()
        },
        onPreferredDeviceChanged = { device -> applyPreferredDevice(device) },
    )

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var currentShuffleSeed: String = ""

    /** Each list's shuffle, set aside while something else plays; see [shufflePlay]. */
    private val savedShuffles = ShuffleProgressStore(appContext, engineScope)

    /**
     * Whether each list keeps its own shuffle, picked up where it was left. Turned off, the saved
     * ones are forgotten, so turning it back on later doesn't pick up shuffles long gone stale.
     */
    var preserveShuffleProgress: Boolean = true
        set(value) {
            field = value
            if (!value) savedShuffles.clear()
        }

    /**
     * The list whose Shuffle button last started or picked up the queue's shuffle, while that's
     * still what is playing - so the next press on it means "a new shuffle", not "carry on".
     */
    private var shuffleStartedFrom: String? = null

    private var lastError: String? = null

    /** The sleep timer's fade: 1 normally, falling to 0 over the last moments before it stops. */
    private var sleepGain: Float = 1f

    /** The song actually loaded in the active player, for remembering where it was left. */
    private var loadedSong: Song? = null

    /** Positions saved this session, ahead of the copies of songs the queue still holds. */
    private val resumePositions = HashMap<Long, Long>()

    /** Saves a long track's position; set by the ViewModel, which owns the database. */
    var onResumePositionChanged: ((songId: Long, positionMs: Long) -> Unit)? = null

    /** Whether long tracks pick up where they were left off; see [LONG_TRACK_MS]. */
    var rememberLongTracks: Boolean = true

    /** Evens out loudness between songs; see [NormalizationAudioProcessor]. */
    var normalizeVolume: Boolean = false
        set(value) {
            field = value
            slots.forEach { it.normalization.enabled = value }
        }

    /** Skips stretches of silence within songs - long intros, gaps between live tracks. */
    var skipSilence: Boolean = false
        set(value) {
            field = value
            slots.forEach { slot -> runCatching { slot.player.skipSilenceEnabled = value } }
        }
    private var notice: String? = null
    private var dspBypassedAfterFailure = false
    private var retriesForCurrentItem = 0
    private var duckGain: Float = 1f

    /** Guards against reacting to transitions we caused ourselves while rewriting the queue window. */
    private var rebuildingWindow = false

    /** Unplayable songs skipped in a row; see [skipUnplayableSong]. */
    private var consecutiveUnplayable = 0

    /** Overlap length in milliseconds; zero disables crossfading and re-enables the gapless window. */
    @Volatile var crossfadeMs: Int = SettingsDefaults.CROSSFADE_MS
        set(value) {
            val changed = field != value
            field = value
            // The two modes need different things loaded in the player, so switching between them
            // mid-song has to rewrite what's queued - otherwise turning crossfade on would leave a
            // preloaded next track that plays at the same time as the one being faded in.
            if (changed) {
                updateWindowForCurrentMode()
                // A DJ mix's length comes from this too.
                if (djRun == null) mixPlanSongs = null
            }
        }

    /** 0f..1f through the current overlap, or null when no crossfade is running. */
    private var fadeProgress: Float? = null

    /**
     * How much of the outgoing track the overlap in progress spans, in that track's own time -
     * capped to what was left of it. See [crossfadeProgress].
     */
    private var activeFadeLengthMs: Long = 0L

    // --- Persistence state --------------------------------------------------------------------
    //
    // Saving lives here rather than in a ViewModel because the engine outlives the UI: music keeps
    // playing in the background after the activity is gone, and the restore point has to keep up
    // with it so a later process death resumes from the right place.

    private val stateRepository = PlaybackStateRepository(appContext)
    private val persistMutex = Mutex()
    private var lastSavedQueueVersion = -1L
    private var lastSavedPosition: SavedPosition? = null

    /**
     * Nothing is saved until a restore has been attempted. The first save would otherwise land
     * before the saved state had been read back, and overwrite it with an empty queue.
     */
    private var restoreAttempted = false

    /** Whether the saved queue has been read back (or there was none); an empty queue before then means nothing. */
    val restoreDone: Boolean get() = restoreAttempted

    /** Play was asked for before the restore had run; see [play]. */
    private var playWhenRestored = false

    /**
     * Paused by another app's sound - a call, an interruption - and due to start again by itself.
     * The media service stays in the foreground through these, or it couldn't come back.
     */
    val resumesByItself: Boolean get() = focusController.resumePending

    /** Whether a save of a changed queue is already on its way; see [scheduleQueueSave]. */
    private var queueSaveScheduled = false

    /**
     * Whether either player is playing. The tick and save loops wait on this rather than running
     * all the time: with nothing playing there's nothing to update, and waking up twenty times a
     * second regardless kept the app busy in the background for as long as it stayed in memory.
     */
    private val anyPlaying = MutableStateFlow(false)

    init {
        // Any change to the queue is saved straight away, not only on a pause, a seek or leaving
        // the app: skipping songs while paused - in the app, from the notification or on a headset
        // - changed nothing that was saved, so if the app was then closed for good (swiped away
        // from the notification, killed for memory) it reopened on the song before.
        queueManager.onChanged = ::scheduleQueueSave
        outputDeviceManager.start()
        slots.forEachIndexed { index, slot -> slot.player.addListener(listenerFor(index)) }

        // Position doesn't advance on its own: ExoPlayer reports discrete events, not a running
        // clock, so without this tick the time readout and the seek bar never moved.
        engineScope.launch {
            while (true) {
                if (!anyPlaying.value) {
                    syncState()
                    anyPlaying.first { it }
                }
                advanceTransition()
                updateSleepFade()
                syncState()
                delay(nextTickDelayMs())
            }
        }

        // The position only moves while playing, and everything that changes while paused - a
        // pause itself, a seek, leaving the app - saves straight away.
        engineScope.launch {
            while (true) {
                anyPlaying.first { it }
                delay(PERSIST_INTERVAL_MS)
                persistState()
            }
        }
    }

    private fun listenerFor(slotIndex: Int) = object : Player.Listener {
        private val isActiveSlot: Boolean get() = slotIndex == activeIndex

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isActiveSlot) return
            // Only reached once the preloaded window is exhausted - the end of the queue, a
            // repeat-all wrap, or a track too short to have been crossfaded. Ordinary song-to-song
            // moves go through onMediaItemTransition or the crossfade instead.
            if (playbackState == Player.STATE_ENDED && fadeProgress == null) {
                // Played to the end: next time it starts from the top.
                rememberPosition(loadedSong, 0L, force = true)
                if (sleepTimerManager.consumeStopAfterCurrentSong()) {
                    // Explicitly pause and rewind: left in STATE_ENDED the transport would still
                    // read as playing, with no way to resume without re-preparing.
                    pause()
                    runCatching { player.seekTo(0) }
                    // Back to normal for whenever play is pressed next.
                    applyRepeatModeToPlayer()
                    syncUpcomingWindow()
                } else if (!hasNextSong()) {
                    stopAtEndOfQueue()
                } else {
                    // A song that played to its end was playing, so the next one plays too.
                    advanceTo(autoPlay = true)
                }
            }
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isActiveSlot) return
            // The player rolled onto the item preloaded behind the current one. Mirror that in the
            // queue, then top the window back up so the following song is ready too.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !rebuildingWindow) {
                rememberPosition(loadedSong, 0L, force = true)
                queueManager.advanceToNext(currentShuffleSeed)
                loadedSong = queueManager.current
                retriesForCurrentItem = 0
                syncUpcomingWindow()
            }
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            anyPlaying.value = slots.any { it.player.isPlaying }
            if (!isActiveSlot) return
            // Something is genuinely playing, so whatever run of unplayable files came before it
            // is over, and so is any error.
            if (isPlaying) {
                consecutiveUnplayable = 0
                lastError = null
                notice = null
            }
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlaybackEngine", "Player error on slot $slotIndex: ${error.errorCodeName}", error)
            if (!isActiveSlot) {
                // Only the outgoing track's tail can fail here - the incoming one becomes active
                // the moment the fade starts. Nothing to recover: end the overlap early and let
                // the song that's taken over carry on.
                cancelCrossfade()
                return
            }
            lastError = describe(error)

            // ERROR_CODE_FAILED_RUNTIME_CHECK means something threw inside the playback pipeline.
            // The only non-stock code in that pipeline is our own DSP, so drop it out of the path
            // entirely and try again - silence beats an effect that takes playback down with it.
            if (error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK && !dspBypassedAfterFailure) {
                dspBypassedAfterFailure = true
                slots.forEach {
                    it.normalization.forceBypass = true
                    it.reverb.forceBypass = true
                    it.threeD.forceBypass = true
                    it.mixChannel.forceBypass = true
                    it.tempo.forceBypass = true
                }
                Log.w("PlaybackEngine", "Runtime check failed - bypassing custom DSP and retrying")
            }

            // An error drops the player to STATE_IDLE, where play() does nothing at all until the
            // player is prepared again - so without this retry the first failure would leave
            // playback permanently dead, with every later tap on play silently ignored. Retrying
            // only helps with transient faults, though: a file that's gone, unreadable or in a
            // format we can't decode will fail the same way every time.
            val unplayable = error.errorCode in UNPLAYABLE_ERRORS
            if (!unplayable && retriesForCurrentItem < MAX_RETRIES_PER_ITEM) {
                retriesForCurrentItem++
                Log.i("PlaybackEngine", "Retrying after error (attempt $retriesForCurrentItem)")
                runCatching {
                    player.prepare()
                    player.play()
                }
            } else {
                skipUnplayableSong()
            }
            syncState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActiveSlot) return
            syncState()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (!isActiveSlot) return
            Log.i("PlaybackEngine", "playback suppressed, reason=$playbackSuppressionReason")
            syncState()
        }
    }

    /**
     * Whether anything is on screen to show the position. Set by the UI; while it's false the tick
     * only needs to keep the crossfade on time.
     */
    @Volatile var uiVisible: Boolean = true

    /**
     * How long until the next tick. The tick used to run every 50 ms for as long as anything
     * played - twenty wake-ups a second, screen off, crossfade off - when only an overlap in
     * progress needs that. Otherwise it's paced to what's watching: a few times a second for the
     * time readout, once a second in the background, and close to the start of a crossfade when
     * one is coming up, so it still begins on time.
     */
    private fun nextTickDelayMs(): Long {
        if (fadeProgress != null || sleepGain < 1f || djRun != null) return TICK_MS
        val idle = if (uiVisible) UI_TICK_MS else BACKGROUND_TICK_MS
        // Close enough to the sleep timer's fade that the next tick has to start it on time.
        val untilSleepFade = sleepFadeRemainingMs() - SLEEP_FADE_MS
        if (untilSleepFade < idle) return untilSleepFade.coerceIn(TICK_MS, idle)
        val fadeMs = crossfadeMs
        // A DJ mix that's been planned: woken in time for its tempo ramp and its start, which
        // take the place of the crossfade's.
        val upcomingMix = mixPlan?.takeIf { djMix && fadeMs > 0 && mixPlanSongs != null }
        if (upcomingMix != null) {
            val playerRef = active.player
            val speed = playerRef.playbackParameters.speed.coerceAtLeast(0.01f)
            val position = playerRef.currentPosition.toDouble()
            if (upcomingMix.style == MixStyle.BEATMATCH && position >= upcomingMix.outRampStartMs) return TICK_MS
            val untilEvent = minOf(upcomingMix.outRampStartMs, upcomingMix.outPrerollMs - mixStartLeadMs(upcomingMix)) - position
            val untilReal = (untilEvent / speed).toLong()
            return if (untilReal < idle) untilReal.coerceIn(TICK_MS, idle) else idle
        }
        if (fadeMs > 0) {
            val playerRef = active.player
            val duration = playerRef.duration
            if (duration > 0) {
                val speed = playerRef.playbackParameters.speed
                val untilFadeMedia = duration - playerRef.currentPosition - crossfadeMediaLengthMs(fadeMs, speed)
                // The track's time runs at the playback speed; the delay is in real time. Read as
                // real time, a fade at 2x speed started a tick late, with half of it already gone.
                val untilFade = (untilFadeMedia / speed.coerceAtLeast(0.01f)).toLong()
                if (untilFade < idle) return untilFade.coerceIn(TICK_MS, idle)
            }
        }
        return idle
    }

    // --- Long tracks -------------------------------------------------------------------------

    private fun isLongTrack(song: Song): Boolean {
        val duration = if (song.id == loadedSong?.id && player.duration > 0) player.duration else song.durationMs
        return duration >= LONG_TRACK_MS
    }

    /** Where [song] should start: its remembered position if it's a long track, otherwise the top. */
    private fun resumePositionFor(song: Song): Long {
        if (!rememberLongTracks || song.isExternal || song.durationMs < LONG_TRACK_MS) return 0L
        return resumePositions[song.id] ?: song.resumePositionMs
    }

    /**
     * A song can only be lined up behind the current one for a gapless roll-over if it starts
     * from the top; one that resumes part-way is loaded when it's reached, so it can be started
     * where it was left.
     */
    private fun preloadable(song: Song): Boolean = resumePositionFor(song) == 0L

    /**
     * Notes how far into a long track playback got. Positions right at either end count as
     * "from the start", so a track that was finished, or barely begun, isn't resumed. While
     * playing it's saved every [RESUME_SAVE_STEP_MS] rather than on every tick.
     */
    private fun rememberPosition(song: Song?, positionMs: Long, force: Boolean) {
        if (song == null || song.isExternal || !rememberLongTracks || !isLongTrack(song)) return
        val duration = if (song.id == loadedSong?.id && player.duration > 0) player.duration else song.durationMs
        val position = if (positionMs < RESUME_EDGE_MS || positionMs > duration - RESUME_EDGE_MS) 0L else positionMs
        val previous = resumePositions[song.id] ?: song.resumePositionMs
        if (position == previous) return
        if (!force && position != 0L && kotlin.math.abs(position - previous) < RESUME_SAVE_STEP_MS) return
        resumePositions[song.id] = position
        onResumePositionChanged?.invoke(song.id, position)
    }

    // --- Sleep timer fade ----------------------------------------------------------------------

    /**
     * How long until the sleep timer stops the music - the timer itself, or the end of the song
     * it's waiting for - or [Long.MAX_VALUE] when it isn't about to.
     */
    private fun sleepFadeRemainingMs(): Long = when {
        sleepTimerManager.isStopAfterCurrentSongPending() -> {
            val duration = player.duration
            if (duration > 0) (duration - player.currentPosition).coerceAtLeast(0) else Long.MAX_VALUE
        }
        sleepTimerManager.state.value.isActive && !sleepTimerManager.state.value.playLastSongToEnd ->
            sleepTimerManager.remainingMs()
        else -> Long.MAX_VALUE
    }

    /** Lowers the volume over the sleep timer's last [SLEEP_FADE_MS], rather than cutting off dead. */
    private fun updateSleepFade() {
        val remaining = sleepFadeRemainingMs()
        val gain = if (remaining >= SLEEP_FADE_MS) 1f else (remaining.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f)
        if (kotlin.math.abs(gain - sleepGain) > 0.002f) {
            sleepGain = gain
            applyVolumes()
        }
    }

    // --- DJ mix -------------------------------------------------------------------------------
    //
    // With "DJ mix" on, songs aren't simply crossfaded: each is analysed (tempo, beat grid, bar
    // lines, key, where its intro and outro are - see BeatAnalyzer), and the hand-over is planned
    // like a DJ's (MixPlanner). The two songs' tempos meet in the middle, the next song starts in
    // silence and is locked onto the beat, then comes in over the outgoing song's outro, bar line
    // on bar line, with the basslines swapped half-way so they never fight. Afterwards its tempo
    // eases back to normal too slowly to hear. Songs whose beats can't be matched get a filtered
    // fade that skips the silences either side instead, and songs not yet analysed a plain
    // crossfade.

    /** Song analyses for the DJ mix, worked out in the background as songs come up. */
    private val analyses = TrackAnalysisRepository(appContext, engineScope).also { repository ->
        repository.onReady = { songId ->
            val pair = mixPlanSongs
            if (djRun == null && pair != null && (songId == pair.first || songId == pair.second)) mixPlanSongs = null
        }
    }

    /** Beat-matched DJ transitions in place of plain crossfades; the length is still [crossfadeMs]. */
    var djMix: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // A mix already under way finishes; one only planned is dropped.
            if (!value && djRun == null) {
                mixPlan = null
                mixPlanSongs = null
                releaseTempo(active, beatMs = 0.0)
            }
        }

    /** The plan for mixing the current song into the next, or null for a plain crossfade. */
    private var mixPlan: MixPlan? = null

    /** The (current, next) songs [mixPlan] was made for; null when it needs making (again). */
    private var mixPlanSongs: Pair<Long, Long>? = null

    /** A DJ mix under way: the incoming song is the active slot, the outgoing one the standby. */
    private var djRun: DjRun? = null

    private class DjRun(val plan: MixPlan, val incomingTitle: String) {
        /** When the incoming song's clock was first seen moving; 0 until then. */
        var incomingMovingSinceMs = 0L
        var lastShiftMs = 0L
        var shifts = 0
        val errors = ArrayDeque<Double>()
        var smoothedErrorMs = 0.0
        var lastLogMs = 0L
    }

    private var lastTransitionTickMs = 0L

    /** Moves whichever kind of transition is due or under way on by one tick. */
    private fun advanceTransition() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (lastTransitionTickMs == 0L) 0L else (now - lastTransitionTickMs).coerceIn(0L, MAX_TICK_GAP_MS)
        lastTransitionTickMs = now
        advanceGlides(elapsed)
        if (djRun != null) {
            advanceDjMix(now)
            return
        }
        if (fadeProgress == null && djMix && crossfadeMs > 0) {
            val plan = currentMixPlan()
            if (plan != null) {
                followMixPlan(plan)
                return
            }
        }
        advanceCrossfade()
    }

    /**
     * The plan for the current song into the next, made once both have been analysed and kept
     * until either changes, the song is seeked or a setting it depends on changes. Null means a
     * plain crossfade: no next song, songs that can't be analysed, or analysis still under way
     * (which the crossfade takes over from if it isn't done in time).
     */
    private fun currentMixPlan(): MixPlan? {
        val current = queueManager.current ?: return null
        val next = queueManager.peekNext() ?: return null
        if (queueManager.repeatMode == RepeatMode.ONE || sleepTimerManager.isStopAfterCurrentSongPending()) return null
        // A long track that picks up part-way starts where it was left, not on a bar line.
        if (!preloadable(next)) return null
        analyses.prefetch(listOfNotNull(current, next, queueManager.visibleUpcoming(2).getOrNull(1)))

        val songs = current.id to next.id
        val position = player.currentPosition.toDouble()
        val existing = mixPlan
        if (mixPlanSongs == songs && (existing == null || position <= existing.outPrerollMs + MISSED_PLAN_SLACK_MS)) {
            return existing
        }
        if (!analyses.isSettled(current) || !analyses.isSettled(next)) return null
        val duration = player.duration.takeIf { it > 0 } ?: current.durationMs
        val plan = MixPlanner.plan(
            out = analyses.analysisFor(current),
            outDurationMs = duration.toDouble(),
            outPositionMs = position,
            incoming = analyses.analysisFor(next),
            maxBlendMs = crossfadeMs,
            speed = player.playbackParameters.speed.toDouble(),
        )
        mixPlan = plan
        mixPlanSongs = songs
        if (plan == null) releaseTempo(active, beatMs = 0.0)
        Log.i("PlaybackEngine", "Mix plan ${current.displayName} -> ${next.displayName}: $plan")
        return plan
    }

    /** Before a planned mix starts: eases the outgoing song's tempo, and starts the mix on time. */
    private fun followMixPlan(plan: MixPlan) {
        val playerRef = active.player
        if (!playerRef.isPlaying) return
        val position = playerRef.currentPosition.toDouble()
        if (plan.style == MixStyle.BEATMATCH && position >= plan.outRampStartMs) {
            // The ramp takes over from any glide still running from the last mix.
            active.glideTotalMs = 0.0
            active.tempo.rate = plan.outgoingRate(position).toFloat()
        }
        if (position >= plan.outPrerollMs - mixStartLeadMs(plan) && position < plan.outEndMs) startDjMix(plan)
    }

    /**
     * How early, in the outgoing song's time, to start the next song so its first sound comes
     * about when planned: a player takes a moment to get going. The beat lock takes up the rest.
     */
    private fun mixStartLeadMs(plan: MixPlan): Double =
        MIX_START_LATENCY_MS * player.playbackParameters.speed * plan.outMeetRate

    /** Starts the next song on the standby slot as [plan] says, and hands "active" over to it. */
    private fun startDjMix(plan: MixPlan) {
        val next = queueManager.peekNext() ?: return
        try {
            val incoming = standby
            val outgoing = active
            val levels = plan.levels(-1.0)
            incoming.resetMix()
            incoming.mixChannel.gain = levels.inGain.toFloat()
            incoming.mixChannel.cutoffHz = levels.inCutoffHz.toFloat()
            incoming.tempo.rate = plan.inMeetRate.toFloat()
            if (plan.inPrerollMs < 0) incoming.tempo.holdAtStart((-plan.inPrerollMs * 1000).toLong())
            outgoing.mixChannel.gain = levels.outGain.toFloat()
            outgoing.mixChannel.cutoffHz = levels.outCutoffHz.toFloat()
            // Levels are the channel strips' job here, so the player volumes carry only ducking
            // and the sleep timer.
            incoming.fadeGain = 1f
            outgoing.fadeGain = 1f
            incoming.player.repeatMode = Player.REPEAT_MODE_OFF
            rebuildingWindow = true
            incoming.player.setMediaItem(mediaItemFor(next), plan.inPrerollMs.coerceAtLeast(0.0).toLong())
            incoming.player.prepare()
            applyVolumes()
            incoming.player.play()
            rebuildingWindow = false

            // From here it's as a crossfade: the incoming song is what's playing, as far as the
            // rest of the app is concerned. See startCrossfade.
            rememberPosition(loadedSong, 0L, force = true)
            queueManager.advanceToNext(currentShuffleSeed)
            loadedSong = next
            activeIndex = 1 - activeIndex
            retriesForCurrentItem = 0
            fadeProgress = 0f
            djRun = DjRun(plan, next.displayName)
            mixPlan = null
            mixPlanSongs = null
            _activePlayer.value = incoming.player
            syncState()
            Log.i("PlaybackEngine", "DJ mix into ${next.displayName}: ${plan.style}, ${plan.beats} beats")
        } catch (t: Throwable) {
            rebuildingWindow = false
            Log.e("PlaybackEngine", "Could not start the DJ mix", t)
            djRun = null
            fadeProgress = null
            stopStandby()
        }
    }

    private fun advanceDjMix(now: Long) {
        val run = djRun ?: return
        val plan = run.plan
        val outgoing = standby
        val incoming = active
        val outPlayer = outgoing.player
        val outGone = outPlayer.playbackState == Player.STATE_ENDED || outPlayer.playbackState == Player.STATE_IDLE
        val outPosition = outPlayer.currentPosition.toDouble()
        val progress = if (outGone) 1.0 else plan.progress(outPosition)
        if (progress >= 1.0) {
            finishDjMix()
            return
        }
        val levels = plan.levels(progress)
        outgoing.mixChannel.gain = levels.outGain.toFloat()
        outgoing.mixChannel.cutoffHz = levels.outCutoffHz.toFloat()
        incoming.mixChannel.gain = levels.inGain.toFloat()
        incoming.mixChannel.cutoffHz = levels.inCutoffHz.toFloat()
        fadeProgress = progress.coerceIn(0.0, 1.0).toFloat()
        if (plan.style == MixStyle.BEATMATCH) {
            outgoing.tempo.rate = plan.outgoingRate(outPosition).toFloat()
            lockBeats(run, outPosition, levels.inGain, now)
        }
    }

    /**
     * Keeps the incoming song's beats on the outgoing song's, as a DJ does by ear and with the
     * pitch fader. The players' positions are compared every tick; a clear error early on, while
     * the incoming song is still silent or nearly, is corrected at once by moving it (the median
     * of several readings, since each position is only good to ten milliseconds or so), and what's
     * left is pulled in by running it very slightly faster or slower.
     */
    private fun lockBeats(run: DjRun, outPosition: Double, inGain: Double, now: Long) {
        val plan = run.plan
        val inPlayer = active.player
        val outPlayer = standby.player
        if (!inPlayer.isPlaying || !outPlayer.isPlaying) {
            // Buffering or paused: the readings start over once both are going again.
            run.incomingMovingSinceMs = 0L
            run.errors.clear()
            return
        }
        val inPosition = inPlayer.currentPosition.toDouble()
        // Still in its silent hold, where its clock stands still at the start.
        if (inPosition <= plan.inPrerollMs.coerceAtLeast(0.0) + 1.0) return
        if (run.incomingMovingSinceMs == 0L) {
            run.incomingMovingSinceMs = now
            return
        }
        // A player's clock takes a moment to settle after it starts.
        if (now - run.incomingMovingSinceMs < CLOCK_SETTLE_MS) return

        val errorMs = plan.beatError(outPosition, inPosition) * plan.inBeatMs
        run.errors.addLast(errorMs)
        if (run.errors.size > LOCK_READINGS) run.errors.removeFirst()
        val sinceShift = now - run.lastShiftMs
        // A shift is only heard - and only shows in the position - once the audio already
        // buffered ahead of it has played.
        if (sinceShift < SHIFT_SETTLE_MS) return

        if (run.errors.size >= SHIFT_READINGS && run.shifts < MAX_LOCK_SHIFTS) {
            val median = run.errors.toList().takeLast(SHIFT_READINGS).sorted()[SHIFT_READINGS / 2]
            val threshold = if (inGain < QUIET_GAIN) LOCK_SHIFT_MIN_MS else RESCUE_SHIFT_MIN_MS
            if (kotlin.math.abs(median) > threshold) {
                active.tempo.shiftBy((-median * 1000).toLong())
                run.shifts++
                run.lastShiftMs = now
                run.errors.clear()
                run.smoothedErrorMs = 0.0
                Log.i("PlaybackEngine", "Beat lock: moved ${run.incomingTitle} by ${"%.1f".format(-median)} ms")
                return
            }
        }
        // The fine correction follows the median of the last readings, slowly: each reading is off
        // by the players' own clock jitter, and chasing that would only make the tempo wander.
        if (run.errors.size < LOCK_READINGS) return
        val median = run.errors.sorted()[LOCK_READINGS / 2]
        run.smoothedErrorMs += (median - run.smoothedErrorMs) * LOCK_SMOOTHING
        val error = run.smoothedErrorMs
        val correction = if (kotlin.math.abs(error) < LOCK_DEADBAND_MS) {
            0.0
        } else {
            (-error * LOCK_GAIN_PER_MS).coerceIn(-LOCK_MAX_CORRECTION, LOCK_MAX_CORRECTION)
        }
        active.tempo.rate = (plan.inMeetRate * (1 + correction)).toFloat()
        if (now - run.lastLogMs >= 1_000L) {
            run.lastLogMs = now
            Log.d("PlaybackEngine", "Beat lock: ${"%.1f".format(errorMs)} ms off (median ${"%.1f".format(median)}), tempo ${"%.4f".format(active.tempo.rate)}")
        }
    }

    /** The outgoing song has gone: the incoming one carries on alone, easing back to its own tempo. */
    private fun finishDjMix() {
        val run = djRun ?: return
        djRun = null
        fadeProgress = null
        stopStandby()
        active.mixChannel.gain = 1f
        active.mixChannel.cutoffHz = MixChannelAudioProcessor.FLOOR_HZ
        releaseTempo(active, run.plan.inBeatMs)
        syncState()
    }

    /** Ends a mix early (a seek, a skip, an error), leaving the incoming song playing on its own. */
    private fun cancelDjMix() {
        val run = djRun ?: return
        djRun = null
        fadeProgress = null
        stopStandby()
        active.mixChannel.gain = 1f
        active.mixChannel.cutoffHz = MixChannelAudioProcessor.FLOOR_HZ
        releaseTempo(active, run.plan.inBeatMs)
        applyVolumes()
    }

    private fun stopStandby() {
        runCatching {
            standby.player.stop()
            standby.player.clearMediaItems()
        }
        standby.resetMix()
        slots.forEach { it.fadeGain = 1f }
        applyVolumes()
    }

    /** Starts [slot]'s tempo easing back to normal, if it isn't there already. */
    private fun releaseTempo(slot: PlayerSlot, beatMs: Double) {
        val rate = slot.tempo.rate.toDouble()
        if (kotlin.math.abs(rate - 1.0) < 1e-4) {
            slot.tempo.rate = 1f
            slot.glideTotalMs = 0.0
            return
        }
        slot.glideFrom = rate
        slot.glideElapsedMs = 0.0
        slot.glideTotalMs = MixPlanner.glideMs(rate, beatMs, slot.player.playbackParameters.speed.toDouble())
    }

    /** Moves each easing tempo on by [elapsedMs], counting only time spent playing. */
    private fun advanceGlides(elapsedMs: Long) {
        for (slot in slots) {
            if (slot.glideTotalMs <= 0.0) continue
            if (slot.player.isPlaying) slot.glideElapsedMs += elapsedMs
            val t = (slot.glideElapsedMs / slot.glideTotalMs).coerceIn(0.0, 1.0)
            val eased = t * t * (3 - 2 * t)
            slot.tempo.rate = (slot.glideFrom + (1.0 - slot.glideFrom) * eased).toFloat()
            if (t >= 1.0) {
                slot.tempo.rate = 1f
                slot.glideTotalMs = 0.0
            }
        }
    }

    // --- Crossfade ----------------------------------------------------------------------------

    /**
     * Drives the overlap. Called every tick: starts a fade once the active track is within
     * [crossfadeMs] of its end, then sets both gains from how much of the outgoing track is left
     * (see [crossfadeProgress]) until the incoming track has fully replaced it.
     */
    private fun advanceCrossfade() {
        val fadeMs = crossfadeMs
        val progress = fadeProgress

        if (progress == null) {
            if (fadeMs > 0 && shouldStartCrossfade(fadeMs)) startCrossfade()
            return
        }

        val outgoing = standby.player
        val outgoingDuration = outgoing.duration
        // Nothing left to read a position from - played out, or failed - means it's over.
        val outgoingRemaining = if (outgoingDuration <= 0 || outgoing.playbackState == Player.STATE_ENDED || outgoing.playbackState == Player.STATE_IDLE) {
            0L
        } else {
            outgoingDuration - outgoing.currentPosition
        }
        val next = crossfadeProgress(activeFadeLengthMs, outgoingRemaining, previous = progress)
        if (next >= 1f) {
            finishCrossfade()
            return
        }
        fadeProgress = next
        // The incoming track is already the active slot by this point - see startCrossfade.
        val (incomingGain, outgoingGain) = crossfadeGains(next)
        active.fadeGain = incomingGain
        standby.fadeGain = outgoingGain
        applyVolumes()
    }

    private fun shouldStartCrossfade(fadeMs: Int): Boolean {
        val playerRef = active.player
        if (!playerRef.isPlaying) return false
        if (queueManager.repeatMode == RepeatMode.ONE) return false
        // The sleep timer wants this song to be the last one.
        if (sleepTimerManager.isStopAfterCurrentSongPending()) return false
        val duration = playerRef.duration
        if (duration <= 0) return false
        // In the track's own time, so the overlap heard is as long as the setting at any speed.
        val fadeMedia = crossfadeMediaLengthMs(fadeMs, playerRef.playbackParameters.speed)
        // A track has to be comfortably longer than the overlap for one to make sense.
        if (duration < fadeMedia * 2L) return false
        if (queueManager.peekNext() == null) return false
        val remaining = duration - playerRef.currentPosition
        // Too little left to fade across - which is what seeking to the very end leaves - so let
        // the track simply finish instead of starting an overlap it can't complete.
        if (remaining < MIN_CROSSFADE_MS) return false
        return remaining <= fadeMedia
    }

    /**
     * Begins the overlap and hands "active" straight over to the incoming track, rather than
     * waiting for the fade to finish.
     *
     * The incoming song is what the user is now listening to and what the rest of the app should
     * report - its title, its elapsed time, its seek position. Leaving the outgoing track active
     * for the length of the fade left the UI stuck on a song that had already handed over, for up
     * to fifteen seconds. The outgoing player keeps running underneath as the standby slot purely
     * to play out its tail.
     */
    private fun startCrossfade() {
        val next = queueManager.peekNext() ?: return
        try {
            // Never longer than what's actually left of the outgoing track, so the fade finishes
            // as that track does rather than running on past its end.
            val outgoing = active.player
            val remaining = (outgoing.duration - outgoing.currentPosition).coerceAtLeast(0)
            activeFadeLengthMs = minOf(crossfadeMediaLengthMs(crossfadeMs, outgoing.playbackParameters.speed), remaining)

            val incoming = standby
            incoming.fadeGain = 0f
            incoming.player.repeatMode = Player.REPEAT_MODE_OFF
            rebuildingWindow = true
            incoming.player.setMediaItem(mediaItemFor(next), resumePositionFor(next))
            incoming.player.prepare()
            applyVolumes()
            incoming.player.play()
            rebuildingWindow = false

            // The outgoing track is playing out to its end, so next time it starts from the top.
            rememberPosition(loadedSong, 0L, force = true)
            queueManager.advanceToNext(currentShuffleSeed)
            loadedSong = next
            activeIndex = 1 - activeIndex
            retriesForCurrentItem = 0
            fadeProgress = 0f
            _activePlayer.value = incoming.player
            syncState()
            Log.i("PlaybackEngine", "Crossfading into ${next.displayName} over ${crossfadeMs}ms")
        } catch (t: Throwable) {
            rebuildingWindow = false
            Log.e("PlaybackEngine", "Could not start crossfade", t)
            cancelCrossfade()
        }
    }

    /** Completes the overlap: the outgoing track has faded out, so its player is reset. */
    private fun finishCrossfade() {
        fadeProgress = null
        slots.forEach { it.fadeGain = 1f }
        applyVolumes()
        runCatching {
            standby.player.stop()
            standby.player.clearMediaItems()
        }
        syncState()
    }

    /** Aborts an in-flight overlap, leaving the currently active track playing on its own. */
    private fun cancelCrossfade() {
        if (djRun != null) {
            cancelDjMix()
            return
        }
        if (fadeProgress == null) return
        fadeProgress = null
        runCatching {
            standby.player.stop()
            standby.player.clearMediaItems()
        }
        slots.forEach { it.fadeGain = 1f }
        applyVolumes()
    }

    private fun applyVolumes() {
        slots.forEach { slot ->
            val target = (slot.fadeGain * duckGain * sleepGain).coerceIn(0f, 1f)
            if (kotlin.math.abs(slot.player.volume - target) > 0.005f) {
                runCatching { slot.player.volume = target }
            }
        }
    }

    private fun applyPreferredDevice(device: AudioDeviceInfo?) {
        slots.forEach { slot ->
            runCatching { slot.player.setPreferredAudioDevice(device) }
                .onFailure { Log.w("PlaybackEngine", "Could not set preferred audio device", it) }
        }
    }

    // --- Effects ------------------------------------------------------------------------------

    fun applyEffects(effects: EffectsState) {
        slots.forEach { slot ->
            slot.reverb.enabled = effects.globalEnabled && effects.reverb.enabled
            slot.reverb.mixPercent = effects.reverb.mixPercent
            slot.reverb.decaySeconds = effects.reverb.decaySeconds
            slot.reverb.preDelayMs = effects.reverb.preDelayMs
            slot.reverb.dampingPercent = effects.reverb.dampingPercent
            slot.reverb.diffusionPercent = effects.reverb.diffusionPercent

            slot.threeD.enabled = effects.globalEnabled && effects.threeD.enabled
            slot.threeD.intensityPercent = effects.threeD.intensityPercent
            slot.threeD.widthPercent = effects.threeD.widthPercent
            slot.threeD.distancePercent = effects.threeD.distancePercent
            slot.threeD.roomPercent = effects.threeD.roomPercent
            slot.threeD.centerPercent = effects.threeD.centerPercent
        }
        applyPlaybackParameters(effects)
    }

    private fun applyPlaybackParameters(effects: EffectsState) {
        val speedEnabled = effects.globalEnabled && effects.speed.enabled
        val pitchEnabled = effects.globalEnabled && effects.pitch.enabled

        val speed = if (speedEnabled) effects.speed.speed else 1f
        val pitchEffectMultiplier = if (pitchEnabled) semitoneAndCentsToRatio(effects.pitch.semitones, effects.pitch.cents) else 1f

        // Without "preserve pitch", changing speed naturally changes pitch too (tape-speed
        // behaviour), on top of any deliberate Pitch-effect shift.
        val speedInducedPitch = if (speedEnabled && !effects.speed.preservePitch) speed else 1f
        val finalPitch = pitchEffectMultiplier * speedInducedPitch

        // PlaybackParameters requires both values to be strictly positive and finite; guard against
        // any edge case in the math above ever producing something it would reject.
        val safeSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safePitch = finalPitch.takeIf { it.isFinite() && it > 0f } ?: 1f
        // A DJ mix's real-time lengths depend on the speed; it's planned again.
        if (djRun == null) mixPlanSongs = null
        slots.forEach { slot ->
            try {
                slot.player.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
            } catch (t: Throwable) {
                Log.e("PlaybackEngine", "Failed to apply playback parameters (speed=$safeSpeed, pitch=$safePitch)", t)
            }
        }
    }

    private fun semitoneAndCentsToRatio(semitones: Int, cents: Int): Float {
        val totalCents = semitones * 100 + cents
        return Math.pow(2.0, totalCents / 1200.0).toFloat()
    }

    // --- Transport ----------------------------------------------------------------------------

    fun setShuffleSeed(seed: String) {
        // Empty means "not generated yet" - keep whatever seed is already in use.
        if (seed.isNotEmpty()) currentShuffleSeed = seed
    }

    fun refreshShuffleSeed(newSeed: String) {
        currentShuffleSeed = newSeed
        queueManager.rebuildUpcoming(newSeed)
        // The order just changed, so the song preloaded for a gapless transition may no longer be
        // the one that comes next.
        syncUpcomingWindow()
        syncState()
    }

    /** @param sourceKey which list [songs] is - see [listSourceKey] - or null if it isn't one. */
    fun playSongs(songs: List<Song>, label: String, order: PlayOrder, startIndex: Int? = null, sourceKey: String? = null) {
        // Pressing play on a playlist that's still scanning, or genuinely empty, would otherwise
        // replace whatever is playing with nothing and stop it.
        if (songs.isEmpty()) return
        setAsideShuffle()
        shuffleStartedFrom = null
        queueManager.startPlaylist(songs, label, order, currentShuffleSeed, startIndex, sourceKey)
        loadAndPlay(queueManager.current)
    }

    suspend fun playSpecificSong(songs: List<Song>, label: String, song: Song, sourceKey: String? = null) {
        // Tapping the song that's already loaded carries on with it rather than starting it over.
        if (queueManager.current?.id == song.id && queueManager.playlistLabel == label) {
            if (!player.playWhenReady) play()
            return
        }
        // A list keeping its shuffle carries on with it from the tapped song, rather than dealing
        // a new order around it and forgetting what had played.
        if (preserveShuffleProgress && sourceKey != null && queueManager.playOrder == PlayOrder.SHUFFLED) {
            if (queueManager.sourceKey == sourceKey) {
                queueManager.addNewSongs(songs, currentShuffleSeed)
                jumpWithinShuffle(song)
                return
            }
            val saved = savedShuffles.take(sourceKey)
            if (saved != null && resumeSavedShuffle(songs, label, saved)) {
                jumpWithinShuffle(song)
                return
            }
        }
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        // Keeps whichever order is in use: with shuffle on, the tapped song plays and the rest
        // follow shuffled. Always starting in order switched shuffle off on every tap.
        playSongs(songs, label, queueManager.playOrder, startIndex = index, sourceKey = sourceKey)
    }

    /**
     * A list's Shuffle button. With [preserveShuffleProgress] on, every list keeps its own shuffle
     * while other things play: pressing Shuffle on it again picks up the same order at the song
     * it was left on. A second press while that shuffle is still playing starts a new one - so
     * pressing twice is how to get a genuinely new order.
     *
     * @param newSeed a fresh seed for a new shuffle, or null to keep the one in use (it's locked).
     */
    suspend fun shufflePlay(songs: List<Song>, label: String, sourceKey: String?, newSeed: suspend () -> String?) {
        if (songs.isEmpty()) return
        if (preserveShuffleProgress && sourceKey != null && shuffleStartedFrom != sourceKey) {
            val alreadyPlaying = queueManager.sourceKey == sourceKey && queueManager.playOrder == PlayOrder.SHUFFLED
            if (alreadyPlaying) {
                // Already the queue - restored after a restart, say, or shuffled from the Play
                // screen. Carries on; a finished one falls through to a new shuffle.
                if (queueManager.peekNext() != null) {
                    if (queueManager.addNewSongs(songs, currentShuffleSeed)) syncUpcomingWindow()
                    shuffleStartedFrom = sourceKey
                    if (!shouldKeepPlaying()) play()
                    syncState()
                    return
                }
            } else {
                val saved = savedShuffles.take(sourceKey)
                if (saved != null && resumeSavedShuffle(songs, label, saved)) {
                    shuffleStartedFrom = sourceKey
                    loadAndPlay(queueManager.current)
                    return
                }
            }
        }
        newSeed()?.let { currentShuffleSeed = it }
        playSongs(songs, label, PlayOrder.SHUFFLED, sourceKey = sourceKey)
        // Pressed again on its own list, the old shuffle was just set aside - but it's been
        // replaced, not left for something else, and would have come back after the new one was
        // turned off.
        sourceKey?.let(savedShuffles::remove)
        shuffleStartedFrom = sourceKey
    }

    /** Swaps the queue for [saved], setting aside the shuffle it replaces; false if [saved] has nothing left. */
    private fun resumeSavedShuffle(songs: List<Song>, label: String, saved: SavedShuffle): Boolean {
        // Set aside first, while it's still the queue. If [saved] turns out to have nothing left,
        // whatever starts instead sets the same shuffle aside again, which changes nothing.
        setAsideShuffle()
        if (!queueManager.resumeShuffle(songs, label, saved, currentShuffleSeed)) return false
        shuffleStartedFrom = null
        return true
    }

    private fun jumpWithinShuffle(song: Song) {
        shuffleStartedFrom = null
        queueManager.jumpTo(song)
        loadAndPlay(song)
    }

    /**
     * Keeps the queue's shuffle for its list, just before something else replaces it. A finished
     * one is forgotten instead: its list's next shuffle should be a new one, not the last song again.
     */
    private fun setAsideShuffle() {
        if (!preserveShuffleProgress || queueManager.playOrder != PlayOrder.SHUFFLED) return
        val key = queueManager.sourceKey ?: return
        val progress = queueManager.shuffleProgress()
        if (progress != null) savedShuffles.put(progress) else savedShuffles.remove(key)
    }

    /** The list being shuffled, if one is; see [addNewSongs]. */
    fun shuffledSourceKey(): String? = queueManager.sourceKey?.takeIf { queueManager.playOrder == PlayOrder.SHUFFLED }

    /**
     * Deals songs that have joined [sourceKey] - a rescan, files added, a new favourite or tag -
     * into the songs still to come, if that list's shuffle is what's playing. Without this they
     * only joined once the shuffle was set aside and picked up again.
     */
    fun addNewSongs(sourceKey: String, songs: List<Song>) {
        if (!preserveShuffleProgress || queueManager.sourceKey != sourceKey) return
        if (queueManager.addNewSongs(songs, currentShuffleSeed)) {
            syncUpcomingWindow()
            syncState()
        }
    }

    fun togglePlayPause() {
        // play() only sets playWhenReady; in STATE_IDLE - where any earlier failure left the
        // player - it has no effect at all until the player is prepared again, which is what
        // [play] handles.
        if (isPlayingOrAboutTo()) pause() else play()
    }

    /**
     * Playing, or buffering on the way to it. The play/pause button follows this rather than
     * isPlaying, which drops to false for a moment whenever a new song or a seek buffers - the
     * button flickered to "play" on every skip, and a tap in that moment played instead of paused.
     */
    private fun isPlayingOrAboutTo(): Boolean =
        player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)

    /**
     * Resumes playback, re-preparing first if an earlier failure left the player idle. Not while
     * audio focus is refused or put off - see [gainFocusToStart].
     */
    fun play() {
        // Started before the saved queue was read back - a headset's play button starting the
        // app from cold. Played once it's there, rather than pressing play on nothing.
        if (queueManager.current == null && !restoreAttempted) {
            playWhenRestored = true
            return
        }
        if (!gainFocusToStart()) return
        // The fade that ended the last sleep timer would otherwise leave this silent until the
        // next tick put the volume back.
        updateSleepFade()
        if (player.playbackState == Player.STATE_IDLE) {
            retriesForCurrentItem = 0
            runCatching { player.prepare() }
        }
        // An ended player ignores play() until it's moved back into the song.
        if (player.playbackState == Player.STATE_ENDED) runCatching { player.seekTo(0) }
        player.play()
        // Both halves of an interrupted overlap resume together.
        if (fadeProgress != null) standby.player.play()
    }

    /**
     * Asks for audio focus before playback starts, and says whether it may. Refused, the music
     * stays paused, saying why; put off, it starts by itself once focus arrives (the focus
     * controller's onDelayedGain) unless it's paused before then.
     */
    private fun gainFocusToStart(): Boolean {
        when (focusController.requestFocus()) {
            FocusResult.GRANTED -> return true
            FocusResult.DELAYED -> notice = WAITING_FOR_FOCUS
            FocusResult.DENIED -> notice = "Can't play while another app, such as a call, has the audio"
        }
        syncState()
        return false
    }

    /**
     * Stops playback deliberately - the user, the sleep timer, or an output device change. Any
     * pending auto-resume is cancelled, so an interruption that arrives while stopped can't start
     * the music back up when it ends.
     */
    fun pause() {
        focusController.onPlaybackPausedDeliberately()
        // It won't start by itself any more, so it mustn't say it will.
        if (notice == WAITING_FOR_FOCUS) notice = null
        pausePlayers()
        rememberPosition(loadedSong, player.currentPosition, force = true)
        // Saved straight away rather than at the next periodic save: a pause is exactly the point
        // someone expects to come back to.
        persistNow()
    }

    private fun pausePlayers() {
        slots.forEach { runCatching { it.player.pause() } }
    }

    fun seekTo(positionMs: Long) {
        // Seeking away from the tail of a track makes an in-flight overlap meaningless.
        cancelCrossfade()
        // A DJ mix planned from where the song was is planned again from where it is now, and a
        // tempo ramp already under way eases back.
        if (mixPlanSongs != null) {
            mixPlanSongs = null
            releaseTempo(active, beatMs = 0.0)
        }
        val duration = player.duration
        // Held just short of the end: landing exactly on it ends the track and advances the queue,
        // so dragging a seek bar fully to the right skipped the song - and with the finger still
        // down, went on skipping through the ones after it.
        val target = if (duration > 0) {
            positionMs.coerceIn(0, (duration - SEEK_END_GUARD_MS).coerceAtLeast(0))
        } else {
            positionMs.coerceAtLeast(0)
        }
        player.seekTo(target)
        // The player reports the new position straight away; pushed out here because nothing
        // else refreshes the UI while paused.
        syncState()
        persistNow()
    }

    // --- Persistence --------------------------------------------------------------------------

    /**
     * Puts the saved queue and position back, once per process, leaving playback paused -
     * resuming on its own when the app opens would be startling.
     *
     * Once per process, and only when nothing is loaded, because the UI asks for this every time
     * it's created. Reopening the app while music is still playing in the background used to
     * re-run the restore and yank playback back to the last saved point.
     *
     * @param lookup resolves saved song ids to songs; ids that no longer exist are simply missing.
     * @param restartFromBeginning resume the saved song from 0:00 rather than where it was.
     */
    suspend fun restoreIfNeeded(
        lookup: suspend (List<Long>) -> List<Song>,
        restartFromBeginning: Boolean,
    ) = restoreLock.withLock {
        // Locked: the media service (started by a headset button) and the UI can both ask at
        // once, and the second has to wait for the first rather than restore all over again.
        if (restoreAttempted) return@withLock
        restore(lookup, restartFromBeginning)
    }

    private val restoreLock = Mutex()

    private suspend fun restore(
        lookup: suspend (List<Long>) -> List<Song>,
        restartFromBeginning: Boolean,
    ) {
        try {
            if (queueManager.current != null) return
            val saved = runSuspendCatching { stateRepository.loadQueue() }.getOrNull() ?: return
            val ids = buildSet {
                addAll(saved.poolSongIds)
                addAll(saved.queuedSongIds)
                addAll(saved.upcomingSongIds)
                addAll(saved.historySongIds)
                saved.currentSongId?.let(::add)
            }
            if (ids.isEmpty()) return

            val external = saved.externalSongs.associate { it.id to it.toSong() }
            val byId = lookup(ids.filter { it !in external }).associateBy { it.id } + external
            // The user may have started something themselves while the lookup was running; what
            // they picked wins over what was saved.
            if (queueManager.current != null) return

            val savedCurrent = saved.currentSongId?.let(byId::get)
            val resumeAt = if (savedCurrent == null || restartFromBeginning) {
                0L
            } else {
                runSuspendCatching { stateRepository.loadPosition() }.getOrNull()
                    ?.takeIf { it.songId == savedCurrent.id }
                    ?.positionMs ?: 0L
            }
            // Always off: the repeat button is gone, and a queue saved with repeat on would have
            // kept looping with no way left to stop it.
            val repeat = RepeatMode.OFF
            queueManager.restore(
                pool = saved.poolSongIds.mapNotNull(byId::get),
                label = saved.playlistLabel,
                current = savedCurrent,
                queued = saved.queuedSongIds.mapNotNull(byId::get),
                upcomingSongs = saved.upcomingSongIds.mapNotNull(byId::get),
                played = saved.historySongIds.mapNotNull(byId::get),
                repeat = repeat,
                order = if (saved.shuffled) PlayOrder.SHUFFLED else PlayOrder.SEQUENTIAL,
                sourceKey = saved.sourceKey,
            )
            // If the song that was playing has since been deleted, carry on from whatever was due
            // next rather than restoring nothing at all.
            val current = savedCurrent ?: queueManager.dropCurrent()
            if (current == null) {
                syncState()
                return
            }
            loadAndPlay(current, autoPlay = false)
            if (resumeAt > 0L) runCatching { player.seekTo(resumeAt) }
            syncState()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("PlaybackEngine", "Could not restore the saved queue", t)
        } finally {
            restoreAttempted = true
            if (playWhenRestored) {
                playWhenRestored = false
                if (queueManager.current != null) play()
            }
        }
    }

    /**
     * For "Restart song on open" when the app is reopened while its process is still alive: the
     * paused song goes back to 0:00. Something still playing is left alone - reopening the app to
     * look at it shouldn't restart the song you're listening to.
     */
    fun restartCurrentIfPaused() {
        if (queueManager.current == null || player.playWhenReady) return
        seekTo(0L)
    }

    /**
     * Saves the queue once the change that prompted this has finished. On the plain main
     * dispatcher, not the engine's immediate one: run inline, it saved a queue caught halfway
     * through being rebuilt - a new playlist's pool with the old current song still in place.
     */
    private fun scheduleQueueSave() {
        if (queueSaveScheduled) return
        queueSaveScheduled = true
        engineScope.launch(Dispatchers.Main) {
            queueSaveScheduled = false
            persistState()
        }
    }

    /** Saves now rather than at the next periodic save - for pauses, seeks and leaving the app. */
    fun persistNow() {
        engineScope.launch { persistState() }
    }

    /**
     * Writes the queue if it has changed since the last save, and the position if it has moved.
     * Called every couple of seconds and on demand, so a process death loses at most that much.
     */
    private suspend fun persistState() {
        if (!restoreAttempted) return
        persistMutex.withLock {
            val version = queueManager.version
            if (version != lastSavedQueueVersion) {
                val snapshot = SavedQueue(
                    currentSongId = queueManager.current?.id,
                    playlistLabel = queueManager.playlistLabel,
                    poolSongIds = queueManager.poolSnapshot().map { it.id },
                    queuedSongIds = queueManager.explicitQueue.map { it.id },
                    upcomingSongIds = queueManager.upcoming.map { it.id },
                    historySongIds = queueManager.historySnapshot().map { it.id },
                    repeatMode = queueManager.repeatMode.name,
                    shuffled = queueManager.playOrder == PlayOrder.SHUFFLED,
                    sourceKey = queueManager.sourceKey,
                    externalSongs = savableExternalSongs(),
                )
                runSuspendCatching { stateRepository.saveQueue(snapshot) }
                    .onSuccess { lastSavedQueueVersion = version }
                    .onFailure { Log.w("PlaybackEngine", "Could not save the queue", it) }
            }

            val current = queueManager.current ?: return@withLock
            rememberPosition(loadedSong, player.currentPosition, force = false)
            val position = SavedPosition(current.id, player.currentPosition.coerceAtLeast(0L))
            if (position != lastSavedPosition) {
                runSuspendCatching { stateRepository.savePosition(position) }
                    .onSuccess { lastSavedPosition = position }
                    .onFailure { Log.w("PlaybackEngine", "Could not save the position", it) }
            }
        }
    }

    /**
     * Songs opened from another app that can still be read after a restart. The rest are left out,
     * so a restored queue doesn't lead with a file the app is no longer allowed to open.
     */
    private fun savableExternalSongs(): List<SavedExternalSong> {
        val songs = buildList {
            queueManager.current?.let(::add)
            addAll(queueManager.poolSnapshot())
            addAll(queueManager.explicitQueue)
            addAll(queueManager.upcoming)
            addAll(queueManager.historySnapshot())
        }.filter { it.isExternal }.distinctBy { it.id }
        if (songs.isEmpty()) return emptyList()
        return songs
            .filter { runCatching { ExternalSongs.survivesRestart(appContext, Uri.parse(it.documentUri)) }.getOrDefault(false) }
            .map { SavedExternalSong(it.id, it.documentUri, it.fileName, it.displayName, it.artist, it.durationMs) }
    }

    private fun SavedExternalSong.toSong() = Song(
        id = id,
        playlistId = EXTERNAL_PLAYLIST_ID,
        documentUri = uri,
        fileName = fileName,
        displayName = displayName,
        artist = artist,
        durationMs = durationMs,
        sizeBytes = 0L,
        lastModified = 0L,
        sortIndex = 0L,
    )

    // --- Queue maintenance --------------------------------------------------------------------

    /**
     * Takes songs that no longer exist out of the queue - a deleted playlist, a song moved to the
     * removed-songs folder, a file deleted outside the app. Left in place they'd fail as soon as
     * playback reached them.
     *
     * If the song that's playing is one of them, playback moves on to whatever was next, keeping
     * its play/pause state.
     */
    fun dropSongs(predicate: (Song) -> Boolean) {
        val currentGone = queueManager.current?.let(predicate) == true
        queueManager.removeSongs(predicate)
        if (currentGone) {
            val wasPlaying = shouldKeepPlaying()
            cancelCrossfade()
            loadAndPlay(queueManager.dropCurrent(), autoPlay = wasPlaying)
        } else {
            syncUpcomingWindow()
        }
        syncState()
    }

    /** Applies [transform] to one song wherever it sits in the queue - e.g. after a rename. */
    fun updateSong(songId: Long, transform: (Song) -> Song) {
        queueManager.updateSongs { if (it.id == songId) transform(it) else it }
        syncState()
    }

    /**
     * Whether playback is meant to be running - true while buffering towards playing, too. Track
     * changes carry this over: skipping while paused shows the new song but stays paused, rather
     * than starting the music on its own.
     */
    private fun shouldKeepPlaying(): Boolean = player.playWhenReady

    /** Whether there's anything after the current song, counting a repeat-all wrap. */
    private fun hasNextSong(): Boolean =
        queueManager.peekNext() != null ||
            (queueManager.repeatMode == RepeatMode.ALL && queueManager.hasActiveSource())

    fun skipToNext() {
        // At the end of the queue there's nowhere to go. Moving on anyway used to clear the player
        // entirely, losing the song and leaving nothing to press play on.
        if (queueManager.current != null && !hasNextSong()) return
        val keepPlaying = shouldKeepPlaying()
        cancelCrossfade()
        advanceTo(autoPlay = keepPlaying)
    }

    fun skipToPrevious() {
        val keepPlaying = shouldKeepPlaying()
        cancelCrossfade()
        // Restart current song if we're more than a couple seconds in, matching common player UX;
        // otherwise go back in history.
        if (player.currentPosition > 3000L || !queueManager.hasHistory()) {
            seekTo(0L)
            return
        }
        val previous = queueManager.goToPrevious()
        loadAndPlay(previous, autoPlay = keepPlaying)
    }

    /**
     * The last song of the queue has finished: stay on it, rewound and paused, so it can be played
     * again. Clearing the player here left the Play screen empty, as though nothing had been
     * started.
     */
    private fun stopAtEndOfQueue() {
        pause()
        runCatching { player.seekTo(0) }
        // Nothing more is coming, so other apps can have the audio back.
        focusController.abandonFocus()
        syncState()
    }

    fun queueSong(song: Song) {
        // With nothing loaded there's no "next" to queue behind: start it instead.
        if (queueManager.current == null) {
            playSongs(listOf(song), song.displayName, PlayOrder.SEQUENTIAL)
            return
        }
        queueManager.queueSong(song)
        syncUpcomingWindow()
        syncState()
    }

    fun playNext(song: Song) {
        if (queueManager.current == null) {
            playSongs(listOf(song), song.displayName, PlayOrder.SEQUENTIAL)
            return
        }
        queueManager.playNext(song)
        syncUpcomingWindow()
        syncState()
    }

    fun replaceUpcomingSlot(visibleIndex: Int) {
        queueManager.replaceUpcomingSlot(visibleIndex)
        syncUpcomingWindow()
        syncState()
    }

    fun reorderUpcoming(from: Int, to: Int) {
        queueManager.reorderUpcoming(from, to)
        syncUpcomingWindow()
        syncState()
    }

    fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
        applyRepeatModeToPlayer()
        syncUpcomingWindow()
        syncState()
    }

    /**
     * Repeat-one is handed to the player itself, which loops the item seamlessly rather than
     * tearing down and reloading it. OFF/ALL stay ours, since the queue - with its shuffle order
     * and user-queued songs - is the only thing that knows what "all" means.
     *
     * Re-applied whenever a song is loaded, because the active player changes at every crossfade.
     * A pending sleep-timer stop overrides it: a looping song never ends, so the timer would never
     * get the end-of-song it's waiting for.
     */
    private fun applyRepeatModeToPlayer() {
        val loopCurrent = queueManager.repeatMode == RepeatMode.ONE &&
            !sleepTimerManager.isStopAfterCurrentSongPending()
        runCatching {
            player.repeatMode = if (loopCurrent) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Moves past a song that can't be played - missing, unreadable, or in a format we can't
     * decode - instead of stopping dead on it with the next song never starting.
     */
    private fun skipUnplayableSong() {
        consecutiveUnplayable++
        // A run this long means it isn't one bad file - most likely the whole folder has become
        // unreadable - so stop rather than racing through the entire queue failing on every song.
        if (consecutiveUnplayable > MAX_CONSECUTIVE_UNPLAYABLE) {
            Log.w("PlaybackEngine", "$consecutiveUnplayable unplayable songs in a row - stopping")
            consecutiveUnplayable = 0
            pausePlayers()
            return
        }
        val keepPlaying = player.playWhenReady
        loadAndPlay(queueManager.advanceToNext(currentShuffleSeed), autoPlay = keepPlaying)
    }

    /** Mirrors the shuffle seed lock, for [switchShuffle]; set by the ViewModel, which reads the settings. */
    var shuffleSeedLocked: Boolean = false

    /** Saves a seed picked by [switchShuffle]; set by the ViewModel, which owns the settings. */
    var onShuffleSeedGenerated: ((String) -> Unit)? = null

    private val shuffleObservers = mutableListOf<(Boolean) -> Unit>()

    /** Told whenever shuffle goes on or off, for media controllers showing it; main thread only. */
    fun addShuffleObserver(observer: (Boolean) -> Unit) {
        shuffleObservers += observer
    }

    fun removeShuffleObserver(observer: (Boolean) -> Unit) {
        shuffleObservers -= observer
    }

    /**
     * The shuffle switch, from the Play screen or a media controller (a car, a watch). Turned on,
     * it gets a new seed - unless the seed is locked - so off and on again is a new order rather
     * than the same one dealt again.
     */
    fun switchShuffle(enabled: Boolean) {
        val seed = if (enabled && !shuffleSeedLocked) SettingsRepository.generateShuffleSeed() else null
        setShuffleEnabled(enabled, seed)
        seed?.let { onShuffleSeedGenerated?.invoke(it) }
    }

    /** @param newSeed a fresh seed to shuffle with, so turning shuffle off and on again reshuffles. */
    fun setShuffleEnabled(enabled: Boolean, newSeed: String? = null) {
        newSeed?.let { currentShuffleSeed = it }
        // Whatever the list's Shuffle button started is no longer the queue's order.
        shuffleStartedFrom = null
        queueManager.setShuffleEnabled(enabled, currentShuffleSeed)
        // Without this the player kept the song preloaded under the old order and rolled into it,
        // while the queue moved on to the first song of the new order - playing one song while
        // reporting another.
        syncUpcomingWindow()
        syncState()
    }

    private fun advanceTo(autoPlay: Boolean) {
        val next = queueManager.advanceToNext(currentShuffleSeed)
        loadAndPlay(next, autoPlay = autoPlay)
    }

    private fun loadAndPlay(song: Song?, autoPlay: Boolean = true) {
        // Called directly from UI click handlers (not a coroutine), so nothing else catches
        // exceptions here - an unexpected throw (a malformed URI, the player having been released,
        // etc.) would otherwise crash the app the moment the user taps play.
        try {
            cancelCrossfade()
            if (song == null) {
                pause()
                slots.forEach {
                    runCatching {
                        it.player.stop()
                        it.player.clearMediaItems()
                    }
                }
                focusController.abandonFocus()
                loadedSong = null
                syncState()
                return
            }
            // Leaving a long track part-way through: note where, for next time.
            if (loadedSong?.id != song.id) rememberPosition(loadedSong, player.currentPosition, force = true)
            loadedSong = song
            // Whatever a DJ mix left on this deck - a tempo easing back, a filter - goes; the new
            // song plays as it is.
            active.resetMix()
            mixPlanSongs = null
            retriesForCurrentItem = 0
            lastError = null
            rebuildingWindow = true
            val start = autoPlay && gainFocusToStart()
            player.setMediaItems(windowFor(song), 0, resumePositionFor(song))
            applyRepeatModeToPlayer()
            player.prepare()
            // Loaded but held when focus didn't come: the new song mustn't roll on with the old
            // one's play state, over a phone call.
            if (start) player.play() else if (autoPlay) player.pause()
            rebuildingWindow = false
            applyVolumes()
            syncState()
        } catch (t: Throwable) {
            rebuildingWindow = false
            Log.e("PlaybackEngine", "loadAndPlay failed", t)
        }
    }

    /**
     * With crossfade off, the player carries the following song too, so ExoPlayer can roll into it
     * without stopping the pipeline. With crossfade on it must not - the incoming track is started
     * deliberately, on the other player, and a preloaded second item would play it twice.
     */
    private fun windowFor(song: Song): List<MediaItem> =
        // Also single when the sleep timer is waiting for this song to finish: a preloaded next
        // item would carry playback straight past the point it's meant to stop.
        if (crossfadeMs > 0 || sleepTimerManager.isStopAfterCurrentSongPending()) {
            listOf(mediaItemFor(song))
        } else {
            listOfNotNull(mediaItemFor(song), queueManager.peekNext()?.takeIf(::preloadable)?.let(::mediaItemFor))
        }

    private fun mediaItemFor(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(Uri.parse(song.documentUri))
        // Carried through to the system media notification and lock screen.
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.displayName)
                .setArtist(song.artist)
                .build(),
        )
        .build()

    /**
     * Keeps the active player holding exactly [current, next]: drops anything already played, and
     * makes the trailing item match whatever the queue now says comes next. The trailing item is
     * only replaced when it actually differs, so re-queuing doesn't disturb buffered audio.
     */
    private fun updateWindowForCurrentMode() {
        if (crossfadeMs == 0) syncUpcomingWindow() else dropPreloadedNext()
    }

    /** Leaves only the current song loaded, so it ends rather than rolling into another. */
    private fun dropPreloadedNext() {
        try {
            if (player.mediaItemCount == 0) return
            rebuildingWindow = true
            val firstTrailing = player.currentMediaItemIndex + 1
            if (player.mediaItemCount > firstTrailing) {
                player.removeMediaItems(firstTrailing, player.mediaItemCount)
            }
        } catch (t: Throwable) {
            Log.e("PlaybackEngine", "Failed to drop the preloaded item", t)
        } finally {
            rebuildingWindow = false
        }
    }

    private fun syncUpcomingWindow() {
        if (crossfadeMs > 0) return // no preloaded window in crossfade mode
        // The current song is meant to be the last one; don't queue anything behind it.
        if (sleepTimerManager.isStopAfterCurrentSongPending()) return
        try {
            if (player.mediaItemCount == 0) return
            rebuildingWindow = true
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex > 0) player.removeMediaItems(0, currentIndex)

            val desiredNext = queueManager.peekNext()?.takeIf(::preloadable)
            val existingNextId = if (player.mediaItemCount > 1) player.getMediaItemAt(1).mediaId else null
            val desiredNextId = desiredNext?.id?.toString()
            if (existingNextId != desiredNextId) {
                if (player.mediaItemCount > 1) player.removeMediaItems(1, player.mediaItemCount)
                desiredNext?.let { player.addMediaItem(mediaItemFor(it)) }
            }
        } catch (t: Throwable) {
            Log.e("PlaybackEngine", "Failed to refresh the queue window", t)
        } finally {
            rebuildingWindow = false
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "the file is missing"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "openplayer isn't allowed to read the file"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> "this audio format isn't supported"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "the file is damaged"
        else -> "something went wrong"
    }

    fun syncState() {
        val shuffleOn = queueManager.playOrder == PlayOrder.SHUFFLED
        if (shuffleOn != _uiState.value.shuffleOn) shuffleObservers.toList().forEach { it(shuffleOn) }
        _uiState.value = PlaybackUiState(
            currentSong = queueManager.current,
            // Only while something is loaded: once a playlist's songs are all gone (it was deleted
            // while playing, say) its name stayed in the header over an empty player.
            playlistLabel = if (queueManager.current != null) queueManager.playlistLabel else "",
            isPlaying = isPlayingOrAboutTo(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            // The library's stored length stands in until the player has read the file itself,
            // so a restored or just-loaded song doesn't read 0:00 / 0:00.
            durationMs = player.duration.takeIf { it > 0 }
                ?: queueManager.current?.durationMs?.coerceAtLeast(0) ?: 0L,
            repeatMode = queueManager.repeatMode,
            shuffleOn = shuffleOn,
            upcoming = queueManager.visibleUpcoming(),
            error = lastError,
            notice = notice,
        )
    }

    companion object {
        private const val WAITING_FOR_FOCUS = "Will play once the other audio, such as a call, ends"

        /** Tick while a crossfade is running, which moves the gains in small steps. */
        private const val TICK_MS = 50L

        /** Tick while the time readout is on screen - about four refreshes a second. */
        private const val UI_TICK_MS = 250L

        /** Tick in the background, where nothing shows the position. */
        private const val BACKGROUND_TICK_MS = 1_000L

        // Two, so there's one attempt left after the DSP has been dropped from the pipeline.
        private const val MAX_RETRIES_PER_ITEM = 2

        /** Level to drop to when another app asks us to duck rather than stop. */
        private const val DUCKED_GAIN = 0.2f

        /** How far before the end of a track a seek is capped; see [seekTo]. */
        private const val SEEK_END_GUARD_MS = 500L

        /** The longest gap between ticks a tempo glide counts; more means the app was asleep. */
        private const val MAX_TICK_GAP_MS = 2_000L

        /** How far past a planned mix's start the plan still stands before it's made again. */
        private const val MISSED_PLAN_SLACK_MS = 250.0

        /** Roughly how long a player takes from play() to its first sound; see [mixStartLeadMs]. */
        private const val MIX_START_LATENCY_MS = 120.0

        // The beat lock; see lockBeats.
        private const val CLOCK_SETTLE_MS = 300L
        private const val SHIFT_SETTLE_MS = 900L
        private const val LOCK_READINGS = 9
        private const val SHIFT_READINGS = 7
        private const val MAX_LOCK_SHIFTS = 4

        /** Below this level the incoming song is quiet enough to be moved unheard. */
        private const val QUIET_GAIN = 0.3
        private const val LOCK_SHIFT_MIN_MS = 4.0

        /** Once it's audible, only an error this big - a clear flam - is worth a jump. */
        private const val RESCUE_SHIFT_MIN_MS = 35.0
        private const val LOCK_SMOOTHING = 0.15
        private const val LOCK_DEADBAND_MS = 3.0
        private const val LOCK_GAIN_PER_MS = 0.0003
        private const val LOCK_MAX_CORRECTION = 0.008

        /** Below this much remaining, a track just finishes rather than starting an overlap. */
        private const val MIN_CROSSFADE_MS = 1_500L

        /** How often the position is saved while playing; the most a sudden process death can lose. */
        private const val PERSIST_INTERVAL_MS = 2_000L

        /** Tracks at least this long remember where they were left off. */
        private const val LONG_TRACK_MS = 20 * 60_000L

        /** A remembered position this close to either end counts as "from the start". */
        private const val RESUME_EDGE_MS = 10_000L

        /** How far a long track has to move before its position is saved again while playing. */
        private const val RESUME_SAVE_STEP_MS = 15_000L

        /** How long the sleep timer's fade-out lasts. */
        private const val SLEEP_FADE_MS = 30_000L

        /** Unplayable songs skipped in a row before playback gives up; see [skipUnplayableSong]. */
        private const val MAX_CONSECUTIVE_UNPLAYABLE = 5

        /** Errors that mean this particular file will never play, so retrying it is pointless. */
        private val UNPLAYABLE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )

        @Volatile private var instance: PlaybackEngine? = null

        fun get(context: Context): PlaybackEngine =
            instance ?: synchronized(this) {
                instance ?: PlaybackEngine(context.applicationContext).also { instance = it }
            }
    }
}
