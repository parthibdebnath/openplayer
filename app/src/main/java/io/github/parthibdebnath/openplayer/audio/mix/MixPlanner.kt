package io.github.parthibdebnath.openplayer.audio.mix

import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class MixStyle {
    /** Tempos matched, beats and bars locked together, basslines swapped half-way. */
    BEATMATCH,

    /**
     * For songs whose beats can't be matched (no steady beat, or tempos too far apart): the
     * outgoing song filtered and faded out over its last moments, the next one faded in from its
     * first sound, with the silences either side skipped.
     */
    SMOOTH,
}

/**
 * How one song hands over to the next. Positions are each song's own media time, in ms.
 *
 * The outgoing song plays on its own until [outRampStartMs], then eases to [outMeetRate] by
 * [outPrerollMs], where the incoming song starts silently from [inPrerollMs] at [inMeetRate] -
 * the same beat rate - so it can be locked onto the beat before anyone hears it. (A negative
 * [inPrerollMs] means the song starts from its beginning after that long a silent hold.) From
 * [outStartMs] (when the incoming song is at [inStartMs]) the two are blended for [beats] beats,
 * until at [outEndMs] the outgoing song has gone.
 */
data class MixPlan(
    val style: MixStyle,
    val outRampStartMs: Double,
    val outPrerollMs: Double,
    val outStartMs: Double,
    val outEndMs: Double,
    val inPrerollMs: Double,
    val inStartMs: Double,
    /** The outgoing song's beat, in its media time; 0 for [MixStyle.SMOOTH]. */
    val outBeatMs: Double,
    /** The incoming song's beat as counted against the outgoing one (halved or doubled if need be). */
    val inBeatMs: Double,
    val outMeetRate: Double,
    val inMeetRate: Double,
    val beats: Int,
    /** Whether the keys sit well together; null when either key is unknown. */
    val harmonic: Boolean?,
) {
    /** How far through the blend the outgoing song is at [outPositionMs]; below 0 before it, 1 at its end. */
    fun progress(outPositionMs: Double): Double = (outPositionMs - outStartMs) / (outEndMs - outStartMs)

    /** The outgoing song's tempo at [outPositionMs]: 1 until the ramp, easing to [outMeetRate]. */
    fun outgoingRate(outPositionMs: Double): Double {
        if (outPositionMs <= outRampStartMs || outPrerollMs <= outRampStartMs) {
            return if (outPositionMs >= outPrerollMs) outMeetRate else 1.0
        }
        val t = ((outPositionMs - outRampStartMs) / (outPrerollMs - outRampStartMs)).coerceIn(0.0, 1.0)
        return 1.0 + (outMeetRate - 1.0) * smoothstep(t)
    }

    /**
     * How far the incoming song's beats are ahead of the outgoing one's, in beats: zero when its
     * bar lines land exactly on the outgoing song's.
     */
    fun beatError(outPositionMs: Double, inPositionMs: Double): Double =
        (inPositionMs - inStartMs) / inBeatMs - (outPositionMs - outStartMs) / outBeatMs

    /** Levels and filters at [progress] (see [MixPlan.progress]). */
    fun levels(progress: Double): MixLevels = when (style) {
        MixStyle.BEATMATCH -> beatmatchLevels(progress, beats)
        MixStyle.SMOOTH -> smoothLevels(progress)
    }
}

/** Both songs' channel settings at one moment of a mix. */
data class MixLevels(val outGain: Double, val outCutoffHz: Double, val inGain: Double, val inCutoffHz: Double)

object MixPlanner {
    /** The most the incoming song's tempo is moved, overall, to match the outgoing one. */
    const val MAX_STRETCH = 0.08

    /** Beats over which the outgoing song eases towards the meeting tempo. */
    const val RAMP_BEATS = 16

    /** Beats the incoming song plays silently first, to be locked onto the beat. */
    const val PREROLL_BEATS = 4

    /** The longest a smooth (unmatched) transition runs, however long the setting. */
    const val SMOOTH_MAX_MS = 8_000

