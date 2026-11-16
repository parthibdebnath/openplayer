package io.github.parthibdebnath.openplayer.audio.stems

import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Random access to one channel of audio, as floats in -1..1. */
internal abstract class SampleReader {
    abstract val length: Int
    abstract operator fun get(index: Int): Float
}

/** One channel of interleaved stereo 16-bit audio. */
internal class InterleavedShortReader(
    private val data: ShortBuffer,
    private val channel: Int,
    override val length: Int,
) : SampleReader() {
    override fun get(index: Int): Float = data.get(2 * index + channel) / 32768f
}

/** [source] at another sample rate, worked out sample by sample as asked for. */
internal class ResampledReader(private val source: SampleReader, private val resampler: Resampler) : SampleReader() {
    override val length: Int = resampler.outputLength(source.length)
    override fun get(index: Int): Float = resampler.valueAt(index, source)
}

/**
 * Converts between sample rates with a Kaiser-windowed sinc, to studio quality (stopband about
 * -90 dB, flat to 0.95 of the lower Nyquist frequency) and with no delay: output sample j is the
 * input at exactly j * from / to, so a stem resampled to 44.1 kHz for the model and back lines up
 * with the song to the sample.
 *
 * Random access ([valueAt]), so nothing longer than a segment is ever held at the other rate.
 * Demucs only takes 44.1 kHz; songs at 48 kHz or 96 kHz go through this both ways.
 */
internal class Resampler(private val fromRate: Int, private val toRate: Int) {
    init {
        require(fromRate > 0 && toRate > 0)
    }

    val identity: Boolean get() = fromRate == toRate

    /** The cutoff, as a fraction of the input's Nyquist frequency. */
    private val cutoff = CUTOFF * min(1.0, toRate.toDouble() / fromRate)

    /** Input samples either side of each output that the filter reaches. */
    private val reach = ceil(ZERO_CROSSINGS / cutoff).toInt()
    private val taps = 2 * reach

    /** The kernel at [PHASES] + 1 fractional offsets, each [taps] long, interpolated between. */
    private val table = FloatArray((PHASES + 1) * taps).also { table ->
        for (p in 0..PHASES) {
            val frac = p.toDouble() / PHASES
            for (k in 0 until taps) {
                // Tap k is input sample (floor + k - reach + 1), at distance x from the output.
                val x = (k - reach + 1) - frac
                table[p * taps + k] = kernel(x).toFloat()
            }
        }
    }

    private fun kernel(x: Double): Double {
        if (abs(x) >= reach) return 0.0
        val arg = PI * cutoff * x
        val sinc = if (abs(arg) < 1e-12) 1.0 else sin(arg) / arg
        val r = x / reach
        val window = besselI0(BETA * sqrt(1 - r * r)) / besselI0(BETA)
        return cutoff * sinc * window
    }

    /** Output samples for [length] input samples. */
    fun outputLength(length: Int): Int =
        if (length <= 0) 0 else (((length - 1).toLong() * toRate) / fromRate + 1).toInt()

    /** Output sample [j] of [input]. */
    fun valueAt(j: Int, input: SampleReader): Float {
        if (fromRate == toRate) return if (j in 0 until input.length) input[j] else 0f
        val length = input.length
        val position = j.toLong() * fromRate
        val whole = (position / toRate).toInt()
        val fracScaled = (position % toRate).toDouble() / toRate * PHASES
        val phase = fracScaled.toInt()
        val blend = (fracScaled - phase).toFloat()
        val rowA = phase * taps
        val rowB = rowA + taps
        val first = whole - reach + 1
        var sum = 0f
        val from = if (first < 0) -first else 0
        val to = if (first + taps > length) length - first else taps
        for (k in from until to) {
            val a = table[rowA + k]
            sum += (a + (table[rowB + k] - a) * blend) * input[first + k]
        }
        return sum
    }

    private companion object {
        const val CUTOFF = 0.95
        const val ZERO_CROSSINGS = 32
        const val PHASES = 512
        const val BETA = 9.0

        fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2
            var k = 1
            while (k < 200) {
                term *= (half / k) * (half / k)
                sum += term
                if (term < sum * 1e-17) break
                k++
            }
            return sum
        }
    }
}
