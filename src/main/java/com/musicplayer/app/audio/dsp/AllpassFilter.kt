package com.musicplayer.app.audio.dsp

/** Freeverb-style allpass filter used to diffuse comb filter output into a smoother reverb tail. */
class AllpassFilter(delaySamples: Int) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Float, feedback: Float): Float {
        val bufferedValue = buffer[index]
        val output = -input + bufferedValue
        buffer[index] = input + bufferedValue * feedback
        index++
        if (index >= buffer.size) index = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
    }
}
