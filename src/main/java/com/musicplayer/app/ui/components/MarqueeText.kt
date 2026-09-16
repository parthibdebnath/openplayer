package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

/**
 * A single line of text that, if it doesn't fit its available width, waits briefly, scrolls to
 * reveal the rest, pauses at the end, then snaps back to the start and repeats - per spec, for
 * song/artist/playlist names that overflow their space.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.onBackground,
) {
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var textWidthPx by remember { mutableIntStateOf(0) }

    val overflowPx = (textWidthPx - containerWidthPx).coerceAtLeast(0)
    val offsetPx = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(text, overflowPx) {
        offsetPx.floatValue = 0f
        if (overflowPx <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(START_PAUSE_MS)
            animateOffset(offsetPx, from = 0f, to = -overflowPx.toFloat(), durationMs = (overflowPx * 8).coerceIn(800, 6000))
            kotlinx.coroutines.delay(END_PAUSE_MS)
            // Ease back to the start instead of cutting to it, so the loop doesn't jolt.
            animateOffset(offsetPx, from = -overflowPx.toFloat(), to = 0f, durationMs = RETURN_MS)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { containerWidthPx = it.size.width },
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            modifier = Modifier
                // Without an unbounded width the Box hands the Text its own (clipped) width, so
                // the measured text width always equalled the container width, the overflow was
                // always computed as zero, and long names sat statically cut off at the edge
                // instead of ever scrolling.
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .onGloballyPositioned { textWidthPx = it.size.width }
                .offset { IntOffset(offsetPx.floatValue.toInt(), 0) },
        )
    }
}

private suspend fun animateOffset(state: androidx.compose.runtime.MutableFloatState, from: Float, to: Float, durationMs: Int) {
    val steps = (durationMs / 16).coerceAtLeast(1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        state.floatValue = from + (to - from) * t
        kotlinx.coroutines.delay(16)
    }
}

private const val START_PAUSE_MS = 1000L
private const val END_PAUSE_MS = 1400L
private const val RETURN_MS = 500
