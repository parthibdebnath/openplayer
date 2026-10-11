package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/** Width of the pill thumb; the touch mapping has to match how it's drawn. */
private val THUMB_WIDTH = 22.dp

/**
 * The thin-track, pill-shaped-thumb slider used throughout the app - Material3's default
 * `Slider` (thick track, circular thumb, ripple) doesn't match the liquid-glass minimal look, so
 * this draws its own track/thumb and handles drag/tap directly.
 *
 * Tapping the track glides the thumb to that point; dragging keeps it under the finger and leaves
 * it where it was let go. See [SliderMotion].
 *
 * @param commitOnRelease report the value once, when the finger lifts, rather than on every move -
 *   for seeking, where each report restarts buffering.
 * @param followSpec how the thumb moves to a value reported from elsewhere.
 */
@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    commitOnRelease: Boolean = false,
    followSpec: AnimationSpec<Float> = tween(220),
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    // Read outside the draw lambda: a DrawScope isn't a composable, so it can't read the local.
    val contentColor = LocalAppColors.current.onBackground
    val motion = rememberSliderMotion(value, valueRange, followSpec)

    // The gesture handler outlives recompositions, so it reads the callbacks through these rather
    // than capturing whichever ones existed when it started - a seek bar's callback closes over
    // the current song's duration, and a stale one seeked within the previous song's length.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    // The thumb is a pill drawn between 0 and (width - thumbWidth), so its centre only travels the
    // inset range. Mapping a touch against the full width therefore put the thumb slightly off the
    // finger, increasingly so toward the ends. This inverts the drawing maths exactly.
    val thumbWidthPx = with(LocalDensity.current) { THUMB_WIDTH.toPx() }

    fun valueAt(x: Float): Float {
        val travel = (widthPx - thumbWidthPx).coerceAtLeast(1f)
        val fraction = ((x - thumbWidthPx / 2f) / travel).coerceIn(0f, 1f)
        val raw = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
        if (steps <= 0) return raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
        val stepped = valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize
        return stepped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(valueRange, steps, commitOnRelease) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // Horizontal slop only: a vertical swipe that starts on a slider is left to scroll
                // the page around it.
                val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                if (dragStart == null) {
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && up.changedToUpIgnoreConsumed() && !up.isConsumed) {
                        up.consume()
                        val tapped = valueAt(down.position.x)
                        motion.tapTo(tapped)
                        currentOnValueChange(tapped)
                        currentOnValueChangeFinished()
                    }
                    return@awaitEachGesture
                }

                var current = valueAt(dragStart.position.x)
                var lastReported: Float? = null
                fun moveTo(next: Float) {
                    current = next
                    motion.dragTo(next)
                    // Stepped sliders produce the same value for many pixels of movement; each
                    // report can mean a storage write, so repeats are skipped.
                    if (!commitOnRelease && next != lastReported) {
                        lastReported = next
                        currentOnValueChange(next)
                    }
                }
                moveTo(current)
                val completed = horizontalDrag(dragStart.id) { change ->
                    change.consume()
                    moveTo(valueAt(change.position.x))
                }
                if (completed) {
                    motion.release()
                    if (commitOnRelease) currentOnValueChange(current)
                    currentOnValueChangeFinished()
                } else {
                    motion.cancel()
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .then(gestureModifier),
    ) {
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val fraction = ((motion.value - valueRange.start) / span).coerceIn(0f, 1f)
        val trackY = size.height / 2f
        val trackStroke = 3.dp.toPx()
        val alpha = if (enabled) 1f else 0.4f
        val thumbWidth = THUMB_WIDTH.toPx()
        val thumbHeight = 13.dp.toPx()
        val thumbX = fraction * (size.width - thumbWidth)
        drawLine(
            color = contentColor.copy(alpha = 0.35f * alpha),
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = contentColor.copy(alpha = 0.9f * alpha),
            start = Offset(0f, trackY),
            end = Offset(thumbX + thumbWidth / 2f, trackY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = contentColor.copy(alpha = alpha),
            topLeft = Offset(thumbX, trackY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbHeight / 2f),
        )
    }
}
