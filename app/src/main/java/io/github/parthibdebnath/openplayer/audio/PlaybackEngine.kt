@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
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
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import io.github.parthibdebnath.openplayer.data.settings.OtherAppSound
import io.github.parthibdebnath.openplayer.data.settings.PlaybackStateRepository
import io.github.parthibdebnath.openplayer.data.settings.SavedPosition
import io.github.parthibdebnath.openplayer.data.settings.SavedExternalSong
import io.github.parthibdebnath.openplayer.data.settings.SavedQueue
import io.github.parthibdebnath.openplayer.data.repository.EXTERNAL_PLAYLIST_ID
import io.github.parthibdebnath.openplayer.data.repository.ExternalSongs
import io.github.parthibdebnath.openplayer.data.repository.isExternal
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
    val reverb = ReverbAudioProcessor()
    val threeD = ThreeDAudioProcessor()

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        CustomRenderersFactory(context, reverb, threeD),
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
    private var lastError: String? = null
    private var notice: String? = null
    private var dspBypassedAfterFailure = false
    private var retriesForCurrentItem = 0
    private var duckGain: Float = 1f

    /** Guards against reacting to transitions we caused ourselves while rewriting the queue window. */
    private var rebuildingWindow = false

    /** Unplayable songs skipped in a row; see [skipUnplayableSong]. */
    private var consecutiveUnplayable = 0

    /** Overlap length in milliseconds; zero disables crossfading and re-enables the gapless window. */
    @Volatile var crossfadeMs: Int = 0
        set(value) {
            val changed = field != value
            field = value
            // The two modes need different things loaded in the player, so switching between them
            // mid-song has to rewrite what's queued - otherwise turning crossfade on would leave a
            // preloaded next track that plays at the same time as the one being faded in.
            if (changed) updateWindowForCurrentMode()
        }

    /** 0f..1f through the current overlap, or null when no crossfade is running. */
    private var fadeProgress: Float? = null

    /** Length of the overlap actually in progress - capped to what was left of the outgoing track. */
    private var activeFadeDurationMs: Int = 0

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

    /**
     * Whether either player is playing. The tick and save loops wait on this rather than running
     * all the time: with nothing playing there's nothing to update, and waking up twenty times a
     * second regardless kept the app busy in the background for as long as it stayed in memory.
     */
    private val anyPlaying = MutableStateFlow(false)

    init {
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
                advanceCrossfade()
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
                queueManager.advanceToNext(currentShuffleSeed)
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
                    it.reverb.forceBypass = true
                    it.threeD.forceBypass = true
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
        if (fadeProgress != null) return TICK_MS
        val idle = if (uiVisible) UI_TICK_MS else BACKGROUND_TICK_MS
        val fadeMs = crossfadeMs
        if (fadeMs > 0) {
            val playerRef = active.player
            val duration = playerRef.duration
            if (duration > 0) {
                val untilFade = duration - playerRef.currentPosition - fadeMs
                if (untilFade < idle) return untilFade.coerceIn(TICK_MS, idle)
            }
        }
        return idle
    }

    // --- Crossfade ----------------------------------------------------------------------------

    /**
     * Drives the overlap. Called every tick: starts a fade once the active track is within
     * [crossfadeMs] of its end, then advances both gains together until the incoming track has
     * fully replaced the outgoing one.
     *
     * Progress accumulates per tick rather than being derived from wall-clock time, so pausing
     * mid-fade holds it in place instead of skipping ahead.
     */
    private fun advanceCrossfade() {
        val fadeMs = crossfadeMs
        val progress = fadeProgress

        if (progress == null) {
            if (fadeMs > 0 && shouldStartCrossfade(fadeMs)) startCrossfade()
            return
        }

        if (!active.player.isPlaying && !standby.player.isPlaying) return // paused mid-fade

        val next = progress + TICK_MS.toFloat() / activeFadeDurationMs.coerceAtLeast(1)
        if (next >= 1f) {
            finishCrossfade()
            return
        }
        fadeProgress = next
        // Equal-power rather than linear: two linearly-crossfaded tracks sum to a dip in loudness
        // through the middle of the overlap, which is audible as the transition "sagging".
        // The incoming track is already the active slot by this point - see startCrossfade.
        active.fadeGain = kotlin.math.sin(next * (Math.PI / 2)).toFloat()
        standby.fadeGain = kotlin.math.cos(next * (Math.PI / 2)).toFloat()
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
        // A track has to be comfortably longer than the overlap for one to make sense.
        if (duration < fadeMs * 2L) return false
        if (queueManager.peekNext() == null) return false
        val remaining = duration - playerRef.currentPosition
        // Too little left to fade across - which is what seeking to the very end leaves - so let
        // the track simply finish instead of starting an overlap it can't complete.
        if (remaining < MIN_CROSSFADE_MS) return false
        return remaining <= fadeMs
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
            val remaining = (active.player.duration - active.player.currentPosition).coerceAtLeast(0)
            activeFadeDurationMs = minOf(crossfadeMs.toLong(), remaining).toInt()

            val incoming = standby
            incoming.fadeGain = 0f
            incoming.player.repeatMode = Player.REPEAT_MODE_OFF
            rebuildingWindow = true
            incoming.player.setMediaItem(mediaItemFor(next))
            incoming.player.prepare()
            applyVolumes()
            incoming.player.play()
            rebuildingWindow = false

            queueManager.advanceToNext(currentShuffleSeed)
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
            val target = (slot.fadeGain * duckGain).coerceIn(0f, 1f)
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

    fun playSongs(songs: List<Song>, label: String, order: PlayOrder, startIndex: Int? = null) {
        // Pressing play on a playlist that's still scanning, or genuinely empty, would otherwise
        // replace whatever is playing with nothing and stop it.
        if (songs.isEmpty()) return
        queueManager.startPlaylist(songs, label, order, currentShuffleSeed, startIndex)
        loadAndPlay(queueManager.current)
    }

    fun playSpecificSong(songs: List<Song>, label: String, song: Song) {
        // Tapping the song that's already loaded carries on with it rather than starting it over.
        if (queueManager.current?.id == song.id && queueManager.playlistLabel == label) {
            if (!player.playWhenReady) play()
            return
        }
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        // Keeps whichever order is in use: with shuffle on, the tapped song plays and the rest
        // follow shuffled. Always starting in order switched shuffle off on every tap.
        playSongs(songs, label, queueManager.playOrder, startIndex = index)
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

    /** Resumes playback, re-preparing first if an earlier failure left the player idle. */
    fun play() {
        focusController.requestFocus()
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
     * Stops playback deliberately - the user, the sleep timer, or an output device change. Any
     * pending auto-resume is cancelled, so an interruption that arrives while stopped can't start
     * the music back up when it ends.
     */
    fun pause() {
        focusController.onPlaybackPausedDeliberately()
        pausePlayers()
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
    ) {
        if (restoreAttempted) return
        try {
            if (queueManager.current != null) return
            val saved = runCatching { stateRepository.loadQueue() }.getOrNull() ?: return
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
                runCatching { stateRepository.loadPosition() }.getOrNull()
                    ?.takeIf { it.songId == savedCurrent.id }
                    ?.positionMs ?: 0L
            }
            val repeat = runCatching { RepeatMode.valueOf(saved.repeatMode) }.getOrDefault(RepeatMode.OFF)
            queueManager.restore(
                pool = saved.poolSongIds.mapNotNull(byId::get),
                label = saved.playlistLabel,
                current = savedCurrent,
                queued = saved.queuedSongIds.mapNotNull(byId::get),
                upcomingSongs = saved.upcomingSongIds.mapNotNull(byId::get),
                played = saved.historySongIds.mapNotNull(byId::get),
                repeat = repeat,
                order = if (saved.shuffled) PlayOrder.SHUFFLED else PlayOrder.SEQUENTIAL,
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
        } catch (t: Throwable) {
            Log.e("PlaybackEngine", "Could not restore the saved queue", t)
        } finally {
            restoreAttempted = true
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
                    externalSongs = savableExternalSongs(),
                )
                runCatching { stateRepository.saveQueue(snapshot) }
                    .onSuccess { lastSavedQueueVersion = version }
                    .onFailure { Log.w("PlaybackEngine", "Could not save the queue", it) }
            }

            val current = queueManager.current ?: return@withLock
            val position = SavedPosition(current.id, player.currentPosition.coerceAtLeast(0L))
            if (position != lastSavedPosition) {
                runCatching { stateRepository.savePosition(position) }
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

    fun setShuffleEnabled(enabled: Boolean) {
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
                syncState()
                return
            }
            retriesForCurrentItem = 0
            lastError = null
            rebuildingWindow = true
            if (autoPlay) focusController.requestFocus()
            player.setMediaItems(windowFor(song))
            applyRepeatModeToPlayer()
            player.prepare()
            if (autoPlay) player.play()
            rebuildingWindow = false
            applyVolumes()
            syncState()
        } catch (t: Throwable) {
            rebuildingWindow = false
            Log.e("PlaybackEngine", "loadAndPlay failed for ${song?.documentUri}", t)
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
            listOfNotNull(mediaItemFor(song), queueManager.peekNext()?.let(::mediaItemFor))
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

            val desiredNext = queueManager.peekNext()
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
        _uiState.value = PlaybackUiState(
            currentSong = queueManager.current,
            playlistLabel = queueManager.playlistLabel,
            isPlaying = isPlayingOrAboutTo(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            // The library's stored length stands in until the player has read the file itself,
            // so a restored or just-loaded song doesn't read 0:00 / 0:00.
            durationMs = player.duration.takeIf { it > 0 }
                ?: queueManager.current?.durationMs?.coerceAtLeast(0) ?: 0L,
            repeatMode = queueManager.repeatMode,
            shuffleOn = queueManager.playOrder == PlayOrder.SHUFFLED,
            upcoming = queueManager.visibleUpcoming(),
            error = lastError,
            notice = notice,
        )
    }

    companion object {
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

        /** Below this much remaining, a track just finishes rather than starting an overlap. */
        private const val MIN_CROSSFADE_MS = 1_500L

        /** How often the position is saved while playing; the most a sudden process death can lose. */
        private const val PERSIST_INTERVAL_MS = 2_000L

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
