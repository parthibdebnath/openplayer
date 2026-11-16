@file:OptIn(UnstableApi::class)

package io.github.parthibdebnath.openplayer.audio.stems

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * One deck's stem mixer for the DJ mix: with a song's stems to hand (see [StemTrack]), turns its
 * drums, bass, vocals and everything else up and down independently, as a DJ with stems does -
 * the next song's drums in under this one's vocals, the basslines swapped, one singer handing
 * over to the next.
 *
 * It works by difference: the output is the song as played, plus each stem times how far its
 * level is from the rest's. With every level at 1 that's the song untouched, to the bit - so it
 * only changes anything while a mix is actually moving a level, and the separation's own small
 * imperfections are only ever heard in a blend, under the other song.
 *
 * That only works with the stems on exactly the samples they were made from: one sample out and
 * subtracting the drums leaves a phasey ghost of them. So before using a track it lines it up by
 * matching the audio coming through against the track's own copy of the song, to the sample.
 * The renderer's buffer times ([noteBufferTime]) say roughly where to look; the match finds the
 * exact frame. Until it has (or if it can't - another decoder, a damaged file) the song passes
 * through untouched and [alignedRegion] stays null, so the engine falls back to a stemless mix.
 *
 * First in the chain, so it sees the decoder's own samples. 16-bit PCM, one or two channels.
 */
class StemMixAudioProcessor : BaseAudioProcessor() {

    /** The stems of whatever song this deck has, or none. Set from the main thread. */
    @Volatile var tracks: List<StemTrack> = emptyList()

    // Target levels, 0..1. Set from the main thread.
    @Volatile var drumsGain = 1f
    @Volatile var bassGain = 1f
    @Volatile var vocalsGain = 1f
    @Volatile var otherGain = 1f

    @Volatile var forceBypass = false

    /** The region of the track the audio is lined up with at the moment, or null. */
    @Volatile var alignedRegion: StemRegion? = null
        private set

    /** Which song's tracks the engine attached, so it can tell when they need replacing. */
    @Volatile var attachedSongId: Long? = null

    private var supported = false
    private var channels = 2
    private var sampleRate = 44_100

    // The clock: media time of the first frame since the last flush, and frames since.
    @Volatile private var notedTimeUs = C.TIME_UNSET
    private var anchorUs = C.TIME_UNSET
    private var framesSinceAnchor = 0L

    // Lining up with one track.
    private var alignTrack: StemTrack? = null
    private var aligned = false

    /** Once lined up: the track's frame index is this plus [framesSinceAnchor]. */
    private var indexOffset = 0L
    private var probeStartFrames = 0L
    private var failed = HashSet<StemTrack>()
    private val probe = IntArray(PROBE_FRAMES)
    private var probeCount = 0
    private var probeEstimate = 0L

    // Applied levels, gliding towards the targets.
    private var gD = 1.0
    private var gB = 1.0
    private var gV = 1.0
    private var gO = 1.0

    /** The media time of the buffer the renderer is about to hand over. Playback thread. */
    fun noteBufferTime(presentationTimeUs: Long) {
        notedTimeUs = presentationTimeUs
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..2
        if (!supported) return AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val start = inputBuffer.position()
        val frames = inputBuffer.remaining() / (2 * channels)
        if (anchorUs == C.TIME_UNSET) {
            anchorUs = notedTimeUs
            framesSinceAnchor = 0L
        }
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val list = tracks
        if (forceBypass || !supported || list.isEmpty() || anchorUs == C.TIME_UNSET) {
            output.put(inputBuffer)
            output.flip()
            framesSinceAnchor += frames
            if (list.isEmpty()) resetAlignment()
            return
        }
        try {
            process(inputBuffer, output, list)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed; bypassing permanently", t)
            forceBypass = true
            alignedRegion = null
            inputBuffer.position(start)
            output.clear()
            output.put(inputBuffer)
            output.flip()
        }
    }

    /** Where in [track] the frame [framesSinceAnchor] frames in is thought to be, before lining up. */
    private fun estimatedIndex(track: StemTrack): Long {
        val timeMs = anchorUs / 1000.0 + framesSinceAnchor * 1000.0 / sampleRate
        return ((timeMs - track.startMs) * track.sampleRate / 1000.0).roundToLong()
    }

    private fun trackAt(list: List<StemTrack>): StemTrack? {
        val timeMs = anchorUs / 1000.0 + framesSinceAnchor * 1000.0 / sampleRate
        return list.firstOrNull { it.sampleRate == sampleRate && it !in failed && timeMs >= it.startMs - 200 && timeMs < it.endMs - 200 }
    }

    private fun process(input: ByteBuffer, output: ByteBuffer, list: List<StemTrack>) {
        val stereo = channels == 2
        val smoothing = 1.0 - exp(-1.0 / (SMOOTHING_SECONDS * sampleRate))
        val tD = drumsGain.toDouble().coerceIn(0.0, 1.0)
        val tB = bassGain.toDouble().coerceIn(0.0, 1.0)
        val tV = vocalsGain.toDouble().coerceIn(0.0, 1.0)
        val tO = otherGain.toDouble().coerceIn(0.0, 1.0)

        while (input.remaining() >= 2 * channels) {
            // Which track (if any) this frame falls in, and lining up with it.
            val current = alignTrack
            if (current == null || current !in list) {
                val next = trackAt(list)
                if (next !== current) startAligning(next)
            }
            val track = alignTrack
            val l = input.getShort()
            val r = if (stereo) input.getShort() else l
            val index = if (track != null && aligned) indexOffset + framesSinceAnchor else -1L
            if (track != null && !aligned) feedProbe(track, l, r)
            framesSinceAnchor++

            gD += (tD - gD) * smoothing
            gB += (tB - gB) * smoothing
            gV += (tV - gV) * smoothing
            gO += (tO - gO) * smoothing
            val untouched = abs(gD - 1) < 1e-4 && abs(gB - 1) < 1e-4 && abs(gV - 1) < 1e-4 && abs(gO - 1) < 1e-4

            if (track == null || !aligned || index < 0 || index >= track.frames || untouched) {
                if (track != null && aligned && index >= track.frames) {
                    // Past its end: the next track, if there is one, has to be lined up afresh.
                    alignTrack = null
                    aligned = false
                    alignedRegion = null
                }
                output.putShort(l)
                if (stereo) output.putShort(r)
                continue
            }
            val data = track.data
            val base = (index * StemTrack.FRAME_SHORTS).toInt()
            val dD = gD - gO
            val dB = gB - gO
            val dV = gV - gO
            val outL = gO * l + dD * data.get(base + 2) + dB * data.get(base + 4) + dV * data.get(base + 6)
            output.putShort(clip(outL))
            if (stereo) {
                val outR = gO * r + dD * data.get(base + 3) + dB * data.get(base + 5) + dV * data.get(base + 7)
                output.putShort(clip(outR))
            }
        }
        if (abs(gD - tD) < 1e-5) gD = tD
        if (abs(gB - tB) < 1e-5) gB = tB
        if (abs(gV - tV) < 1e-5) gV = tV
        if (abs(gO - tO) < 1e-5) gO = tO
        while (input.hasRemaining()) output.put(input.get())
        output.flip()
    }

    private fun startAligning(track: StemTrack?) {
        alignTrack = track
        aligned = false
        alignedRegion = null
        probeCount = 0
    }

    private fun feedProbe(track: StemTrack, l: Short, r: Short) {
        if (probeCount == 0) {
            probeEstimate = estimatedIndex(track)
            probeStartFrames = framesSinceAnchor
        }
        probe[probeCount++] = l + r
        if (probeCount < PROBE_FRAMES) return
        probeCount = 0
        var energy = 0L
        for (v in probe) energy += abs(v)
        // Near-silence can't be matched; try again further on.
        if (energy / PROBE_FRAMES < MIN_PROBE_LEVEL) return
        val found = findLag(track, probeEstimate)
        if (found == null) {
            val tries = (attempts[track] ?: 0) + 1
            attempts[track] = tries
            if (tries >= MAX_ATTEMPTS) {
                Log.w(TAG, "Could not line up ${track.region} stems with the audio; mixing without them")
                failed.add(track)
                alignTrack = null
            }
            return
        }
        // Counted in whole frames from here on, so it can never drift by rounding.
        indexOffset = probeEstimate + found - probeStartFrames
        aligned = true
        alignedRegion = track.region
        Log.i(TAG, "Lined up ${track.region} stems, ${found} frames from the estimate")
    }

    private val attempts = HashMap<StemTrack, Int>()

    /**
     * The offset from [estimate] at which [track]'s copy of the song matches the probe exactly,
     * or null. Coarse over every candidate with a sparse comparison, then exactly around the best.
     */
    private fun findLag(track: StemTrack, estimate: Long): Long? {
        val data = track.data
        fun stored(index: Long): Int {
            val base = (index * StemTrack.FRAME_SHORTS).toInt()
            return data.get(base) + data.get(base + 1)
        }
        val lowest = maxOf(-SEARCH_FRAMES.toLong(), -estimate)
        val highest = minOf(SEARCH_FRAMES.toLong(), track.frames - PROBE_FRAMES - estimate)
        if (lowest > highest) return null
        var best = 0L
        var bestError = Long.MAX_VALUE
        var lagCandidate = lowest
        while (lagCandidate <= highest) {
            var error = 0L
            var i = 0
            val at = estimate + lagCandidate
            while (i < PROBE_FRAMES) {
                val d = (stored(at + i) - probe[i]).toLong()
                error += d * d
                if (error >= bestError) break
                i += COARSE_STEP
            }
            if (error < bestError) {
                bestError = error
                best = lagCandidate
            }
            lagCandidate++
        }
        var exact = best
        var exactError = Long.MAX_VALUE
        for (candidate in maxOf(lowest, best - REFINE)..minOf(highest, best + REFINE)) {
            var error = 0L
            val at = estimate + candidate
            for (i in 0 until PROBE_FRAMES) {
                val d = (stored(at + i) - probe[i]).toLong()
                error += d * d
            }
            if (error < exactError) {
                exactError = error
                exact = candidate
            }
        }
        var energy = 0L
        for (v in probe) energy += v.toLong() * v
        // Exact up to rounding: a different decoder, or the wrong file, won't come close.
        if (exactError > energy * MAX_MATCH_ERROR) {
            Log.d(TAG, "No match: best error ${exactError.toDouble() / energy} of the signal")
            return null
        }
        return exact
    }

    private fun resetAlignment() {
        alignTrack = null
        aligned = false
        alignedRegion = null
        probeCount = 0
    }

    private fun clip(v: Double): Short = v.toInt().coerceIn(-32768, 32767).toShort()

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        anchorUs = C.TIME_UNSET
        framesSinceAnchor = 0L
        resetAlignment()
        failed = HashSet()
        attempts.clear()
        // Nothing either side of a flush to glide across.
        gD = drumsGain.toDouble().coerceIn(0.0, 1.0)
        gB = bassGain.toDouble().coerceIn(0.0, 1.0)
        gV = vocalsGain.toDouble().coerceIn(0.0, 1.0)
        gO = otherGain.toDouble().coerceIn(0.0, 1.0)
    }

    override fun onReset() {
        onFlush(AudioProcessor.StreamMetadata.DEFAULT)
        supported = false
    }

    /** Sets all four target levels. */
    fun setLevels(levels: io.github.parthibdebnath.openplayer.audio.mix.StemGains) {
        drumsGain = levels.drums.toFloat()
        bassGain = levels.bass.toFloat()
        vocalsGain = levels.vocals.toFloat()
        otherGain = levels.other.toFloat()
    }

    /** Back to plain playback: every level at 1. The tracks are left for the engine to manage. */
    fun resetLevels() {
        drumsGain = 1f
        bassGain = 1f
        vocalsGain = 1f
        otherGain = 1f
    }

    private companion object {
        const val TAG = "StemMix"
        const val SMOOTHING_SECONDS = 0.015
        const val PROBE_FRAMES = 4096

        /** How far the buffer times may be out: a FLAC frame, or an MP3's priming samples. */
        const val SEARCH_FRAMES = 6000
        const val COARSE_STEP = 16
        const val REFINE = 24

        /** Mean |L+R| below which a probe is too quiet to match (about -60 dB). */
        const val MIN_PROBE_LEVEL = 60
        const val MAX_MATCH_ERROR = 1e-4
        const val MAX_ATTEMPTS = 6
    }
}
