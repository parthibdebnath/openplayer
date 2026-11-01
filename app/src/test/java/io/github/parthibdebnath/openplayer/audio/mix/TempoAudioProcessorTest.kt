@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.mix

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class TempoAudioProcessorTest {
    private val sr = 44_100

    private fun configured(): TempoAudioProcessor = TempoAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(sr, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun chunk(startFrame: Int, frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (f in startFrame until startFrame + frames) {
            val v = (8000 * sin(2 * PI * 330 * f / sr)).toInt().toShort()
            buffer.putShort(v)
            buffer.putShort(v)
        }
        buffer.flip()
        return buffer
    }

    /** Feeds [seconds] of audio in 20 ms chunks and returns the output frames. */
    private fun feed(processor: TempoAudioProcessor, seconds: Double, from: Int = 0, onChunk: (Int) -> Unit = {}): Long {
        var out = 0L
        val frames = (seconds * sr).toInt()
        val step = sr / 50
        var f = from
        while (f < from + frames) {
            onChunk(f)
            processor.queueInput(chunk(f, step))
            val output = processor.output
            out += output.remaining() / 4
            output.position(output.limit())
            f += step
        }
        return out
    }

    @Test fun atRateOneItIsAPlainCopy() {
        val processor = configured()
        val input = chunk(0, 2048)
        val copy = ByteBuffer.allocate(input.remaining()).also { it.put(input.duplicate()) }
        processor.queueInput(input)
        val output = processor.output
        assertEquals(copy.capacity(), output.remaining())
        copy.flip()
        while (output.hasRemaining()) assertEquals(copy.get(), output.get())
        assertEquals(1_000L, processor.mediaFramesFor(1_000L))
    }

    @Test fun positionFollowsTheSongWhileStretching() {
        val processor = configured()
        feed(processor, 1.0)
        processor.rate = 1.05f
        val out = feed(processor, 6.0, from = sr)
        // The song moves on by 1.05 frames per frame out. Asked in order, as the player asks:
        // the map forgets what's behind the latest question.
        val middle = sr + out / 2
        val end = sr + out - 4_000
        val atMiddle = processor.mediaFramesFor(middle)
        val atEnd = processor.mediaFramesFor(end)
        assertTrue(atEnd > end) // ahead of a 1:1 reading
        val slope = (atEnd - atMiddle).toDouble() / (end - middle)
        assertEquals(1.05, slope, 0.01)
    }

    @Test fun holdDelaysTheSong() {
        val processor = configured()
        processor.holdAtStart(500_000)
        processor.flush() // what loading the song does; the hold must outlast it
        val out = feed(processor, 2.0)
        // Half a second of silence was added: half a second more out than in.
        assertEquals(2.0 * sr + 0.5 * sr, out.toDouble(), sr * 0.06)
        // And the song is half a second behind the output.
        val media = processor.mediaFramesFor(sr.toLong())
        assertEquals(0.5 * sr, media.toDouble(), sr * 0.02)
    }

    @Test fun handsBackToAPlainCopyAfterwards() {
        val processor = configured()
        processor.rate = 1.04f
        feed(processor, 2.0)
        processor.rate = 1f
        val out = feed(processor, 3.0, from = 2 * sr)
        val frames = (3.0 * sr).toInt()
        // Once back at 1 it carries on at one frame out per frame in.
        assertEquals(frames.toDouble(), out.toDouble(), sr * 0.25)
        val a = processor.mediaFramesFor(4L * sr)
        val b = processor.mediaFramesFor(4L * sr + 1000)
        assertEquals(1000L, b - a)
    }
}
