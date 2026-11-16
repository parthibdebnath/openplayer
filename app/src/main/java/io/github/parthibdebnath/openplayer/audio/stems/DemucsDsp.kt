package io.github.parthibdebnath.openplayer.audio.stems

import io.github.parthibdebnath.openplayer.audio.analysis.Fft
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The fixed shapes of Demucs v4 (htdemucs), and the spectrogram transforms it was trained with.
 *
 * The network itself (see [DemucsCore]) can't contain its STFT and inverse STFT - ONNX has no
 * export for them - so they're done here, exactly as the PyTorch model does them: a reflect-padded
 * signal, a periodic Hann window of 4096 with a hop of 1024, `normalized=True` scaling, the top
 * bin dropped and two frames trimmed either side so the spectrogram is exactly a quarter-hop
 * aligned with the waveform. Get any of that slightly different and the frequency branch's output
 * no longer adds up with the time branch's, which is heard as a smeared, phasey separation.
 */
internal object Demucs {
    const val SAMPLE_RATE = 44_100

    /** One segment: 7.8 s, the length the model was trained on and the only one it takes. */
    const val SEGMENT = 343_980

    /** Segments overlap by a quarter, and are cross-faded with [segmentWeight]. */
    const val STRIDE = 257_985

    const val N_FFT = 4096
    const val HOP = 1024
    const val BINS = 2048
    const val FRAMES = 336
    const val SOURCES = 4

    // The model's source order.
    const val DRUMS = 0
    const val BASS = 1
    const val OTHER = 2
    const val VOCALS = 3

    /** Reflect padding either side before the STFT, which keeps frames on the hop grid. */
    const val PAD = HOP / 2 * 3
    const val PADDED = FRAMES * HOP + 2 * PAD

    /** Floats in the model's spectrogram input: [1, 4, BINS, FRAMES]. */
    const val SPEC_SIZE = 4 * BINS * FRAMES

    /** Floats in the model's frequency-branch output: [1, SOURCES, 4, BINS, FRAMES]. */
    const val FREQ_OUT_SIZE = SOURCES * SPEC_SIZE

    /** Floats in the model's time-branch output: [1, SOURCES, 2, SEGMENT]. */
    const val TIME_OUT_SIZE = SOURCES * 2 * SEGMENT

    /**
     * The cross-fade weight of sample [i] of a segment: a triangle peaking mid-segment, as
     * Demucs's own `apply_model` uses (transition power 1).
     */
    fun segmentWeight(i: Int): Float {
        val half = SEGMENT / 2
        val w = if (i < half) i + 1 else SEGMENT - i
        return w.toFloat() / half
    }
}

/**
 * The STFT and inverse STFT of [Demucs], two channels at a time: the left and right channels are
 * packed as the real and imaginary parts of one complex FFT, which halves the work and is exact.
 *
 * Not thread-safe: one per worker.
 */
internal class DemucsSpectrogram {
    private val fft = Fft(Demucs.N_FFT)
    private val window = DoubleArray(Demucs.N_FFT) { 0.5 - 0.5 * cos(2.0 * PI * it / Demucs.N_FFT) }
    private val re = DoubleArray(Demucs.N_FFT)
    private val im = DoubleArray(Demucs.N_FFT)
    private val padded = Array(2) { FloatArray(Demucs.PADDED) }

    /** 1 / (the sum of squared windows over every frame), per sample of a segment. */
    private val inverseEnvelope: FloatArray = run {
        // torch.istft divides by the squared-window sum over all 340 frames, the zero padding
        // frames included; over the part kept that's a constant 1.5, but worked out rather than
        // assumed.
        val envelope = DoubleArray(Demucs.PADDED + Demucs.N_FFT)
        for (j in 0 until Demucs.FRAMES + 4) {
            val start = (j - 2) * Demucs.HOP
            for (n in 0 until Demucs.N_FFT) {
                val index = start + n
                if (index in envelope.indices) envelope[index] += window[n] * window[n]
            }
        }
        FloatArray(Demucs.SEGMENT) { (1.0 / envelope[it + Demucs.PAD]).toFloat() }
    }

