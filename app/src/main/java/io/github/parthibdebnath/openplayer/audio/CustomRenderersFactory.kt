@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.Ac3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.wav.WavExtractor

/**
 * Builds an audio-only player: one audio renderer, with [ReverbAudioProcessor] and
 * [ThreeDAudioProcessor] inserted after ExoPlayer's built-in Sonic-based speed/pitch processor, so
 * our custom effects apply on top of the existing (already time-stretched) audio stream.
 *
 * Deliberately not DefaultRenderersFactory. That one also builds video, text, metadata, image and
 * camera-motion renderers - a whole video pipeline and every subtitle and caption decoder - which
 * a music player never uses but which were all compiled into the app, since R8 can't see that
 * they're never reached.
 */
class CustomRenderersFactory(
    private val context: Context,
    val reverbAudioProcessor: ReverbAudioProcessor,
    val threeDAudioProcessor: ThreeDAudioProcessor,
) : RenderersFactory {

    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> {
        val sink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(reverbAudioProcessor, threeDAudioProcessor))
            .build()
        return arrayOf(
            MediaCodecAudioRenderer(
                context,
                MediaCodecSelector.DEFAULT,
                // A decoder that fails to start falls back to the next one that can take the
                // format, rather than failing the song outright - some devices list a hardware
                // decoder that turns out not to handle a particular stream.
                /* enableDecoderFallback= */ true,
                eventHandler,
                audioRendererEventListener,
                sink,
            ),
        )
    }
}

/**
 * Reads local audio files with only the container formats audio comes in. The default factory
 * also carries extractors for MPEG-TS, PS, FLV, AVI, HEIF, JPEG, PNG, WebP and BMP, none of which
 * hold music.
 *
 * Constant-bitrate seeking is on for the formats that have no seek table of their own. Without it
 * a raw .aac (ADTS) file couldn't be seeked at all - dragging the seek bar put it straight back at
 * the start - and the same went for AMR and for MP3s missing their header.
 */
fun audioMediaSourceFactory(context: Context): MediaSource.Factory {
    val extractors = ExtractorsFactory {
        arrayOf<Extractor>(
            Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
            FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
            FlacExtractor(),
            OggExtractor(),
            WavExtractor(),
            AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED),
            Ac3Extractor(),
        )
    }
    return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context), extractors)
}
