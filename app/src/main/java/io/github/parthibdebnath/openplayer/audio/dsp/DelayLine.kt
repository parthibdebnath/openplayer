package io.github.parthibdebnath.openplayer.audio.dsp

/** Simple circular delay line, used for pre-delay and other fixed-length echo taps. */
class DelayLine(maxSamples: Int) {
    private val buffer = FloatArray(maxSamples.coerceAtLeast(1))
    private var writeIndex = 0

    fun process(input: Float, delaySamples: Int): Float {
        val clampedDelay = delaySamples.coerceIn(0, buffer.size - 1)
        // Written before reading, so a delay of zero hands back this very sample. Reading first
        // made zero the longest delay instead - the slot about to be overwritten is the oldest one
        // - so a pre-delay set to 0 ms actually played at the full 100 ms.
        buffer[writeIndex] = input
        var readIndex = writeIndex - clampedDelay
        if (readIndex < 0) readIndex += buffer.size
        val output = buffer[readIndex]
        writeIndex++
        if (writeIndex >= buffer.size) writeIndex = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
    }
}