    /**
     * The model's spectrogram input for one segment ([left] and [right], [Demucs.SEGMENT] long),
     * written to [out] in its [1, 4, BINS, FRAMES] layout: left real, left imaginary, right real,
     * right imaginary.
     */
    fun forward(left: FloatArray, right: FloatArray, out: FloatBuffer) {
        reflectPad(left, padded[0])
        reflectPad(right, padded[1])
        // torch's normalized=True: frame_length^-0.5. The packing below halves too.
        val scale = 0.5 / sqrt(Demucs.N_FFT.toDouble())
        val n = Demucs.N_FFT
        val planeStride = Demucs.BINS * Demucs.FRAMES
        for (t in 0 until Demucs.FRAMES) {
            val start = t * Demucs.HOP
            val l = padded[0]
            val r = padded[1]
            for (i in 0 until n) {
                re[i] = l[start + i] * window[i]
                im[i] = r[start + i] * window[i]
            }
            fft.transform(re, im)
            for (k in 0 until Demucs.BINS) {
                val mirror = (n - k) and (n - 1)
                val ar = re[k]
                val ai = im[k]
                val cr = re[mirror]
                val ci = im[mirror]
                val base = k * Demucs.FRAMES + t
                out.put(base, ((ar + cr) * scale).toFloat())
                out.put(planeStride + base, ((ai - ci) * scale).toFloat())
                out.put(2 * planeStride + base, ((ai + ci) * scale).toFloat())
                out.put(3 * planeStride + base, ((cr - ar) * scale).toFloat())
            }
        }
    }

    /**
     * The waveform of one source's frequency-branch output ([spec] from [specOffset], in the
     * [4, BINS, FRAMES] layout), added into [left] and [right] ([Demucs.SEGMENT] long).
     */
    fun inverseAdd(spec: FloatBuffer, specOffset: Int, left: FloatArray, right: FloatArray) {
        val n = Demucs.N_FFT
        val planeStride = Demucs.BINS * Demucs.FRAMES
        // torch's normalized=True multiplies by sqrt(n); the inverse FFT here is unscaled.
        val scale = sqrt(n.toDouble()) / n
        val out = padded
        out[0].fill(0f)
        out[1].fill(0f)
        for (t in 0 until Demucs.FRAMES) {
            // Y = XL + i XR, a full Hermitian-pair spectrum whose inverse is xL + i xR.
            for (k in 0 until Demucs.BINS) {
                val base = specOffset + k * Demucs.FRAMES + t
                val lr = spec.get(base).toDouble()
                val rr = spec.get(base + 2 * planeStride).toDouble()
                // A real inverse (torch's irfft) ignores the imaginary part of the DC bin; packed
                // as here it would leak into the other channel.
                val li = if (k == 0) 0.0 else spec.get(base + planeStride).toDouble()
                val ri = if (k == 0) 0.0 else spec.get(base + 3 * planeStride).toDouble()
                // Inverse by conjugating around the forward transform.
                re[k] = lr - ri
                im[k] = -(li + rr)
                if (k > 0) {
                    // Y[n-k] = conj(XL[k]) + i conj(XR[k]).
                    re[n - k] = lr + ri
                    im[n - k] = -(-li + rr)
                }
            }
            // The Nyquist bin, which the model never sees: zero.
            re[Demucs.BINS] = 0.0
            im[Demucs.BINS] = 0.0
            fft.transform(re, im)
            val start = t * Demucs.HOP
            val l = out[0]
            val r = out[1]
            for (i in 0 until n) {
                val w = window[i] * scale
                l[start + i] += (re[i] * w).toFloat()
                // Conjugated back.
                r[start + i] += (-im[i] * w).toFloat()
            }
        }
        val inv = inverseEnvelope
        for (i in 0 until Demucs.SEGMENT) {
            left[i] += out[0][i + Demucs.PAD] * inv[i]
            right[i] += out[1][i + Demucs.PAD] * inv[i]
        }
    }

    private fun reflectPad(x: FloatArray, out: FloatArray) {
        val size = x.size
        val pad = Demucs.PAD
        System.arraycopy(x, 0, out, pad, size)
        for (k in 1..pad) out[pad - k] = x[k]
        val right = Demucs.PADDED - pad - size
        for (k in 1..right) out[pad + size - 1 + k] = x[size - 1 - k]
    }
}
