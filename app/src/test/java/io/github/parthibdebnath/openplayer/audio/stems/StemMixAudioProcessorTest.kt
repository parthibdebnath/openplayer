@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.stems

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class StemMixAudioProcessorTest {
    private val rate = 44_100
    private val frames = rate * 6

    // A "song" of three stems and something else, each a different tone, so any misalignment or
    // leftover shows.
    private fun drums(i: Int) = (3000 * sin(2 * PI * 60 * i / rate) * if ((i / 5000) % 2 == 0) 1 else 0).toInt()
    private fun bass(i: Int) = (4000 * sin(2 * PI * 110 * i / rate)).toInt()
    private fun vocals(i: Int) = (2500 * sin(2 * PI * 440 * i / rate + 0.3)).toInt()
    private fun other(i: Int) = (2000 * sin(2 * PI * 1234.5 * i / rate)).toInt()
    private fun mix(i: Int) = drums(i) + bass(i) + vocals(i) + other(i)

    /** A track of frames [first, first + count) of the song, starting [songStartMs] in. */
    private fun track(first: Int, count: Int): StemTrack {
        val data = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asShortBuffer()
        val d = directShorts(2 * count)
        val b = directShorts(2 * count)
        val v = directShorts(2 * count)
        for (k in 0 until count) {
            val i = first + k
            data.put(2 * k, mix(i).toShort())
            data.put(2 * k + 1, mix(i).toShort())
            d.put(2 * k, drums(i).toShort()); d.put(2 * k + 1, drums(i).toShort())
            b.put(2 * k, bass(i).toShort()); b.put(2 * k + 1, bass(i).toShort())
            v.put(2 * k, vocals(i).toShort()); v.put(2 * k + 1, vocals(i).toShort())
        }
        val window = DecodedWindow(rate, 2, 0.0, first.toLong(), count, data)
        val file = File.createTempFile("stem", ".stem").also { it.deleteOnExit() }
        fun pair(buffer: java.nio.ShortBuffer) = InterleavedShortReader(buffer, 0, count) to InterleavedShortReader(buffer, 1, count)
        StemTrack.write(file, StemRegion.TAIL, window, listOf(pair(d), pair(b), pair(v)))
        return StemTrack.open(file)!!
    }

    private fun processor(): StemMixAudioProcessor = StemMixAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    /** Plays the song from [from] for [count] frames in 10 ms buffers; returns the left channel out. */
    private fun play(p: StemMixAudioProcessor, from: Int, count: Int, noteUs: Long, onBuffer: (Int) -> Unit = {}): IntArray {
        val out = IntArray(count)
        var at = 0
        val step = rate / 100
        while (at < count) {
            val n = minOf(step, count - at)
            onBuffer(from + at)
            p.noteBufferTime(noteUs)
            val buffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until n) {
                buffer.putShort(mix(from + at + k).toShort())
                buffer.putShort(mix(from + at + k).toShort())
            }
            buffer.flip()
            p.queueInput(buffer)
            val o = p.output
            for (k in 0 until n) {
                out[at + k] = o.getShort().toInt()
                o.getShort()
            }
            at += n
        }
        return out
    }

    @Test fun levelsAtOneLeaveTheSongUntouched() {
        val p = processor()
        p.tracks = listOf(track(0, frames))
        val out = play(p, 0, rate, noteUs = 0)
        for (i in out.indices) assertEquals(mix(i), out[i])
    }

    @Test fun linesUpDespiteAWrongTimeAndTakesStemsOutExactly() {
        val p = processor()
        val first = rate // the track starts 1 s into the song
        p.tracks = listOf(track(first, frames - first))
        // Playing from 1.5 s, but the buffer times claim 1.5 s + 37 ms: as an MP3's priming
        // samples or a FLAC frame would put them out.
        val start = rate * 3 / 2
        val claimedUs = 1_500_000L + 37_000L
        p.drumsGain = 0f
        p.vocalsGain = 0f
        val out = play(p, start, rate * 2, noteUs = claimedUs)
        assertNotNull(p.alignedRegion)
        // Once lined up (and the levels have glided there), only bass and other are left, exactly.
        for (k in rate until rate * 2) {
            val i = start + k
            assertEquals("frame $i", (bass(i) + other(i)).toDouble(), out[k].toDouble(), 2.0)
        }
    }

    @Test fun refusesTracksThatDoNotMatch() {
        val p = processor()
        val t = track(0, frames)
        p.tracks = listOf(t)
        p.drumsGain = 0f
        // A different song through the same deck: the tones are all shifted.
        val out = IntArray(rate * 3)
        val step = rate / 100
        var at = 0
        while (at < out.size) {
            p.noteBufferTime(0)
            val buffer = ByteBuffer.allocateDirect(step * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until step) {
                val v = (8000 * sin(2 * PI * 333 * (at + k) / rate)).toInt().toShort()
                buffer.putShort(v); buffer.putShort(v)
            }
            buffer.flip()
            p.queueInput(buffer)
            val o = p.output
            for (k in 0 until step) {
                out[at + k] = o.getShort().toInt(); o.getShort()
            }
            at += step
        }
        assertNull(p.alignedRegion)
        // Passed through untouched.
        for (k in 0 until out.size step 101) {
            assertEquals((8000 * sin(2 * PI * 333 * k / rate)).toInt(), out[k])
        }
    }
}
