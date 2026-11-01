package io.github.parthibdebnath.openplayer.audio.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Works out a song's tempo, beat grid, bar lines, key and shape (where it starts, builds, fades
 * and ends) from its audio: mono, at around 11 kHz, as [AudioDecoder] makes it.
 *
 * Pure computation, no Android, so it's tested on synthetic songs whose answers are known.
 */
object BeatAnalyzer {

    /**
     * @param samples mono audio, -1..1.
     * @param startMs the media time of the first sample: where the decoder's output begins on the
     *   player's clock.
     * @return null if the audio is silent throughout.
     */
    fun analyze(samples: FloatArray, sampleRate: Int, startMs: Double = 0.0): TrackAnalysis? {
        val durationMs = samples.size * 1000.0 / sampleRate
        val shape = shapeOf(samples, sampleRate) ?: return null

        val onsets = onsetEnvelope(samples, sampleRate)
        val beatEnvelope = beatEnvelope(onsets)
        val frameRate = sampleRate.toDouble() / ONSET_HOP
        fun frameToMs(frame: Double) = startMs + (frame * ONSET_HOP + ONSET_WINDOW / 2.0) * 1000.0 / sampleRate
        fun msToFrame(ms: Double) = ((ms - startMs) * sampleRate / 1000.0 - ONSET_WINDOW / 2.0) / ONSET_HOP

        val chroma = chromagram(samples, sampleRate)
        val (key, keyConfidence) = keyOf(chroma.total)

        val audioStartMs = startMs + shape.audioStartMs
        val audioEndMs = startMs + shape.audioEndMs
        fun noBeat() = TrackAnalysis(
            durationMs = durationMs,
            bpm = 120.0,
            beatConfidence = 0.0,
            tempoStable = false,
            introDownbeatMs = audioStartMs,
            outroDownbeatMs = audioEndMs,
            downbeatConfidence = 0.0,
            key = key,
            keyConfidence = keyConfidence,
            audioStartMs = audioStartMs,
            audioEndMs = audioEndMs,
            introEndMs = startMs + shape.introEndMs,
            outroStartMs = startMs + shape.outroStartMs,
            loudnessDb = shape.loudnessDb,
        )

        val first = msToFrame(audioStartMs).toInt().coerceIn(0, beatEnvelope.size)
        val last = msToFrame(audioEndMs).toInt().coerceIn(first, beatEnvelope.size)
        if (last - first < frameRate * MIN_BEAT_SECONDS) return noBeat()

        val coarseBpm = coarseTempo(beatEnvelope, frameRate) ?: return noBeat()
        val grid = fineGrid(beatEnvelope, first, last, 60.0 * frameRate / coarseBpm)
        if (grid.confidence <= 0.0) return noBeat()
        val period = grid.period

        // The grid near each end, where the two sides of a mix happen.
        val windowFrames = (ANCHOR_WINDOW_SECONDS * frameRate).toInt()
        val introPhase = localPhase(beatEnvelope, first, min(last, first + windowFrames), period)
            .takeIf { it.strength >= LOCAL_MIN_STRENGTH }?.offset ?: grid.offset
        val outroPhase = localPhase(beatEnvelope, max(first, last - windowFrames), last, period)
            .takeIf { it.strength >= LOCAL_MIN_STRENGTH }?.offset ?: grid.offset
        val stable = isStable(beatEnvelope, first, last, period, grid.offset, frameRate)

        val barPhase = barPhaseOf(onsets, chroma, grid, first, last, sampleRate)

        // Bar lines: the beat of each local grid whose place on the global grid is "one".
        fun globalIndex(frame: Double) = floor((frame - grid.offset) / period + 0.5).toLong()
        fun isDownbeat(frame: Double) = Math.floorMod(globalIndex(frame) - barPhase.phase, 4L) == 0L
        val tolerance = 0.1 * period
        var introDown = beatAtOrAfter(introPhase, period, first - tolerance)
        while (!isDownbeat(introDown)) introDown += period
        var outroDown = beatAtOrAfter(outroPhase, period, last + tolerance) - period
        while (!isDownbeat(outroDown)) outroDown -= period

        return TrackAnalysis(
            durationMs = durationMs,
            bpm = 60.0 * frameRate / period,
            beatConfidence = grid.confidence,
            tempoStable = stable,
            introDownbeatMs = frameToMs(introDown),
            outroDownbeatMs = frameToMs(outroDown),
            downbeatConfidence = barPhase.confidence,
            key = key,
            keyConfidence = keyConfidence,
            audioStartMs = audioStartMs,
            audioEndMs = audioEndMs,
            introEndMs = startMs + shape.introEndMs,
            outroStartMs = startMs + shape.outroStartMs,
            loudnessDb = shape.loudnessDb,
        )
    }