    /** How fast the incoming song's tempo returns to normal afterwards, at most: 0.3% a second. */
    const val GLIDE_RATE_PER_SECOND = 0.003

    /** Beats the return to normal tempo takes at least. */
    const val GLIDE_MIN_BEATS = 16

    private val BLEND_BEATS = intArrayOf(64, 32, 16, 8, 4)

    /**
     * How to mix from the outgoing song into the incoming one, or null if the outgoing song has
     * no analysis (or the setting is off) and a plain crossfade has to do.
     *
     * @param outPositionMs where the outgoing song is now; the plan never starts before there.
     * @param maxBlendMs the longest the overlap may last, in real time (the crossfade setting).
     * @param speed the playback speed setting, which the plan's real-time lengths allow for.
     */
    fun plan(
        out: TrackAnalysis?,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis?,
        maxBlendMs: Int,
        speed: Double,
    ): MixPlan? {
        if (out == null || maxBlendMs <= 0 || outDurationMs <= 0) return null
        val safeSpeed = if (speed.isFinite() && speed > 0) speed else 1.0
        if (incoming != null && out.beatmatchable && incoming.beatmatchable) {
            planBeatmatch(out, outDurationMs, outPositionMs, incoming, maxBlendMs, safeSpeed)?.let { return it }
        }
        return planSmooth(out, outDurationMs, outPositionMs, incoming, maxBlendMs, safeSpeed)
    }

    private fun planBeatmatch(
        out: TrackAnalysis,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis,
        maxBlendMs: Int,
        speed: Double,
    ): MixPlan? {
        val outBeat = out.beatPeriodMs
        // Counted in halves or doubles if that's closer: a 70 BPM song mixes with a 140 BPM one.
        val inBeat = listOf(0.5, 1.0, 2.0).map { incoming.beatPeriodMs * it }.minBy { abs(ln(it / outBeat)) }
        val ratio = inBeat / outBeat
        if (abs(ln(ratio)) > ln(1 + MAX_STRETCH)) return null
        // A small difference is made up by the incoming song alone, so the outgoing one plays on
        // exactly as it is. A bigger one is split, so neither strays far from its own tempo.
        val split = abs(ln(ratio)) > SPLIT_ABOVE
        val outMeet = if (split) 1.0 / sqrt(ratio) else 1.0
        val inMeet = if (split) sqrt(ratio) else ratio

        val distance = camelotDistance(out, incoming)
        val harmonic = distance?.let { it <= 1 }
        // Keys that clash are blended quickly, so the clash is brief.
        val cap = when {
            distance == null || distance <= 1 -> 64
            distance == 2 -> 16
            else -> 8
        }
        val realBeatMs = outBeat / (speed * outMeet)
        // A little over the setting is allowed rather than halving the blend: 32 beats that come
        // to 15.2 s suit a 15 s setting far better than 16.
        val maxBeats = floor(maxBlendMs * (1 + LENGTH_SLACK) / realBeatMs).toInt()
        val bar = 4 * outBeat
        val lastDownbeat = out.outroDownbeatMs
        // From the second bar line when the first is right at the start: the bar before the blend
        // is played silently to lock onto the beat, and there has to be one to play.
        var inStart = incoming.introDownbeatMs.coerceAtLeast(0.0)
        if (inStart < PREROLL_BEATS * inBeat) inStart += 4 * inBeat
        // Below zero when the song's first bar line is too near its start to play into silently:
        // it's then held in silence for that long first (see TempoAudioProcessor.shiftBy), which
        // gives it the same time to be locked onto the beat.
        val inPreroll = max(-MAX_HOLD_MS, inStart - PREROLL_BEATS * inBeat)
        val prerollOnOut = (inStart - inPreroll) / ratio
        val lead = RAMP_BEATS * outBeat + prerollOnOut + LEAD_SAFETY_MS * speed

        for (beats in BLEND_BEATS) {
            if (beats > maxBeats || beats > cap) continue
            val length = beats * outBeat
            // The incoming song must still have that much ahead of it, and the outgoing song must
            // be long enough that mixing out of it leaves most of it played.
            if (inStart + beats * inBeat > incoming.durationMs || length > outDurationMs * 0.4) continue

            // Where the outgoing song's outro begins, to the nearest bar line - mixing from there
            // lays the next song's intro over this one's outro, as a DJ would. Failing that, the
            // blend ends on the last bar line before the music stops.
            val outroBar = lastDownbeat + ((out.outroStartMs - lastDownbeat) / bar).roundToLong() * bar
            val start = if (outroBar >= outDurationMs * 0.5 && outroBar + length <= out.audioEndMs + outBeat / 2) {
                outroBar
            } else {
                lastDownbeat - length
            }
            if (start - lead < outPositionMs) continue
            val preroll = start - prerollOnOut
            return MixPlan(
                style = MixStyle.BEATMATCH,
                outRampStartMs = preroll - RAMP_BEATS * outBeat,
                outPrerollMs = preroll,
                outStartMs = start,
                outEndMs = start + length,
                inPrerollMs = inPreroll,
                inStartMs = inStart,
                outBeatMs = outBeat,
                inBeatMs = inBeat,
                outMeetRate = outMeet,
                inMeetRate = inMeet,
                beats = beats,
                harmonic = harmonic,
            )
        }
        return null
    }

