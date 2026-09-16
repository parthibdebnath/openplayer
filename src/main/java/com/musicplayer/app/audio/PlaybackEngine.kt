package com.musicplayer.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.settings.EffectsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val playlistLabel: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleOn: Boolean = false,
    val upcoming: List<Song> = emptyList(),
    /** Raw engine state, surfaced on screen while playback problems are being diagnosed. */
    val diagnostics: String = "",
)

/**
 * One player plus the DSP chain feeding it. Audio processors hold per-stream buffers and are
 * configured and flushed by the sink that owns them, so the two players cannot share a set.
 */
private class PlayerSlot(context: Context, audioAttributes: AudioAttributes) {
    val reverb = ReverbAudioProcessor()
    val threeD = ThreeDAudioProcessor()

    val player: ExoPlayer = ExoPlayer.Builder(context, CustomRenderersFactory(context, reverb, threeD))
        // Focus is handled centrally by AudioFocusController instead of per-player - see there.
        .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
        .setHandleAudioBecomingNoisy(true)
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
 * [com.musicplayer.app.playback.PlaybackService] can point its MediaSession at the right one.
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
            // carried straight on and the timer never took effect.
            cancelCrossfade()
            dropPreloadedNext()
        },
    )

    private val focusController = AudioFocusController(
        context = appContext,
        onPauseForFocusLoss = {
            // Reports whether this actually interrupted playback, which is what decides if the
            // interruption should resume us afterwards.
            val wasPlaying = slots.any { it.player.isPlaying }
            if (wasPlaying) pausePlayers()
            wasPlaying
        },
        onResumeAfterFocusLoss = { player.play() },
        onDuckingChanged = { ducking ->
            duckGain = if (ducking) DUCKED_GAIN else 1f
            applyVolumes()
        },
    )

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
    private var lastErrorCause: String? = null
    private var dspBypassedAfterFailure = false
    private var lastPlayWhenReadyReason: Int = -1
    private var retriesForCurrentItem = 0
    private var tickCount = 0L
    private var duckGain: Float = 1f

    /** Guards against reacting to transitions we caused ourselves while rewriting the queue window. */
    private var rebuildingWindow = false

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

    init {
        outputDeviceManager.start()
        slots.forEachIndexed { index, slot -> slot.player.addListener(listenerFor(index)) }

        // Position doesn't advance on its own: ExoPlayer reports discrete events, not a running
        // clock, so without this tick the time readout and the seek bar never moved.
        engineScope.launch {
            while (true) {
                advanceCrossfade()
                if (tickCount++ % SYNC_EVERY_N_TICKS == 0L) syncState()
                delay(TICK_MS)
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
                } else {
                    advanceAndPlayNext()
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
            if (isActiveSlot) syncState()
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
            lastError = error.errorCodeName
            lastErrorCause = error.cause?.let { "${it.javaClass.simpleName}:${it.message.orEmpty().take(60)}" }

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
            // playback permanently dead, with every later tap on play silently ignored.
            if (retriesForCurrentItem < MAX_RETRIES_PER_ITEM) {
                retriesForCurrentItem++
                Log.i("PlaybackEngine", "Retrying after error (attempt $retriesForCurrentItem)")
                runCatching {
                    player.prepare()
                    player.play()
                }
            }
            syncState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActiveSlot) return
            lastPlayWhenReadyReason = reason
            syncState()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (!isActiveSlot) return
            Log.i("PlaybackEngine", "playback suppressed, reason=$playbackSuppressionReason")
            syncState()
        }
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
        currentShuffleSeed = seed
    }

    fun refreshShuffleSeed(newSeed: String) {
        currentShuffleSeed = newSeed
        queueManager.rebuildUpcoming(newSeed)
        syncState()
    }

    fun playSongs(songs: List<Song>, label: String, order: PlayOrder, startIndex: Int? = null) {
        queueManager.startPlaylist(songs, label, order, currentShuffleSeed, startIndex)
        loadAndPlay(queueManager.current)
    }

    fun playSpecificSong(songs: List<Song>, label: String, song: Song) {
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(songs, label, PlayOrder.SEQUENTIAL, startIndex = index)
    }

    fun togglePlayPause() {
        // play() only sets playWhenReady; in STATE_IDLE - where any earlier failure left the
        // player - it has no effect at all until the player is prepared again, which is what
        // [play] handles.
        if (player.isPlaying) pause() else play()
    }

    /** Resumes playback, re-preparing first if an earlier failure left the player idle. */
    fun play() {
        focusController.requestFocus()
        if (player.playbackState == Player.STATE_IDLE) {
            retriesForCurrentItem = 0
            runCatching { player.prepare() }
        }
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
    }

    private fun pausePlayers() {
        slots.forEach { runCatching { it.player.pause() } }
    }

    /**
     * Puts a previously saved queue back in place and seeks to where it was, leaving playback
     * paused - resuming on its own when the app opens would be startling.
     */
    fun restore(
        pool: List<Song>,
        label: String,
        current: Song?,
        queued: List<Song>,
        upcomingSongs: List<Song>,
        repeat: RepeatMode,
        order: PlayOrder,
        positionMs: Long,
    ) {
        queueManager.restore(pool, label, current, queued, upcomingSongs, repeat, order)
        player.repeatMode = if (repeat == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (current == null) {
            syncState()
            return
        }
        loadAndPlay(current, autoPlay = false)
        runCatching { player.seekTo(positionMs) }
        syncState()
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
    }

    fun skipToNext() {
        cancelCrossfade()
        advanceAndPlayNext()
    }

    fun skipToPrevious() {
        cancelCrossfade()
        // Restart current song if we're more than a couple seconds in, matching common player UX;
        // otherwise go back in history.
        if (player.currentPosition > 3000L || !queueManager.hasHistory()) {
            player.seekTo(0)
            return
        }
        val previous = queueManager.goToPrevious()
        loadAndPlay(previous, autoPlay = true)
    }

    fun queueSong(song: Song) {
        queueManager.queueSong(song)
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
        // Repeat-one is handed to the player itself, which loops the item seamlessly rather than
        // tearing down and reloading it. OFF/ALL stay ours, since the queue - with its shuffle
        // order and user-queued songs - is the only thing that knows what "all" means.
        player.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        syncUpcomingWindow()
        syncState()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        queueManager.setShuffleEnabled(enabled, currentShuffleSeed)
        syncState()
    }

    private fun advanceAndPlayNext() {
        val next = queueManager.advanceToNext(currentShuffleSeed)
        loadAndPlay(next, autoPlay = true)
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
            lastErrorCause = null
            rebuildingWindow = true
            if (autoPlay) focusController.requestFocus()
            player.setMediaItems(windowFor(song))
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
        if (crossfadeMs > 0) {
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

    fun syncState() {
        val stateLabel = when (player.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "?"
        }
        _uiState.value = PlaybackUiState(
            currentSong = queueManager.current,
            playlistLabel = queueManager.playlistLabel,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = queueManager.repeatMode,
            shuffleOn = queueManager.playOrder == PlayOrder.SHUFFLED,
            upcoming = queueManager.visibleUpcoming(),
            diagnostics = buildString {
                append(stateLabel)
                append(" pwr=").append(player.playWhenReady)
                append(" sup=").append(player.playbackSuppressionReason)
                append(" pwrWhy=").append(lastPlayWhenReadyReason)
                append(" vol=").append(player.volume)
                lastError?.let { append(" err=").append(it) }
                lastErrorCause?.let { append(" cause=").append(it) }
            },
        )
    }

    companion object {
        private const val TICK_MS = 50L
        private const val SYNC_EVERY_N_TICKS = 5L // -> UI state refreshes ~4x a second

        // Two, so there's one attempt left after the DSP has been dropped from the pipeline.
        private const val MAX_RETRIES_PER_ITEM = 2

        /** Level to drop to when another app asks us to duck rather than stop. */
        private const val DUCKED_GAIN = 0.2f

        /** How far before the end of a track a seek is capped; see [seekTo]. */
        private const val SEEK_END_GUARD_MS = 500L

        /** Below this much remaining, a track just finishes rather than starting an overlap. */
        private const val MIN_CROSSFADE_MS = 1_500L

        @Volatile private var instance: PlaybackEngine? = null

        fun get(context: Context): PlaybackEngine =
            instance ?: synchronized(this) {
                instance ?: PlaybackEngine(context.applicationContext).also { instance = it }
            }
    }
}
