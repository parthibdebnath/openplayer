package io.github.parthibdebnath.openplayer.audio

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTest {
    @Test fun parsesTheWaysTaggersWriteDecibels() {
        assertEquals(-7.89f, parseDb("-7.89 dB")!!, 1e-6f)
        assertEquals(2.1f, parseDb("+2.1dB")!!, 1e-6f)
        assertEquals(-7.89f, parseDb("-7,89 dB")!!, 1e-6f)
        assertEquals(3f, parseDb("  3 db ")!!, 1e-6f)
        assertEquals(-1.5f, parseDb("-1.5 DB")!!, 1e-6f)
        assertEquals(0.5f, parseDb("0.5")!!, 1e-6f)
    }

    @Test fun rejectsWhatIsNotANumber() {
        assertNull(parseDb(""))
        assertNull(parseDb("loud"))
        assertNull(parseDb("dB"))
    }

    @Test fun noMetadataMeansNoGain() {
        assertNull(replayGainDb(null))
        assertNull(replayGainDb(Metadata(VorbisComment("ARTIST", "Someone"))))
    }

    @Test fun readsVorbisCommentsWhateverTheCase() {
        assertEquals(-6.5f, replayGainDb(Metadata(VorbisComment("replaygain_track_gain", "-6.50 dB")))!!, 1e-6f)
    }

    // Opus R128 gain: Q7.8 relative to -23 LUFS, 5 dB below the ReplayGain reference.
    @Test fun convertsR128Gain() {
        assertEquals(4f, replayGainDb(Metadata(VorbisComment("R128_TRACK_GAIN", "-256")))!!, 1e-6f)
        assertEquals(5f, replayGainDb(Metadata(VorbisComment("R128_TRACK_GAIN", "0")))!!, 1e-6f)
    }

    @Test fun readsId3UserTextFrames() {
        val frame = TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("-3.20 dB"))
        assertEquals(-3.2f, replayGainDb(Metadata(frame))!!, 1e-6f)
    }

    @Test fun firstReadableGainWins() {
        val metadata = Metadata(
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "unreadable"),
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "-1.00 dB"),
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "-9.00 dB"),
        )
        assertEquals(-1f, replayGainDb(metadata)!!, 1e-6f)
    }
}