    private fun planSmooth(
        out: TrackAnalysis,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis?,
        maxBlendMs: Int,
        speed: Double,
    ): MixPlan? {
        val length = min(maxBlendMs, SMOOTH_MAX_MS) * speed
        val end = min(out.audioEndMs + END_MARGIN_MS, outDurationMs)
        val start = max(end - length, outPositionMs + LEAD_SAFETY_MS * speed)
        if (end - start < MIN_SMOOTH_MS * speed || end - start > outDurationMs * 0.5) return null
        // Straight in at the first sound, past any silence at the start.
        val inStart = (incoming?.audioStartMs ?: 0.0).coerceIn(0.0, MAX_SKIPPED_INTRO_MS)
        return MixPlan(
            style = MixStyle.SMOOTH,
            outRampStartMs = start,
            outPrerollMs = start,
            outStartMs = start,
            outEndMs = end,
            inPrerollMs = inStart,
            inStartMs = inStart,
            outBeatMs = 0.0,
            inBeatMs = 0.0,
            outMeetRate = 1.0,
            inMeetRate = 1.0,
            beats = 0,
            harmonic = incoming?.let { camelotDistance(out, it) }?.let { it <= 1 },
        )
    }

    /**
     * How long, in real time, the incoming song takes to ease from [fromRate] back to its own
     * tempo once the outgoing one has gone: slowly enough that nobody hears the tempo move.
     */
    fun glideMs(fromRate: Double, beatMs: Double, speed: Double): Double {
        val byRate = abs(fromRate - 1.0) / GLIDE_RATE_PER_SECOND * 1000.0
        val byBeats = if (beatMs > 0) GLIDE_MIN_BEATS * beatMs / speed.coerceAtLeast(0.01) else 0.0
        return max(byRate, byBeats)
    }

    /**
     * Steps apart on the Camelot wheel, as harmonic mixing counts it: 0 the same key, 1 a fifth
     * either way or the relative major/minor. Null when either key isn't known well enough.
     */
    fun camelotDistance(a: TrackAnalysis, b: TrackAnalysis): Int? {
        if (a.key < 0 || b.key < 0 || a.keyConfidence < MIN_KEY_CONFIDENCE || b.keyConfidence < MIN_KEY_CONFIDENCE) return null
        return camelotDistance(a.key, b.key)
    }

    fun camelotDistance(keyA: Int, keyB: Int): Int {
        val (numberA, minorA) = camelot(keyA)
        val (numberB, minorB) = camelot(keyB)
        val around = abs(numberA - numberB).let { min(it, 12 - it) }
        return if (minorA == minorB) around else if (around == 0) 1 else around + 1
    }

