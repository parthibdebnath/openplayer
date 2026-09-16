package com.musicplayer.app.audio

import com.musicplayer.app.data.model.Song
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

    /** Combined next-up list for UI: explicit queue first, then the generated order. */
    fun visibleUpcoming(count: Int = VISIBLE_UPCOMING_COUNT): List<Song> =
        (explicitQueue + upcoming).take(count)

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

    /** Rebuilds the queue exactly as it was, without touching playback - see [PlaybackEngine.restore]. */
    fun restore(
        pool: List<Song>,
        label: String,
        current: Song?,
        queued: List<Song>,
        upcomingSongs: List<Song>,
        repeat: RepeatMode,
        order: PlayOrder,
    ) {
        this.pool = pool
        playlistLabel = label
        this.current = current
        repeatMode = repeat
        playOrder = order
        history.clear()
        explicitQueue.clear()
        explicitQueue += queued
        upcoming.clear()
        upcoming += upcomingSongs
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
        if (songs.isEmpty()) {
            current = null
            upcoming.clear()
            return
        }
        if (startIndex != null) {
            val startSong = songs[startIndex.coerceIn(0, songs.lastIndex)]
            current = startSong
            rebuildUpcoming(shuffleSeed, excluding = setOf(startSong.id))
            return
        }
        current = null
        rebuildUpcoming(shuffleSeed)
        current = upcoming.removeFirstOrNull()
    }

    /** Rebuilds [upcoming] from the remaining (not-yet-played, not-current) pool. Used on start and on seed change/refresh. */
    fun rebuildUpcoming(shuffleSeed: String, excluding: Set<Long> = emptySet()) {
        val playedIds = (history.map { it.id } + listOfNotNull(current?.id) + excluding).toSet()
        val remaining = pool.filter { it.id !in playedIds }
        upcoming.clear()
        upcoming += when (playOrder) {
            PlayOrder.SEQUENTIAL -> remaining
            PlayOrder.SHUFFLED -> remaining.shuffled(Random(shuffleSeed.hashCode().toLong()))
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
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
    }

    /** Advances to the next song per repeat mode. Returns the new current song, or null if playback should stop. */
    fun advanceToNext(shuffleSeed: String): Song? {
        if (repeatMode == RepeatMode.ONE) {
            return current
        }
        current?.let { history.add(it) }

        val next = when {
            explicitQueue.isNotEmpty() -> explicitQueue.removeAt(0)
            upcoming.isNotEmpty() -> upcoming.removeAt(0)
            repeatMode == RepeatMode.ALL && pool.isNotEmpty() -> {
                history.clear()
                rebuildUpcoming(shuffleSeed, excluding = emptySet())
                upcoming.removeFirstOrNull()
            }
            else -> null
        }
        current = next
        return next
    }

    /** Moves back to the previous song from history, pushing the current one back onto the front of upcoming. */
    fun goToPrevious(): Song? {
        val previous = history.removeLastOrNull() ?: return null
        current?.let { upcoming.add(0, it) }
        current = previous
        return previous
    }

    /** Replaces one visible slot's song with the next one that "didn't make the top 5", deferring the displaced song. */
    fun replaceUpcomingSlot(visibleIndex: Int) {
        val explicitCount = explicitQueue.size
        if (visibleIndex < explicitCount) return // queued songs aren't swappable
        val localIndex = visibleIndex - explicitCount
        val hiddenIndex = VISIBLE_UPCOMING_COUNT - explicitCount
        if (localIndex !in upcoming.indices || hiddenIndex !in upcoming.indices) return
        val displaced = upcoming[localIndex]
        val incoming = upcoming[hiddenIndex]
        upcoming[localIndex] = incoming
        upcoming.removeAt(hiddenIndex)
        upcoming.add(displaced)
    }

    /** Drag-reorder within the expanded next-5 list (explicit queue entries only; see UI layer for the constraint). */
    fun reorderUpcoming(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in upcoming.indices || toIndex !in upcoming.indices) return
        val item = upcoming.removeAt(fromIndex)
        upcoming.add(toIndex, item)
    }
}
