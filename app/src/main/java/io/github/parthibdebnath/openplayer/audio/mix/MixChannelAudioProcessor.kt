@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.mix

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * One deck's channel strip for the DJ mix: a level, and a high-pass filter whose cutoff sweeps
 * from below hearing (no effect) up through the bass - the "bass kill" that keeps two kick drums
 * and two basslines from piling up during a blend, and the filter that sweeps a song out when its
 * tempo can't be matched.
 *
 * The engine sets targets a few times a second; both glide there sample by sample, so neither the
 * level nor the filter ever steps (a gain that jumps every 50 ms is heard as a buzz).
 *
 * At rest - level 1, cutoff at the floor - it copies its input untouched, so it costs nothing and
 * colours nothing outside a mix. The filter starts with the cutoff at the floor, where it passes
 * everything that can be heard, and once started stays in until the song changes or is seeked.
 *
 * 16-bit PCM only; anything else passes through.
 */
class MixChannelAudioProcessor : BaseAudioProcessor() {

    /** Target level, 0..1. */
    @Volatile var gain: Float = 1f

    /** Target high-pass cutoff in Hz; at or below [FLOOR_HZ] the filter is off. */
    @Volatile var cutoffHz: Float = FLOOR_HZ

    @Volatile var forceBypass: Boolean = false

    private var supported = false
    private var channels = 2
    private var sampleRate = 44_100

    private var currentGain = 1.0
    private var currentLogCutoff = ln(FLOOR_HZ.toDouble())
    private var filtering = false

    // Two cascaded biquads per channel (4th-order Butterworth), transposed direct form II.
    private var z1a = DoubleArray(2)
    private var z2a = DoubleArray(2)
    private var z1b = DoubleArray(2)
    private var z2b = DoubleArray(2)
    private val stageA = DoubleArray(5)
    private val stageB = DoubleArray(5)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!supported) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        z1a = DoubleArray(channels)
        z2a = DoubleArray(channels)
        z1b = DoubleArray(channels)
        z2b = DoubleArray(channels)
        return inputAudioFormat
    }

    /** Whether it's at rest and will stay there: nothing to do for this buffer. */
    private fun resting(): Boolean {
        val targetGain = gain.toDouble().coerceIn(0.0, 1.0)
        val targetCutoff = cutoffHz.coerceAtLeast(FLOOR_HZ)
        return !filtering && abs(currentGain - 1.0) < 1e-6 && abs(targetGain - 1.0) < 1e-6 &&
            targetCutoff <= FLOOR_HZ && currentLogCutoff <= ln(FLOOR_HZ.toDouble()) + 1e-6
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val start = inputBuffer.position()
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (forceBypass || !supported || resting()) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        try {
            process(inputBuffer, output)
        } catch (t: Throwable) {
            Log.e("MixChannelAudioProcessor", "Failed; bypassing permanently", t)
            forceBypass = true
            inputBuffer.position(start)
            output.clear()
            output.put(inputBuffer)
            output.flip()
        }
    }

    private fun process(input: ByteBuffer, output: ByteBuffer) {
        val targetGain = gain.toDouble().coerceIn(0.0, 1.0)
        val targetLogCutoff = ln(cutoffHz.coerceIn(FLOOR_HZ, MAX_HZ).toDouble())
        // About 15 ms to get most of the way to a new target.
        val smoothing = 1.0 - exp(-1.0 / (SMOOTHING_SECONDS * sampleRate))
        val blockSmoothing = 1.0 - exp(-BLOCK_FRAMES.toDouble() / (SMOOTHING_SECONDS * sampleRate))
        val floorLog = ln(FLOOR_HZ.toDouble())

        var frameInBlock = BLOCK_FRAMES
        while (input.remaining() >= 2 * channels) {
            if (frameInBlock == BLOCK_FRAMES) {
                frameInBlock = 0
                currentLogCutoff += (targetLogCutoff - currentLogCutoff) * blockSmoothing
                if (abs(currentLogCutoff - targetLogCutoff) < 1e-4) currentLogCutoff = targetLogCutoff
                val wantFilter = currentLogCutoff > floorLog + 1e-6
                if (wantFilter && !filtering) {
                    // From rest: start from silence-state filters at the floor, inaudible.
                    z1a.fill(0.0); z2a.fill(0.0); z1b.fill(0.0); z2b.fill(0.0)
                }
                // Latched until the next flush: dropping the filter mid-song, even at the floor,
                // would step the phase of the deepest bass and could be heard as a thump.
                if (wantFilter) filtering = true
                if (filtering) designHighPass(exp(currentLogCutoff))
            }
            frameInBlock++
            currentGain += (targetGain - currentGain) * smoothing
            for (c in 0 until channels) {
                var x = input.getShort() / 32768.0
                if (filtering) {
                    x = biquad(x, stageA, z1a, z2a, c)
                    x = biquad(x, stageB, z1b, z2b, c)
                }
                val y = (x * currentGain).coerceIn(-1.0, 1.0)
                output.putShort((y * 32767.0).toInt().toShort())
            }
        }
        if (abs(currentGain - targetGain) < 1e-5) currentGain = targetGain
        while (input.hasRemaining()) output.put(input.get())
        output.flip()
    }

    private fun biquad(x: Double, k: DoubleArray, z1: DoubleArray, z2: DoubleArray, c: Int): Double {
        val y = k[0] * x + z1[c]
        z1[c] = k[1] * x - k[3] * y + z2[c]
        z2[c] = k[2] * x - k[4] * y
        return y
    }

    /** 4th-order Butterworth high-pass at [fc]: two biquads with Q 0.541 and 1.307. */
    private fun designHighPass(fc: Double) {
        val clamped = fc.coerceIn(FLOOR_HZ.toDouble(), sampleRate * 0.45)
        val k = tan(PI * clamped / sampleRate)
        stage(stageA, k, 0.5411961)
        stage(stageB, k, 1.3065630)
    }

    private fun stage(out: DoubleArray, k: Double, q: Double) {
        val norm = 1.0 / (1.0 + k / q + k * k)
        out[0] = norm
        out[1] = -2.0 * norm
        out[2] = norm
        out[3] = 2.0 * (k * k - 1.0) * norm
        out[4] = (1.0 - k / q + k * k) * norm
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        z1a.fill(0.0); z2a.fill(0.0); z1b.fill(0.0); z2b.fill(0.0)
        // Jump straight to the targets: there's no audio either side of a flush to glide across.
        currentGain = gain.toDouble().coerceIn(0.0, 1.0)
        currentLogCutoff = ln(cutoffHz.coerceIn(FLOOR_HZ, MAX_HZ).toDouble())
        filtering = currentLogCutoff > ln(FLOOR_HZ.toDouble()) + 1e-6
        if (filtering) designHighPass(exp(currentLogCutoff))
    }

    override fun onReset() {
        // The targets are the engine's, and are left alone: a reset can come after it has set them
        // for the next song.
        onFlush(AudioProcessor.StreamMetadata.DEFAULT)
        supported = false
    }

    companion object {
        /** Cutoff at which the filter is off: below anything a speaker plays. */
        const val FLOOR_HZ = 12f

        /** The highest a filter sweep goes. */
        const val MAX_HZ = 2_000f

        /** Where a bass kill sits: the kick and bassline gone, the rest of the song left. */
        const val BASS_KILL_HZ = 220f

        private const val SMOOTHING_SECONDS = 0.015
        private const val BLOCK_FRAMES = 32
    }
}
