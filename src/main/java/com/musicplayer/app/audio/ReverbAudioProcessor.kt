package com.musicplayer.app.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.C
import com.musicplayer.app.audio.dsp.AllpassFilter
import com.musicplayer.app.audio.dsp.CombFilter
import com.musicplayer.app.audio.dsp.DelayLine
import java.nio.ByteBuffer
import kotlin.math.pow

/**
 * Custom software reverb (Freeverb-style: parallel comb filters -> series allpass filters,
 * per channel, with an independent pre-delay line) so behaviour is identical on every device
 * -- including Waydroid, where the OS's own hardware reverb effects may be absent or no-op.
 *
 * Operates on 16-bit PCM only; any other encoding is passed through unmodified (see [onConfigure]).
 */
class ReverbAudioProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false
    @Volatile var mixPercent: Int = 0
    @Volatile var decaySeconds: Double = 0.0
    @Volatile var preDelayMs: Int = 0
    @Volatile var dampingPercent: Int = 0
    @Volatile var diffusionPercent: Int = 0

    /** Latched off after any processing failure, and settable by the engine as a recovery measure. */
    @Volatile var forceBypass: Boolean = false

    private var sampleRateHz = 44100
    private var channelCount = 2
    private var formatSupported = false
    private lateinit var combs: Array<Array<CombFilter>>
    private lateinit var allpasses: Array<Array<AllpassFilter>>
    private lateinit var preDelayLines: Array<DelayLine>

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Never throw here: ExoPlayer can hand us encodings other than 16-bit PCM (float output,
        // etc.) depending on device/track, and rejecting the format from an AudioProcessor
        // crashes playback. AudioFormat.NOT_SET is the framework's own way of saying "I have
        // nothing to do with this format" - it drops the processor out of the chain entirely.
        formatSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!formatSupported) {
            return AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        val scale = sampleRateHz / 44100.0
        val combTuningsL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val allpassTuningsL = intArrayOf(556, 441, 341, 225)
        val stereoSpread = 23

        combs = Array(channelCount) { channel ->
            val offset = if (channel % 2 == 1) stereoSpread else 0
            Array(combTuningsL.size) { i -> CombFilter(((combTuningsL[i] + offset) * scale).toInt()) }
        }
        allpasses = Array(channelCount) { channel ->
            val offset = if (channel % 2 == 1) stereoSpread else 0
            Array(allpassTuningsL.size) { i -> AllpassFilter(((allpassTuningsL[i] + offset) * scale).toInt()) }
        }
        preDelayLines = Array(channelCount) {
            DelayLine((MAX_PRE_DELAY_MS * sampleRateHz / 1000) + 1)
        }
        return inputAudioFormat
    }

    // isActive() is deliberately NOT overridden. BaseAudioProcessor derives it from what
    // onConfigure returned, which is the contract the rest of the pipeline asserts against;
    // forcing it to a constant true meant claiming to be active before ever being configured.
    // Instant on/off still works because the processor stays in the chain for any 16-bit PCM
    // stream and bypasses internally on [enabled].

    override fun queueInput(inputBuffer: ByteBuffer) {
        // An empty input must return without touching the output buffer. ExoPlayer hands
        // processors empty buffers routinely, and for a zero-byte request replaceOutputBuffer
        // hands back the very same shared EMPTY_BUFFER instance that was passed in - at which
        // point copying input to output is a buffer copying into itself, which ByteBuffer.put
        // rejects outright. That throw surfaces as a fatal ERROR_CODE_FAILED_RUNTIME_CHECK and
        // kills playback before a single frame is heard.
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
            // A throw from here surfaces as a fatal player error and kills playback outright.
            // Degrade to transparent instead, permanently, and let the music keep going.
            Log.e("ReverbAudioProcessor", "DSP failed; bypassing permanently", t)
            forceBypass = true
            inputBuffer.position(startPosition)
            output.clear()
            if (output !== inputBuffer) output.put(inputBuffer)
            output.flip()
        }
    }

    private fun processInto(inputBuffer: ByteBuffer, output: ByteBuffer) {
        val preDelaySamples = (preDelayMs.coerceIn(0, MAX_PRE_DELAY_MS) * sampleRateHz / 1000)
        val decay = decaySeconds.coerceIn(0.0, 8.0)
        val damp1 = (dampingPercent.coerceIn(0, 100) / 100f) * 0.9f
        val damp2 = 1f - damp1
        val diffusionGain = (diffusionPercent.coerceIn(0, 100) / 100f) * 0.7f
        val mixGain = mixPercent.coerceIn(0, 100) / 100f

        var channel = 0
        // Whole samples only; a partial trailing sample would underflow, which is fatal here.
        while (inputBuffer.remaining() >= BYTES_PER_SAMPLE) {
            val sampleShort = inputBuffer.getShort()
            val drySample = sampleShort / 32768f
            val delayed = preDelayLines[channel].process(drySample, preDelaySamples)

            // Scale the comb input by (1 - feedback). A feedback comb settles at
            // input / (1 - feedback), so at the long decay times this effect allows - feedback
            // around 0.97 - feeding it full-scale audio built up to roughly 30x and slammed into
            // the output clamp, which is what made the reverb sound crunchy and tinny. Scaling the
            // input by the same factor holds the steady-state level at about unity for any decay.
            val feedback = combFeedback(decay)
            val combInput = delayed * (1f - feedback).coerceAtLeast(MIN_COMB_INPUT_GAIN)

            var combSum = 0f
            for (comb in combs[channel]) {
                combSum += comb.process(combInput, feedback, damp1, damp2)
            }
            combSum /= combs[channel].size

            var wet = combSum
            for (allpass in allpasses[channel]) {
                wet = allpass.process(wet, diffusionGain)
            }

            val mixed = drySample * (1f - mixGain) + wet * mixGain
            val clamped = mixed.coerceIn(-1f, 1f)
            output.putShort((clamped * 32767f).toInt().toShort())

            channel++
            if (channel >= channelCount) channel = 0
        }
        // Any trailing partial sample passes through untouched, so the output is always exactly
        // as many bytes as the input.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() {
        if (::combs.isInitialized) {
            combs.forEach { channelCombs -> channelCombs.forEach { it.clear() } }
            allpasses.forEach { channelAllpasses -> channelAllpasses.forEach { it.clear() } }
            preDelayLines.forEach { it.clear() }
        }
    }

    override fun onReset() {
        onFlush()
    }

    companion object {
        private const val MAX_PRE_DELAY_MS = 100
        private const val BYTES_PER_SAMPLE = 2

        /** Floor for the comb input scaling, so a zero-decay setting still passes signal through. */
        private const val MIN_COMB_INPUT_GAIN = 0.05f

        /**
         * Approximate RT60-based feedback gain: after [decaySeconds] the comb's echoes should
         * have decayed to ~0.1% of their original level. Clamped well below 1.0 for stability
         * even at very short comb delay lengths combined with the max 8s decay setting.
         */
        private fun combFeedback(decaySeconds: Double): Float {
            if (decaySeconds <= 0.0) return 0f
            val avgDelaySeconds = 1400.0 / 44100.0 // representative comb delay length
            val feedback = 0.001.pow(avgDelaySeconds / decaySeconds)
            return feedback.coerceAtMost(0.995).toFloat()
        }
    }
}
