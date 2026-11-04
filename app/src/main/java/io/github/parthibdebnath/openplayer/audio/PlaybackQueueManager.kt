package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.data.model.Song
import kotlin.random.Random

enum class RepeatMode { OFF, ONE, ALL }

/** How the currently active source's songs are ordered into the queue. */
enum class PlayOrder { SEQUENTIAL, SHUFFLED }

/** Number of upcoming songs shown on the main play screen when expanded. */
const val VISIBLE_UPCOMING_COUNT = 5

/** The most swaps remembered for bringing songs back; the oldest are forgotten first. */
private const val MAX_REMEMBERED_SWAPS = 200

/**
 * One use of the swap arrow on the next-5 list: [incoming] took the place of [displaced]. Kept so
 * a swipe on [incoming] can put [displaced] back - see [PlaybackQueueManager.undoSwap].
 *
 * Keyed by song rather than by slot, because slots move: once the current song finishes, every
 * song in the list is one place nearer the top, and a record of "slot 3" would then point at the
 * wrong row.
 */
data class UpcomingSwap(
    val incoming: Song,
    val displaced: Song,
    /** Whether [displaced] was added to the end of the generated order, rather than already in it. */
    val displacedAppended: Boolean,
    /** Whether [incoming] was a queued song, rather than one from the generated order. */
    val incomingFromQueue: Boolean,
    /**
     * The song [incoming] came straight after, just out of view, before the swap took it: undoing
     * puts it back behind that song again, so the order is what it would have been. Null if
     * unknown, in which case it goes back to just past the visible list.
     */
    val incomingAfter: Song? = null,
)

/**
 * Owns the play queue: the active pool of songs (single or merged multi-playlist), shuffle
 * (seeded, reproducible) vs sequential ordering, repeat mode, the user-explicit "queue next"
 * list, and history for the Previous button. UI reads [current] / [explicitQueue] / [upcoming]
 * to render "now playing" and the next-5 list; [playlistLabel] drives the now-playing playlist
 * name ("Multi-Playlist" when merged).
 *
 * Only ever touched from the main thread.
 */
class PlaybackQueueManager {
    private var pool: List<Song> = emptyList()
    var playlistLabel: String = ""
        private set

    var repeatMode: RepeatMode = RepeatMode.OFF
        private set
    var playOrder: PlayOrder = PlayOrder.SEQUENTIAL
        private set

    var current: Song? = null
        private set
    val explicitQueue: MutableList<Song> = mutableListOf()
    val upcoming: MutableList<Song> = mutableListOf()
    private val history: MutableList<Song> = mutableListOf()

    /** Swaps made from the next-5 list, oldest first; see [undoSwap]. */
    private val swaps: MutableList<UpcomingSwap> = mutableListOf()

    /**
     * Bumped on every change to the queue's shape, so persistence can tell when there's something
     * new to save without diffing thousands of ids every time it checks.
     */
    var version: Long = 0L
        private set

    // Which of the visible songs can be swiped back, worked out once per change to the queue
    // rather than on each of the several UI refreshes a second.
    private var restorableVersion = -1L
    private var restorable: List<Boolean> = emptyList()

    /** Told after every change, so the engine can save the queue as it now stands. */
    var onChanged: (() -> Unit)? = null

    private fun changed() {
        version++
        onChanged?.invoke()
    }

    /** Combined next-up list for UI: explicit queue first, then the generated order. */
    fun visibleUpcoming(count: Int = VISIBLE_UPCOMING_COUNT): List<Song> {
        // Taken from the two lists directly: this runs on every UI refresh, and concatenating
        // them first copied the entire remaining queue - tens of thousands of songs for a big
        // library - several times a second, just to keep five.
        val fromQueue = explicitQueue.take(count)
        return if (fromQueue.size == count) fromQueue else fromQueue + upcoming.take(count - fromQueue.size)
    }

    fun hasActiveSource(): Boolean = pool.isNotEmpty()
    fun hasHistory(): Boolean = history.isNotEmpty()

