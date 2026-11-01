package io.github.parthibdebnath.openplayer.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * How far through a crossfade playback is, 0 to 1, read off how much of the outgoing track is
 * left to play.
 *
 * Both are in the track's own time, so the fade follows the track wherever it goes: faster at a
 * higher speed, held while paused, caught up after the main thread stalls - and always at 1 just
 * as the outgoing track runs out. It used to be counted in 50 ms ticks of wall time instead,
 * which ran behind at speeds over 1x, or whenever a tick came late, and the outgoing track was
 * cut off still part-way up.
 *
 * @param fadeLengthMs how much of the outgoing track the fade spans, measured when it started.
 * @param outgoingRemainingMs how much of the outgoing track is left now.
 * @param previous the progress reported last time; the result never goes back below it, so a
 *   position that wobbles back a few milliseconds can't make the volumes twitch.
 */
fun crossfadeProgress(fadeLengthMs: Long, outgoingRemainingMs: Long, previous: Float = 0f): Float {
    if (fadeLengthMs <= 0L) return 1f
    val remaining = outgoingRemainingMs.coerceIn(0L, fadeLengthMs)
    val progress = 1f - remaining.toFloat() / fadeLengthMs
    return maxOf(progress, previous).coerceIn(0f, 1f)
}

/**
 * How much of the outgoing track a fade of [fadeMs] real time spans at playback [speed] - so the
 * overlap you hear lasts as long as the setting says, whatever the speed.
 */
fun crossfadeMediaLengthMs(fadeMs: Int, speed: Float): Long {
    val safeSpeed = if (speed.isFinite() && speed > 0f) speed else 1f
    return (fadeMs * safeSpeed).toLong()
}

/**
 * The incoming and outgoing tracks' gains at [progress]. Equal-power rather than linear: two
 * linearly-crossfaded tracks sum to a dip in loudness through the middle of the overlap, which is
 * audible as the transition "sagging".
 */
fun crossfadeGains(progress: Float): Pair<Float, Float> {
    val angle = progress.coerceIn(0f, 1f) * (Math.PI / 2)
    return sin(angle).toFloat() to cos(angle).toFloat()
}
