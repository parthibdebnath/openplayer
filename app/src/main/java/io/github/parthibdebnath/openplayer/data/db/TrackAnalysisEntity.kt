package io.github.parthibdebnath.openplayer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis

/**
 * A song's DJ-mix analysis, kept so each song is only analysed once.
 *
 * Keyed by song id with no foreign key, for the same reason as `song_tags`: the scanner rewrites a
 * changed song's row as a delete and an insert, which would cascade this away. The file's size and
 * modification time are kept instead, and an analysis of a different version of the file is
 * ignored and redone. Rows for songs that are gone are cleared by [TrackAnalysisDao.deleteOrphans].
 *
 * Only analyses are kept. A song that couldn't be analysed is remembered for the run it happened
 * in only (see TrackAnalysisRepository): a decoder that gave up on a busy phone, or a file still
 * being copied, deserves another try next time.
 */
@Entity(tableName = "track_analysis")
data class TrackAnalysisEntity(
    @PrimaryKey val songId: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    val version: Int,
    val durationMs: Double,
    val bpm: Double,
    val beatConfidence: Double,
    val tempoStable: Boolean,
    val introDownbeatMs: Double,
    val outroDownbeatMs: Double,
    val downbeatConfidence: Double,
    val musicalKey: Int,
    val keyConfidence: Double,
    val audioStartMs: Double,
    val audioEndMs: Double,
    val introEndMs: Double,
    val outroStartMs: Double,
    val loudnessDb: Double,
) {
    fun toAnalysis() = TrackAnalysis(
        durationMs = durationMs,
        bpm = bpm,
        beatConfidence = beatConfidence,
        tempoStable = tempoStable,
        introDownbeatMs = introDownbeatMs,
        outroDownbeatMs = outroDownbeatMs,
        downbeatConfidence = downbeatConfidence,
        key = musicalKey,
        keyConfidence = keyConfidence,
        audioStartMs = audioStartMs,
        audioEndMs = audioEndMs,
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        loudnessDb = loudnessDb,
    )

    companion object {
        fun of(songId: Long, sizeBytes: Long, lastModified: Long, analysis: TrackAnalysis) =
            TrackAnalysisEntity(
                songId = songId,
                sizeBytes = sizeBytes,
                lastModified = lastModified,
                version = TrackAnalysis.VERSION,
                durationMs = analysis.durationMs,
                bpm = analysis.bpm,
                beatConfidence = analysis.beatConfidence,
                tempoStable = analysis.tempoStable,
                introDownbeatMs = analysis.introDownbeatMs,
                outroDownbeatMs = analysis.outroDownbeatMs,
                downbeatConfidence = analysis.downbeatConfidence,
                musicalKey = analysis.key,
                keyConfidence = analysis.keyConfidence,
                audioStartMs = analysis.audioStartMs,
                audioEndMs = analysis.audioEndMs,
                introEndMs = analysis.introEndMs,
                outroStartMs = analysis.outroStartMs,
                loudnessDb = analysis.loudnessDb,
            )
    }
}

@Dao
interface TrackAnalysisDao {
    @Query("SELECT * FROM track_analysis WHERE songId = :songId")
    suspend fun get(songId: Long): TrackAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TrackAnalysisEntity)

    @Query("DELETE FROM track_analysis WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteOrphans()
}
