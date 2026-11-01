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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * The DJ mix's tempo control: plays a song faster or slower without changing its key, at a rate
 * the engine can move smoothly while it plays (see [TimeStretcher]), and can nudge it forwards or
 * back by a few milliseconds to land its beats on the other song's.
 *
 * At rate 1 with nothing to correct it copies its input straight through, so outside a mix it
 * costs nothing and changes nothing. It starts stretching the moment it's asked to, and hands back
 * to the plain input as soon as the stretcher has caught up with it exactly.
 *
 * ExoPlayer works out the song position from how much audio has played, assuming its own speed
 * setting is the only thing changing the length of the audio. [mediaFramesFor] says how much of
 * the song a given amount of this processor's output really was, and [MixingAudioProcessorChain]
 * hands that to ExoPlayer, so the position - and with it the beat matching, the seek bar and the
 * crossfade - stays exact while the tempo moves.
 *
 * 16-bit PCM only, like the other processors here; anything else passes through untouched.
 */
class TempoAudioProcessor : BaseAudioProcessor() {

    /** Song frames per output frame: 1.03 plays 3% faster. Set from the main thread. */
    @Volatile var rate: Float = 1f

    /** Latched off after any failure; set by the engine too, as a recovery measure. */
    @Volatile var forceBypass: Boolean = false

    private val pendingShiftMicros = AtomicLong(0L)

    private var supported = false
    private var channels = 2
    private var sampleRate = 44_100
    private var stretcher: TimeStretcher? = null

    /** The rate actually applied, moving towards [rate] a little per grain so it never jumps. */
    private var appliedRate = 1.0

    // What has come in and gone out since the last flush, in frames.
    private var inputFramesTotal = 0L
    private var outputFramesTotal = 0L

    /**
     * Output frame -> song frame, from each grain, since the last flush. Past the last one the
     * song moves at [mapSlopeAfterLast] (1 once the stretcher has handed back to the input).
     */
    private val anchorOut = LongRingBuffer()
    private val anchorIn = LongRingBuffer()
    private var mapSlopeAfterLast = 1.0

    private var floatIn = FloatArray(0)
    private var floatOut = FloatArray(0)

    /** Output made by one call, written to the output buffer in one go at the end. */
    private var pending = FloatArray(0)
    private var pendingCount = 0

    /**
     * Moves the song by [micros] of its own time before the next grain: positive skips ahead,
     * negative goes back. Small amounts only, while the song is quiet; see the engine's beat lock.
     */
    fun shiftBy(micros: Long) {
        pendingShiftMicros.addAndGet(micros)
    }

    /**
     * Starts the next stream with [micros] of silence before the song: set before a song is
     * loaded, so it outlasts the flush that loading it causes (a shift would be cleared by it).
     */
    fun holdAtStart(micros: Long) {
        pendingHoldMicros = micros.coerceIn(0L, MAX_HOLD_MICROS)
    }

