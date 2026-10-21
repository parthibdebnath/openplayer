package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** How long a tapped thumb takes to glide to the tapped point. */
internal const val GLIDE_MS = 250

/** How close the reported value has to come to where the thumb was left, as a share of the range. */
private const val SETTLE_TOLERANCE_FRACTION = 0.03f

/** Safety net for a value that never catches up, such as a seek that fails. */
private const val SETTLE_TIMEOUT_MS = 1500L

/**
 * Where a slider's thumb is drawn, kept apart from the value the slider reports.
 *
 * A drag puts the thumb under the finger and leaves it exactly where it was let go. A tap glides it
 * to the tapped point. Either way it then stays there until the reported value has caught up: values
 * arrive late (a seek has to land, a setting has to go through storage and back), and following them
 * straight away made the thumb jump back to the old value and then slide across to the new one.
 */
@Stable
class SliderMotion internal constructor(initial: Float) {
    internal val shown = Animatable(initial)

    /** Where the user put the thumb, while that still overrides the reported value. */
    internal var held by mutableStateOf<Float?>(null)
    internal var glide by mutableStateOf(false)
    internal var dragging by mutableStateOf(false)

    /** The position to draw the thumb at. */
    val value: Float get() = shown.value

    fun dragTo(target: Float) {
        dragging = true
        glide = false
        held = target
    }

    fun release() {
        dragging = false
    }

    fun tapTo(target: Float) {
        dragging = false
        glide = true
        held = target
    }

    fun cancel() {
        dragging = false
        held = null
    }
}

/**
 * @param value the value currently reported, which the thumb follows when the user isn't touching it.
 * @param followSpec how the thumb moves to a newly reported value, for example a preset button
 *   moving several sliders at once, or playback position moving on.
 */
@Composable
fun rememberSliderMotion(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    followSpec: AnimationSpec<Float> = tween(220),
): SliderMotion {
    val motion = remember { SliderMotion(value) }
    val latestValue by rememberUpdatedState(value)
    val latestSpec by rememberUpdatedState(followSpec)

    LaunchedEffect(motion) {
        snapshotFlow {
            val held = motion.held
            when {
                held == null -> Target(latestValue, Motion.FOLLOW)
                motion.glide -> Target(held, Motion.GLIDE)
                else -> Target(held, Motion.SNAP)
            }
        }
            .distinctUntilChanged()
            .collectLatest { target ->
                when (target.motion) {
                    Motion.SNAP -> motion.shown.snapTo(target.value)
                    Motion.GLIDE -> motion.shown.animateTo(target.value, tween(GLIDE_MS, easing = FastOutSlowInEasing))
                    Motion.FOLLOW -> motion.shown.animateTo(target.value, latestSpec)
                }
            }
    }

    val held = motion.held
    val dragging = motion.dragging
    LaunchedEffect(held, dragging) {
        if (held == null || dragging) return@LaunchedEffect
        // A tapped thumb finishes its glide first, even if the value lands sooner.
        if (motion.glide) delay(GLIDE_MS.toLong())
        val tolerance = (range.endInclusive - range.start) * SETTLE_TOLERANCE_FRACTION
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
            snapshotFlow { latestValue }.first { abs(it - held) <= tolerance }
        }
        motion.held = null
    }
    return motion
}

private enum class Motion { SNAP, GLIDE, FOLLOW }

private data class Target(val value: Float, val motion: Motion)
