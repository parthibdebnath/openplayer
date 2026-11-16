package io.github.parthibdebnath.openplayer.audio.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class StemSeparatorTest {
    /** Stands in for the network: each source is the mix times a fixed factor, via the time branch. */
    private class ScalingCore : DemucsCore {
        var runs = 0
        override fun run(mix: FloatBuffer, spec: FloatBuffer, freqOut: FloatBuffer, timeOut: FloatBuffer) {
            runs++
            for (i in 0 until Demucs.FREQ_OUT_SIZE) freqOut.put(i, 0f)
            for (s in 0 until Demucs.SOURCES) {
                val factor = FACTORS[s]
                for (c in 0 until 2) {
                    for (i in 0 until Demucs.SEGMENT) {
                        timeOut.put((s * 2 + c) * Demucs.SEGMENT + i, mix.get(c * Demucs.SEGMENT + i) * factor)
                    }
                }
            }
        }

        override fun close() {}

        companion object {
            val FACTORS = floatArrayOf(0.5f, 0.25f, 0.15f, 0.1f)
        }
    }

    private class ArrayReader(private val values: FloatArray) : SampleReader() {
        override val length: Int get() = values.size
        override fun get(index: Int): Float = values[index]
    }

    private fun tone(n: Int, freq: Double, phase: Double = 0.0) =
        FloatArray(n) { (0.4 * sin(2 * PI * freq * it / 44_100 + phase)).toFloat() }

    @Test fun overlapAddPutsEverySampleBackWhereItCameFrom() {
        // Long enough for several segments, and not a whole number of them.
        val n = Demucs.SEGMENT * 3 + 12_345
        val left = tone(n, 220.0)
        val right = tone(n, 331.0, 0.4)
        val core = ScalingCore()
        val result = StemSeparator(core).separate(ArrayReader(left), ArrayReader(right), { false })!!
        assertEquals(StemSeparator(core).segmentCount(n), core.runs)
        for (i in 0 until n step 1_013) {
            // drums = mix * 0.5, bass 0.25, vocals 0.1 (the mean is ~0 for a tone).
            assertEquals(left[i] * 0.5f, result.drums.get(2 * i) / 32768f, 2e-4f)
            assertEquals(right[i] * 0.25f, result.bass.get(2 * i + 1) / 32768f, 2e-4f)
            assertEquals(left[i] * 0.1f, result.vocals.get(2 * i) / 32768f, 2e-4f)
        }
    }

    @Test fun stopsWhenAsked() {
        val n = Demucs.SEGMENT * 2
        var calls = 0
        val result = StemSeparator(ScalingCore()).separate(ArrayReader(tone(n, 100.0)), ArrayReader(tone(n, 100.0)), { calls++ > 0 })
        assertNull(result)
    }

    @Test fun resamplingThereAndBackIsTransparent() {
        val n = 48_000
        val source = FloatArray(n) { (0.5 * sin(2 * PI * 1000 * it / 48_000) + 0.2 * sin(2 * PI * 13_000 * it / 48_000)).toFloat() }
        val down = ResampledReader(ArrayReader(source), Resampler(48_000, 44_100))
        val up = ResampledReader(down, Resampler(44_100, 48_000))
        var worst = 0f
        // Away from the very ends, where the filter runs off the signal.
        for (i in 200 until n - 200) worst = maxOf(worst, abs(up[i] - source[i]))
        assert(worst < 2e-3f) { "worst error $worst" }
        // And with no delay: a 1 kHz tone at 44.1 kHz is where it should be in time.
        for (j in 100 until down.length - 100 step 97) {
            val t = j / 44_100.0
            val expected = 0.5 * sin(2 * PI * 1000 * t) + 0.2 * sin(2 * PI * 13_000 * t)
            assertEquals(expected, down[j].toDouble(), 3e-3)
        }
    }
}
