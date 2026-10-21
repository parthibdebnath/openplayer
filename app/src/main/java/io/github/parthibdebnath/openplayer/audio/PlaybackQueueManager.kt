package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.data.model.Song
import kotlin.random.Random

enum class RepeatMode { OFF, ONE, ALL }

/** How the currently active source's songs are ordered into the queue. */
enum class PlayOrder { SEQUENTIAL, SHUFFLED }

/** Number of upcoming songs shown on the main play screen when expanded. */
const val VISIBLE_UPCOMING_COUNT = 5

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

    /**
     * Bumped on every change to the queue's shape, so persistence can tell when there's something
     * new to save without diffing thousands of ids every time it checks.
     */
    var version: Long = 0L
        private set

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

        if (visibleIndex >= explicitCount) {
            val localIndex = visibleIndex - explicitCount
            val localHidden = hiddenIndex - explicitCount
            val displaced = upcoming[localIndex]
            upcoming[localIndex] = upcoming.removeAt(localHidden)
            upcoming.add(displaced)
        } else {
            val displaced = explicitQueue[visibleIndex]
            // Removing from further down never shifts the slot being replaced.
            val incoming = if (hiddenIndex < explicitCount) {
                explicitQueue.removeAt(hiddenIndex)
            } else {
                upcoming.removeAt(hiddenIndex - explicitCount)
            }
            explicitQueue[visibleIndex] = incoming
            if (upcoming.none { it.id == displaced.id }) upcoming.add(displaced)
        }
        changed()
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
        if (pool.size + explicitQueue.size + upcoming.size + history.size != before) changed()
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
        changed()
    }
}
