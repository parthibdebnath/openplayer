package io.github.parthibdebnath.openplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSongsTest {
    @Test fun idsAreNegativeAndStable() {
        val uri = "content://com.android.providers.downloads.documents/document/42"
        val id = ExternalSongs.idFor(uri)
        assertTrue(id < 0)
        assertEquals(id, ExternalSongs.idFor(uri))
    }

    @Test fun differentFilesGetDifferentIds() {
        val ids = (1..10_000).map { ExternalSongs.idFor("content://provider/document/$it") }.toSet()
        assertEquals(10_000, ids.size)
        assertTrue(ids.all { it < 0 })
    }

    // "Aa" and "BB" share a String.hashCode, which is how the old 31-bit ids collided.
    @Test fun stringHashCollisionsNoLongerShareAnId() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(ExternalSongs.idFor("content://x/Aa"), ExternalSongs.idFor("content://x/BB"))
    }
}
