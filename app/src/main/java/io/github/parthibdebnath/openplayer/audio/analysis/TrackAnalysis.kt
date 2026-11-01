package io.github.parthibdebnath.openplayer.audio.analysis

/**
 * What the DJ mix knows about one song, worked out from its audio by [BeatAnalyzer]. Every time is
 * in the song's own media time - the same clock ExoPlayer reports positions on - in milliseconds.
 *
 * The beat grid is a constant tempo: beats fall every [beatPeriodMs], on [introDownbeatMs] plus
 * whole periods near the start and on [outroDownbeatMs] plus whole periods near the end. Two
 * anchors rather than one so a song whose tempo drifts a little over its length still lines up
 * at the end that matters for each side of a mix.
 */
data class TrackAnalysis(
    val durationMs: Double,
    /** Tempo in beats per minute, octave-corrected into the range DJs count in. */
    val bpm: Double,
    /** How clearly the song has a steady beat, 0..1. */
    val beatConfidence: Double,
    /** Whether the beat stays on one grid from start to end (false for live or rubato music). */
    val tempoStable: Boolean,
    /** The first bar line at or after [audioStartMs]. */
    val introDownbeatMs: Double,
    /** The last bar line at or before [audioEndMs]. */
    val outroDownbeatMs: Double,
    /** How sure the bar lines are, 0..1; below about 0.15 they're a guess at which beat is "one". */
    val downbeatConfidence: Double,
    /** 0..11 major keys from C, 12..23 minor keys from C; -1 if none could be told. */
    val key: Int,
    val keyConfidence: Double,
    /** Where the sound starts and ends: everything outside is silence. */
    val audioStartMs: Double,
    val audioEndMs: Double,
    /** Where the song reaches its typical loudness, and where it last drops below it. */
    val introEndMs: Double,
    val outroStartMs: Double,
    /** Typical loudness of the playing part, in dBFS. */
    val loudnessDb: Double,
) {
    val beatPeriodMs: Double get() = 60_000.0 / bpm

    /** Whether beats can be matched against this song with any confidence. */
    val beatmatchable: Boolean get() = tempoStable && beatConfidence >= MIN_BEAT_CONFIDENCE

    companion object {
        /** Bumped whenever the analysis changes, so songs analysed by an older version are redone. */
        const val VERSION = 1

        const val MIN_BEAT_CONFIDENCE = 0.18
    }
}