    // --- Shape: where the sound is, and how loud ------------------------------------------------

    internal class Shape(
        val audioStartMs: Double,
        val audioEndMs: Double,
        val introEndMs: Double,
        val outroStartMs: Double,
        val loudnessDb: Double,
    )

    internal fun shapeOf(samples: FloatArray, sampleRate: Int): Shape? {
        val block = (sampleRate * SHAPE_BLOCK_SECONDS).roundToInt().coerceAtLeast(1)
        val blocks = samples.size / block
        if (blocks == 0) return null
        val power = DoubleArray(blocks) { b ->
            var sum = 0.0
            for (i in b * block until (b + 1) * block) sum += samples[i] * samples[i]
            sum / block
        }
        val db = DoubleArray(blocks) { 10 * log10(power[it] + 1e-12) }
        val peak = db.max()
        if (peak < SILENCE_DB) return null
        val threshold = max(peak - AUDIBLE_RANGE_DB, SILENCE_DB)
        val start = db.indexOfFirst { it >= threshold }
        val end = db.indexOfLast { it >= threshold }
        val blockMs = block * 1000.0 / sampleRate

        // Loudness over a couple of seconds, centred, for the song's build and fade.
        val half = (LOUDNESS_WINDOW_SECONDS / SHAPE_BLOCK_SECONDS / 2).roundToInt()
        val prefix = DoubleArray(blocks + 1)
        for (i in 0 until blocks) prefix[i + 1] = prefix[i] + power[i]
        val smooth = DoubleArray(blocks) { i ->
            val lo = max(0, i - half)
            val hi = min(blocks, i + half + 1)
            10 * log10((prefix[hi] - prefix[lo]) / (hi - lo) + 1e-12)
        }
        val playing = smooth.copyOfRange(start, end + 1).sorted()
        val typical = playing[playing.size / 2]
        val loudEnough = typical - BUILD_RANGE_DB
        val introEnd = (start..end).firstOrNull { smooth[it] >= loudEnough } ?: start
        val outroStart = (end downTo start).firstOrNull { smooth[it] >= loudEnough } ?: end
        return Shape(
            audioStartMs = start * blockMs,
            audioEndMs = (end + 1) * blockMs,
            introEndMs = introEnd * blockMs,
            outroStartMs = (outroStart + 1) * blockMs,
            loudnessDb = typical,
        )
    }

    // --- Onsets --------------------------------------------------------------------------------

    internal class Onsets(val full: DoubleArray, val low: DoubleArray)

    /**
     * How much new sound starts in each ~12 ms frame: the rise in log-magnitude spectrum, summed
     * over all frequencies ([Onsets.full]) and over the kick and bass alone ([Onsets.low]), each
     * with its local average taken off so only the attacks stand out.
     */
    internal fun onsetEnvelope(samples: FloatArray, sampleRate: Int): Onsets {
        val n = ONSET_WINDOW
        val frames = if (samples.size < n) 0 else (samples.size - n) / ONSET_HOP + 1
        val fft = Fft(n)
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val bins = n / 2
        var previous = DoubleArray(bins)
        var current = DoubleArray(bins)
        val binHz = sampleRate.toDouble() / n
        val lowFrom = max(1, (LOW_FROM_HZ / binHz).roundToInt())
        val lowTo = max(lowFrom, (LOW_TO_HZ / binHz).roundToInt())
        val full = DoubleArray(frames)
        val low = DoubleArray(frames)
        val scale = 2.0 / n
        for (f in 0 until frames) {
            val offset = f * ONSET_HOP
            for (i in 0 until n) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            var flux = 0.0
            var lowFlux = 0.0
            for (k in 1 until bins) {
                val magnitude = ln(1.0 + LOG_COMPRESSION * hypot(re[k], im[k]) * scale)
                current[k] = magnitude
                if (f > 0) {
                    val rise = magnitude - previous[k]
                    if (rise > 0) {
                        flux += rise
                        if (k in lowFrom..lowTo) lowFlux += rise
                    }
                }
            }
            full[f] = flux
            low[f] = lowFlux
            val t = previous; previous = current; current = t
        }
        val frameRate = sampleRate.toDouble() / ONSET_HOP
        return Onsets(rectifiedDetrend(full, frameRate), rectifiedDetrend(low, frameRate))
    }

