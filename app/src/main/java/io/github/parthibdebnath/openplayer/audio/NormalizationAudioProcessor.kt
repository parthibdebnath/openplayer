@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Evens out loudness between songs. A song tagged with ReplayGain plays at its tagged track gain;
 * an untagged one - most people's files - is levelled towards the same loudness by a slow
 * automatic gain, so a quiet old recording isn't drowned out by the loud master after it.
 *
 * The tag gain for each song is set by [NormalizingAudioRenderer] on the playback thread just as
 * that song's first decoded audio arrives, so it changes exactly at the gapless boundary rather
 * than when the player reports the transition, which is a buffer's length late.
 *
 * Boosts go through a peak limiter, so a quiet song turned up never clips. 16-bit PCM only, like
 * the other effects; anything else drops the processor out of the chain (see [onConfigure]).
 */
class NormalizationAudioProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false

    /** Latched off after any processing failure, and settable by the engine as a recovery measure. */
    @Volatile var forceBypass: Boolean = false

    /**
     * The current song's ReplayGain track gain in dB, or null when it has none and is levelled
     * automatically. Written and read on the playback thread only.
     */
    var trackGainDb: Float? = null

    private var sampleRateHz = 44100
    private var channelCount = 2
    private var formatSupported = false

    // The gain being applied, in dB, and the target it moves towards. Kept across songs: the last
    // song's level is the best first guess for the next one, where starting again from 0 dB
    // blasted the start of every loud song after a quiet one.
    private var currentGainDb = 0f
    private var appliedLinearGain = 1f

    // Loudness detector: mean square of the signal (with the bass filtered out, since bass carries
    // most of the energy but little of how loud music sounds), over a slow and a fast window.
    private var slowMeanSquare = TARGET_MEAN_SQUARE
    private var fastMeanSquare = TARGET_MEAN_SQUARE
    private var highPassState = FloatArray(2)
    private var highPassPrevious = FloatArray(2)

    // Limiter envelope, in linear amplitude.
    private var limiterEnvelope = 0f

    private var blockFrames = 441
    private var slowCoefficient = 0f
    private var fastCoefficient = 0f
    private var limiterRelease = 0f
    private var highPassCoefficient = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Never throw: rejecting a format from an AudioProcessor crashes playback. NOT_SET is the
        // framework's way of saying "nothing to do with this one".
        formatSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!formatSupported) return AudioFormat.NOT_SET
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        blockFrames = max(1, sampleRateHz * BLOCK_MS / 1000)
        val blockSeconds = blockFrames.toFloat() / sampleRateHz
        slowCoefficient = exp(-blockSeconds / SLOW_WINDOW_SECONDS)
        fastCoefficient = exp(-blockSeconds / FAST_WINDOW_SECONDS)
        limiterRelease = exp(-1f / (LIMITER_RELEASE_SECONDS * sampleRateHz))
        highPassCoefficient = exp(-2f * Math.PI.toFloat() * DETECTOR_HIGH_PASS_HZ / sampleRateHz)
        highPassState = FloatArray(channelCount)
        highPassPrevious = FloatArray(channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // An empty input must leave the output alone: for a zero-byte request replaceOutputBuffer
        // hands back the shared EMPTY_BUFFER that came in, and copying it into itself throws.
        if (!inputBuffer.hasRemaining()) return

        val startPosition = inputBuffer.position()
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (!enabled || !formatSupported || forceBypass) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        try {
            processInto(inputBuffer, output)
        } catch (t: Throwable) {
            // A throw from here is a fatal player error. Go transparent for good instead.
            Log.e("NormalizationAudioProcessor", "DSP failed; bypassing permanently", t)
            forceBypass = true
            inputBuffer.position(startPosition)
            output.clear()
            if (output !== inputBuffer) output.put(inputBuffer)
            output.flip()
        }
    }

    private fun processInto(inputBuffer: ByteBuffer, output: ByteBuffer) {
        val frameBytes = channelCount * BYTES_PER_SAMPLE
        val tagGain = trackGainDb
        while (inputBuffer.remaining() >= frameBytes) {
            val frames = min(blockFrames, inputBuffer.remaining() / frameBytes)
            val blockStart = inputBuffer.position()

            // Measure this block before applying gain, so the detector follows the music itself.
            if (tagGain == null) measure(inputBuffer, frames)
            inputBuffer.position(blockStart)

            val targetDb = tagGain?.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) ?: levelledGainDb()
            val blockSeconds = frames.toFloat() / sampleRateHz
            currentGainDb = when {
                // A tagged song plays at its gain from its first sample; a short ramp only keeps
                // the step from clicking.
                tagGain != null -> targetDb
                // Turning down is quick, so a loud song after a quiet one settles within a second
                // or two; turning up is slow, so quiet passages within a song aren't pumped up.
                targetDb < currentGainDb -> max(targetDb, currentGainDb - DOWN_DB_PER_SECOND * blockSeconds)
                else -> min(targetDb, currentGainDb + UP_DB_PER_SECOND * blockSeconds)
            }
            val endLinear = dbToLinear(currentGainDb)
            val step = (endLinear - appliedLinearGain) / frames

            for (frame in 0 until frames) {
                appliedLinearGain += step
                // Linked across channels, so limiting a loud left channel doesn't shift the image.
                var peak = 0f
                val framePosition = inputBuffer.position()
                for (channel in 0 until channelCount) {
                    peak = max(peak, abs(inputBuffer.getShort() / 32768f * appliedLinearGain))
                }
                inputBuffer.position(framePosition)
                limiterEnvelope = max(peak, limiterEnvelope * limiterRelease)
                val limit = if (limiterEnvelope > LIMITER_CEILING) LIMITER_CEILING / limiterEnvelope else 1f
                val gain = appliedLinearGain * limit
                for (channel in 0 until channelCount) {
                    val sample = (inputBuffer.getShort() / 32768f * gain).coerceIn(-1f, 1f)
                    output.putShort((sample * 32767f).toInt().toShort())
                }
            }
            appliedLinearGain = endLinear
        }
        // A trailing partial frame passes through, so output is always as long as input.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    /** Feeds one block into the loudness detector. Near-silence is skipped, so fades and gaps aren't boosted. */
    private fun measure(inputBuffer: ByteBuffer, frames: Int) {
        var sum = 0f
        for (frame in 0 until frames) {
            for (channel in 0 until channelCount) {
                val x = inputBuffer.getShort() / 32768f
                // One-pole high-pass: y[n] = a * (y[n-1] + x[n] - x[n-1]).
                val y = highPassCoefficient * (highPassState[channel] + x - highPassPrevious[channel])
                highPassPrevious[channel] = x
                highPassState[channel] = y
                sum += y * y
            }
        }
        val meanSquare = sum / (frames * channelCount)
        if (meanSquare < GATE_MEAN_SQUARE) return
        slowMeanSquare = slowCoefficient * slowMeanSquare + (1f - slowCoefficient) * meanSquare
        fastMeanSquare = fastCoefficient * fastMeanSquare + (1f - fastCoefficient) * meanSquare
    }

    /**
     * The gain that brings the detected loudness to the target. The fast window only counts when
     * it's well above the slow one - a loud song starting - so the level reacts to that quickly
     * without riding every drum hit.
     */
    private fun levelledGainDb(): Float {
        val level = max(slowMeanSquare, fastMeanSquare / FAST_WINDOW_MARGIN)
        val levelDb = 10f * log10(max(level, 1e-10f))
        return (TARGET_DB - levelDb).coerceIn(MIN_GAIN_DB, MAX_LEVELLED_GAIN_DB)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // A seek or a new song: the limiter starts fresh, but the level carries over (see above).
        limiterEnvelope = 0f
        highPassState.fill(0f)
        highPassPrevious.fill(0f)
        appliedLinearGain = dbToLinear(currentGainDb)
    }

    override fun onReset() {
        onFlush(AudioProcessor.StreamMetadata.DEFAULT)
        trackGainDb = null
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val BLOCK_MS = 10

        /**
         * Where untagged songs are levelled to: about the ReplayGain reference loudness, measured
         * the way [measure] does. A typical modern master comes out around -9 dB, as its tags
         * would say.
         */
        private const val TARGET_DB = -20f
        private val TARGET_MEAN_SQUARE = 10f.pow(TARGET_DB / 10f)
        private val GATE_MEAN_SQUARE = 10f.pow(-50f / 10f)

        private const val MIN_GAIN_DB = -15f
        private const val MAX_GAIN_DB = 12f

        /** Untagged songs get less boost than tagged ones: the guess is rougher than a tag. */
        private const val MAX_LEVELLED_GAIN_DB = 8f

        private const val SLOW_WINDOW_SECONDS = 3f
        private const val FAST_WINDOW_SECONDS = 0.4f
        /** 4 dB, as a power ratio. */
        private const val FAST_WINDOW_MARGIN = 2.5f
        private const val DOWN_DB_PER_SECOND = 15f
        private const val UP_DB_PER_SECOND = 1f
        private const val DETECTOR_HIGH_PASS_HZ = 120f

        /** -1 dBFS. */
        private const val LIMITER_CEILING = 0.891f
        private const val LIMITER_RELEASE_SECONDS = 0.08f

        private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    }
}