    /**
     * The song that will play after the current one, without advancing anything - used to preload
     * it for a gapless transition.
     *
     * Deliberately returns null at the end of the queue even under repeat-all: working out the
     * wrapped order without mutating state is not worth it, and the cost is that one transition
     * per lap through a playlist isn't gapless.
     */
    fun peekNext(): Song? = explicitQueue.firstOrNull() ?: upcoming.firstOrNull()

    /** The full pool backing the current source, for persisting the queue across restarts. */
    fun poolSnapshot(): List<Song> = pool

    /** Songs already played this session, oldest first, for persisting the queue across restarts. */
    fun historySnapshot(): List<Song> = history.toList()

    /** Swaps that can still be undone, oldest first, for persisting the queue across restarts. */
    fun swapSnapshot(): List<UpcomingSwap> = swaps.filter { isWaiting(it.incoming.id) && isWaiting(it.displaced.id) }

    /** Rebuilds the queue exactly as it was, without touching playback - see [PlaybackEngine.restoreIfNeeded]. */
    fun restore(
        pool: List<Song>,
        label: String,
        current: Song?,
        queued: List<Song>,
        upcomingSongs: List<Song>,
        played: List<Song>,
        repeat: RepeatMode,
        order: PlayOrder,
        swapHistory: List<UpcomingSwap> = emptyList(),
    ) {
        this.pool = pool
        playlistLabel = label
        this.current = current
        repeatMode = repeat
        playOrder = order
        history.clear()
        history += played
        explicitQueue.clear()
        explicitQueue += queued
        upcoming.clear()
        upcoming += upcomingSongs
        swaps.clear()
        swaps += swapHistory.takeLast(MAX_REMEMBERED_SWAPS)
        changed()
    }

    /**
     * @param startIndex index into [songs] to begin from, or null to let [order] decide - which
     *   for a shuffle means the first song of the shuffled order. Passing 0 here for a shuffle
     *   pinned the playlist's first song to the front and only shuffled everything after it.
     */
    fun startPlaylist(songs: List<Song>, label: String, order: PlayOrder, shuffleSeed: String, startIndex: Int? = null) {
        pool = songs
        playlistLabel = label
        playOrder = order
        history.clear()
        explicitQueue.clear()
        swaps.clear()
        changed()
        if (songs.isEmpty()) {
            current = null
            upcoming.clear()
            return
        }
        if (startIndex != null) {
            val start = startIndex.coerceIn(0, songs.lastIndex)
            val startSong = songs[start]
            current = startSong
            // In order, the songs before the tapped one count as already played: playback carries
            // on with the songs after it, and Previous steps back through the earlier ones.
            // Treating them as still to come played them straight after the tapped song.
            if (order == PlayOrder.SEQUENTIAL) history += songs.subList(0, start)
            rebuildUpcoming(shuffleSeed, excluding = setOf(startSong.id))
            return
        }
        current = null
        rebuildUpcoming(shuffleSeed)
        current = upcoming.removeFirstOrNull()
    }

    /** Rebuilds [upcoming] from the remaining (not-yet-played, not-current) pool. Used on start and on seed change/refresh. */
    fun rebuildUpcoming(shuffleSeed: String, excluding: Set<Long> = emptySet()) {
        val playedIds = HashSet<Long>(history.size + excluding.size + 1).apply {
            history.forEach { add(it.id) }
            current?.let { add(it.id) }
            addAll(excluding)
        }
        // A fresh order: what a song "replaced" in the old one no longer means anything.
        swaps.clear()
        upcoming.clear()
        upcoming += when (playOrder) {
            PlayOrder.SEQUENTIAL -> {
                // Carries on from wherever the current song sits in the list, then wraps round to
                // anything earlier that hasn't played - so switching shuffle off mid-playlist
                // continues from here instead of jumping back to the top of the list.
                val currentIndex = current?.let { song -> pool.indexOfFirst { it.id == song.id } } ?: -1
                val after = ArrayList<Song>()
                val before = ArrayList<Song>()
                pool.forEachIndexed { index, song ->
                    if (song.id !in playedIds) (if (index > currentIndex) after else before).add(song)
                }
                after + before
            }
            PlayOrder.SHUFFLED -> pool.filter { it.id !in playedIds }.shuffled(Random(shuffleSeed.hashCode().toLong()))
        }
        changed()
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        changed()
    }

