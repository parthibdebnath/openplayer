package io.github.parthibdebnath.openplayer.ui.theme

import android.os.SystemClock
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.pow

/** How long the background and the content colour take to glide to a new colour. */
const val COLOR_TRANSITION_MS = 600

/** Saturation given to a grey or black background when it cycles, which otherwise has no hue to turn. */
private const val GREY_CYCLE_SATURATION = 0.55f

/** The darkest a cycling background goes: a deep colour, rather than black that can't show one. */
private const val MIN_CYCLE_LUMINANCE = 0.04f

/**
 * The brightest a cycling background goes. White is as bright as a colour can be, so held at its
 * brightness the cycle had no room for any hue and stayed white; just below it, it goes round
 * soft pastels, still well on the dark-text side.
 */
private const val MAX_CYCLE_LUMINANCE = 0.8f

/** Below this a picked colour counts as grey, its hue too faint to cycle. */
private const val GREY_SATURATION = 0.15f

/**
 * [base] turned [turns] of the way round the colour wheel, at the same brightness.
 *
 * The brightness kept is relative luminance, the measure [contentColorFor] goes by - so text and
 * icons never flip between white and black part way round, as they would going from a dark blue to
 * an equally saturated yellow. It also keeps the cycle even, rather than flaring up at yellow and
 * green. A grey or black [base] has no hue to turn, so it goes round deep, saturated colours.
 */
fun cycleColor(base: Color, turns: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val grey = hsv[1] < GREY_SATURATION
    val saturation = if (grey) GREY_CYCLE_SATURATION else hsv[1]
    val luminance = base.luminance().coerceIn(MIN_CYCLE_LUMINANCE, MAX_CYCLE_LUMINANCE)
    val hue = ((hsv[0] + turns * 360f) % 360f + 360f) % 360f

    // The hue at full brightness, in linear light, then darkened - or, for a target brighter than
    // the hue can be, lightened towards white - until its luminance is the one wanted.
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
    val r = pure.red.toLinear()
    val g = pure.green.toLinear()
    val b = pure.blue.toLinear()
    val pureLuminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val (lr, lg, lb) = if (luminance <= pureLuminance) {
        val k = luminance / pureLuminance
        Triple(r * k, g * k, b * k)
    } else {
        val t = (luminance - pureLuminance) / (1f - pureLuminance)
        Triple(r + (1f - r) * t, g + (1f - g) * t, b + (1f - b) * t)
    }
    return Color(lr.toSrgb(), lg.toSrgb(), lb.toSrgb())
}

/**
 * The colour content is drawn in on [base]'s background, cycling or not. Every colour of the cycle
 * has the same luminance (see [cycleColor]), so its starting point stands for all of them.
 */
fun contentColorForBackground(base: Color, cycling: Boolean): Color =
    contentColorFor(if (cycling) cycleColor(base, 0f) else base)

/**
 * The background as shown: [base], or with [cycling] on, [base] going round the colour wheel once
 * every [periodSeconds]. Changes glide rather than cut - turning cycling on or off, picking a new
 * colour, the daily colour coming round.
 *
 * The cycle steps a fraction of a degree at a time, a few times a second at most, rather than
 * every frame: each step redraws every screen, and a hue step that small can't be seen. It only
 * runs while the app is on screen, and carries on from where it was when the app comes back.
 */
@Composable
fun rememberCyclingBackground(base: Color, cycling: Boolean, periodSeconds: Int): Color {
    val shown = remember { Animatable(if (cycling) cycleColor(base, 0f) else base) }
    // How far round the wheel the cycle is, kept across speed changes and trips away from the app
    // so neither sends it back to the start.
    val turns = remember { floatArrayOf(0f) }
    val period by rememberUpdatedState(periodSeconds.coerceAtLeast(1))
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(base, cycling) {
        val glide = tween<Color>(COLOR_TRANSITION_MS, easing = FastOutSlowInEasing)
        if (!cycling) {
            turns[0] = 0f
            shown.animateTo(base, glide)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            shown.animateTo(cycleColor(base, turns[0]), glide)
            var last = SystemClock.uptimeMillis()
            while (true) {
                // About a degree a step, between roughly fifteen steps a second and one.
                delay((period * 1000L / 360L).coerceIn(MIN_STEP_MS, MAX_STEP_MS))
                val now = SystemClock.uptimeMillis()
                turns[0] = (turns[0] + (now - last) / 1000f / period) % 1f
                last = now
                shown.snapTo(cycleColor(base, turns[0]))
            }
        }
    }
    return shown.value
}

private const val MIN_STEP_MS = 66L
private const val MAX_STEP_MS = 1000L

private fun Float.toLinear(): Float = if (this <= 0.04045f) this / 12.92f else ((this + 0.055f) / 1.055f).pow(2.4f)

private fun Float.toSrgb(): Float {
    val c = coerceIn(0f, 1f)
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}
