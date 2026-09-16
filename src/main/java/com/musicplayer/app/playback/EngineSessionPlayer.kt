package com.musicplayer.app.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.musicplayer.app.audio.PlaybackEngine

/**
 * The player handed to the MediaSession: the real ExoPlayer, with everything the notification and
 * lock screen can do routed back through [PlaybackEngine].
 *
 * Two problems this solves. First, the engine's queue lives in [com.musicplayer.app.audio.PlaybackQueueManager],
 * not in ExoPlayer's playlist - ExoPlayer only ever holds the current song and (when crossfade is
 * off) the one after it. Media3 decides which transport buttons to show from the player's
 * available commands, so with a single loaded item it concluded there was nothing to skip to and
 * left the next button out entirely. Second, a skip or seek from the notification went straight to
 * ExoPlayer, moving it within that tiny window without the queue ever knowing, which desynced
 * what's playing from what the app thinks is playing.
 *
 * Declaring the skip commands as always available and forwarding them to the engine fixes both:
 * the buttons appear, and pressing them goes through the same path as pressing them in the app.
 */
class EngineSessionPlayer(
    player: Player,
    private val engine: PlaybackEngine,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*SKIP_COMMANDS).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in SKIP_COMMANDS || super.isCommandAvailable(command)

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
        val SKIP_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
