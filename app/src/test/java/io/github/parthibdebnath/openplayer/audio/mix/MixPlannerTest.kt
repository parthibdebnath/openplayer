package io.github.parthibdebnath.openplayer.audio.mix

import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MixPlannerTest {
    private fun song(
        bpm: Double,
        durationMs: Double = 240_000.0,
        key: Int = 21,
        stable: Boolean = true,
        confidence: Double = 0.5,
        audioStartMs: Double = 150.0,
        outroStartMs: Double = durationMs - 40_000,
    ): TrackAnalysis {
        val beat = 60_000.0 / bpm
        val intro = audioStartMs + 100.0
        // The last bar line before the music ends, on the same grid as the first.
        val audioEnd = durationMs - 500
        val bars = Math.floor((audioEnd - intro) / (4 * beat))
        return TrackAnalysis(
            durationMs = durationMs,
            bpm = bpm,
            beatConfidence = confidence,
            tempoStable = stable,
            introDownbeatMs = intro,
            outroDownbeatMs = intro + bars * 4 * beat,
            downbeatConfidence = 0.5,
            key = key,
            keyConfidence = 0.2,
            audioStartMs = audioStartMs,
            audioEndMs = audioEnd,
            introEndMs = audioStartMs + 8_000,
            outroStartMs = outroStartMs,
            loudnessDb = -10.0,
        )
    }

    @Test fun closeTemposAreMatchedByTheIncomingSongAlone() {
        val out = song(128.0)
        val incoming = song(124.0)
        val plan = MixPlanner.plan(out, out.durationMs, 30_000.0, incoming, 15_000, 1.0)!!
        assertEquals(MixStyle.BEATMATCH, plan.style)
        // Equal beat rates in real time during the blend.
        assertEquals(plan.outMeetRate / plan.outBeatMs, plan.inMeetRate / plan.inBeatMs, 1e-12)
        // A 3% difference: the outgoing song plays on untouched.
        assertEquals(1.0, plan.outMeetRate, 0.0)
        assertEquals(128.0 / 124.0, plan.inMeetRate, 1e-9)
        // Starts on a bar line of the outgoing song, and ends before its music does.
        val barsFromLast = (out.outroDownbeatMs - plan.outStartMs) / (4 * out.beatPeriodMs)
        assertEquals(Math.round(barsFromLast).toDouble(), barsFromLast, 1e-6)
        assertTrue(plan.outEndMs <= out.audioEndMs + out.beatPeriodMs)
        // The incoming song's first bar line is right at its start, so it comes in from the
        // second, with the first played silently to lock onto the beat.
        assertEquals(incoming.introDownbeatMs + 4 * plan.inBeatMs, plan.inStartMs, 1e-9)
        assertTrue(plan.inPrerollMs >= 0.0)
        // 32 beats at 128 BPM is 15 s: the longest that fits the setting.
        assertEquals(32, plan.beats)
    }

    @Test fun biggerDifferencesMeetInTheMiddle() {
        val plan = MixPlanner.plan(song(128.0), 240_000.0, 30_000.0, song(120.0), 15_000, 1.0)!!
        assertEquals(MixStyle.BEATMATCH, plan.style)
        assertTrue(plan.outMeetRate < 1.0 && plan.inMeetRate > 1.0)
        assertEquals(abs(1 - plan.outMeetRate), abs(plan.inMeetRate - 1), 0.003)
        assertEquals(plan.outMeetRate / plan.outBeatMs, plan.inMeetRate / plan.inBeatMs, 1e-12)
    }

    @Test fun beatsStayLockedWhenBothRunAtTheirMeetingRates() {
        val out = song(128.0)
        val incoming = song(124.0)
        val plan = MixPlanner.plan(out, out.durationMs, 30_000.0, incoming, 15_000, 1.0)!!
        for (realMs in listOf(0.0, 1_000.0, 7_500.0, 14_000.0)) {
            val outPos = plan.outStartMs + realMs * plan.outMeetRate
            val inPos = plan.inStartMs + realMs * plan.inMeetRate
            assertEquals(0.0, plan.beatError(outPos, inPos), 1e-9)
        }
        // And from the silent preroll: the incoming song reaches its start as the outgoing one does.
        val toStartOnOut = plan.outStartMs - plan.outPrerollMs
        val inAtStart = plan.inPrerollMs + toStartOnOut / plan.outMeetRate * plan.inMeetRate
        assertEquals(plan.inStartMs, inAtStart, 1e-6)
    }

    @Test fun halfTimeMatchesDoubleTime() {
        val plan = MixPlanner.plan(song(140.0), 240_000.0, 30_000.0, song(70.5), 15_000, 1.0)!!
        assertEquals(MixStyle.BEATMATCH, plan.style)
        assertEquals(plan.outBeatMs, plan.inBeatMs, plan.outBeatMs * 0.01)
    }

    @Test fun distantTemposFadeSmoothlyAndSkipSilence() {
        val out = song(128.0)
        val incoming = song(100.0, audioStartMs = 1_200.0)
        val plan = MixPlanner.plan(out, out.durationMs, 30_000.0, incoming, 15_000, 1.0)!!
        assertEquals(MixStyle.SMOOTH, plan.style)
        assertEquals(1_200.0, plan.inStartMs, 1e-9)
        assertTrue(plan.outEndMs <= out.audioEndMs + 100)
        assertTrue(plan.outEndMs - plan.outStartMs <= MixPlanner.SMOOTH_MAX_MS.toDouble())
    }

    @Test fun unsteadyBeatIsNotMatched() {
        val plan = MixPlanner.plan(song(128.0), 240_000.0, 30_000.0, song(128.0, stable = false), 15_000, 1.0)!!
        assertEquals(MixStyle.SMOOTH, plan.style)
    }

    @Test fun clashingKeysBlendQuickly() {
        // A minor into E-flat minor: as far round the wheel as it gets.
        val plan = MixPlanner.plan(song(128.0, key = 21), 240_000.0, 30_000.0, song(128.0, key = 15), 30_000, 1.0)!!
        assertEquals(MixStyle.BEATMATCH, plan.style)
        assertTrue(plan.beats <= 8)
        assertEquals(false, plan.harmonic)
    }

    @Test fun noPlanWithoutAnAnalysisOrWhenOff() {
        assertNull(MixPlanner.plan(null, 240_000.0, 0.0, song(128.0), 15_000, 1.0))
        assertNull(MixPlanner.plan(song(128.0), 240_000.0, 0.0, song(128.0), 0, 1.0))
    }

    @Test fun neverStartsBehindThePlayhead() {
        val out = song(128.0)
        // Already in the outro: the mix still starts ahead, shorter if need be.
        val plan = MixPlanner.plan(out, out.durationMs, out.durationMs - 25_000, song(126.0), 15_000, 1.0)
        assertNotNull(plan)
        assertTrue(plan!!.outRampStartMs >= out.durationMs - 25_000 || plan.style == MixStyle.SMOOTH)
        assertTrue(plan.outStartMs > out.durationMs - 25_000)
    }

    @Test fun levelsRunFromOneSongToTheOther() {
        val start = beatmatchLevels(0.0, 32)
        assertEquals(0.0, start.inGain, 1e-9)
        assertEquals(1.0, start.outGain, 1e-9)
        assertEquals(MixChannelAudioProcessor.BASS_KILL_HZ.toDouble(), start.inCutoffHz, 1e-6)
        val end = beatmatchLevels(1.0, 32)
        assertEquals(1.0, end.inGain, 1e-9)
        assertEquals(0.0, end.outGain, 1e-9)
        assertEquals(MixChannelAudioProcessor.FLOOR_HZ.toDouble(), end.inCutoffHz, 1e-6)
        // The basslines swap in the middle: before it the incoming bass is cut and the outgoing
        // one isn't; after it, the other way round.
        val before = beatmatchLevels(0.45, 32)
        val after = beatmatchLevels(0.55, 32)
        assertTrue(before.inCutoffHz > 200 && before.outCutoffHz < 20)
        assertTrue(after.inCutoffHz < 20 && after.outCutoffHz > 200)
    }

    @Test fun camelotWheel() {
        assertEquals(0, MixPlanner.camelotDistance(0, 0))
        assertEquals(1, MixPlanner.camelotDistance(0, 21)) // C major / A minor
        assertEquals(1, MixPlanner.camelotDistance(0, 7)) // C / G
        assertEquals(1, MixPlanner.camelotDistance(0, 5)) // C / F
        assertEquals(6, MixPlanner.camelotDistance(0, 6)) // C / F sharp
        assertEquals(1, MixPlanner.camelotDistance(21, 16)) // A minor / E minor
    }

    @Test fun outgoingTempoEasesIntoTheMeetingRate() {
        val plan = MixPlanner.plan(song(128.0), 240_000.0, 30_000.0, song(119.0), 15_000, 1.0)!!
        assertEquals(1.0, plan.outgoingRate(plan.outRampStartMs - 1), 1e-12)
        assertEquals(plan.outMeetRate, plan.outgoingRate(plan.outPrerollMs), 1e-12)
        assertEquals(plan.outMeetRate, plan.outgoingRate(plan.outStartMs + 1_000), 1e-12)
        val middle = plan.outgoingRate((plan.outRampStartMs + plan.outPrerollMs) / 2)
        assertTrue(middle < 1.0 && middle > plan.outMeetRate)
    }

    @Test fun glideBackIsSlow() {
        // 3% off takes at least ten seconds to undo.
        assertTrue(MixPlanner.glideMs(1.03, 480.0, 1.0) >= 10_000.0)
    }
}
