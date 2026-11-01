package io.github.parthibdebnath.openplayer.audio.mix

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Changes tempo without changing pitch, by WSOLA (waveform-similarity overlap-add), with a rate
 * that can move continuously while it runs. That's the whole point of having our own: ExoPlayer's
 * speed setting drains and rebuilds its processing chain on every change, so riding the tempo
 * through a mix the way a DJ rides a pitch fader would click at every step.
 *
 * Audio is cut into Hann-windowed grains [window] frames long, written out every [hop] frames,
 * which sums back to exactly the input when nothing is moved. To play faster each grain is read
 * from further along the input than the last; to keep that from smearing waveforms, each grain is
 * taken from wherever within [tolerance] of its nominal place best continues the previous one.
 *
 * At a rate of exactly 1 the grains are read back to back and the output is the input, sample for
 * sample - see [isAligned], which tells the caller it can stop stretching altogether.
 *
 * Positions are absolute frame counts since the stretcher was created. [onGrain] reports, for
 * each block of output, which input frame it starts at, so the player's clock can be mapped back
 * to the song's own time; see [TempoAudioProcessor].
 *
 * Interleaved float samples in, interleaved float samples out. Not thread-safe; the rate and
 * shifts are handed in by the processor that owns it.
 */
class TimeStretcher(
    val channels: Int,
    sampleRate: Int,
    /** The absolute input frame the first input frame is. */
    startInputFrame: Long = 0L,
    /** The absolute output frame the first output frame is. */
    startOutputFrame: Long = 0L,
) {
    val window: Int = (sampleRate * WINDOW_SECONDS).toInt().let { it + (it and 1) }.coerceAtLeast(64)
    val hop: Int = window / 2
    val tolerance: Int = (sampleRate * TOLERANCE_SECONDS).toInt().coerceAtLeast(8)

    /** Periodic Hann: two copies a half-window apart add up to exactly 1. */
    private val hann = FloatArray(window) { 0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * it / window).toFloat() }

    // Input, interleaved; inputBase is the absolute frame of in[0].
    private var input = FloatArray(window * channels * 8)
    private var inputBase = startInputFrame
    private var inputFrames = 0

    /** Where the next grain would be read from at the requested tempo. */
    private var nominal = startInputFrame.toDouble()

    /** Where the next grain must start to continue the last one seamlessly. */
    private var natural = startInputFrame

    /** Second half of the last grain, already windowed, waiting for the next grain's first half. */
    private val tail = FloatArray(hop * channels)
    private var started = false
    private var ended = false

    /** Total frames written out so far: the absolute output frame of the next one. */
    var outputPosition = startOutputFrame
        private set

    /** Frames to jump the input by before the next grain; positive skips ahead. */
    private var pendingShift = 0.0

    var onGrain: ((outputFrame: Long, inputFrame: Long) -> Unit)? = null

    /** The absolute input frame one past the last frame given. */
    val inputEnd: Long get() = inputBase + inputFrames

    /** The input frame the next output frame comes from. */
    val nextContentFrame: Long get() = natural

    /**
     * Whether the output has caught up with the input exactly: the last grain was read straight
     * on from the one before at rate 1, so carrying on with the plain input from [nextContentFrame]
     * is the same audio this would produce.
     */
    var isAligned = true
        private set

    fun shift(frames: Double) {
        pendingShift += frames
    }

    /** Appends interleaved samples; [count] is in samples, a whole number of frames. */
    fun write(samples: FloatArray, offset: Int, count: Int) {
        val frames = count / channels
        ensureCapacity(inputFrames + frames)
        System.arraycopy(samples, offset, input, inputFrames * channels, frames * channels)
        inputFrames += frames
    }

    /**
     * Writes as many output frames as the input allows into [out], at [rate] (input frames per
     * output frame), and returns how many samples were written. [out] must hold at least
     * [hop] frames; output comes in whole hops.
     */
    fun read(out: FloatArray, rate: Double): Int {
        var written = 0
        while (out.size - written >= hop * channels && canStep(rate)) {
            step(rate, out, written)
            written += hop * channels
        }
        return written
    }

    /**
     * Marks the end of the input, so the last grains can be written out. Call [read] until it
     * returns 0 afterwards.
     */
    fun end() {
        ended = true
    }

    /**
     * The input held from absolute frame [from], [frames] long, as it was given: what's left to
     * play when handing back to the plain input (see [isAligned]).
     */
    fun takeRemaining(from: Long, frames: Int): FloatArray =
        FloatArray(frames * channels) { sample(from * channels + it) }

    private fun canStep(rate: Double): Boolean {
        if (ended) return natural < inputEnd
        val searching = needsSearch(rate)
        val reach = if (searching) {
            maxOf((nominal + pendingShift).roundToLong() + tolerance + window, natural + hop)
        } else {
            natural + window
        }
        return reach <= inputEnd
    }

    private fun needsSearch(rate: Double) = started && (abs(rate - 1.0) > 1e-9 || abs(pendingShift) >= 0.5)

    private fun step(rate: Double, out: FloatArray, outOffset: Int) {
        val position: Long
        if (!started) {
            started = true
            nominal += pendingShift
            pendingShift = 0.0
            position = maxOf(nominal.roundToLong(), inputBase)
            // Nothing came before, so the first half goes out as it is: as if the grain before had
            // been its perfect continuation.
            for (i in 0 until hop * channels) out[outOffset + i] = sample(position * channels + i)
        } else {
            val searching = needsSearch(rate)
            position = if (searching) {
                nominal += pendingShift
                pendingShift = 0.0
                bestMatch(nominal.roundToLong())
            } else {
                natural
            }
            isAligned = !searching
            for (f in 0 until hop) {
                val w = hann[f]
                val base = (position + f) * channels
                for (c in 0 until channels) {
                    val i = f * channels + c
                    out[outOffset + i] = tail[i] + w * sample(base + c)
                }
            }
        }
        for (f in 0 until hop) {
            val w = hann[hop + f]
            val base = (position + hop + f) * channels
            for (c in 0 until channels) tail[f * channels + c] = w * sample(base + c)
        }
        onGrain?.invoke(outputPosition, position)
        outputPosition += hop
        natural = position + hop
        // At rate 1 the nominal place follows the grains exactly, so tempo resumes cleanly.
        nominal = if (abs(rate - 1.0) <= 1e-9) natural.toDouble() else nominal + hop * rate
        discardBefore(minOf(natural, nominal.toLong()) - tolerance - MAX_BACK_SHIFT_FRAMES)
    }

    /** One sample by absolute index (frame * channels + channel); silence outside what's held. */
    private fun sample(absoluteIndex: Long): Float {
        val i = absoluteIndex - inputBase * channels
        return if (i < 0 || i >= inputFrames.toLong() * channels) 0f else input[i.toInt()]
    }

    /**
     * The read position within [tolerance] of [center] whose audio best continues the last grain:
     * a coarse search on a quarter-rate mono sketch, then a fine one around the winner.
     */
    private fun bestMatch(center: Long): Long {
        val low = maxOf(center - tolerance, inputBase)
        val high = maxOf(minOf(center + tolerance, inputEnd - window), low)
        if (high <= low) return low
        // A grain with an attack in it - a kick, a snare - is read from exactly its nominal place.
        // Searching would move it by up to the tolerance, and every beat landing up to 12 ms early
        // or late is exactly what a DJ mix can't have; the splice it costs is hidden under the attack.
        if (hasAttack(center)) return center.coerceIn(low, high)

        // The reference: what the previous grain would naturally have run on into.
        val refLength = hop / DECIMATION
        val reference = FloatArray(refLength) { monoAverage(natural + it * DECIMATION) }
        val span = ((high - low) / DECIMATION).toInt() + refLength + 1
        val sketch = FloatArray(span) { monoAverage(low + it.toLong() * DECIMATION) }

        var bestOffset = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var energy = 0.0
        for (i in 0 until refLength) energy += sketch[i] * sketch[i]
        val candidates = ((high - low) / DECIMATION).toInt()
        for (k in 0..candidates) {
            var dot = 0.0
            for (i in 0 until refLength) dot += reference[i] * sketch[k + i]
            val score = dot / sqrt(energy + 1e-9)
            if (score > bestScore) {
                bestScore = score
                bestOffset = k
            }
            if (k + refLength < span) {
                energy += sketch[k + refLength] * sketch[k + refLength] - sketch[k] * sketch[k]
                if (energy < 0) energy = 0.0
            }
        }

        // Refine at full rate, every frame within one sketch step either side.
        val coarse = low + bestOffset.toLong() * DECIMATION
        var best = coarse
        bestScore = Double.NEGATIVE_INFINITY
        for (candidate in maxOf(low, coarse - DECIMATION)..minOf(high, coarse + DECIMATION)) {
            var dot = 0.0
            var e = 0.0
            var f = 0
            while (f < hop) {
                val r = mono(natural + f)
                val s = mono(candidate + f)
                dot += r * s
                e += s * s
                f += FINE_STRIDE
            }
            val score = dot / sqrt(e + 1e-9)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /**
     * Whether a sharp rise in level starts within the grain at [start]: some stretch of it at
     * least [ATTACK_RATIO] times as loud as the stretch before.
     */
    private fun hasAttack(start: Long): Boolean {
        val block = window / ATTACK_BLOCKS
        var previous = blockEnergy(start - block, block)
        for (b in 0 until ATTACK_BLOCKS) {
            val energy = blockEnergy(start + b * block, block)
            if (energy > ATTACK_FLOOR * block && energy > previous * ATTACK_RATIO) return true
            previous = energy
        }
        return false
    }

    private fun blockEnergy(from: Long, length: Int): Float {
        var sum = 0f
        var f = 0
        while (f < length) {
            val v = mono(from + f)
            sum += v * v
            f += 2
        }
        return sum * 2
    }

    private fun mono(frame: Long): Float {
        var sum = 0f
        val base = frame * channels
        for (c in 0 until channels) sum += sample(base + c)
        return sum
    }

    private fun monoAverage(frame: Long): Float {
        var sum = 0f
        for (d in 0 until DECIMATION) sum += mono(frame + d)
        return sum
    }

    private fun ensureCapacity(frames: Int) {
        if (frames * channels <= input.size) return
        var size = input.size
        while (size < frames * channels) size *= 2
        input = input.copyOf(size)
    }

    private fun discardBefore(frame: Long) {
        val drop = (frame - inputBase).coerceIn(0L, inputFrames.toLong()).toInt()
        // In big steps only, so the copy isn't made on every grain.
        if (drop < window * 2) return
        System.arraycopy(input, drop * channels, input, 0, (inputFrames - drop) * channels)
        inputFrames -= drop
        inputBase += drop
    }

    companion object {
        /** Grain length: long enough for bass notes to keep their shape, short enough not to echo. */
        const val WINDOW_SECONDS = 0.046

        /** How far a grain may move from its nominal place to match the one before. */
        const val TOLERANCE_SECONDS = 0.012

        /** How far back the input can be jumped; what [shift] can undo of a skip. */
        const val MAX_BACK_SHIFT_FRAMES = 12_000L

        private const val DECIMATION = 4
        private const val ATTACK_BLOCKS = 4
        private const val ATTACK_RATIO = 4f

        /** Per frame, about -50 dBFS: quieter than this isn't an attack worth keeping in place. */
        private const val ATTACK_FLOOR = 1e-5f
        private const val FINE_STRIDE = 2
    }
}
