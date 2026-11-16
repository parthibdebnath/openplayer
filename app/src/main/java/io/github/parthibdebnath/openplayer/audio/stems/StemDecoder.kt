@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.stems

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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * A stretch of a song as the player hears it: 16-bit, at the song's own rate, one or two channels
 * (always stored as two). Frame i is kept frame [startFrame] + i of the song - counted the way
 * ExoPlayer counts, after the encoder's priming samples - which is media time
 * [songStartMs] + (startFrame + i) / rate.
 */
internal class DecodedWindow(
    val sampleRate: Int,
    val channels: Int,
    val songStartMs: Double,
    val startFrame: Long,
    val frames: Int,
    /** Interleaved left, right; [frames] * 2 shorts. */
    val data: ShortBuffer,
) {
    val startMs: Double get() = songStartMs + startFrame * 1000.0 / sampleRate
}

/**
 * Decodes the stretches of a song the stems are made from, at full quality - the samples are
 * later matched against the player's own, so they come through the same extractors and the
 * device's default decoder, from the start of the file (as the player does when it plays a song
 * through), with the priming samples trimmed in the same place.
 */
internal object StemDecoder {

    /** The windows [fromMs, toMs) of [uri], each decoded; null if the file can't be. */
    fun decode(context: Context, uri: Uri, windows: List<ClosedRange<Double>>, shouldStop: () -> Boolean): List<DecodedWindow>? {
        val extractor = MediaExtractorCompat(audioExtractorsFactory(), DefaultDataSource.Factory(context))
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(uri, 0)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var rate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var floatPcm = false
            var toTrim = format.intOr(KEY_ENCODER_DELAY, 0).toLong()
            val lastWindowEndMs = windows.maxOf { it.endInclusive }

            var startUs = Long.MIN_VALUE
            var kept = 0L
            var outputs: Array<ShortBuffer?> = arrayOfNulls(windows.size)
            var firstFrames = LongArray(windows.size)
            var lengths = IntArray(windows.size)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var lastQueued = false
            var outputDone = false
            var idle = 0
            val started = System.nanoTime()
            while (!outputDone) {
                if (shouldStop()) return null
                if (System.nanoTime() - started > MAX_DECODE_NANOS || idle > MAX_IDLE_LOOPS) {
                    Log.w(TAG, "Gave up decoding $uri")
                    return null
                }
                var progressed = false
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: error("No input buffer")
                        val size = if (lastQueued) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(index, 0, size, time, 0)
                            // See AudioDecoder: FLAC through MediaExtractorCompat never reports its end.
                            if (!extractor.advance() || extractor.sampleTime <= time) lastQueued = true
                        }
                        progressed = true
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    rate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, rate)
                    floatPcm = out.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (index >= 0) {
                    progressed = true
                    if (channels !in 1..2) return null
                    if (info.size > 0) {
                        if (startUs == Long.MIN_VALUE) {
                            startUs = info.presentationTimeUs
                            // Now the rate is known: where each window falls, in kept frames.
                            val songStartMs = startUs / 1000.0
                            for ((w, window) in windows.withIndex()) {
                                val first = ((window.start - songStartMs) * rate / 1000.0).toLong().coerceAtLeast(0L)
                                val last = ((window.endInclusive - songStartMs) * rate / 1000.0).toLong().coerceAtLeast(first)
                                firstFrames[w] = first
                                lengths[w] = (last - first).toInt()
                                outputs[w] = ByteBuffer.allocateDirect(lengths[w] * 4).order(ByteOrder.nativeOrder()).asShortBuffer()
                            }
                        }
                        val buffer = decoder.getOutputBuffer(index) ?: error("No output buffer")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val bytesPerSample = if (floatPcm) 4 else 2
                        val frames = info.size / (bytesPerSample * channels)
                        for (f in 0 until frames) {
                            val l = readSample(buffer, floatPcm)
                            val r = if (channels == 2) readSample(buffer, floatPcm) else l
                            if (toTrim > 0) {
                                toTrim--
                                continue
                            }
                            for (w in windows.indices) {
                                val at = kept - firstFrames[w]
                                if (at >= 0 && at < lengths[w]) {
                                    val out = outputs[w]!!
                                    out.put((2 * at).toInt(), l)
                                    out.put((2 * at + 1).toInt(), r)
                                }
                            }
                            kept++
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    // Nothing needed past the last window: stop decoding there.
                    if (startUs != Long.MIN_VALUE && startUs / 1000.0 + kept * 1000.0 / rate > lastWindowEndMs) outputDone = true
                }
                idle = if (progressed) 0 else idle + 1
            }
            if (startUs == Long.MIN_VALUE) return null
            return windows.indices.map { w ->
                // A window running past the end of the song ends where the song does.
                val frames = (kept - firstFrames[w]).coerceIn(0L, lengths[w].toLong()).toInt()
                DecodedWindow(rate, channels, startUs / 1000.0, firstFrames[w], frames, outputs[w]!!)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not decode $uri for stems", t)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** As ExoPlayer turns a decoder's float output into the 16-bit samples the processors see. */
    private fun readSample(buffer: ByteBuffer, float: Boolean): Short =
        if (float) (buffer.getFloat().coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() else buffer.getShort()

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private const val TAG = "StemDecoder"
    private const val TIMEOUT_US = 10_000L
    private const val MAX_DECODE_NANOS = 180L * 1_000_000_000
    private const val MAX_IDLE_LOOPS = 500
    private const val KEY_ENCODER_DELAY = "encoder-delay"
}
