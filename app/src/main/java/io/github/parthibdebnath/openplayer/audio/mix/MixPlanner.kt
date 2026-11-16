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
    /**
     * Where (as [progress]) one song's vocals hand over to the other's, when the mix is planned
     * with both songs' stems; null for a mix without them.
     */
    val vocalHandoff: Double? = null,
) {
    /** Whether this mix is meant to be played with stems. */
    val withStems: Boolean get() = vocalHandoff != null

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

    /** Levels and filters at [progress] (see [MixPlan.progress]), with stems if planned with them. */
    fun levels(progress: Double): MixLevels {
        val handoff = vocalHandoff ?: return levelsWithoutStems(progress)
        return when (style) {
            MixStyle.BEATMATCH -> beatmatchStemLevels(progress, beats, handoff, clash = harmonic == false)
            MixStyle.SMOOTH -> smoothStemLevels(progress)
        }
    }

    /** The levels for when the stems can't be used after all: the channel strips' filters only. */
    fun levelsWithoutStems(progress: Double): MixLevels = when (style) {
        MixStyle.BEATMATCH -> beatmatchLevels(progress, beats)
        MixStyle.SMOOTH -> smoothLevels(progress)
    }
}

/** One deck's stem levels, 0..1 each. */
data class StemGains(val drums: Double, val bass: Double, val vocals: Double, val other: Double) {
    companion object {
        val FULL = StemGains(1.0, 1.0, 1.0, 1.0)
        val SILENT = StemGains(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * What the planner can see of a song's stems, over the stretch they cover (see
 * [io.github.parthibdebnath.openplayer.audio.stems.StemTrack]).
 */
interface StemView {
    fun covers(fromMs: Double, toMs: Double): Boolean

    /** How prominent the vocals are over [fromMs, toMs]: their level against the song's, in dB; null outside. */
    fun vocalsDb(fromMs: Double, toMs: Double): Double?
}

/** Both songs' channel settings at one moment of a mix. */
data class MixLevels(
    val outGain: Double,
    val outCutoffHz: Double,
    val inGain: Double,
    val inCutoffHz: Double,
    val outStems: StemGains = StemGains.FULL,
    val inStems: StemGains = StemGains.FULL,
)

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
     * @param outStems the outgoing song's stems around its end, if separated.
     * @param inStems the incoming song's stems around its start, if separated. With both, the mix
     *     is planned around where each song's vocals are, and played stem by stem.
     */
    fun plan(
        out: TrackAnalysis?,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis?,
        maxBlendMs: Int,
        speed: Double,
        outStems: StemView? = null,
        inStems: StemView? = null,
    ): MixPlan? {
        if (out == null || maxBlendMs <= 0 || outDurationMs <= 0) return null
        val safeSpeed = if (speed.isFinite() && speed > 0) speed else 1.0
        if (incoming != null && out.beatmatchable && incoming.beatmatchable) {
            if (outStems != null && inStems != null) {
                planBeatmatch(out, outDurationMs, outPositionMs, incoming, maxBlendMs, safeSpeed, outStems to inStems)
                    ?.let { return it }
            }
            planBeatmatch(out, outDurationMs, outPositionMs, incoming, maxBlendMs, safeSpeed, null)?.let { return it }
        }
        val smooth = planSmooth(out, outDurationMs, outPositionMs, incoming, maxBlendMs, safeSpeed) ?: return null
        // A smooth mix uses stems when both sides of it have been separated.
        if (outStems != null && inStems != null &&
            outStems.covers(smooth.outStartMs - STEM_ALIGN_LEAD_MS, smooth.outEndMs) &&
            inStems.covers(smooth.inStartMs, smooth.inStartMs + (smooth.outEndMs - smooth.outStartMs))
        ) {
            return smooth.copy(vocalHandoff = SMOOTH_VOCAL_HANDOFF)
        }
        return smooth
    }

    private fun planBeatmatch(
        out: TrackAnalysis,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis,
        maxBlendMs: Int,
        speed: Double,
        stems: Pair<StemView, StemView>?,
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
        // Keys that clash are blended quickly, so the clash is brief. With stems the two songs'
        // melodies never sound together (see beatmatchStemLevels), so they can take longer.
        val cap = when {
            distance == null || distance <= 1 -> 64
            stems != null -> 32
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

        if (stems != null) {
            return planWithStems(
                out, outDurationMs, outPositionMs, incoming, stems, maxBeats, cap, inStart, inPreroll,
                prerollOnOut, lead, outBeat, inBeat, outMeet, inMeet, harmonic,
            )
        }

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

    /**
     * A beatmatched mix chosen with both songs' stems to hand: every bar line in the outgoing
     * song's last stretch is tried as the start, for every blend length, and the vocals decide.
     * The mix is played stem by stem, and the moment one singer hands over to the other is put on
     * a bar line where neither is cut off mid-phrase - the outgoing song's vocals done, the
     * incoming song's not yet started - if there is one. Long blends, starting on a phrase (eight
     * bars counted back from the song's last bar line), and leaving little of the outgoing song
     * unplayed all count in a mix's favour.
     */
    private fun planWithStems(
        out: TrackAnalysis,
        outDurationMs: Double,
        outPositionMs: Double,
        incoming: TrackAnalysis,
        stems: Pair<StemView, StemView>,
        maxBeats: Int,
        cap: Int,
        inStart: Double,
        inPreroll: Double,
        prerollOnOut: Double,
        lead: Double,
        outBeat: Double,
        inBeat: Double,
        outMeet: Double,
        inMeet: Double,
        harmonic: Boolean?,
    ): MixPlan? {
        val (outStems, inStems) = stems
        val bar = 4 * outBeat
        val lastDownbeat = out.outroDownbeatMs
        var best: MixPlan? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (beats in BLEND_BEATS) {
            if (beats > maxBeats || beats > cap) continue
            val length = beats * outBeat
            val inLength = beats * inBeat
            if (inStart + inLength > incoming.durationMs || length > outDurationMs * 0.4) continue
            if (!inStems.covers(inStart, inStart + inLength)) continue
            var start = lastDownbeat - length
            var barsBack = 0
            while (start >= outDurationMs * 0.5 && barsBack <= MAX_BARS_BACK) {
                // The outgoing song's stems are lined up with it a little before they're needed.
                if (start - lead >= outPositionMs && outStems.covers(start - STEM_ALIGN_LEAD_MS, start + length)) {
                    val (handoff, vocalCost) = bestHandoff(outStems, inStems, start, length, inStart, inLength, beats)
                    // Music of the outgoing song left unplayed after it's gone.
                    val unplayed = (out.audioEndMs - (start + length) - 2 * bar).coerceAtLeast(0.0)
                    val score = LENGTH_WEIGHT * beats / 64.0 -
                        VOCAL_WEIGHT * vocalCost -
                        UNPLAYED_WEIGHT * unplayed / 30_000.0 +
                        (if (barsBack % 8 == 0) PHRASE_BONUS else if (barsBack % 4 == 0) PHRASE_BONUS / 2 else 0.0) +
                        (if (start >= out.outroStartMs - bar) OUTRO_BONUS else 0.0)
                    if (score > bestScore) {
                        bestScore = score
                        val preroll = start - prerollOnOut
                        best = MixPlan(
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
                            vocalHandoff = handoff,
                        )
                    }
                }
                start -= bar
                barsBack++
            }
        }
        return best
    }

    /**
     * Where in a blend the vocals should hand over, as progress on a bar line between a quarter
     * and three quarters of the way through, and what it costs: the share of the blend in which
     * the outgoing singer would be cut off (still singing after the hand-over) plus the share in
     * which the incoming one would be held back (singing before it).
     */
    internal fun bestHandoff(
        outStems: StemView,
        inStems: StemView,
        outStart: Double,
        length: Double,
        inStart: Double,
        inLength: Double,
        beats: Int,
    ): Pair<Double, Double> {
        val beatsPerBar = 4
        // Per beat: whether each singer is singing.
        val outSinging = BooleanArray(beats) { b ->
            val from = outStart + b * length / beats
            (outStems.vocalsDb(from, from + length / beats) ?: -100.0) > VOCALS_ACTIVE_DB
        }
        val inSinging = BooleanArray(beats) { b ->
            val from = inStart + b * inLength / beats
            (inStems.vocalsDb(from, from + inLength / beats) ?: -100.0) > VOCALS_ACTIVE_DB
        }
        var bestHandoff = 0.5
        var bestCost = Double.MAX_VALUE
        val candidates = (1 until beats / beatsPerBar).map { it * beatsPerBar }.filter { it * 4 >= beats && it * 4 <= 3 * beats }
            .ifEmpty { listOf(beats / 2) }
        for (at in candidates) {
            // The outgoing vocals fade over the bar before the hand-over.
            val cut = (maxOf(0, at - beatsPerBar) until beats).count { outSinging[it] }
            val held = (0 until at).count { inSinging[it] }
            val cost = (cut + held).toDouble() / beats
            // Ties go to the middle, where the basslines swap.
            val tie = abs(at.toDouble() / beats - 0.5) * 1e-3
            if (cost + tie < bestCost) {
                bestCost = cost + tie
                bestHandoff = at.toDouble() / beats
            }
        }
        return bestHandoff to bestCost
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

    /** Vocals louder than this against the whole song count as singing. */
    private const val VOCALS_ACTIVE_DB = -14.0
    private const val SMOOTH_VOCAL_HANDOFF = 0.6
    private const val STEM_ALIGN_LEAD_MS = 3_000.0
    private const val MAX_BARS_BACK = 24
    private const val LENGTH_WEIGHT = 1.0
    private const val VOCAL_WEIGHT = 2.5
    private const val UNPLAYED_WEIGHT = 0.6
    private const val PHRASE_BONUS = 0.3
    private const val OUTRO_BONUS = 0.2

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

/**
 * A DJ's blend with stems, as progress runs 0..1 over the blend's [beats] beats:
 *
 * - the incoming drums come in first (the first quarter), then its music (to half-way), over the
 *   outgoing song still playing in full;
 * - the basslines swap in one beat at half-way, as without stems, but cleanly - the bass itself,
 *   not everything under a filter's cutoff;
 * - the vocals hand over at [handoff]: the outgoing singer faded out over the bar before it, the
 *   incoming one let in from it - so two voices are never heard at once;
 * - the outgoing drums hand over in the third quarter and its music fades out over the second half.
 *
 * With the keys clashing ([clash]), the two songs' music (and vocals) don't overlap at all: the
 * outgoing song's goes by half-way, the incoming song's comes in after, and the drums and the
 * bass swap carry the mix across.
 *
 * Both decks dip a little in the middle, where most of two songs are playing at once.
 */
internal fun beatmatchStemLevels(progress: Double, beats: Int, handoff: Double, clash: Boolean): MixLevels {
    if (progress < 0) {
        return MixLevels(1.0, FLOOR, 1.0, FLOOR, outStems = StemGains.FULL, inStems = StemGains.SILENT)
    }
    val p = progress.coerceIn(0.0, 1.0)
    val beat = if (beats > 0) 1.0 / beats else 0.1
    val bar = 4 * beat
    val swap = smoothstep(((p - (0.5 - beat / 2)) / beat).coerceIn(0.0, 1.0))
    val inDrums = rise(p, 0.0, 0.25)
    val outDrums = 1 - rise(p, 0.5, 0.75)
    val inOther = if (clash) rise(p, 0.5, 0.5 + bar) else rise(p, 0.125, 0.5)
    val outOther = if (clash) 1 - rise(p, 0.5 - bar, 0.5) else 1 - rise(p, 0.5, 1.0)
    val outVocals = 1 - rise(p, handoff - bar, handoff)
    val inVocals = rise(p, handoff, handoff + beat)
    val headroom = 1.0 - STEM_HEADROOM * kotlin.math.sin(PI * p)
    return MixLevels(
        outGain = headroom,
        outCutoffHz = FLOOR,
        inGain = headroom,
        inCutoffHz = FLOOR,
        outStems = StemGains(drums = outDrums, bass = 1 - swap, vocals = outVocals, other = outOther),
        inStems = StemGains(drums = inDrums, bass = swap, vocals = inVocals, other = inOther),
    )
}

/**
 * The unmatched transition with stems: the outgoing song's beat goes first, so two beats that
 * don't line up are never heard together, then its bass, its vocals, and its music fading out
 * last; the incoming song's music comes in from the start under it, its singer once the other
 * has gone, and its drums and bass only after the outgoing ones have left.
 */
internal fun smoothStemLevels(progress: Double): MixLevels {
    if (progress < 0) {
        return MixLevels(1.0, FLOOR, 1.0, FLOOR, outStems = StemGains.FULL, inStems = StemGains.SILENT)
    }
    val p = progress.coerceIn(0.0, 1.0)
    val outStems = StemGains(
        drums = 1 - rise(p, 0.0, 0.35),
        bass = 1 - rise(p, 0.1, 0.45),
        vocals = 1 - rise(p, 0.2, 0.55),
        other = kotlin.math.cos(PI / 2 * rise(p, 0.3, 1.0)),
    )
    val inStems = StemGains(
        drums = rise(p, 0.55, 0.8),
        bass = rise(p, 0.6, 0.85),
        vocals = rise(p, 0.6, 0.7),
        other = kotlin.math.sin(PI / 2 * rise(p, 0.0, 0.6)),
    )
    return MixLevels(1.0, FLOOR, 1.0, FLOOR, outStems = outStems, inStems = inStems)
}

/** 0 before [from], 1 after [to], an equal-power-ish smooth rise between. */
private fun rise(p: Double, from: Double, to: Double): Double {
    if (to <= from) return if (p >= to) 1.0 else 0.0
    return smoothstep(((p - from) / (to - from)).coerceIn(0.0, 1.0))
}

private val FLOOR = MixChannelAudioProcessor.FLOOR_HZ.toDouble()
private const val STEM_HEADROOM = 0.1

private fun smoothstep(t: Double): Double = t * t * (3 - 2 * t)

private fun logLerp(from: Double, to: Double, t: Double): Double = exp(ln(from) + (ln(to) - ln(from)) * t)

private val BASS_KILL = MixChannelAudioProcessor.BASS_KILL_HZ.toDouble()
private const val FADE_OUT_HZ = 600.0
private const val SWEEP_OUT_HZ = 900.0
private const val HEADROOM = 0.16
