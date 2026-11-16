package io.github.parthibdebnath.openplayer.audio.stems

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sqrt

/**
 * The network part of Demucs: one 7.8 s segment in, every source out. [OnnxDemucsCore] runs it on
 * the phone; tests stand in their own.
 */
internal interface DemucsCore : Closeable {
    /**
     * Runs one segment. [mix] holds [Demucs.SEGMENT] left then right samples, [spec] the
     * spectrogram of the same (see [DemucsSpectrogram.forward]). Writes the frequency branch's
     * output to [freqOut] and the time branch's to [timeOut]; all four are direct buffers in the
     * model's own layouts.
     */
    fun run(mix: FloatBuffer, spec: FloatBuffer, freqOut: FloatBuffer, timeOut: FloatBuffer)
}

internal fun directFloats(count: Int): FloatBuffer =
    ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

internal fun directShorts(count: Int): ShortBuffer =
    ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder()).asShortBuffer()

/**
 * Separates a stretch of a song into stems the way Demucs's own `apply_model` does: normalised
 * by the stretch's loudness, cut into 7.8 s segments overlapping by a quarter, each run through
 * the network and the triangle-weighted results added up.
 *
 * Memory is the constraint on a phone (direct buffers count against the Java heap on Android), so
 * it streams: the input is read sample by sample as each segment needs it (resampled on the fly,
 * if need be), the overlap-add is a ring one segment long, and each stretch is finished as soon as
 * no later segment can touch it, into 16-bit stems. A minute of song peaks at about 100 MB.
 *
 * Only drums, bass and vocals are kept. The rest of the song ("other") is whatever is left once
 * those three are taken away - which also means the stems always add back up to exactly the
 * original, so mixing with them can never change a song that isn't being mixed.
 *
 * Holds its segment buffers between runs; one per worker.
 */
internal class StemSeparator(private val core: DemucsCore) {
    private val spectrogram = DemucsSpectrogram()
    private val segLeft = FloatArray(Demucs.SEGMENT)
    private val segRight = FloatArray(Demucs.SEGMENT)
    private val outLeft = FloatArray(Demucs.SEGMENT)
    private val outRight = FloatArray(Demucs.SEGMENT)
    private val mixBuffer = directFloats(2 * Demucs.SEGMENT)
    private val specBuffer = directFloats(Demucs.SPEC_SIZE)
    private val freqOut = directFloats(Demucs.FREQ_OUT_SIZE)
    private val timeOut = directFloats(Demucs.TIME_OUT_SIZE)

    // The overlap-add ring: [kept source][channel][i % SEGMENT], and the weights.
    private val ring = Array(KEPT.size) { Array(2) { FloatArray(Demucs.SEGMENT) } }
    private val ringWeights = FloatArray(Demucs.SEGMENT)

    /** Drums, bass and vocals, each interleaved stereo 16-bit at 44.1 kHz, [length] frames. */
    class Result(val length: Int, val drums: ShortBuffer, val bass: ShortBuffer, val vocals: ShortBuffer)

    /** Runs the network on one segment of made-up audio; how long it took, in ms. */
    fun timeSegment(): Long {
        for (i in 0 until Demucs.SEGMENT) {
            segLeft[i] = (kotlin.math.sin(i * 0.031) * 0.5 + kotlin.math.sin(i * 0.0071) * 0.3).toFloat()
            segRight[i] = (kotlin.math.sin(i * 0.027) * 0.5 + kotlin.math.cos(i * 0.0053) * 0.3).toFloat()
        }
        mixBuffer.clear()
        mixBuffer.put(segLeft)
        mixBuffer.put(segRight)
        mixBuffer.clear()
        spectrogram.forward(segLeft, segRight, specBuffer)
        val started = System.nanoTime()
        core.run(mixBuffer, specBuffer, freqOut, timeOut)
        return (System.nanoTime() - started) / 1_000_000
    }

    /** Segments [length] samples take. */
    fun segmentCount(length: Int): Int = (length + Demucs.STRIDE - 1) / Demucs.STRIDE

