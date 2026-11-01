package io.github.parthibdebnath.openplayer.util

import io.github.parthibdebnath.openplayer.audio.ShuffleRandom
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableHashTest {
    // Published FNV-1a 64-bit test vectors.
    @Test fun fnv1aMatchesReferenceVectors() {
        assertEquals(0xcbf29ce484222325uL.toLong(), stableHash64(""))
        assertEquals(0xaf63dc4c8601ec8cuL.toLong(), stableHash64("a"))
        assertEquals(0x85944171f73967e8uL.toLong(), stableHash64("foobar"))
    }

    // The reference SplitMix64 sequence for seed 0.
    @Test fun splitMix64MatchesReferenceSequence() {
        val random = ShuffleRandom(0L)
        assertEquals(0xe220a8397b1dcdafuL.toLong(), random.nextLong())
        assertEquals(0x6e789e6aa1b965f4uL.toLong(), random.nextLong())
        assertEquals(0x06c45d188009454fuL.toLong(), random.nextLong())
    }

    @Test fun nextIntStaysInBounds() {
        val random = ShuffleRandom("bounds")
        repeat(10_000) {
            val bound = it % 37 + 1
            val value = random.nextInt(bound)
            assertTrue(value in 0 until bound)
        }
    }

    @Test(expected = CancellationException::class)
    fun runSuspendCatchingRethrowsCancellation() {
        runSuspendCatching { throw CancellationException("cancelled") }
    }

    @Test fun runSuspendCatchingCapturesOtherFailures() {
        val result = runSuspendCatching { error("boom") }
        assertTrue(result.isFailure)
    }
}