    /**
     * What the beat is tracked on: all onsets, plus the kick and bass's again with extra weight.
     * Broadband sounds - hi-hats, shakers, often on the off-beats - make the biggest spectral
     * changes, and on their own pulled the grid half a beat out; the beat itself lives in the low end.
     */
    internal fun beatEnvelope(onsets: Onsets): DoubleArray {
        val fullMean = onsets.full.average().takeIf { it > 0 } ?: 1.0
        val lowMean = onsets.low.average().takeIf { it > 0 } ?: 1.0
        return DoubleArray(onsets.full.size) { onsets.full[it] / fullMean + LOW_WEIGHT * onsets.low[it] / lowMean }
    }

    private fun rectifiedDetrend(values: DoubleArray, frameRate: Double): DoubleArray {
        val half = (DETREND_SECONDS * frameRate / 2).roundToInt().coerceAtLeast(1)
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return DoubleArray(values.size) { i ->
            val lo = max(0, i - half)
            val hi = min(values.size, i + half + 1)
            max(0.0, values[i] - (prefix[hi] - prefix[lo]) / (hi - lo))
        }
    }

    // --- Tempo ---------------------------------------------------------------------------------

    /**
     * The tempo to within a percent or so, from the onset envelope's autocorrelation: each
     * candidate scored by how strongly the envelope repeats at one, two, three and four beats,
     * with a gentle preference for tempos near 120 to settle which octave it is.
     */
    internal fun coarseTempo(onsets: DoubleArray, frameRate: Double): Double? {
        val acf = autocorrelation(onsets)
        if (acf.isEmpty() || acf[0] <= 0.0) return null
        fun at(lag: Double): Double {
            if (lag >= acf.size - 1) return 0.0
            val i = lag.toInt()
            val t = lag - i
            return (acf[i] * (1 - t) + acf[i + 1] * t) / acf[0]
        }
        fun score(bpm: Double): Double {
            val lag = 60.0 * frameRate / bpm
            return at(lag) + 0.5 * at(2 * lag) + 0.33 * at(3 * lag) + 0.25 * at(4 * lag)
        }
        fun prior(bpm: Double): Double {
            val octaves = log2(bpm / PRIOR_CENTRE_BPM) / PRIOR_WIDTH_OCTAVES
            return exp(-0.5 * octaves * octaves)
        }
        var best = 0.0
        var bestScore = 0.0
        var bpm = SEARCH_MIN_BPM
        while (bpm <= SEARCH_MAX_BPM) {
            val s = max(0.0, score(bpm)) * prior(bpm)
            if (s > bestScore) {
                bestScore = s
                best = bpm
            }
            bpm += 0.25
        }
        if (best == 0.0) return null
        // Into the range DJs count in, choosing between octaves on the evidence.
        val candidates = listOf(best / 2, best, best * 2).filter { it in DJ_MIN_BPM..DJ_MAX_BPM }
        if (candidates.isEmpty()) return best
        return candidates.maxBy { max(0.0, score(it)) * prior(it) }
    }