    /** A key (0..11 major, 12..23 minor, from C) as its Camelot number and whether it's minor. */
    private fun camelot(key: Int): Pair<Int, Boolean> {
        val minor = key >= 12
        // A minor key sits at the same number as its relative major, three semitones up.
        val majorTonic = if (minor) (key - 12 + 3) % 12 else key
        // Round the circle of fifths, C major being 8.
        val number = (majorTonic * 7 % 12 + 7) % 12 + 1
        return number to minor
    }

    /** The longest the incoming song is held in silence before its start; see [planBeatmatch]. */
    const val MAX_HOLD_MS = 1_500.0

    private const val LENGTH_SLACK = 0.05

    /** Tempo differences up to this (as a log ratio, about 4%) are made up by the incoming song alone. */
    private const val SPLIT_ABOVE = 0.04
    private const val LEAD_SAFETY_MS = 1_500.0
    private const val END_MARGIN_MS = 50.0
    private const val MIN_SMOOTH_MS = 1_500.0
    private const val MAX_SKIPPED_INTRO_MS = 30_000.0
    private const val MIN_KEY_CONFIDENCE = 0.05
}

// --- Curves ---------------------------------------------------------------------------------

/**
 * A DJ's blend: the incoming song brought up to full level over the first half with its bass cut,
 * the basslines swapped over one beat in the middle, then the outgoing song faded and filtered
 * away. Both dip a little through the middle, where two full songs play at once.
 */
internal fun beatmatchLevels(progress: Double, beats: Int): MixLevels {
    if (progress < 0) return MixLevels(1.0, MixChannelAudioProcessor.FLOOR_HZ.toDouble(), 0.0, BASS_KILL)
    val p = progress.coerceIn(0.0, 1.0)
    val headroom = 1.0 - HEADROOM * sin(PI * p)
    val inGain = sin(PI / 2 * (p / 0.5).coerceIn(0.0, 1.0)) * headroom
    val outGain = cos(PI / 2 * ((p - 0.5) / 0.5).coerceIn(0.0, 1.0)) * headroom
    val swapWidth = if (beats > 0) 1.0 / beats else 0.1
    val swap = smoothstep(((p - (0.5 - swapWidth / 2)) / swapWidth).coerceIn(0.0, 1.0))
    val floor = MixChannelAudioProcessor.FLOOR_HZ.toDouble()
    val inCutoff = logLerp(BASS_KILL, floor, swap)
    // After the swap the outgoing song keeps thinning out as it fades, so it leaves cleanly.
    val thinning = smoothstep(((p - 0.75) / 0.25).coerceIn(0.0, 1.0))
    val outCutoff = logLerp(logLerp(floor, BASS_KILL, swap), FADE_OUT_HZ, thinning)
    return MixLevels(outGain, outCutoff, inGain, inCutoff)
}

/** The unmatched transition: equal-power levels, the outgoing song filtered out as it fades. */
internal fun smoothLevels(progress: Double): MixLevels {
    val floor = MixChannelAudioProcessor.FLOOR_HZ.toDouble()
    if (progress < 0) return MixLevels(1.0, floor, 0.0, BASS_KILL)
    val p = progress.coerceIn(0.0, 1.0)
    val outCutoff = logLerp(floor, SWEEP_OUT_HZ, smoothstep(((p - 0.15) / 0.85).coerceIn(0.0, 1.0)))
    val inCutoff = logLerp(BASS_KILL, floor, smoothstep(((p - 0.5) / 0.3).coerceIn(0.0, 1.0)))
    return MixLevels(cos(PI / 2 * p), outCutoff, sin(PI / 2 * p), inCutoff)
}

private fun smoothstep(t: Double): Double = t * t * (3 - 2 * t)

private fun logLerp(from: Double, to: Double, t: Double): Double = exp(ln(from) + (ln(to) - ln(from)) * t)

private val BASS_KILL = MixChannelAudioProcessor.BASS_KILL_HZ.toDouble()
private const val FADE_OUT_HZ = 600.0
private const val SWEEP_OUT_HZ = 900.0
private const val HEADROOM = 0.16
