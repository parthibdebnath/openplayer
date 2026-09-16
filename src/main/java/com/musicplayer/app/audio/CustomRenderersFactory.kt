package com.musicplayer.app.audio

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Inserts [ReverbAudioProcessor] and [ThreeDAudioProcessor] after ExoPlayer's built-in
 * Sonic-based speed/pitch processor, so our custom effects apply on top of the existing
 * (already time-stretched) audio stream.
 */
class CustomRenderersFactory(
    context: Context,
    val reverbAudioProcessor: ReverbAudioProcessor,
    val threeDAudioProcessor: ThreeDAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(reverbAudioProcessor, threeDAudioProcessor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}
