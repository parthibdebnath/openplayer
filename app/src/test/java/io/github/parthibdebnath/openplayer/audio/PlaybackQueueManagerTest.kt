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

    // --- Swiping a swap back -------------------------------------------------------------

    private fun PlaybackQueueManager.swipe(index: Int) = swapBack(index, visibleUpcoming()[index].id)

    @Test fun swipeUndoesASwapExactly() {
        val q = inOrder(10)
        val before = q.upcoming.ids
        q.replaceUpcomingSlot(2)
        assertEquals(listOf(null, null, 4L, null, null), q.visibleSwapBacks().map { it?.id })
        assertTrue(q.swipe(2))
        assertEquals(before, q.upcoming.ids)
        assertEquals(List(5) { null }, q.visibleSwapBacks())
    }

    @Test fun swipesWalkBackThroughEverySwapOnARow() {
        val q = inOrder(12)
        val before = q.upcoming.ids
        repeat(3) { q.replaceUpcomingSlot(0) }
        // Shown first: 2 swapped for 7, for 8, for 9.
        assertEquals(9L, q.visibleUpcoming()[0].id)
        assertTrue(q.swipe(0))
        assertEquals(8L, q.visibleUpcoming()[0].id)
        assertTrue(q.swipe(0))
        assertEquals(7L, q.visibleUpcoming()[0].id)
        assertTrue(q.swipe(0))
        assertEquals(before, q.upcoming.ids)
        assertFalse(q.swipe(0))
    }

    @Test fun swipesOnDifferentRowsInAnyOrder() {
        val q = inOrder(12)
        // Shown: 2..6, hidden from 7.
        q.replaceUpcomingSlot(0) // 7 in for 2
        q.replaceUpcomingSlot(3) // 8 in for 5
        assertTrue(q.swipe(0))
        assertEquals(listOf(2L, 3, 4, 8, 6), q.visibleUpcoming().ids)
        assertTrue(q.swipe(3))
        assertEquals(listOf(2L, 3, 4, 5, 6), q.visibleUpcoming().ids)
        // Each song swapped in went back to the first place past the rows, so undone the other
        // way round from how they were made, those two come back the other way round too.
        assertEquals(listOf(8L, 7, 9, 10, 11, 12), q.upcoming.ids.drop(5))
    }

    @Test fun aSongAlreadyOnShowChangesPlacesWithTheRow() {
        val q = inOrder(7)
        // Shown 2..6, only 7 hidden. Swapping row 0 puts 2 last, right after the rows...
        q.replaceUpcomingSlot(0)
        // ...so swapping row 1 brings 2 back into view.
        q.replaceUpcomingSlot(1)
        assertEquals(listOf(7L, 2, 4, 5, 6), q.visibleUpcoming().ids)
        assertTrue(q.swipe(0))
        assertEquals(listOf(2L, 7, 4, 5, 6), q.visibleUpcoming().ids)
    }

    @Test fun swipeFollowsTheSongAsTheListMovesOn() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(3) // 7 in for 5: shown 2, 3, 4, 7, 6
        q.advanceToNext(seed)
        // Now shown 3, 4, 7, 6, 8: the row 7 is in has moved up one.
        assertEquals(5L, q.visibleSwapBacks()[2]?.id)
        assertTrue(q.swipe(2))
        // Only that row changes; 7 waits just past the rows.
        assertEquals(listOf(3L, 4, 5, 6, 8), q.visibleUpcoming().ids)
        assertEquals(7L, q.upcoming[5].id)
    }

    @Test fun aSongThatHasPlayedSinceCannotComeBack() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(0) // 7 in for 2
        q.jumpTo(song(2))
        assertEquals(7L, q.visibleUpcoming()[0].id)
        assertTrue(q.visibleSwapBacks().all { it == null })
        val before = q.upcoming.ids
        assertFalse(q.swipe(0))
        assertEquals(before, q.upcoming.ids)
    }

    @Test fun swipeIsIgnoredIfTheRowNoLongerShowsTheSong() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(1)
        val swiped = q.visibleUpcoming()[1].id
        q.advanceToNext(seed)
        val before = q.upcoming.ids
        assertFalse(q.swapBack(1, swiped))
        assertEquals(before, q.upcoming.ids)
    }

    @Test fun swipeOnAQueuedRowPutsBothSongsBackInTheQueue() {
        val q = inOrder(10, queued = listOf(100L, 101L, 102L, 103L, 104L, 105L))
        val queued = q.explicitQueue.ids
        val upcoming = q.upcoming.ids
        // Shown: 100..104; first hidden is the queued 105.
        q.replaceUpcomingSlot(1)
        assertEquals(listOf(100L, 105, 102, 103, 104), q.explicitQueue.ids)
        assertTrue(q.swipe(1))
        assertEquals(queued, q.explicitQueue.ids)
        assertEquals(upcoming, q.upcoming.ids)
    }

    @Test fun swipeOnAQueuedRowFilledFromTheOrder() {
        val q = inOrder(10, queued = listOf(100L))
        val upcoming = q.upcoming.ids
        // Shown: 100, 2, 3, 4, 5; first hidden 6 takes the queued row.
        q.replaceUpcomingSlot(0)
        assertTrue(q.swipe(0))
        assertEquals(listOf(100L), q.explicitQueue.ids)
        assertEquals(upcoming, q.upcoming.ids)
    }

    @Test fun swipeOnAQueuedSongThatWasAlsoInTheOrder() {
        // 4 is queued and also further down the order, so the swap leaves that copy alone.
        val q = inOrder(10, queued = listOf(4L))
        val upcoming = q.upcoming.ids
        q.replaceUpcomingSlot(0)
        assertEquals(upcoming.filter { it != 6L }, q.upcoming.ids)
        assertTrue(q.swipe(0))
        assertEquals(listOf(4L), q.explicitQueue.ids)
        assertEquals(upcoming, q.upcoming.ids)
    }

    @Test fun reshufflingForgetsTheSwaps() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(0)
        q.setShuffleEnabled(true, seed)
        assertTrue(q.visibleSwapBacks().all { it == null })
        assertTrue(q.slotSwapsSnapshot().isEmpty())
    }

    @Test fun removingTheSongForgetsItsSwap() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(0) // 7 in for 2
        q.removeSongs { it.id == 2L }
        assertTrue(q.slotSwapsSnapshot().isEmpty())
        assertFalse(q.swipe(0))
    }

    @Test fun swapsSurviveARestore() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(2)
        val saved = q.slotSwapsSnapshot()
        val pool = songs(1L..10L).associateBy { it.id }
        val r = PlaybackQueueManager()
        r.restore(
            pool = pool.values.toList(),
            label = "A",
            current = q.current,
            queued = emptyList(),
            upcomingSongs = q.upcoming.ids.map(pool::getValue),
            played = emptyList(),
            repeat = RepeatMode.OFF,
            order = PlayOrder.SEQUENTIAL,
            sourceKey = null,
            swaps = saved,
        )
        assertTrue(r.swipe(2))
        assertEquals((2L..10L).toList(), r.upcoming.ids)
    }

    @Test fun renamedSongsStillSwipeBack() {
        val q = inOrder(10)
        q.replaceUpcomingSlot(0)
        q.updateSongs { it.copy(displayName = it.displayName + "!") }
        assertEquals("s2!", q.visibleSwapBacks()[0]?.displayName)
        assertTrue(q.swipe(0))
        assertEquals(2L, q.visibleUpcoming()[0].id)
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
