package io.github.parthibdebnath.openplayer.audio.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class TimeStretcherTest {
    private val rate = 44_100

    private fun tone(seconds: Double, hz: Double, channels: Int = 2): FloatArray {
        val frames = (seconds * rate).toInt()
        return FloatArray(frames * channels) { i -> (0.5 * sin(2 * PI * hz * (i / channels) / rate)).toFloat() }
    }

    private fun run(stretcher: TimeStretcher, input: FloatArray, speed: Double, chunk: Int = 4096): FloatArray {
        val out = ArrayList<Float>()
        val buffer = FloatArray(stretcher.hop * stretcher.channels * 8)
        var offset = 0
        while (offset < input.size) {
            val count = minOf(chunk * stretcher.channels, input.size - offset)
            stretcher.write(input, offset, count)
            offset += count
            while (true) {
                val n = stretcher.read(buffer, speed)
                if (n == 0) break
                for (i in 0 until n) out += buffer[i]
            }
        }
        stretcher.end()
        while (true) {
            val n = stretcher.read(buffer, speed)
            if (n == 0) break
            for (i in 0 until n) out += buffer[i]
        }
        return out.toFloatArray()
    }

    // At rate 1 it must be completely transparent - the DJ mix leaves every song running through
    // it at rate 1 between mixes, and anything else would colour all the music.
    @Test fun rateOneReproducesTheInputExactly() {
        val input = tone(2.0, 440.0)
        val output = run(TimeStretcher(2, rate), input, 1.0)
        val compared = minOf(input.size, output.size) - 4096
        for (i in 0 until compared) assertEquals("sample $i", input[i], output[i], 1e-5f)
    }

    @Test fun fasterRateShortensTheAudioButKeepsThePitch() {
        val input = tone(4.0, 440.0, channels = 1)
        val output = run(TimeStretcher(1, rate), input, 1.08)
        val expected = input.size / 1.08
        assertEquals(expected, output.size.toDouble(), expected * 0.02)
        // Pitch: zero crossings per second in the middle of the output, where it's steady.
        val from = output.size / 4
        val to = output.size * 3 / 4
        var crossings = 0
        for (i in from + 1 until to) if ((output[i - 1] < 0) != (output[i] < 0)) crossings++
        val hz = crossings / 2.0 / ((to - from).toDouble() / rate)
        assertEquals(440.0, hz, 6.0)
    }

    @Test fun grainsMapOutputBackToTheSongAtTheRate() {
        val anchors = ArrayList<Pair<Long, Long>>()
        val stretcher = TimeStretcher(1, rate).apply { onGrain = { o, i -> anchors += o to i } }
        run(stretcher, tone(3.0, 220.0, channels = 1), 0.95)
        assertTrue(anchors.size > 10)
        for (k in 1 until anchors.size) {
            assertTrue(anchors[k].first > anchors[k - 1].first)
            assertTrue(anchors[k].second > anchors[k - 1].second)
        }
        // Over the whole run, the song advances at 0.95 of the output, give or take one grain's
        // search tolerance.
        val (o0, i0) = anchors[1]
        val (o1, i1) = anchors[anchors.size - 2]
        val slope = (i1 - i0).toDouble() / (o1 - o0)
        assertEquals(0.95, slope, 0.01)
    }

    @Test fun aShiftMovesTheSongAhead() {
        val anchors = ArrayList<Pair<Long, Long>>()
        val stretcher = TimeStretcher(1, rate).apply { onGrain = { o, i -> anchors += o to i } }
        val input = tone(2.0, 220.0, channels = 1)
        stretcher.write(input, 0, input.size / 2)
        val buffer = FloatArray(stretcher.hop * 16)
        while (stretcher.read(buffer, 1.0) > 0) Unit
        val before = anchors.last()
        stretcher.shift(441.0) // 10 ms ahead
        stretcher.write(input, input.size / 2, input.size / 2)
        while (stretcher.read(buffer, 1.0) > 0) Unit
        val after = anchors[anchors.indexOf(before) + 1]
        val jump = (after.second - before.second) - (after.first - before.first)
        assertTrue("jumped $jump frames", abs(jump - 441) <= stretcher.tolerance)
    }

    @Test fun aligned_onlyOnceBackAtRateOne() {
        val stretcher = TimeStretcher(1, rate)
        val input = tone(1.0, 220.0, channels = 1)
        stretcher.write(input, 0, input.size)
        val buffer = FloatArray(stretcher.hop)
        stretcher.read(buffer, 1.0)
        stretcher.read(buffer, 1.05)
        assertTrue(!stretcher.isAligned)
        stretcher.read(buffer, 1.0)
        assertTrue(stretcher.isAligned)
    }
}
