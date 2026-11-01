package io.github.parthibdebnath.openplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator

class SongSortTest {
    private fun song(id: Long, name: String, artist: String = "", lastModified: Long = 0, duration: Long = 0) = Song(
        id = id, playlistId = 1, documentUri = "u$id", fileName = "$name.mp3", displayName = name, artist = artist,
        durationMs = duration, sizeBytes = 0, lastModified = lastModified, sortIndex = 0,
    )

    private fun names(sort: SongSort, songs: List<Song>) = sort.sort(songs).map { it.displayName }

    private val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

    private fun compare(a: String, b: String) = NaturalKey(a).compareTo(NaturalKey(b), collator)

    @Test fun numbersCompareByValue() {
        assertTrue(compare("Track 2", "Track 10") < 0)
        assertTrue(compare("Track 10", "Track 9") > 0)
    }

    @Test fun leadingZerosDoNotMatter() {
        assertEquals(0, compare("01 Intro", "1 Intro"))
        assertTrue(compare("002", "10") < 0)
    }

    @Test fun numbersSortBeforeText() {
        assertTrue(compare("1999", "Abba") < 0)
    }

    @Test fun veryLongNumbersStillCompare() {
        assertTrue(compare("123456789012345678901234567890", "123456789012345678901234567891") < 0)
    }

    @Test fun textIgnoresCase() {
        assertEquals(0, compare("hello", "HELLO"))
    }

    @Test fun shorterPrefixComesFirst() {
        assertTrue(compare("Song", "Song 2") < 0)
    }

    @Test fun nameSortIsNatural() {
        val list = listOf(song(1, "Track 10"), song(2, "Track 2"), song(3, "track 1"), song(4, "Intro"))
        assertEquals(listOf("Intro", "track 1", "Track 2", "Track 10"), names(SongSort.NAME, list))
    }

    @Test fun artistSortFallsBackToName() {
        val list = listOf(song(1, "B", "Zed"), song(2, "Track 10", "Abe"), song(3, "Track 9", "Abe"))
        assertEquals(listOf("Track 9", "Track 10", "B"), names(SongSort.ARTIST, list))
    }

    @Test fun recentIsNewestFirst() {
        val list = listOf(song(1, "Old", lastModified = 1), song(2, "New", lastModified = 3), song(3, "Mid", lastModified = 2))
        assertEquals(listOf("New", "Mid", "Old"), names(SongSort.RECENT, list))
    }

    @Test fun lengthIsShortestFirst() {
        val list = listOf(song(1, "Long", duration = 300), song(2, "Short", duration = 100))
        assertEquals(listOf("Short", "Long"), names(SongSort.LENGTH, list))
    }

    @Test fun unknownNameFallsBackToNameSort() {
        assertEquals(SongSort.NAME, SongSort.fromName("nonsense"))
        assertEquals(SongSort.LENGTH, SongSort.fromName("LENGTH"))
    }
}
