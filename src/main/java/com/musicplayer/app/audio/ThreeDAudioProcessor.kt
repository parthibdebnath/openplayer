package com.musicplayer.app.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import com.musicplayer.app.audio.dsp.CombFilter
import java.nio.ByteBuffer

/**
 * Custom "3D / spatial audio" processor. Not true HRTF binaural rendering (that needs bundled
 * HRIR filter data, out of scope here) but a stereo-speaker-appropriate approximation:
 *  - Center: a balance control (L100..R100) scaling one channel down, matching the spec's naming.
 *  - Width: mid/side processing that narrows (0%) or restores (100%) the stereo image.
 *  - Distance: amplitude attenuation plus a one-pole lowpass, simulating air absorption.
 *  - Room: a light comb-filter send simulating early reflections, separate from the dedicated
 *    Reverb effect.
 *  - Intensity: overall wet/dry blend of everything above (0% = untouched passthrough).
 *
 * Operates on 16-bit PCM only; any other encoding is passed through unmodified.
 */
class ThreeDAudioProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false
    @Volatile var intensityPercent: Int = 0
    @Volatile var widthPercent: Int = 0
    @Volatile var distancePercent: Int = 0
    @Volatile var roomPercent: Int = 0
    @Volatile var centerPercent: Int = 0

    /** Latched off after any processing failure, and settable by the engine as a recovery measure. */
    @Volatile var forceBypass: Boolean = false

    private var channelCount = 2
    private var formatSupported = false
    private lateinit var lowpassState: FloatArray
    private lateinit var roomCombs: Array<Array<CombFilter>>

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Never throw here, and report NOT_SET for formats we don't handle - see
        // ReverbAudioProcessor.onConfigure for the reasoning.
        formatSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!formatSupported) {
            return AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        val scale = inputAudioFormat.sampleRate / 44100.0
        val roomTuningsMs = intArrayOf(17, 29, 37) // short, distinct from the Reverb effect's longer taps
        lowpassState = FloatArray(channelCount)
        roomCombs = Array(channelCount) { channel ->
            Array(roomTuningsMs.size) { i ->
                val delaySamples = (roomTuningsMs[i] * inputAudioFormat.sampleRate / 1000.0 * scale).toInt()
                CombFilter(delaySamples)
            }
        }
        return inputAudioFormat
    }

    // isActive() deliberately left to BaseAudioProcessor; see ReverbAudioProcessor for why.

    override fun queueInput(inputBuffer: ByteBuffer) {
        // See ReverbAudioProcessor.queueInput: an empty input must return before the output buffer
        // is touched, or the passthrough copy ends up copying a buffer into itself and takes
        // playback down with it.
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        val startPosition = inputBuffer.position()
        val output = replaceOutputBuffer(remaining)
        if (!enabled || !formatSupported || forceBypass) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        try {
            processInto(inputBuffer, output)
        } catch (t: Throwable) {
            Log.e("ThreeDAudioProcessor", "DSP failed; bypassing permanently", t)
            forceBypass = true
            inputBuffer.position(startPosition)
            output.clear()
            if (output !== inputBuffer) output.put(inputBuffer)
            output.flip()
        }
    }

    private fun processInto(inputBuffer: ByteBuffer, output: ByteBuffer) {
        val intensity = intensityPercent.coerceIn(0, 100) / 100f
        val widthGain = widthPercent.coerceIn(0, 100) / 100f
        val distance = distancePercent.coerceIn(0, 100) / 100f
        val room = roomPercent.coerceIn(0, 100) / 100f
        val center = centerPercent.coerceIn(-100, 100)
        val leftGain = if (center > 0) 1f - (center / 100f) else 1f
        val rightGain = if (center < 0) 1f - (-center / 100f) else 1f
        val distanceAmplitude = 1f - distance * 0.5f
        val lowpassAlpha = 1f - distance * 0.8f
        val roomFeedback = 0.3f

        if (channelCount == 2) {
            // Whole frames only: reading a pair of samples from a buffer holding an odd number of
            // them underflows, and an underflow here is a fatal player error.
            while (inputBuffer.remaining() >= BYTES_PER_STEREO_FRAME) {
                val leftDry = inputBuffer.getShort() / 32768f
                val rightDry = inputBuffer.getShort() / 32768f

                val mid = (leftDry + rightDry) * 0.5f
                val side = (leftDry - rightDry) * 0.5f * widthGain
                var left = mid + side
                var right = mid - side

                left *= distanceAmplitude
                right *= distanceAmplitude
                lowpassState[0] = lowpassState[0] + lowpassAlpha * (left - lowpassState[0])
                lowpassState[1] = lowpassState[1] + lowpassAlpha * (right - lowpassState[1])
                left = lowpassState[0]
                right = lowpassState[1]

                var roomLeft = 0f
                for (comb in roomCombs[0]) roomLeft += comb.process(left, roomFeedback, 0.2f, 0.8f)
                roomLeft /= roomCombs[0].size
                var roomRight = 0f
                for (comb in roomCombs[1]) roomRight += comb.process(right, roomFeedback, 0.2f, 0.8f)
                roomRight /= roomCombs[1].size
                left += roomLeft * room
                right += roomRight * room

                left *= leftGain
                right *= rightGain

                val outLeft = (leftDry * (1f - intensity) + left * intensity).coerceIn(-1f, 1f)
                val outRight = (rightDry * (1f - intensity) + right * intensity).coerceIn(-1f, 1f)
                output.putShort((outLeft * 32767f).toInt().toShort())
                output.putShort((outRight * 32767f).toInt().toShort())
            }
        } else {
            var channel = 0
            while (inputBuffer.remaining() >= BYTES_PER_SAMPLE) {
                val dry = inputBuffer.getShort() / 32768f
                var sample = dry * distanceAmplitude
                lowpassState[channel] = lowpassState[channel] + lowpassAlpha * (sample - lowpassState[channel])
                sample = lowpassState[channel]
                var roomWet = 0f
                for (comb in roomCombs[channel]) roomWet += comb.process(sample, roomFeedback, 0.2f, 0.8f)
                roomWet /= roomCombs[channel].size
                sample += roomWet * room
                val out = (dry * (1f - intensity) + sample * intensity).coerceIn(-1f, 1f)
                output.putShort((out * 32767f).toInt().toShort())
                channel++
                if (channel >= channelCount) channel = 0
            }
        }
        // Any trailing partial frame passes through untouched, so the output is always exactly as
        // many bytes as the input.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_STEREO_FRAME = 4
    }

    override fun onFlush() {
        if (::lowpassState.isInitialized) {
            lowpassState.fill(0f)
            roomCombs.forEach { channelCombs -> channelCombs.forEach { it.clear() } }
        }
    }

    override fun onReset() {
        onFlush()
    }
}