    /**
     * Separates [left] and [right] (44.1 kHz, the same length). Null if [shouldStop] said so
     * between segments. [onSegment] is told after each one, with how many are done.
     */
    fun separate(left: SampleReader, right: SampleReader, shouldStop: () -> Boolean, onSegment: (Int) -> Unit = {}): Result? {
        val n = left.length
        val outputs = Array(KEPT.size) { directShorts(2 * n) }
        for (source in ring) for (channel in source) channel.fill(0f)
        ringWeights.fill(0f)

        // Normalised as Demucs's separate does, by the mono mix's mean and spread.
        var sum = 0.0
        var sumSquares = 0.0
        for (i in 0 until n) {
            val m = (left[i] + right[i]) * 0.5
            sum += m
            sumSquares += m * m
        }
        val mean = if (n > 0) sum / n else 0.0
        val variance = if (n > 1) (sumSquares - n * mean * mean) / (n - 1) else 0.0
        val std = sqrt(variance.coerceAtLeast(0.0))
        if (std < SILENT_STD) {
            // Nothing there to separate: every stem is silence.
            return Result(n, outputs[0], outputs[1], outputs[2])
        }
        val scale = (1.0 / std).toFloat()
        val shift = mean.toFloat()
        val back = std.toFloat()

        var offset = 0
        var done = 0
        while (offset < n) {
            if (shouldStop()) return null
            val chunkLength = minOf(Demucs.SEGMENT, n - offset)
            // A short last chunk is centred in its segment, with the song either side of it where
            // there is any (Demucs's TensorChunk.padded).
            val delta = Demucs.SEGMENT - chunkLength
            val start = offset - delta / 2
            for (i in 0 until Demucs.SEGMENT) {
                val index = start + i
                if (index in 0 until n) {
                    segLeft[i] = (left[index] - shift) * scale
                    segRight[i] = (right[index] - shift) * scale
                } else {
                    segLeft[i] = 0f
                    segRight[i] = 0f
                }
            }
            mixBuffer.clear()
            mixBuffer.put(segLeft)
            mixBuffer.put(segRight)
            mixBuffer.clear()
            spectrogram.forward(segLeft, segRight, specBuffer)
            core.run(mixBuffer, specBuffer, freqOut, timeOut)

            val trim = delta / 2
            for ((slot, source) in KEPT.withIndex()) {
                val base = source * 2 * Demucs.SEGMENT
                for (i in 0 until Demucs.SEGMENT) {
                    outLeft[i] = timeOut.get(base + i)
                    outRight[i] = timeOut.get(base + Demucs.SEGMENT + i)
                }
                spectrogram.inverseAdd(freqOut, source * Demucs.SPEC_SIZE, outLeft, outRight)
                val ringL = ring[slot][0]
                val ringR = ring[slot][1]
                for (i in 0 until chunkLength) {
                    val w = Demucs.segmentWeight(i)
                    val at = (offset + i) % Demucs.SEGMENT
                    ringL[at] += w * outLeft[trim + i]
                    ringR[at] += w * outRight[trim + i]
                }
            }
            for (i in 0 until chunkLength) ringWeights[(offset + i) % Demucs.SEGMENT] += Demucs.segmentWeight(i)

            // Everything before the next segment is finished.
            val finishedTo = minOf(n, offset + Demucs.STRIDE)
            for (i in offset until finishedTo) {
                val at = i % Demucs.SEGMENT
                val w = back / ringWeights[at]
                for (slot in KEPT.indices) {
                    outputs[slot].put(2 * i, toShort(ring[slot][0][at] * w))
                    outputs[slot].put(2 * i + 1, toShort(ring[slot][1][at] * w))
                    ring[slot][0][at] = 0f
                    ring[slot][1][at] = 0f
                }
                ringWeights[at] = 0f
            }
            done++
            onSegment(done)
            offset += Demucs.STRIDE
        }
        return Result(n, outputs[0], outputs[1], outputs[2])
    }

    private fun toShort(v: Float): Short = (v * 32768f).toInt().coerceIn(-32768, 32767).toShort()

    private companion object {
        val KEPT = intArrayOf(Demucs.DRUMS, Demucs.BASS, Demucs.VOCALS)
        const val SILENT_STD = 1e-5
    }
}