    fun setShuffleEnabled(enabled: Boolean, shuffleSeed: String) {
        val newOrder = if (enabled) PlayOrder.SHUFFLED else PlayOrder.SEQUENTIAL
        if (newOrder == playOrder) return
        playOrder = newOrder
        rebuildUpcoming(shuffleSeed)
    }

    /** A song was picked to jump the queue: it plays next, after any already-queued songs. */
    fun queueSong(song: Song) {
        explicitQueue.add(song)
        changed()
    }

    /** A song to play straight after the current one, ahead of anything already queued. */
    fun playNext(song: Song) {
        explicitQueue.add(0, song)
        changed()
    }

    /**
     * Moves on to the next song. Returns the new current song, or null if playback should stop.
     *
     * Repeat-one doesn't hold the queue here: the player itself loops a repeat-one song, so this
     * only runs when something deliberately moves on - the next button, or skipping a song that
     * can't be played - and both of those should actually move on.
     */
    fun advanceToNext(shuffleSeed: String): Song? {
        current?.let { history.add(it) }

        val next = when {
            explicitQueue.isNotEmpty() -> explicitQueue.removeAt(0)
            upcoming.isNotEmpty() -> upcoming.removeAt(0)
            repeatMode == RepeatMode.ALL && pool.isNotEmpty() -> {
                history.clear()
                // Cleared before rebuilding: rebuildUpcoming leaves out whatever is current, and
                // at this point that's still the song that just finished - so every new lap was
                // one song short, and a single-song playlist stopped instead of repeating.
                current = null
                rebuildUpcoming(shuffleSeed, excluding = emptySet())
                upcoming.removeFirstOrNull()
            }
            else -> null
        }
        current = next
        // Playing now, so no longer in the list to be swiped.
        if (next != null) swaps.removeAll { it.incoming.id == next.id }
        changed()
        return next
    }

    /** Moves back to the previous song from history, pushing the current one back onto the front of upcoming. */
    fun goToPrevious(): Song? {
        val previous = history.removeLastOrNull() ?: return null
        current?.let { upcoming.add(0, it) }
        current = previous
        changed()
        return previous
    }

    /**
     * Replaces one visible slot's song with the next one that "didn't make the top 5", deferring
     * the displaced song to the end of the order.
     *
     * Queued songs can be swapped too. The incoming song takes over the queued slot, so it plays at
     * exactly the point the list shows. The displaced song goes to the end of the generated order,
     * unless it's already in it, as queued songs from the same playlist usually are.
     */
    fun replaceUpcomingSlot(visibleIndex: Int) {
        val explicitCount = explicitQueue.size
        val total = explicitCount + upcoming.size
        // The first song past the visible window is the one swapped in.
        val hiddenIndex = VISIBLE_UPCOMING_COUNT
        if (visibleIndex !in 0 until hiddenIndex || hiddenIndex >= total) return

        // What the song about to be swapped in follows, before anything moves.
        val after = if (hiddenIndex - 1 < explicitCount) explicitQueue[hiddenIndex - 1] else upcoming[hiddenIndex - 1 - explicitCount]
        val swap = if (visibleIndex >= explicitCount) {
            val localIndex = visibleIndex - explicitCount
            val localHidden = hiddenIndex - explicitCount
            val displaced = upcoming[localIndex]
            val incoming = upcoming.removeAt(localHidden)
            upcoming[localIndex] = incoming
            upcoming.add(displaced)
            UpcomingSwap(incoming, displaced, displacedAppended = true, incomingFromQueue = false, incomingAfter = after)
        } else {
            val displaced = explicitQueue[visibleIndex]
            val fromQueue = hiddenIndex < explicitCount
            // Removing from further down never shifts the slot being replaced.
            val incoming = if (fromQueue) {
                explicitQueue.removeAt(hiddenIndex)
            } else {
                upcoming.removeAt(hiddenIndex - explicitCount)
            }
            explicitQueue[visibleIndex] = incoming
            val appended = upcoming.none { it.id == displaced.id }
            if (appended) upcoming.add(displaced)
            UpcomingSwap(incoming, displaced, displacedAppended = appended, incomingFromQueue = fromQueue, incomingAfter = after)
        }
        // A queued song swapped for its own copy further down has nothing to bring back.
        if (swap.incoming.id != swap.displaced.id) {
            swaps += swap
            if (swaps.size > MAX_REMEMBERED_SWAPS) swaps.removeAt(0)
        }
        changed()
    }

