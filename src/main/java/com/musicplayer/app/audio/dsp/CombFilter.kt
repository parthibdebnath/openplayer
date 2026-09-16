package com.musicplayer.app.audio.dsp

/** Freeverb-style feedback comb filter with a one-pole damping (lowpass) filter in the feedback path. */
class CombFilter(delaySamples: Int) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0
    private var filterStore = 0f

    fun process(input: Float, feedback: Float, damp1: Float, damp2: Float): Float {
        val output = buffer[index]
        filterStore = output * damp2 + filterStore * damp1
        buffer[index] = input + filterStore * feedback
        index++
        if (index >= buffer.size) index = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
        filterStore = 0f
    }
}
