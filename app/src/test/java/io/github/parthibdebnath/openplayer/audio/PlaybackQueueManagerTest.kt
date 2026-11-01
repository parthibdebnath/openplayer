package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.data.settings.SavedShuffle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueManagerTest {
    private val seed = "seedABC"

    // --- Shuffle determinism ---------------------------------------------------------------

    /**
     * Pinned against an independent implementation of FNV-1a, SplitMix64 and Fisher-Yates. If this
     * changes, every saved seed now means a different order - which is the point of not using
     * kotlin.random.
     */
    @Test fun seedGivesAFixedOrder() {
        assertEquals(listOf(4L, 3, 2, 8, 1, 7, 5, 9, 10, 6), shuffledOrder(songs(1L..10L), seed).ids)
    }

    @Test fun orderDoesNotDependOnHowTheSongsAreListed() {
        val listed = songs(1L..30L)
        val expected = shuffledOrder(listed, seed).ids
        assertEquals(expected, shuffledOrder(listed.reversed(), seed).ids)
        assertEquals(expected, shuffledOrder(listed.sortedBy { (it.id * 7) % 31 }, seed).ids)
    }

    @Test fun differentSeedsGiveDifferentOrders() {
        val listed = songs(1L..30L)
        assertNotEquals(shuffledOrder(listed, "one").ids, shuffledOrder(listed, "two").ids)
    }

    @Test fun shuffleKeepsEverySongOnce() {
        val listed = songs(1L..50L)
        assertEquals(listed.ids.sorted(), shuffledOrder(listed, seed).ids.sorted())
    }

    @Test fun startingAShuffleFollowsTheSeedWhateverTheListOrder() {
        val a = PlaybackQueueManager().apply { startPlaylist(songs(1L..12L), "A", PlayOrder.SHUFFLED, seed) }
        val b = PlaybackQueueManager().apply { startPlaylist(songs(1L..12L).reversed(), "A", PlayOrder.SHUFFLED, seed) }
        assertEquals(a.current?.id, b.current?.id)
        assertEquals(a.upcoming.ids, b.upcoming.ids)
        assertEquals(shuffledOrder(songs(1L..12L), seed).ids, listOf(a.current!!.id) + a.upcoming.ids)
    }

    @Test fun rebuildLeavesOutPlayedAndCurrent() {
        val q = PlaybackQueueManager()
        q.startPlaylist(songs(1L..10L), "A", PlayOrder.SHUFFLED, seed)
        q.advanceToNext(seed)
        q.advanceToNext(seed)
        val played = q.historySnapshot().ids + q.current!!.id
        q.rebuildUpcoming("other seed")
        assertTrue(q.upcoming.none { it.id in played })
        assertEquals(10 - played.size, q.upcoming.size)
    }

    @Test fun restoreKeepsTheSavedOrderExactly() {
        val pool = songs(1L..6L)
        val q = PlaybackQueueManager()
        q.restore(
            pool = pool,
            label = "A",
            current = pool[3],
            queued = listOf(pool[5]),
            upcomingSongs = listOf(pool[1], pool[4], pool[0]),
            played = listOf(pool[2]),
            repeat = RepeatMode.OFF,
            order = PlayOrder.SHUFFLED,
            sourceKey = "list:1",
        )
        assertEquals(4L, q.current?.id)
        assertEquals(listOf(6L), q.explicitQueue.ids)
        assertEquals(listOf(2L, 5L, 1L), q.upcoming.ids)
        assertEquals(listOf(3L), q.historySnapshot().ids)
    }

    // --- Reordering and swapping -----------------------------------------------------------

    private fun inOrder(count: Long, queued: List<Long> = emptyList()) = PlaybackQueueManager().apply {
        startPlaylist(songs(1L..count), "A", PlayOrder.SEQUENTIAL, seed, startIndex = 0)
        queued.forEach { queueSong(song(it)) }
    }

    @Test fun reorderMovesWithinTheGeneratedOrder() {
        val q = inOrder(8)
        // Upcoming is 2..8.
        q.reorderUpcoming(0, 3)
        assertEquals(listOf(3L, 4, 5, 2, 6, 7, 8), q.upcoming.ids)
    }

    @Test fun reorderAppliesToTheListAsShownWithQueuedSongsFirst() {
        val q = inOrder(6, queued = listOf(100L))
        // Shown: 100, 2, 3, 4, 5, 6. Moving 4 to the top puts it in the queued slot.
        q.reorderUpcoming(3, 0)
        assertEquals(listOf(4L), q.explicitQueue.ids)
        assertEquals(listOf(100L, 2, 3, 5, 6), q.upcoming.ids)
    }

    @Test fun reorderIgnoresOutOfRangeIndices() {
        val q = inOrder(4)
        q.reorderUpcoming(0, 9)
        assertEquals(listOf(2L, 3, 4), q.upcoming.ids)
    }

    @Test fun swapReplacesASlotWithTheFirstHiddenSong() {
        val q = inOrder(10)
        // Shown: 2..6, first hidden: 7.
        q.replaceUpcomingSlot(1)
        assertEquals(listOf(2L, 7, 4, 5, 6), q.visibleUpcoming().ids)
        assertEquals(3L, q.upcoming.last().id)
        assertEquals(9, q.upcoming.size)
    }

    @Test fun swapOfAQueuedSlotKeepsItQueued() {
        val q = inOrder(10, queued = listOf(100L))
        // Shown: 100, 2, 3, 4, 5; first hidden: 6.
        q.replaceUpcomingSlot(0)
        assertEquals(listOf(6L), q.explicitQueue.ids)
        assertEquals(100L, q.upcoming.last().id)
        assertFalse(q.upcoming.any { it.id == 6L })
    }

    @Test fun swapDoesNothingWithoutAHiddenSong() {
        val q = inOrder(4)
        q.replaceUpcomingSlot(0)
        assertEquals(listOf(2L, 3, 4), q.upcoming.ids)
    }

    // --- Set-aside shuffles ----------------------------------------------------------------

    private fun shuffledAndAdvanced(): Pair<PlaybackQueueManager, SavedShuffle> {
        val q = PlaybackQueueManager()
        q.startPlaylist(songs(1L..10L), "A", PlayOrder.SHUFFLED, seed, sourceKey = "list:1")
        repeat(3) { q.advanceToNext(seed) }
        return q to q.shuffleProgress()!!
    }

    @Test fun resumePicksUpExactlyWhereItWasLeft() {
        val (q, saved) = shuffledAndAdvanced()
        val current = q.current!!.id
        val history = q.historySnapshot().ids
        val upcoming = q.upcoming.ids
        q.startPlaylist(songs(100L..105L), "B", PlayOrder.SEQUENTIAL, seed, sourceKey = "list:2")
        assertTrue(q.resumeShuffle(songs(1L..10L), "A", saved, seed))
        assertEquals(current, q.current?.id)
        assertEquals(history, q.historySnapshot().ids)
        assertEquals(upcoming, q.upcoming.ids)
        assertEquals("list:1", q.sourceKey)
    }

    @Test fun resumeDealsNewSongsInAndDropsRemovedOnes() {
        val (q, saved) = shuffledAndAdvanced()
        val upcoming = q.upcoming.ids
        val removed = upcoming[2]
        val now = songs(1L..13L).filter { it.id != removed }
        assertTrue(q.resumeShuffle(now, "A", saved, seed))
        val resumed = q.upcoming.ids
        assertEquals(upcoming - removed, resumed.filter { it <= 10 })
        assertTrue(resumed.containsAll(listOf(11L, 12L, 13L)))
        assertEquals(resumed.size, resumed.toSet().size)
    }

    @Test fun resumeWithNothingLeftRefusesAndLeavesTheQueue() {
        val (q, saved) = shuffledAndAdvanced()
        val before = q.current?.id
        assertFalse(q.resumeShuffle(songs(500L..510L), "A", saved, seed))
        assertEquals(before, q.current?.id)
    }

    @Test fun finishedShuffleHasNothingToSetAside() {
        val q = PlaybackQueueManager()
        q.startPlaylist(songs(1L..3L), "A", PlayOrder.SHUFFLED, seed, sourceKey = "list:1")
        repeat(2) { q.advanceToNext(seed) }
        assertNull(q.shuffleProgress())
    }
}
