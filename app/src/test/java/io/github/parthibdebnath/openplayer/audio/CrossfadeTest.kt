package io.github.parthibdebnath.openplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class CrossfadeTest {
    @Test fun progressFollowsWhatIsLeftOfTheOutgoingTrack() {
        assertEquals(0f, crossfadeProgress(4_000, 4_000), 0f)
        assertEquals(0.25f, crossfadeProgress(4_000, 3_000), 1e-6f)
        assertEquals(0.5f, crossfadeProgress(4_000, 2_000), 1e-6f)
        assertEquals(1f, crossfadeProgress(4_000, 0), 0f)
    }

    // The fault this replaced: progress counted in fixed ticks fell behind at 2x speed, or after a
    // stall, and the outgoing track ran out with its gain still up. Read off the position, it's
    // done exactly when the track is, however the ticks fall.
    @Test fun progressIsCompleteWhenTheOutgoingTrackEnds() {
        var progress = 0f
        // Ticks of uneven length - a stall in the middle - at 2x speed: the position moves
        // twice as fast as the clock.
        var remaining = 5_000L
        for (wallMs in listOf(50L, 50L, 900L, 50L, 1_200L, 300L)) {
            remaining = (remaining - wallMs * 2).coerceAtLeast(0)
            progress = crossfadeProgress(5_000, remaining, progress)
        }
        assertEquals(0L, remaining)
        assertEquals(1f, progress, 0f)
    }

    @Test fun progressNeverGoesBackwards() {
        assertEquals(0.5f, crossfadeProgress(4_000, 3_000, previous = 0.5f), 0f)
    }

    @Test fun overshootAndDegenerateLengthsAreClamped() {
        assertEquals(0f, crossfadeProgress(4_000, 9_000), 0f)
        assertEquals(1f, crossfadeProgress(4_000, -50), 0f)
        assertEquals(1f, crossfadeProgress(0, 1_000), 0f)
    }

    @Test fun mediaLengthScalesWithSpeed() {
        assertEquals(6_000L, crossfadeMediaLengthMs(6_000, 1f))
        assertEquals(12_000L, crossfadeMediaLengthMs(6_000, 2f))
        assertEquals(3_000L, crossfadeMediaLengthMs(6_000, 0.5f))
        assertEquals(6_000L, crossfadeMediaLengthMs(6_000, Float.NaN))
    }

    @Test fun gainsAreEqualPower() {
        assertEquals(0f to 1f, crossfadeGains(0f).let { (a, b) -> a to b })
        val (incoming, outgoing) = crossfadeGains(0.5f)
        assertEquals(sqrt(0.5f), incoming, 1e-6f)
        assertEquals(sqrt(0.5f), outgoing, 1e-6f)
        val (endIn, endOut) = crossfadeGains(1f)
        assertEquals(1f, endIn, 1e-6f)
        assertEquals(0f, endOut, 1e-6f)
    }
}
