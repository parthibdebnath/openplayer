package io.github.parthibdebnath.openplayer.data.model

import java.text.Collator

/** How a playlist's songs are listed, and the order "play in order" follows. */
enum class SongSort(val label: String) {
    NAME("Name"),
    ARTIST("Artist"),
    RECENT("Recently changed"),
    LENGTH("Length"),
    ;

    /**
     * Sorts [songs] into this order. Each name is broken into its comparison pieces once up front,
     * rather than on every one of the thousands of comparisons a large playlist needs.
     */
    fun sort(songs: List<Song>): List<Song> {
        if (songs.size < 2) return songs
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        val nameKeys = HashMap<Long, NaturalKey>(songs.size)
        fun nameKey(song: Song) = nameKeys.getOrPut(song.id) { NaturalKey(song.displayName) }
        val byName = Comparator<Song> { a, b -> nameKey(a).compareTo(nameKey(b), collator) }
        val comparator = when (this) {
            NAME -> byName
            ARTIST -> Comparator<Song> { a, b -> collator.compare(a.artist, b.artist) }.then(byName)
            // Newest first: what's just been added to the folder is usually what's wanted.
            RECENT -> compareByDescending<Song> { it.lastModified }.then(byName)
            LENGTH -> compareBy<Song> { it.durationMs }.then(byName)
        }
        return songs.sortedWith(comparator)
    }

    companion object {
        fun fromName(name: String?): SongSort = entries.firstOrNull { it.name == name } ?: NAME
    }
}

/**
 * A name split into text and number runs, so numbers compare by value: "Track 2" before
 * "Track 10", where plain text ordering puts "10" first.
 */
private class NaturalKey(name: String) {
    private val parts: List<Any> = buildList {
        var i = 0
        while (i < name.length) {
            val start = i
            val digits = name[i].isDigit()
            while (i < name.length && name[i].isDigit() == digits) i++
            val run = name.substring(start, i)
            add(if (digits) run.trimStart('0').ifEmpty { "0" }.let { NumberRun(it) } else run)
        }
    }

    fun compareTo(other: NaturalKey, collator: Collator): Int {
        val count = minOf(parts.size, other.parts.size)
        for (index in 0 until count) {
            val a = parts[index]
            val b = other.parts[index]
            val result = when {
                a is NumberRun && b is NumberRun -> a.compareTo(b)
                a is NumberRun -> -1
                b is NumberRun -> 1
                else -> collator.compare(a as String, b as String)
            }
            if (result != 0) return result
        }
        return parts.size.compareTo(other.parts.size)
    }
}

/** A run of digits with leading zeros removed, compared by length first so any size fits. */
private class NumberRun(private val digits: String) : Comparable<NumberRun> {
    override fun compareTo(other: NumberRun): Int =
        if (digits.length != other.digits.length) digits.length.compareTo(other.digits.length) else digits.compareTo(other.digits)
}
