@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.MediaExtractorCompat
import io.github.parthibdebnath.openplayer.audio.audioExtractorsFactory
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.ensureActive

/** A song's audio for analysis: mono, at [sampleRate], starting at [startMs] of media time. */
class DecodedAudio(val samples: FloatArray, val sampleRate: Int, val startMs: Double)

/**
 * Decodes a song for [BeatAnalyzer]: mono, low-passed and decimated to about 11 kHz, which keeps
 * everything the beat and key are heard in while cutting the work and memory by four.
 *
 * Read with ExoPlayer's own extractors (the same set the player uses), and with the encoder's
 * start-up delay trimmed off the way ExoPlayer trims it, so sample n here is exactly the moment
 * the player calls [DecodedAudio.startMs] + n / rate. Read any other way - the platform's own
 * extractor, say - an MP3's beats would all come out some 25 ms off, which is a clearly audible
 * flam when two songs' kicks are meant to land together.
 */
object AudioDecoder {

    suspend fun decode(context: Context, uri: Uri): DecodedAudio? {
        val extractor = MediaExtractorCompat(audioExtractorsFactory(), DefaultDataSource.Factory(context))
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(uri, 0)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            if (durationUs > MAX_DURATION_US) return null
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var channels = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var sourceRate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var floatPcm = false
            val encoderDelay = format.getIntegerOr(KEY_ENCODER_DELAY, 0)
            var toTrim = encoderDelay.toLong()

            var downsampler: Downsampler? = null
            var startUs = Long.MIN_VALUE
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var lastSampleQueued = false
            var outputDone = false
            val started = System.nanoTime()
            var idleLoops = 0
            var samplesRead = 0L
            while (!outputDone) {
                coroutineContext.ensureActive()
                // Never stuck: a file the decoder chokes on, or one that never signals its end,
                // gives up rather than spinning this thread for good.
                val now = System.nanoTime()
                if (now - started > MAX_DECODE_NANOS || idleLoops > MAX_IDLE_LOOPS) {
                    Log.w("AudioDecoder", "Gave up decoding $uri: ${(now - started) / 1_000_000} ms, $idleLoops idle, input done $inputDone, $samplesRead samples")
                    return null
                }
                var progressed = false
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: error("No input buffer")
                        val size = if (lastSampleQueued) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(index, 0, size, time, 0)
                            samplesRead++
                            // The end is where the samples stop moving on, as well as where the
                            // extractor says so: FLAC through MediaExtractorCompat goes on handing
                            // back its last sample, advance() and all, rather than reporting the
                            // end - which looped here for good.
                            if (!extractor.advance() || extractor.sampleTime <= time) lastSampleQueued = true
                        }
                        progressed = true
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        channels = out.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        sourceRate = out.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sourceRate)
                        floatPcm = out.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                            AudioFormat.ENCODING_PCM_FLOAT
                    }
                    index >= 0 -> {
                        progressed = true
                        if (info.size > 0) {
                            if (startUs == Long.MIN_VALUE) startUs = info.presentationTimeUs
                            val sampler = downsampler ?: Downsampler(sourceRate).also { downsampler = it }
                            val buffer = decoder.getOutputBuffer(index) ?: error("No output buffer")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.nativeOrder())
                            val bytesPerSample = if (floatPcm) 4 else 2
                            val frames = info.size / (bytesPerSample * channels)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) {
                                    sum += if (floatPcm) buffer.getFloat() else buffer.getShort() / 32768f
                                }
                                // The encoder's priming samples, which the player never plays.
                                if (toTrim > 0) {
                                    toTrim--
                                    continue
                                }
                                sampler.push(sum / channels)
                            }
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                idleLoops = if (progressed) 0 else idleLoops + 1
            }
            val sampler = downsampler ?: return null
            if (startUs == Long.MIN_VALUE) return null
            return DecodedAudio(sampler.finish(), sampler.outputRate, startUs / 1000.0)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("AudioDecoder", "Could not decode $uri for analysis", t)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    /** The longest song analysed: anything longer is a mix or a recording, not a track to mix. */
    private const val MAX_DURATION_US = 20L * 60 * 1_000_000
    private const val TIMEOUT_US = 10_000L

    /** Longer than any song takes, even on a slow phone; past it something is wrong. */
    private const val MAX_DECODE_NANOS = 120L * 1_000_000_000

    /** Rounds of neither input taken nor output given (about 10 ms each) before giving up. */
    private const val MAX_IDLE_LOOPS = 500

    /** MediaFormat.KEY_ENCODER_DELAY, which only has a constant from API 30 but is set on any. */
    private const val KEY_ENCODER_DELAY = "encoder-delay"
}

/**
 * Low-pass and decimate by a whole factor to about [TARGET_RATE], one output for every [factor]
 * inputs, the filter centred on that output's own input sample so no delay is introduced.
 */
internal class Downsampler(sourceRate: Int) {
    val factor = (sourceRate / TARGET_RATE.toDouble()).roundToInt().coerceAtLeast(1)
    val outputRate = sourceRate / factor
    private val half = HALF_TAPS_PER_FACTOR * factor
    private val taps = FloatArray(2 * half + 1) { i ->
        val n = i - half
        // Windowed sinc, cut off a little under the new Nyquist frequency.
        val cutoff = 0.45 / factor
        val sinc = if (n == 0) 2 * cutoff else sin(2 * PI * cutoff * n) / (PI * n)
        val window = 0.5 + 0.5 * cos(PI * n / (half + 1))
        (sinc * window).toFloat()
    }
    private val history = FloatArray(taps.size)
    private var written = 0L
    private var output = FloatArray(1 shl 16)
    private var count = 0

    fun push(sample: Float) {
        history[(written % history.size).toInt()] = sample
        written++
        // Output n is centred on input n * factor, which needs `half` inputs beyond it.
        val centre = written - 1 - half
        if (centre >= 0 && centre % factor == 0L) emit(centre)
    }

    private fun emit(centre: Long) {
        var sum = 0f
        for (i in taps.indices) {
            val index = centre - half + i
            if (index >= 0 && index < written) sum += taps[i] * history[(index % history.size).toInt()]
        }
        if (count == output.size) output = output.copyOf(output.size * 2)
        output[count++] = sum
    }

    /** The rest, with silence beyond the end, and the whole output. */
    fun finish(): FloatArray {
        repeat(half) { push(0f) }
        return output.copyOf(count)
    }

    private companion object {
        const val TARGET_RATE = 11_025
        const val HALF_TAPS_PER_FACTOR = 8
    }
}
