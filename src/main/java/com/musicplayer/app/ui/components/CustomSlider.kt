package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Width of the pill thumb; the touch mapping has to match how it's drawn. */
private val THUMB_WIDTH = 22.dp

private const val SETTLE_TOLERANCE_FRACTION = 0.03f
private const val SETTLE_TIMEOUT_MS = 1500L

/**
 * The thin-track, pill-shaped-thumb slider used throughout the app - Material3's default
 * `Slider` (thick track, circular thumb, ripple) doesn't match the liquid-glass minimal look, so
 * this draws its own track/thumb and handles drag/tap directly.
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
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    // Set while dragging a commit-on-release slider, so the thumb tracks the finger without the
    // reported value having to round-trip first.
    var pendingValue by remember { mutableStateOf<Float?>(null) }
    // Read outside the draw lambda: a DrawScope isn't a composable, so it can't read the local.
    val contentColor = LocalAppColors.current.onBackground

    // While the user is actively dragging, track their finger exactly (no lag). Otherwise -
    // e.g. a preset button jumping several sliders at once - animate smoothly instead of
    // snapping straight to the new value.
    val displayValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDragging) tween(0) else tween(220),
        label = "sliderValue",
    )

    // The thumb is a pill drawn between 0 and (width - thumbWidth), so its centre only travels the
    // inset range. Mapping a touch against the full width therefore put the thumb slightly off the
    // finger, increasingly so toward the ends. This inverts the drawing maths exactly.
    val thumbWidthPx = with(LocalDensity.current) { THUMB_WIDTH.toPx() }

    fun touchToFraction(x: Float): Float {
        val travel = (widthPx - thumbWidthPx).coerceAtLeast(1f)
        return ((x - thumbWidthPx / 2f) / travel).coerceIn(0f, 1f)
    }

    fun fractionToValue(fraction: Float): Float {
        val raw = valueRange.start + fraction.coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start)
        if (steps <= 0) return raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
        val stepped = valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize
        return stepped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    // Hold the thumb where it was released until the reported value catches up with it. Clearing
    // it the moment the seek was requested made the thumb snap back to the old position and then
    // animate across to the new one, because the position only refreshes a few times a second.
    // The timeout is a safety net for a seek that never lands.
    val latestValue by rememberUpdatedState(value)
    LaunchedEffect(pendingValue, isDragging) {
        val pending = pendingValue
        if (pending == null || isDragging) return@LaunchedEffect
        val tolerance = (valueRange.endInclusive - valueRange.start) * SETTLE_TOLERANCE_FRACTION
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
            snapshotFlow { latestValue }.first { abs(it - pending) <= tolerance }
        }
        pendingValue = null
    }

    val dragModifier = if (enabled) {
        Modifier
            .pointerInput(valueRange, steps, commitOnRelease) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, _ ->
                        change.consume()
                        val next = fractionToValue(touchToFraction(change.position.x))
                        if (commitOnRelease) pendingValue = next else onValueChange(next)
                    },
                    onDragEnd = {
                        isDragging = false
                        // pendingValue is deliberately left in place: it's cleared once the
                        // reported value catches up (see below), so the thumb doesn't flick back
                        // to where the song was before the seek has taken effect.
                        pendingValue?.let(onValueChange)
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        pendingValue = null
                    },
                )
            }
            .pointerInput(valueRange, steps) {
                detectTapGestures(
                    onTap = { offset ->
                        val tapped = fractionToValue(touchToFraction(offset.x))
                        if (commitOnRelease) pendingValue = tapped
                        onValueChange(tapped)
                        onValueChangeFinished()
                    },
                )
            }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .height(24.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .then(dragModifier),
    ) {
        val shown = pendingValue ?: displayValue
        val fraction = ((shown - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
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
