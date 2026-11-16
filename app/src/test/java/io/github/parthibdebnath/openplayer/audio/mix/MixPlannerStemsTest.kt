package io.github.parthibdebnath.openplayer.audio.mix

import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixPlannerStemsTest {
    private fun song(bpm: Double, durationMs: Double = 240_000.0, key: Int = 21): TrackAnalysis {
        val beat = 60_000.0 / bpm
        val intro = 250.0
        val audioEnd = durationMs - 500
        val bars = Math.floor((audioEnd - intro) / (4 * beat))
        return TrackAnalysis(
            durationMs = durationMs, bpm = bpm, beatConfidence = 0.5, tempoStable = true,
            introDownbeatMs = intro, outroDownbeatMs = intro + bars * 4 * beat, downbeatConfidence = 0.5,
            key = key, keyConfidence = 0.2, audioStartMs = 150.0, audioEndMs = audioEnd,
            introEndMs = 8_000.0, outroStartMs = durationMs - 40_000, loudnessDb = -10.0,
        )
    }

    /** Stems covering [from, to], with vocals wherever [singing] says. */
    private class FakeStems(val from: Double, val to: Double, val singing: (Double) -> Boolean) : StemView {
        override fun covers(fromMs: Double, toMs: Double) = fromMs >= from && toMs <= to
        override fun vocalsDb(fromMs: Double, toMs: Double): Double? {
            if (!covers(fromMs, toMs)) return null
            return if (singing((fromMs + toMs) / 2)) -3.0 else -40.0
        }
    }

    @Test fun vocalsHandOverWhereNeitherSingerIsCutOff() {
        val out = song(124.0)
        val incoming = song(124.0)
        val beat = out.beatPeriodMs
        // Plan first without vocal constraints to find the blend, then put the singers so that
        // the outgoing one stops 10 bars into it and the incoming one starts 12 bars in.
        val probe = MixPlanner.plan(
            out, out.durationMs, 0.0, incoming, 30_000, 1.0,
            FakeStems(out.durationMs - 60_000, out.durationMs) { false },
            FakeStems(0.0, 50_000.0) { false },
        )!!
        assertTrue(probe.withStems)
        val outStops = probe.outStartMs + 40 * beat
        val inStarts = probe.inStartMs + 48 * probe.inBeatMs
        val plan = MixPlanner.plan(
            out, out.durationMs, 0.0, incoming, 30_000, 1.0,
            FakeStems(out.durationMs - 60_000, out.durationMs) { it < outStops },
            FakeStems(0.0, 50_000.0) { it >= inStarts },
        )!!
        assertEquals(MixStyle.BEATMATCH, plan.style)
        val handoff = plan.vocalHandoff!!
        val handoffOut = plan.outStartMs + handoff * (plan.outEndMs - plan.outStartMs)
        val handoffIn = plan.inStartMs + handoff * plan.beats * plan.inBeatMs
        // The outgoing singer is done (its fade is the bar before) and the incoming one hasn't begun.
        assertTrue("hands over at $handoffOut, singer stops at $outStops", handoffOut - 4 * beat >= outStops - 1)
        assertTrue("hands over at $handoffIn, singer starts at $inStarts", handoffIn <= inStarts + 1)
    }

    @Test fun withoutBothSongsStemsItPlansAsBefore() {
        val out = song(124.0)
        val incoming = song(124.0)
        val plain = MixPlanner.plan(out, out.durationMs, 0.0, incoming, 15_000, 1.0)!!
        val oneSided = MixPlanner.plan(
            out, out.durationMs, 0.0, incoming, 15_000, 1.0,
            FakeStems(out.durationMs - 60_000, out.durationMs) { false }, null,
        )!!
        assertNull(plain.vocalHandoff)
        assertEquals(plain, oneSided)
    }

    @Test fun stemsThatDoNotCoverTheBlendAreNotUsed() {
        val out = song(124.0)
        val incoming = song(124.0)
        val plan = MixPlanner.plan(
            out, out.durationMs, 0.0, incoming, 15_000, 1.0,
            FakeStems(0.0, 30_000.0) { false }, // the outgoing song's start, not its end
            FakeStems(0.0, 50_000.0) { false },
        )
        assertNotNull(plan)
        assertNull(plan!!.vocalHandoff)
    }

    @Test fun stemCurvesHandEveryPartOverAndNeverDoubleTheVocals() {
        for (clash in listOf(false, true)) {
            val start = beatmatchStemLevels(-0.1, 32, 0.5, clash)
            assertEquals(StemGains.SILENT, start.inStems)
            assertEquals(StemGains.FULL, start.outStems)
            val end = beatmatchStemLevels(1.0, 32, 0.5, clash)
            assertEquals(StemGains.FULL, end.inStems)
            assertEquals(0.0, end.outStems.drums, 1e-9)
            assertEquals(0.0, end.outStems.bass, 1e-9)
            assertEquals(0.0, end.outStems.vocals, 1e-9)
            assertEquals(0.0, end.outStems.other, 1e-9)
            var p = 0.0
            while (p <= 1.0) {
                val l = beatmatchStemLevels(p, 32, 0.5, clash)
                // One voice at a time, one bassline at a time (bar the beat-long swap).
                assertTrue("vocals at $p", l.outStems.vocals < 0.01 || l.inStems.vocals < 0.01)
                if (p < 0.5 - 1.0 / 64 || p > 0.5 + 1.0 / 64) {
                    assertTrue("bass at $p", l.outStems.bass < 0.01 || l.inStems.bass < 0.01)
                }
                if (clash) assertTrue("music at $p", l.outStems.other < 0.01 || l.inStems.other < 0.01)
                p += 1.0 / 256
            }
        }
    }

    @Test fun smoothStemCurvesNeverOverlapTheBeats() {
        var p = 0.0
        while (p <= 1.0) {
            val l = smoothStemLevels(p)
            assertTrue("drums at $p", l.outStems.drums < 0.01 || l.inStems.drums < 0.01)
            assertTrue("vocals at $p", l.outStems.vocals < 0.01 || l.inStems.vocals < 0.01)
            p += 1.0 / 256
        }
        assertEquals(StemGains.FULL, smoothStemLevels(1.0).inStems)
    }
}
