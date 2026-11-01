@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.mix

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * ExoPlayer's standard chain (our effects, then silence skipping, then its speed processor), with
 * one addition: it tells ExoPlayer how much of the song the audio that has played really was,
 * allowing for [tempo] having stretched or squeezed it. ExoPlayer turns played audio into a song
 * position through this; left to the standard chain, a mix that played the next song 3% fast for
 * thirty seconds would leave its position, and every beat worked out from it, the best part of a
 * second out.
 *
 * [tempo] must be one of [standard]'s own processors, ahead of the standard ones, so its output is
 * what the standard chain's speed processor takes in.
 */
class MixingAudioProcessorChain(
    private val tempo: TempoAudioProcessor,
    private val standard: DefaultAudioSink.DefaultAudioProcessorChain,
) : AudioProcessorChain {
    init {
        require(standard.audioProcessors.any { it === tempo })
    }

    override fun getAudioProcessors(): Array<AudioProcessor> = standard.audioProcessors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        standard.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
        standard.applySkipSilenceEnabled(skipSilenceEnabled)

    /** Played-out time -> what went into the speed processor -> what went into [tempo]. */
    override fun getMediaDuration(playoutDuration: Long): Long =
        tempo.mediaDurationUsFor(standard.getMediaDuration(playoutDuration))

    override fun getSkippedOutputFrameCount(): Long = standard.skippedOutputFrameCount
}