    /**
     * For each song [visibleUpcoming] shows, whether swiping it would bring back the song it
     * replaced - see [undoSwap].
     */
    fun visibleRestorable(): List<Boolean> {
        if (restorableVersion != version) {
            restorableVersion = version
            val visible = visibleUpcoming()
            restorable = if (swaps.isEmpty()) {
                List(visible.size) { false }
            } else {
                visible.map { song -> swapFor(song.id)?.let { it.displaced.id != song.id && isWaiting(it.displaced.id) } == true }
            }
        }
        return restorable
    }

    /** The latest swap that brought [songId] in, if any. */
    private fun swapFor(songId: Long): UpcomingSwap? = swaps.lastOrNull { it.incoming.id == songId }

    /** Whether [songId] is still to come - queued, or in the generated order - and so can be brought back. */
    private fun isWaiting(songId: Long): Boolean =
        current?.id != songId && (explicitQueue.any { it.id == songId } || upcoming.any { it.id == songId })

    /**
     * Undoes the swap that brought the song at [visibleIndex] into the next-5 list: the song it
     * replaced goes back into that slot, and the song itself goes back to just past the visible
     * list, where the swap took it from. Swiping the restored song again goes back another step,
     * if it too was swapped in.
     *
     * The song it replaced has to still be waiting to play. One that has been played since, or
     * taken out of the queue, can't come back, and nothing changes.
     *
     * @param songId the song the slot is expected to hold, so a list that changed under the
     *   finger never undoes the wrong row.
     * @return whether a song was brought back.
     */
    fun undoSwap(visibleIndex: Int, songId: Long): Boolean {
        if (visibleIndex !in 0 until VISIBLE_UPCOMING_COUNT) return false
        val inQueue = visibleIndex < explicitQueue.size
        val slotList = if (inQueue) explicitQueue else upcoming
        var slotIndex = if (inQueue) visibleIndex else visibleIndex - explicitQueue.size
        val song = slotList.getOrNull(slotIndex) ?: return false
        if (song.id != songId) return false
        val swap = swapFor(songId) ?: return false
        val displaced = swap.displaced
        if (displaced.id == songId || !isWaiting(displaced.id)) return false

        // Take the replaced song out of wherever it's waiting, so it doesn't play twice:
        //  - appended to the end of the order by the swap, it's that copy that goes;
        //  - queued again since, it's moved from there;
        //  - a queued song that was in the order all along keeps its place there, as it had before
        //    the swap - unless it's coming back into the order itself, where it would be doubled.
        val fromList: MutableList<Song>? = when {
            swap.displacedAppended && upcoming.any { it.id == displaced.id } -> upcoming
            explicitQueue.any { it.id == displaced.id } -> explicitQueue
            slotList === upcoming -> upcoming
            else -> null
        }
        var restored = displaced
        if (fromList != null) {
            val fromIndex = fromList.indexOfLast { it.id == displaced.id }
            restored = fromList.removeAt(fromIndex)
            if (fromList === slotList && fromIndex < slotIndex) slotIndex--
        }
        slotList.removeAt(slotIndex)

        // Into the very row that was swiped, even when taking it out from further up the list
        // moved that row's position: a short queue's end can be in view, and that's where the
        // swap left it. Queued songs stay ahead of the generated order, as always.
        if (slotList === explicitQueue) {
            explicitQueue.add(visibleIndex.coerceAtMost(explicitQueue.size), restored)
        } else {
            upcoming.add((visibleIndex - explicitQueue.size).coerceIn(0, upcoming.size), restored)
        }

        // And the swapped-in song goes back behind the song it followed before the swap, as
        // though it had never been touched - or, with that one gone, to the first place past the
        // visible list, where the swap took it from. Never ahead of the row just restored.
        val combined = explicitQueue + upcoming
        val afterIndex = swap.incomingAfter?.let { after -> combined.indexOfFirst { it.id == after.id } } ?: -1
        val target = (if (afterIndex >= 0) afterIndex + 1 else VISIBLE_UPCOMING_COUNT).coerceAtLeast(visibleIndex + 1)
        if (swap.incomingFromQueue && target <= explicitQueue.size) {
            explicitQueue.add(target, song)
        } else {
            upcoming.add((target - explicitQueue.size).coerceIn(0, upcoming.size), song)
        }

        swaps.remove(swap)
        changed()
        return true
    }

