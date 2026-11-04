package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Swiping a next-5 row to bring back the song its swap replaced; see [PlaybackQueueManager.undoSwap]. */
class UpcomingSwapUndoTest {

    private fun song(id: Long) = Song(id, 1, "u$id", "f$id", "S$id", "A", 1000, 0, 0, id)

    private fun queue(size: Int = 12) = PlaybackQueueManager().apply {
        startPlaylist((1L..size).map(::song), "p", PlayOrder.SEQUENTIAL, "seed", startIndex = 0)
    }

    private fun PlaybackQueueManager.ids() = (explicitQueue + upcoming).map { it.id }

    private fun PlaybackQueueManager.visible() = visibleUpcoming().map { it.id }

    @Test
    fun undoIsTheExactInverseOfASwap() {
        val q = queue()
        val before = q.ids()
        q.replaceUpcomingSlot(2)
        assertEquals(listOf(2L, 3, 7, 5, 6), q.visible())
        assertEquals(listOf(false, false, true, false, false), q.visibleRestorable())
        assertTrue(q.undoSwap(2, 7))
        assertEquals(before, q.ids())
        assertTrue(q.visibleRestorable().none { it })
    }

    @Test
    fun repeatedSwipesWalkBackThroughRepeatedSwaps() {
        val q = queue()
        val before = q.ids()
        q.replaceUpcomingSlot(1)
        q.replaceUpcomingSlot(1)
        assertEquals(listOf(2L, 8, 4, 5, 6), q.visible())
        assertTrue(q.undoSwap(1, 8))
        assertEquals(listOf(2L, 7, 4, 5, 6), q.visible())
        assertTrue(q.undoSwap(1, 7))
        assertEquals(before, q.ids())
    }

    @Test
    fun aRowThatWasNeverSwappedOrDoesNotMatchIsLeftAlone() {
        val q = queue()
        q.replaceUpcomingSlot(0)
        val after = q.ids()
        assertFalse(q.undoSwap(0, 3))
        assertFalse(q.undoSwap(1, 3))
        assertFalse(q.undoSwap(7, 7))
        assertEquals(after, q.ids())
    }

    @Test
    fun followsTheSongAsTheQueueMovesOn() {
        val q = queue()
        q.replaceUpcomingSlot(3)
        q.advanceToNext("seed")
        assertEquals(listOf(3L, 4, 7, 6, 8), q.visible())
        assertTrue(q.visibleRestorable()[2])
        assertTrue(q.undoSwap(2, 7))
        // As though the swap had never happened.
        assertEquals(listOf(3L, 4, 5, 6, 7, 8), q.ids().take(6))
    }

    @Test
    fun aSongPlayedSinceCannotBeBroughtBack() {
        val q = queue(7)
        q.replaceUpcomingSlot(0)
        repeat(6) { q.advanceToNext("seed") }
        assertTrue(q.visibleRestorable().none { it })
    }

    @Test
    fun theRestoredSongLandsInTheSwipedRowEvenWhenItWasInView() {
        val q = queue(8)
        q.replaceUpcomingSlot(4)
        q.advanceToNext("seed")
        q.advanceToNext("seed")
        assertEquals(listOf(4L, 5, 7, 8, 6), q.visible())
        assertTrue(q.undoSwap(2, 7))
        assertEquals(6L, q.visible()[2])
        assertEquals(listOf(4L, 5, 6, 7, 8), q.ids().sorted())
    }

    @Test
    fun queuedSlotsUndoExactly() {
        val q = queue(20)
        q.queueSong(song(10))
        val before = q.ids()
        q.replaceUpcomingSlot(0)
        assertTrue(q.undoSwap(0, 6))
        assertEquals(before, q.ids())
        assertEquals(listOf(10L), q.explicitQueue.map { it.id })

        (13L..18).forEach { q.queueSong(song(it)) }
        val beforeQueued = q.ids()
        val queued = q.explicitQueue.map { it.id }
        q.replaceUpcomingSlot(1)
        assertTrue(q.undoSwap(1, q.visibleUpcoming()[1].id))
        assertEquals(beforeQueued, q.ids())
        assertEquals(queued, q.explicitQueue.map { it.id })
    }

    @Test
    fun aNewOrderForgetsSwaps() {
        val q = queue()
        q.replaceUpcomingSlot(0)
        q.setShuffleEnabled(true, "other")
        assertTrue(q.visibleRestorable().none { it })
    }

    @Test
    fun removedSongsForgetTheirSwaps() {
        val q = queue()
        q.replaceUpcomingSlot(0)
        q.removeSongs { it.id == 2L }
        assertTrue(q.visibleRestorable().none { it })
    }

    @Test
    fun survivesBeingSavedAndRestored() {
        val q = queue()
        q.replaceUpcomingSlot(2)
        val r = PlaybackQueueManager()
        r.restore(
            q.poolSnapshot(), "p", q.current, q.explicitQueue.toList(), q.upcoming.toList(), q.historySnapshot(),
            RepeatMode.OFF, PlayOrder.SEQUENTIAL, q.swapSnapshot(),
        )
        assertTrue(r.visibleRestorable()[2])
        assertTrue(r.undoSwap(2, 7))
        assertEquals(listOf(2L, 3, 4, 5, 6), r.visible())
    }

    @Test
    fun randomUseNeverLosesOrDoublesASongAndRestorableRowsAlwaysRestore() {
        val random = Random(7)
        repeat(2_000) {
            val size = 3 + random.nextInt(15)
            val q = queue(size)
            val withQueued = random.nextBoolean()
            if (withQueued) repeat(random.nextInt(4)) { q.queueSong(song(1L + random.nextInt(size))) }
            repeat(30) {
                val counts = q.ids().groupingBy { it }.eachCount()
                when (random.nextInt(5)) {
                    0, 1 -> q.replaceUpcomingSlot(random.nextInt(VISIBLE_UPCOMING_COUNT))
                    2 -> {
                        val visible = q.visibleUpcoming()
                        val restorable = q.visibleRestorable()
                        assertEquals(visible.size, restorable.size)
                        restorable.indices.filter { restorable[it] }.randomOrNull(random)?.let { index ->
                            assertTrue(q.undoSwap(index, visible[index].id))
                            if (!withQueued) assertEquals(counts, q.ids().groupingBy { it }.eachCount())
                        }
                    }
                    3 -> q.advanceToNext("seed")
                    4 -> if (!withQueued) {
                        val visible = q.visibleUpcoming()
                        if (visible.size > 1) q.reorderUpcoming(random.nextInt(visible.size), random.nextInt(visible.size))
                    }
                }
                if (!withQueued) assertEquals(q.ids().size, q.ids().toSet().size)
            }
        }
    }
}
