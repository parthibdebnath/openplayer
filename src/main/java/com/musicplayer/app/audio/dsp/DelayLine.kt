package com.musicplayer.app.audio.dsp

/** Simple circular delay line, used for pre-delay and other fixed-length echo taps. */
class DelayLine(maxSamples: Int) {
    private val buffer = FloatArray(maxSamples.coerceAtLeast(1))
    private var writeIndex = 0

    fun process(input: Float, delaySamples: Int): Float {
        val clampedDelay = delaySamples.coerceIn(0, buffer.size - 1)
        var readIndex = writeIndex - clampedDelay
        if (readIndex < 0) readIndex += buffer.size
        val output = buffer[readIndex]
        buffer[writeIndex] = input
        writeIndex++
        if (writeIndex >= buffer.size) writeIndex = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
    }
}