    /**
     * Drag-reorder within the expanded next-5 list.
     *
     * The indices are positions in the list as displayed - queued songs first, then the generated
     * order - so they're applied to that combined list. Applying them to [upcoming] alone moved the
     * wrong songs whenever anything had been queued. The queued block keeps its size, so the songs
     * in its slots afterwards are the ones that play first, exactly as the list showed.
     */
    fun reorderUpcoming(fromIndex: Int, toIndex: Int) {
        val combined = (explicitQueue + upcoming).toMutableList()
        if (fromIndex !in combined.indices || toIndex !in combined.indices) return
        val queuedCount = explicitQueue.size
        combined.add(toIndex, combined.removeAt(fromIndex))
        explicitQueue.clear()
        explicitQueue += combined.take(queuedCount)
        upcoming.clear()
        upcoming += combined.drop(queuedCount)
        changed()
    }

    /**
     * Removes every song matching [predicate] from everywhere except [current] - which the caller
     * has to deal with, since replacing what's playing involves the player too.
     *
     * For songs that have stopped existing: a deleted playlist, a song moved to the removed-songs
     * folder, a file deleted outside the app. Left in the queue they'd fail the moment playback
     * reached them.
     */
    fun removeSongs(predicate: (Song) -> Boolean) {
        val before = pool.size + explicitQueue.size + upcoming.size + history.size
        pool = pool.filterNot(predicate)
        explicitQueue.removeAll(predicate)
        upcoming.removeAll(predicate)
        history.removeAll(predicate)
        val swapsBefore = swaps.size
        swaps.removeAll { predicate(it.incoming) || predicate(it.displaced) }
        // Only a reference point; without it the song goes back to just past the visible list.
        swaps.replaceAll { swap -> if (swap.incomingAfter?.let(predicate) == true) swap.copy(incomingAfter = null) else swap }
        if (pool.size + explicitQueue.size + upcoming.size + history.size != before || swaps.size != swapsBefore) changed()
    }

    /**
     * Replaces [current] with whatever is next, without counting it as played - it's being taken
     * out, not finished. Ignores repeat-one, which would otherwise hand the same song straight back.
     */
    fun dropCurrent(): Song? {
        val next = explicitQueue.removeFirstOrNull() ?: upcoming.removeFirstOrNull()
        current = next
        changed()
        return next
    }

    /** Rewrites matching songs in place everywhere they appear - e.g. after a rename. */
    fun updateSongs(transform: (Song) -> Song) {
        pool = pool.map(transform)
        current = current?.let(transform)
        explicitQueue.replaceAll { transform(it) }
        upcoming.replaceAll { transform(it) }
        history.replaceAll { transform(it) }
        swaps.replaceAll {
            it.copy(incoming = transform(it.incoming), displaced = transform(it.displaced), incomingAfter = it.incomingAfter?.let(transform))
        }
        changed()
    }
}