    @Volatile private var pendingHoldMicros = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!supported) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val start = inputBuffer.position()
        try {
            process(inputBuffer)
        } catch (t: Throwable) {
            // Never take playback down: drop back to the plain audio for good.
            Log.e("TempoAudioProcessor", "Stretching failed; bypassing permanently", t)
            forceBypass = true
            stretcher = null
            inputBuffer.position(start)
            passThrough(inputBuffer)
        }
    }

    private fun process(inputBuffer: ByteBuffer) {
        val hold = pendingHoldMicros
        if (hold > 0L) {
            pendingHoldMicros = 0L
            pendingShiftMicros.addAndGet(-hold)
        }
        val target = rate.toDouble().takeIf { it.isFinite() && it > 0.0 }?.coerceIn(MIN_RATE, MAX_RATE) ?: 1.0
        var active = stretcher
        if (active == null) {
            if (forceBypass || (abs(target - 1.0) < 1e-6 && abs(appliedRate - 1.0) < 1e-6 && pendingShiftMicros.get() == 0L)) {
                passThrough(inputBuffer)
                return
            }
            active = engage()
        }

        val frames = inputBuffer.remaining() / (2 * channels)
        if (floatIn.size < frames * channels) floatIn = FloatArray(frames * channels)
        for (i in 0 until frames * channels) floatIn[i] = inputBuffer.getShort() / 32768f
        // A trailing partial frame (never seen in practice) is dropped rather than misaligning
        // every channel after it.
        inputBuffer.position(inputBuffer.limit())
        active.write(floatIn, 0, frames * channels)
        inputFramesTotal += frames

        val shiftMicros = pendingShiftMicros.getAndSet(0L)
        if (shiftMicros != 0L) active.shift(shiftMicros * sampleRate / 1_000_000.0)

        pendingCount = 0
        stretchInto(active, target)

        // Caught up and back at normal speed: carry on with the plain input from here.
        if (abs(target - 1.0) < 1e-6 && abs(appliedRate - 1.0) < 1e-6 && active.isAligned &&
            pendingShiftMicros.get() == 0L
        ) {
            handBack(active)
        }
        emitPending()
    }

    /**
     * Starts stretching from the next input frame. A shift back asked for before there's any
     * stretching history to go back into is made by holding: silence for that long, then the
     * song from where it was - only ever asked for while the song is still silent in the mix.
     */
    private fun engage(): TimeStretcher {
        val back = (-pendingShiftMicros.get()).coerceAtLeast(0L)
        val holdFrames = (minOf(back, MAX_HOLD_MICROS) * sampleRate / 1_000_000L).toInt()
        if (back > 0L) pendingShiftMicros.addAndGet(back)
        val created = TimeStretcher(
            channels,
            sampleRate,
            startInputFrame = inputFramesTotal - holdFrames,
            startOutputFrame = outputFramesTotal,
        )
        created.onGrain = { outFrame, inFrame -> addAnchor(outFrame, inFrame) }
        if (holdFrames > 0) created.write(FloatArray(holdFrames * channels), 0, holdFrames * channels)
        stretcher = created
        return created
    }

    /** Stretches whatever the input so far allows into [pending]. */
    private fun stretchInto(active: TimeStretcher, target: Double) {
        val grainSamples = active.hop * channels
        if (floatOut.size < grainSamples) floatOut = FloatArray(grainSamples)
        while (true) {
            appliedRate = approach(appliedRate, target)
            val written = active.read(floatOut, appliedRate)
            if (written == 0) break
            appendPending(floatOut, written)
            outputFramesTotal += written / channels
        }
    }

    /**
     * Back to the plain input: the stretcher holds input it hasn't played yet, from
     * [TimeStretcher.nextContentFrame] on, which goes out as it is, and everything after it
     * passes straight through.
     */
    private fun handBack(active: TimeStretcher) {
        val from = active.nextContentFrame
        addAnchor(outputFramesTotal, from)
        synchronized(anchorOut) { mapSlopeAfterLast = 1.0 }
        stretcher = null
        val leftover = (active.inputEnd - from).toInt()
        if (leftover <= 0) return
        val rest = active.takeRemaining(from, leftover)
        appendPending(rest, rest.size)
        outputFramesTotal += leftover
    }

    private fun appendPending(samples: FloatArray, count: Int) {
        if (pending.size < pendingCount + count) pending = pending.copyOf(maxOf(pending.size * 2, pendingCount + count))
        System.arraycopy(samples, 0, pending, pendingCount, count)
        pendingCount += count
    }

    private fun emitPending() {
        val output = replaceOutputBuffer(pendingCount * 2)
        for (i in 0 until pendingCount) output.putShort(toShort(pending[i]))
        output.flip()
        pendingCount = 0
    }

    private fun passThrough(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * channels)
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer)
        output.flip()
        inputFramesTotal += frames
        outputFramesTotal += frames
    }

    override fun onQueueEndOfStream() {
        val active = stretcher ?: return
        try {
            active.end()
            pendingCount = 0
            stretchInto(active, rate.toDouble().coerceIn(MIN_RATE, MAX_RATE))
            emitPending()
        } catch (t: Throwable) {
            Log.e("TempoAudioProcessor", "Could not finish stretching", t)
        }
        stretcher = null
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        stretcher = null
        inputFramesTotal = 0L
        outputFramesTotal = 0L
        synchronized(anchorOut) {
            anchorOut.clear()
            anchorIn.clear()
            mapSlopeAfterLast = 1.0
        }
        pendingShiftMicros.set(0L)
        // Straight to the rate asked for, with no audio either side to glide across: a song
        // started at a mix's meeting tempo is at it from its first sample. The rate itself carries
        // on, so a seek in the middle of a mix keeps the song at its tempo.
        appliedRate = rate.toDouble().takeIf { it.isFinite() && it > 0.0 }?.coerceIn(MIN_RATE, MAX_RATE) ?: 1.0
    }

    override fun onReset() {
        // The rate and a pending hold are the engine's to set, and are left alone: a reset can come
        // after it has set them for the next song.
        onFlush(AudioProcessor.StreamMetadata.DEFAULT)
        supported = false
    }

    private fun addAnchor(outFrame: Long, inFrame: Long) = synchronized(anchorOut) {
        anchorOut.add(outFrame)
        anchorIn.add(inFrame)
        mapSlopeAfterLast = appliedRate
        if (anchorOut.size > MAX_ANCHORS) {
            anchorOut.removeFirst()
            anchorIn.removeFirst()
        }
    }

    /**
     * How many song frames [outputFrames] of this processor's output (counted from the last flush)
     * were. Called by the player as it works out the position; prunes what it won't ask about
     * again.
     */
    fun mediaFramesFor(outputFrames: Long): Long = synchronized(anchorOut) {
        if (anchorOut.size == 0) return outputFrames
        // Before the first grain the audio went straight through.
        if (outputFrames < anchorOut.first()) return outputFrames
        var index = anchorOut.lastIndexAtOrBefore(outputFrames)
        // What's before this one won't be asked about again (the position only moves forwards
        // between flushes), apart from a little slack for the clock's own smoothing.
        if (index > PRUNE_SLACK) {
            val drop = index - PRUNE_SLACK
            anchorOut.removeFirst(drop)
            anchorIn.removeFirst(drop)
            index -= drop
        }
        val out = anchorOut[index]
        val inFrame = anchorIn[index]
        val slope = if (index + 1 < anchorOut.size) {
            (anchorIn[index + 1] - inFrame).toDouble() / (anchorOut[index + 1] - out).coerceAtLeast(1)
        } else {
            mapSlopeAfterLast
        }
        inFrame + ((outputFrames - out) * slope).toLong()
    }

    fun mediaDurationUsFor(outputDurationUs: Long): Long {
        if (!supported) return outputDurationUs
        val frames = outputDurationUs * sampleRate / 1_000_000L
        val remainderUs = outputDurationUs - frames * 1_000_000L / sampleRate
        return mediaFramesFor(frames) * 1_000_000L / sampleRate + remainderUs
    }

    private fun approach(current: Double, target: Double): Double {
        val delta = target - current
        return if (abs(delta) <= MAX_RATE_STEP) target else current + MAX_RATE_STEP * kotlin.math.sign(delta)
    }

    private fun toShort(value: Float): Short = (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    companion object {
        const val MIN_RATE = 0.8
        const val MAX_RATE = 1.25

        /** The most the rate moves per grain (~23 ms): about 4% a second, smooth to the ear. */
        private const val MAX_RATE_STEP = 0.001

        private const val MAX_ANCHORS = 8192

        /** The longest hold a shift back can make; see [engage]. */
        private const val MAX_HOLD_MICROS = 1_600_000L
        private const val PRUNE_SLACK = 64
    }
}

/** A growable ring of longs, for the position map's anchors. */
internal class LongRingBuffer {
    private var data = LongArray(256)
    private var head = 0
    var size = 0
        private set

    operator fun get(index: Int): Long = data[(head + index) % data.size]
    fun first(): Long = get(0)

    fun add(value: Long) {
        if (size == data.size) {
            val grown = LongArray(data.size * 2)
            for (i in 0 until size) grown[i] = get(i)
            data = grown
            head = 0
        }
        data[(head + size) % data.size] = value
        size++
    }

    fun removeFirst(count: Int = 1) {
        val n = count.coerceAtMost(size)
        head = (head + n) % data.size
        size -= n
    }

    fun clear() {
        head = 0
        size = 0
    }

    /** The last index whose value is at or before [value]; values are ascending. */
    fun lastIndexAtOrBefore(value: Long): Int {
        var lo = 0
        var hi = size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (get(mid) <= value) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
