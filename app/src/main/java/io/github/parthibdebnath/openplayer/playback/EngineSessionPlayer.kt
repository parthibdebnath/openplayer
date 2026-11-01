@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine

/**
 * The player handed to the MediaSession: the real ExoPlayer, with everything the notification and
 * lock screen can do routed back through [PlaybackEngine].
 *
 * Two problems this solves. First, the engine's queue lives in [io.github.parthibdebnath.openplayer.audio.PlaybackQueueManager],
 * not in ExoPlayer's playlist - ExoPlayer only ever holds the current song and (when crossfade is
 * off) the one after it. Media3 decides which transport buttons to show from the player's
 * available commands, so with a single loaded item it concluded there was nothing to skip to and
 * left the next button out entirely. Second, a skip or seek from the notification went straight to
 * ExoPlayer, moving it within that tiny window without the queue ever knowing, which desynced
 * what's playing from what the app thinks is playing.
 *
 * Declaring the skip commands as always available and forwarding them to the engine fixes both:
 * the buttons appear, and pressing them goes through the same path as pressing them in the app.
 *
 * The same goes for every other command a controller (a car, a watch, Assistant) can send: each
 * one either goes through the engine or isn't offered at all. Left to the wrapped player, a
 * speed change was undone by the next effects update, a stop or a new playlist from a car
 * changed what ExoPlayer held with the queue none the wiser, and shuffle switched ExoPlayer's own
 * shuffle over a two-song window - which could play the preloaded next song out of turn.
 */
class EngineSessionPlayer(
    player: Player,
    private val engine: PlaybackEngine,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands()
            .buildUpon()
            .addAll(*ALWAYS_AVAILABLE)
            .removeAll(*REFUSED_COMMANDS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        in ALWAYS_AVAILABLE -> true
        in REFUSED_COMMANDS -> false
        else -> super.isCommandAvailable(command)
    }

    // --- Listeners, so shuffle changes made through the engine are heard about -----------------

    private val listeners = mutableListOf<Player.Listener>()

    private val shuffleObserver: (Boolean) -> Unit = { enabled ->
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED).build())
        listeners.toList().forEach {
            it.onShuffleModeEnabledChanged(enabled)
            it.onEvents(this, events)
        }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        if (listeners.isEmpty()) engine.addShuffleObserver(shuffleObserver)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
        if (listeners.isEmpty()) engine.removeShuffleObserver(shuffleObserver)
    }

    // The queue's shuffle, not ExoPlayer's - see the class comment.
    override fun getShuffleModeEnabled(): Boolean = engine.uiState.value.shuffleOn

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) = engine.switchShuffle(shuffleModeEnabled)

    // Speed and pitch belong to the Speed and Pitch effects, which the engine reapplies on every
    // change - a controller's speed would last until then, with nothing in the app showing it.
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    // The player's own volume carries the crossfade, ducking and the sleep timer's fade.
    override fun setVolume(volume: Float) = Unit

    override fun mute() = Unit

    override fun unmute() = Unit

    // Stopping would drop ExoPlayer's media with the queue still pointing at the song. A pause
    // keeps the two together, and the notification with it.
    override fun stop() = engine.pause()

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()

    // The queue decides what plays; a controller's playlist would only replace ExoPlayer's window.
    override fun setMediaItem(mediaItem: MediaItem) = Unit

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) = Unit

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) = Unit

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) = Unit

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = Unit

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = Unit

    override fun addMediaItem(mediaItem: MediaItem) = Unit

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = Unit

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) = Unit

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) = Unit

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) = Unit

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) = Unit

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) = Unit

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) = Unit

    override fun removeMediaItem(index: Int) = Unit

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) = Unit

    override fun clearMediaItems() = Unit

    // The current song's start, through the engine like any other seek. Media3 uses this to
    // restart a song that has ended when play is pressed.
    override fun seekToDefaultPosition() = engine.seekTo(0L)

    // Another index would be the preloaded next song, moving ExoPlayer on without the queue.
    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (mediaItemIndex == currentMediaItemIndex) engine.seekTo(0L)
    }

    // The app has no repeat control, and repeat set from a car or watch would have gone to the
    // player directly, past the queue, looping a song with nothing in the app to turn it off.
    override fun setRepeatMode(repeatMode: Int) = Unit

    // ExoPlayer's seek-back increment defaults to exactly five seconds, and these commands are
    // reachable from media buttons, the lock screen and accessibility actions even though the app
    // has no rewind control of its own - which is where playback jumping back five seconds out of
    // nowhere came from. Refused outright rather than left to the wrapped player.
    override fun seekBack() = Unit

    override fun seekForward() = Unit

    // The queue always has somewhere to go, even when ExoPlayer's own playlist doesn't.
    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = engine.skipToNext()

    override fun seekToNextMediaItem() = engine.skipToNext()

    override fun seekToPrevious() = engine.skipToPrevious()

    override fun seekToPreviousMediaItem() = engine.skipToPrevious()

    // Routed through the engine so a seek from the notification gets the same treatment as one
    // from the app: any in-flight crossfade is cancelled and the end-of-track guard applies.
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = engine.seekTo(positionMs)

    override fun play() = engine.play()

    override fun pause() = engine.pause()

    private companion object {
        /** Handled by the engine whatever ExoPlayer's own window holds. */
        val ALWAYS_AVAILABLE = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SET_SHUFFLE_MODE,
        )

        /** Not offered to controllers; their methods above do nothing in case one is called anyway. */
        val REFUSED_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        )
    }
}
