@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.nio.ByteBuffer

/**
 * The audio renderer, plus handing each song's ReplayGain to [normalizer] as its audio comes out
 * of the decoder.
 *
 * [processOutputBuffer] is given the format of the buffer it's outputting, which the codec
 * renderer lines up with each song's first output sample, and it runs on the playback thread just
 * before the buffer goes through the audio processors. Switching the gain here lands it on the
 * exact sample where one song gives way to the next in a gapless window.
 */
class NormalizingAudioRenderer(
    context: Context,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
    private val normalizer: NormalizationAudioProcessor,
) : MediaCodecAudioRenderer(
    context,
    MediaCodecSelector.DEFAULT,
    // A decoder that fails to start falls back to the next one that can take the format, rather
    // than failing the song outright - some devices list a hardware decoder that turns out not to
    // handle a particular stream.
    /* enableDecoderFallback= */ true,
    eventHandler,
    eventListener,
    audioSink,
) {
    private var lastFormat: Format? = null

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format,
    ): Boolean {
        if (format !== lastFormat) {
            lastFormat = format
            normalizer.trackGainDb = replayGainDb(format.metadata)        }
        return super.processOutputBuffer(
            positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount,
            bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format,
        )
    }

    override fun onDisabled() {
        lastFormat = null
        super.onDisabled()
    }
}

/**
 * The track gain in a song's tags, in dB relative to the ReplayGain reference, or null if it has
 * none. Reads every place taggers put it: Vorbis comments (FLAC, Ogg, Opus), ID3 TXXX frames
 * (MP3), iTunes-style freeform atoms (M4A), and failing those the LAME header's own value.
 */
fun replayGainDb(metadata: Metadata?): Float? {
    if (metadata == null) return null
    var lameGain: Float? = null
    for (i in 0 until metadata.length()) {
        val gain = when (val entry = metadata.get(i)) {
            is VorbisComment -> when {
                entry.key.equals(TRACK_GAIN, ignoreCase = true) -> parseDb(entry.value)
                // Opus files carry R128 gain instead: a Q7.8 number relative to -23 LUFS, which
                // is 5 dB quieter than the ReplayGain reference.
                entry.key.equals(R128_TRACK_GAIN, ignoreCase = true) -> entry.value.trim().toIntOrNull()?.let { it / 256f + 5f }
                else -> null
            }
            is TextInformationFrame ->
                if (entry.description.equals(TRACK_GAIN, ignoreCase = true)) entry.values.firstOrNull()?.let(::parseDb) else null
            is InternalFrame ->
                if (entry.description.equals(TRACK_GAIN, ignoreCase = true)) parseDb(entry.text) else null
            is MdtaMetadataEntry ->
                if (entry.key.endsWith(TRACK_GAIN, ignoreCase = true)) parseDb(entry.value.decodeToString()) else null
            is Mp3InfoReplayGain -> {
                lameGain = listOfNotNull(entry.field1, entry.field2)
                    .firstOrNull { it.name == Mp3InfoReplayGain.GainField.NAME_RADIO && it.originator != Mp3InfoReplayGain.GainField.ORIGINATOR_UNSET }
                    ?.gain
                null
            }
            else -> null
        }
        if (gain != null && gain.isFinite()) return gain
    }
    return lameGain
}

private const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
private const val R128_TRACK_GAIN = "R128_TRACK_GAIN"

/** "-7.89 dB", "+2.1dB", "-7,89 dB" (some taggers write a decimal comma). */
private fun parseDb(text: String): Float? =
    text.trim().removeSuffix("dB").removeSuffix("db").removeSuffix("DB").trim().replace(',', '.').toFloatOrNull()
