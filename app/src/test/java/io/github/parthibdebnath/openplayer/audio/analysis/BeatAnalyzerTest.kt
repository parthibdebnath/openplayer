package io.github.parthibdebnath.openplayer.audio.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class BeatAnalyzerTest {
    private val rate = 11_025

    /**
     * A synthetic dance track: a kick on every beat (stronger on each bar's first), a hi-hat on
     * the off-beats, and a chord per bar going round Am - F - C - G, between [leadSilence] and
     * [tailSilence] seconds of silence. [bpmAt] gives the tempo along the way.
     */
    private fun track(
        seconds: Double,
        leadSilence: Double = 2.0,
        tailSilence: Double = 1.5,
        bpmAt: (Double) -> Double,
    ): Pair<FloatArray, List<Double>> {
        val total = ((leadSilence + seconds + tailSilence) * rate).toInt()
        val out = FloatArray(total)
        val random = Random(7)
        val beats = ArrayList<Double>()
        var t = leadSilence
        var beat = 0
        // Am, F, C, G as MIDI note triads.
        val chords = listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))
        while (t < leadSilence + seconds) {
            beats += t
            val period = 60.0 / bpmAt(t - leadSilence)
            val start = (t * rate).toInt()
            val accent = if (beat % 4 == 0) 1.0 else 0.55
            // Kick: a falling sine burst.
            for (i in 0 until (0.18 * rate).toInt()) {
                val idx = start + i
                if (idx >= total) break
                val time = i.toDouble() / rate
                val freq = 50 + 90 * exp(-time * 30)
                out[idx] += (accent * 0.8 * exp(-time * 18) * sin(2 * PI * freq * time)).toFloat()
            }
            // Hat on the off-beat.
            val hat = ((t + period / 2) * rate).toInt()
            for (i in 0 until (0.03 * rate).toInt()) {
                val idx = hat + i
                if (idx >= total) break
                out[idx] += ((random.nextDouble() * 2 - 1) * 0.15 * exp(-i.toDouble() / rate * 120)).toFloat()
            }
            // The bar's chord, held for the beat.
            val chord = chords[(beat / 4) % chords.size]
            for (i in 0 until (period * rate).toInt()) {
                val idx = start + i
                if (idx >= total) break
                val time = idx.toDouble() / rate
                var v = 0.0
                for (note in chord) v += sin(2 * PI * 440.0 * 2.0.pow((note - 69) / 12.0) * time)
                out[idx] += (0.06 * v).toFloat()
            }
            t += period
            beat++
        }
        // Silence really silent outside the music.
        for (i in ((leadSilence + seconds) * rate).toInt() + (0.2 * rate).toInt() until total) out[i] = 0f
        return out to beats
    }

    @Test fun findsTempoBeatsBarsAndShape() {
        val (samples, beats) = track(90.0) { 124.0 }
        val a = BeatAnalyzer.analyze(samples, rate)!!
        assertEquals(124.0, a.bpm, 0.05)
        assertTrue("confidence ${a.beatConfidence}", a.beatmatchable)
        // The first bar line is the first kick (beat 0), within a few ms - the bias of the onset
        // frames is the same for every song, so it cancels between two songs, but keep it small.
        val firstDownbeatMs = beats[0] * 1000
        assertEquals(firstDownbeatMs, a.introDownbeatMs, 25.0)
        // The last bar line lands on a real downbeat too: a multiple of 4 beats from the first.
        val barsBetween = (a.outroDownbeatMs - a.introDownbeatMs) / (4 * a.beatPeriodMs)
        assertEquals(Math.round(barsBetween).toDouble(), barsBetween, 0.02)
        assertEquals(2000.0, a.audioStartMs, 60.0)
        assertTrue(a.audioEndMs in 91_000.0..92_500.0)
    }

    @Test fun keyOfAnAMinorProgression() {
        val (samples, _) = track(60.0) { 120.0 }
        val a = BeatAnalyzer.analyze(samples, rate)!!
        // A minor, or its relative C major: the same notes, and the same place on the Camelot wheel.
        assertTrue("key ${a.key}", a.key == 21 || a.key == 0)
    }

    @Test fun slowTempoIsNotDoubled() {
        val (samples, _) = track(90.0) { 96.0 }
        val a = BeatAnalyzer.analyze(samples, rate)!!
        assertEquals(96.0, a.bpm, 0.05)
    }

    @Test fun driftingTempoIsNotMatched() {
        // Live-band style: from 116 to 128 BPM over the song.
        val (samples, _) = track(120.0) { t -> 116.0 + 12.0 * t / 120.0 }
        val a = BeatAnalyzer.analyze(samples, rate)
        assertFalse(a != null && a.beatmatchable && a.tempoStable)
    }

    @Test fun noiseHasNoBeat() {
        val random = Random(3)
        val samples = FloatArray(rate * 60) { (random.nextDouble() * 2 - 1).toFloat() * 0.3f }
        val a = BeatAnalyzer.analyze(samples, rate)!!
        assertFalse(a.beatmatchable)
    }

    @Test fun silenceIsNothing() {
        assertEquals(null, BeatAnalyzer.analyze(FloatArray(rate * 10), rate))
    }

    @Test fun startTimeShiftsEveryPosition() {
        val (samples, _) = track(40.0) { 128.0 }
        val a = BeatAnalyzer.analyze(samples, rate, startMs = 0.0)!!
        val b = BeatAnalyzer.analyze(samples, rate, startMs = 500.0)!!
        assertEquals(a.introDownbeatMs + 500.0, b.introDownbeatMs, 1e-6)
        assertEquals(a.audioStartMs + 500.0, b.audioStartMs, 1e-6)
        assertTrue(abs(a.bpm - b.bpm) < 1e-9)
    }
}