    private fun autocorrelation(values: DoubleArray): DoubleArray {
        if (values.size < 4) return DoubleArray(0)
        var size = 1
        while (size < values.size * 2) size *= 2
        val mean = values.average()
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        for (i in values.indices) re[i] = values[i] - mean
        val fft = Fft(size)
        fft.transform(re, im)
        for (i in 0 until size) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }
        // The inverse, by conjugating around a forward transform; power is real and symmetric.
        fft.transform(re, im)
        val maxLag = min(values.size, size / 2)
        return DoubleArray(maxLag) { re[it] / size }
    }

    internal class Grid(val period: Double, val offset: Double, val confidence: Double)

    /**
     * The exact period and phase: the period whose pulse the whole song's onsets line up with
     * best - the peak of their Fourier transform near the coarse tempo, which over a few minutes
     * of music pins the period down to a few parts in a hundred thousand - and the phase of that
     * pulse, which is where the beats fall.
     */
    internal fun fineGrid(onsets: DoubleArray, from: Int, to: Int, coarsePeriod: Double): Grid {
        val steps = FINE_STEPS
        val low = coarsePeriod * (1 - FINE_RANGE)
        val high = coarsePeriod * (1 + FINE_RANGE)
        val magnitudes = DoubleArray(steps + 1)
        var bestIndex = 0
        for (s in 0..steps) {
            val period = low + (high - low) * s / steps
            val (re, im) = pulse(onsets, from, to, period, 1)
            val (re2, im2) = pulse(onsets, from, to, period, 2)
            magnitudes[s] = hypot(re, im) + 0.5 * hypot(re2, im2)
            if (magnitudes[s] > magnitudes[bestIndex]) bestIndex = s
        }
        // Between the two neighbouring steps, by the parabola through the three.
        var refined = bestIndex.toDouble()
        if (bestIndex in 1 until steps) {
            val a = magnitudes[bestIndex - 1]
            val b = magnitudes[bestIndex]
            val c = magnitudes[bestIndex + 1]
            val denominator = a - 2 * b + c
            if (denominator < 0) refined += (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5)
        }
        val period = low + (high - low) * refined / steps
        val phase = localPhase(onsets, from, to, period)
        return Grid(period, phase.offset, phase.strength)
    }

    /** The sum of the onsets against a pulse of [harmonic] beats per [period], as (re, im). */
    private fun pulse(onsets: DoubleArray, from: Int, to: Int, period: Double, harmonic: Int): Pair<Double, Double> {
        val step = -2 * PI * harmonic / period
        val stepCos = cos(step)
        val stepSin = sin(step)
        var c = cos(step * from)
        var s = sin(step * from)
        var re = 0.0
        var im = 0.0
        for (n in from until to) {
            val v = onsets[n]
            re += v * c
            im += v * s
            val nc = c * stepCos - s * stepSin
            s = c * stepSin + s * stepCos
            c = nc
            // Renormalised now and then; the rotation otherwise drifts over tens of thousands of steps.
            if ((n and 1023) == 0) {
                val norm = hypot(c, s)
                c /= norm
                s /= norm
            }
        }
        return re to im
    }

    internal class Phase(val offset: Double, val strength: Double)

    /** Where the beats of [period] fall within [from, to), and how strongly the onsets agree. */
    internal fun localPhase(onsets: DoubleArray, from: Int, to: Int, period: Double): Phase {
        if (to <= from) return Phase(0.0, 0.0)
        val (re, im) = pulse(onsets, from, to, period, 1)
        var total = 0.0
        for (n in from until to) total += onsets[n]
        if (total <= 0.0) return Phase(0.0, 0.0)
        // sum(v * e^(-i 2 pi n / P)) peaks for v at n0 + kP with angle -2 pi n0 / P.
        val angle = atan2(im, re)
        val offset = ((-angle / (2 * PI)) * period).mod(period)
        return Phase(offset, hypot(re, im) / total)
    }

    /**
     * Whether the beat keeps to one grid all the way through: in each stretch of the song that
     * has a clear beat, the beats fall within a small fraction of a beat of the whole song's grid.
     */
    private fun isStable(onsets: DoubleArray, from: Int, to: Int, period: Double, offset: Double, frameRate: Double): Boolean {
        val window = (STABILITY_WINDOW_SECONDS * frameRate).toInt()
        val hop = window / 2
        var weightSum = 0.0
        var squared = 0.0
        var worst = 0.0
        var start = from
        while (start + window <= to) {
            val local = localPhase(onsets, start, start + window, period)
            if (local.strength >= LOCAL_MIN_STRENGTH) {
                var deviation = (local.offset - offset) / period
                deviation -= floor(deviation + 0.5)
                weightSum += local.strength
                squared += local.strength * deviation * deviation
                worst = max(worst, abs(deviation))
            }
            start += hop
        }
        if (weightSum == 0.0) return true
        return sqrt(squared / weightSum) <= STABLE_RMS_BEATS && worst <= STABLE_WORST_BEATS
    }

    private fun beatAtOrAfter(phase: Double, period: Double, frame: Double): Double {
        val k = kotlin.math.ceil((frame - phase) / period)
        return phase + k * period
    }

    // --- Bars ----------------------------------------------------------------------------------

    internal class BarPhase(val phase: Long, val confidence: Double)

    /**
     * Which beat of four is "one": the beat that most often carries the strongest kick and bass
     * attack, and where the harmony most often changes.
     */
    private fun barPhaseOf(onsets: Onsets, chroma: Chroma, grid: Grid, first: Int, last: Int, sampleRate: Int): BarPhase {
        val period = grid.period
        val firstIndex = kotlin.math.ceil((first - grid.offset) / period).toLong()
        val lastIndex = floor((last - grid.offset) / period).toLong()
        val count = (lastIndex - firstIndex + 1).toInt()
        if (count < 16) return BarPhase(0, 0.0)

        fun peakNear(values: DoubleArray, frame: Double): Double {
            val centre = frame.roundToInt()
            var best = 0.0
            for (i in centre - 2..centre + 2) if (i in values.indices) best = max(best, values[i])
            return best
        }
        val low = DoubleArray(count)
        val full = DoubleArray(count)
        val beatChroma = Array(count) { DoubleArray(12) }
        val chromaFramesPerOnsetFrame = ONSET_HOP.toDouble() / CHROMA_HOP
        for (b in 0 until count) {
            val frame = grid.offset + (firstIndex + b) * period
            low[b] = peakNear(onsets.low, frame)
            full[b] = peakNear(onsets.full, frame)
            // Chroma frames whose centres fall in this beat.
            val from = ((frame * ONSET_HOP + ONSET_WINDOW / 2.0 - CHROMA_WINDOW / 2.0) / CHROMA_HOP)
            val to = from + period * chromaFramesPerOnsetFrame
            for (c in max(0, kotlin.math.ceil(from).toInt()) until min(chroma.frames.size, kotlin.math.ceil(to).toInt())) {
                val v = chroma.frames[c]
                for (p in 0 until 12) beatChroma[b][p] += v[p]
            }
        }
        val novelty = DoubleArray(count) { b ->
            if (b < 2 || b + 1 >= count) 0.0 else {
                val before = DoubleArray(12) { beatChroma[b - 2][it] + beatChroma[b - 1][it] }
                val after = DoubleArray(12) { beatChroma[b][it] + beatChroma[b + 1][it] }
                1.0 - cosine(before, after)
            }
        }
        val score = DoubleArray(count)
        val zl = zScores(low)
        val zn = zScores(novelty)
        val zf = zScores(full)
        for (b in 0 until count) score[b] = zl[b] + 0.7 * zn[b] + 0.3 * zf[b]
        val sums = DoubleArray(4)
        val counts = IntArray(4)
        for (b in 0 until count) {
            val m = Math.floorMod(firstIndex + b, 4L).toInt()
            sums[m] += score[b]
            counts[m]++
        }
        val means = DoubleArray(4) { if (counts[it] == 0) 0.0 else sums[it] / counts[it] }
        val order = means.indices.sortedByDescending { means[it] }
        val confidence = (means[order[0]] - means[order[1]]).coerceIn(0.0, 1.0)
        return BarPhase(order[0].toLong(), confidence)
    }

    private fun zScores(values: DoubleArray): DoubleArray {
        val mean = values.average()
        var variance = 0.0
        for (v in values) variance += (v - mean) * (v - mean)
        val sd = sqrt(variance / values.size).takeIf { it > 1e-12 } ?: return DoubleArray(values.size)
        return DoubleArray(values.size) { (values[it] - mean) / sd }
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na <= 0 || nb <= 0) 1.0 else dot / sqrt(na * nb)
    }

    // --- Key -----------------------------------------------------------------------------------

    internal class Chroma(val frames: Array<DoubleArray>, val total: DoubleArray)

    /** How much of each of the twelve pitch classes sounds in each ~90 ms, and over the song. */
    internal fun chromagram(samples: FloatArray, sampleRate: Int): Chroma {
        val n = CHROMA_WINDOW
        val frames = if (samples.size < n) 0 else (samples.size - n) / CHROMA_HOP + 1
        val fft = Fft(n)
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val binHz = sampleRate.toDouble() / n
        val pitchClass = IntArray(n / 2) { k ->
            val hz = k * binHz
            if (hz < CHROMA_FROM_HZ || hz > CHROMA_TO_HZ) -1 else Math.floorMod((12 * log2(hz / 440.0) + 69).roundToInt(), 12)
        }
        val result = Array(frames) { DoubleArray(12) }
        val total = DoubleArray(12)
        for (f in 0 until frames) {
            val offset = f * CHROMA_HOP
            for (i in 0 until n) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            val frame = result[f]
            for (k in 1 until n / 2) {
                val pc = pitchClass[k]
                if (pc >= 0) frame[pc] += sqrt(hypot(re[k], im[k]))
            }
            for (p in 0 until 12) total[p] += frame[p]
        }
        return Chroma(result, total)
    }

    /** The key whose Krumhansl-Kessler profile the song's pitch classes match best, and by how much. */
    internal fun keyOf(chroma: DoubleArray): Pair<Int, Double> {
        if (chroma.sum() <= 0.0) return -1 to 0.0
        val scores = DoubleArray(24)
        for (tonic in 0 until 12) {
            scores[tonic] = correlation(chroma, MAJOR_PROFILE, tonic)
            scores[12 + tonic] = correlation(chroma, MINOR_PROFILE, tonic)
        }
        val order = scores.indices.sortedByDescending { scores[it] }
        return order[0] to (scores[order[0]] - scores[order[1]]).coerceAtLeast(0.0)
    }

    private fun correlation(chroma: DoubleArray, profile: DoubleArray, tonic: Int): Double {
        val meanC = chroma.average()
        val meanP = profile.average()
        var dot = 0.0
        var nc = 0.0
        var np = 0.0
        for (i in 0 until 12) {
            val c = chroma[(i + tonic) % 12] - meanC
            val p = profile[i] - meanP
            dot += c * p
            nc += c * c
            np += p * p
        }
        return if (nc <= 0 || np <= 0) 0.0 else dot / sqrt(nc * np)
    }

    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    // --- Tuning --------------------------------------------------------------------------------

    internal const val ONSET_WINDOW = 512
    internal const val ONSET_HOP = 128
    private const val CHROMA_WINDOW = 4096
    private const val CHROMA_HOP = 1024
    private const val LOG_COMPRESSION = 100.0
    private const val LOW_FROM_HZ = 30.0
    private const val LOW_TO_HZ = 180.0
    private const val CHROMA_FROM_HZ = 80.0
    private const val CHROMA_TO_HZ = 2_000.0
    private const val DETREND_SECONDS = 0.5
    private const val LOW_WEIGHT = 1.5

    private const val SHAPE_BLOCK_SECONDS = 0.05
    private const val SILENCE_DB = -70.0
    private const val AUDIBLE_RANGE_DB = 45.0
    private const val LOUDNESS_WINDOW_SECONDS = 2.0
    private const val BUILD_RANGE_DB = 4.0

    private const val MIN_BEAT_SECONDS = 15.0
    private const val SEARCH_MIN_BPM = 50.0
    private const val SEARCH_MAX_BPM = 220.0
    private const val DJ_MIN_BPM = 70.0
    private const val DJ_MAX_BPM = 180.0
    private const val PRIOR_CENTRE_BPM = 120.0
    private const val PRIOR_WIDTH_OCTAVES = 0.8
    private const val FINE_RANGE = 0.015
    private const val FINE_STEPS = 600

    private const val ANCHOR_WINDOW_SECONDS = 45.0
    private const val LOCAL_MIN_STRENGTH = 0.12
    private const val STABILITY_WINDOW_SECONDS = 20.0
    private const val STABLE_RMS_BEATS = 0.06
    private const val STABLE_WORST_BEATS = 0.15
}
