package io.github.parthibdebnath.openplayer.audio.stems

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The STFT and inverse STFT against values from PyTorch's own `torch.stft`/`torch.istft`, run
 * through Demucs's `standalone_spec`/`standalone_ispec` on the same deterministic signals.
 */
class DemucsSpectrogramTest {
    private val spectrogram = DemucsSpectrogram()

    private fun signal(): Pair<FloatArray, FloatArray> {
        val left = FloatArray(Demucs.SEGMENT) { n ->
            val t = n / 44_100.0
            (0.3 * sin(2 * PI * 220 * t) + 0.05 * sin(n * 1.7) * cos(n * 0.013)).toFloat()
        }
        val right = FloatArray(Demucs.SEGMENT) { n ->
            val t = n / 44_100.0
            (0.2 * sin(2 * PI * 331 * t + 0.4) + 0.05 * cos(n * 2.3 + 0.1)).toFloat()
        }
        return left to right
    }

    private fun specIndex(channel: Int, bin: Int, frame: Int) =
        (channel * Demucs.BINS + bin) * Demucs.FRAMES + frame

    @Test fun forwardMatchesTorch() {
        val (left, right) = signal()
        val out = FloatArray(Demucs.SPEC_SIZE)
        spectrogram.forward(left, right, FloatBuffer.wrap(out))
        // (channel, bin, frame, torch's value)
        val expected = listOf(
            Triple(0, 0, 0) to 0.25604116916656494,
            Triple(0, 0, 335) to -0.268174946308136,
            Triple(0, 1, 0) to -0.18124352395534515,
            Triple(0, 17, 0) to -0.5452523827552795,
            Triple(0, 17, 100) to -0.0251067616045475,
            Triple(0, 500, 335) to 0.00047234329394996166,
            Triple(0, 2047, 0) to 0.0005811568116769195,
            Triple(1, 0, 0) to 0.0,
            Triple(1, 1, 0) to -0.18189933896064758,
            Triple(1, 17, 0) to -0.6805633306503296,
            Triple(1, 17, 335) to 0.8878020644187927,
            Triple(1, 2047, 0) to -0.0005811601877212524,
            Triple(2, 0, 0) to 0.10415200144052505,
            Triple(2, 1, 335) to 0.05860055238008499,
            Triple(2, 17, 0) to -0.10418868809938431,
            Triple(2, 2047, 335) to 0.0012511536478996277,
            Triple(3, 1, 0) to -0.07380951195955276,
            Triple(3, 17, 0) to -0.10827099531888962,
            Triple(3, 17, 335) to 0.10470584034919739,
            Triple(3, 2047, 335) to 0.0009610876441001892,
        )
        for ((at, value) in expected) {
            val (c, f, t) = at
            assertEquals("spec[$c, $f, $t]", value, out[specIndex(c, f, t)].toDouble(), 2e-5 + abs(value) * 1e-4)
        }
    }

    @Test fun inverseMatchesTorch() {
        val spec = FloatArray(Demucs.SPEC_SIZE) { k -> (sin(k * 0.37) * cos(k * 0.0011)).toFloat() }
        val left = FloatArray(Demucs.SEGMENT)
        val right = FloatArray(Demucs.SEGMENT)
        spectrogram.inverseAdd(FloatBuffer.wrap(spec), 0, left, right)
        val expectedLeft = mapOf(
            0 to 0.014557662419974804, 1 to -0.002944141859188676, 1000 to 0.04154043644666672,
            1535 to -0.016956286504864693, 1536 to -0.030795393511652946, 100000 to -0.06778410822153091,
            343979 to 0.008263017050921917, 343000 to -0.039964620023965836,
        )
        val expectedRight = mapOf(
            0 to 0.017439832910895348, 1 to -0.0030272689182311296, 1000 to 0.03259681537747383,
            1535 to -0.02358889766037464, 1536 to 0.011304912157356739, 100000 to -0.007800704333931208,
            343979 to 0.008714704774320126, 343000 to -0.020606322214007378,
        )
        for ((i, v) in expectedLeft) assertEquals("left[$i]", v, left[i].toDouble(), 2e-5)
        for ((i, v) in expectedRight) assertEquals("right[$i]", v, right[i].toDouble(), 2e-5)
    }

    @Test fun roundTripIsExact() {
        val (left, right) = signal()
        val spec = FloatArray(Demucs.SPEC_SIZE)
        spectrogram.forward(left, right, FloatBuffer.wrap(spec))
        val l = FloatArray(Demucs.SEGMENT)
        val r = FloatArray(Demucs.SEGMENT)
        spectrogram.inverseAdd(FloatBuffer.wrap(spec), 0, l, r)
        // Exact away from the ends. Within 2.5k samples of either end torch's own inverse fades
        // out, its zero padding frames counted in the window sum; the model was trained with that.
        for (i in 2560 until Demucs.SEGMENT - 2560 step 997) {
            assertEquals(left[i], l[i], 2e-4f)
            assertEquals(right[i], r[i], 2e-4f)
        }
    }

    @Test fun segmentWeightsCrossFadeToOne() {
        // Where two segments overlap their weights are normalised by their sum, so all that
        // matters is that the triangle peaks at 1 mid-segment and is positive everywhere.
        assertEquals(1f, Demucs.segmentWeight(Demucs.SEGMENT / 2 - 1), 1e-6f)
        assertEquals(1f, Demucs.segmentWeight(Demucs.SEGMENT / 2), 1e-6f)
        assert(Demucs.segmentWeight(0) > 0f && Demucs.segmentWeight(Demucs.SEGMENT - 1) > 0f)
    }
}
