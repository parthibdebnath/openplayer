package io.github.parthibdebnath.openplayer.ui.components

import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalScrollLongTitles
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
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
 * reveal the rest, pauses at the end, then eases back to the start and repeats - per spec, for
 * song/artist/playlist names that overflow their space.
 *
 * Scrolling can be switched off in settings, in which case the text sits still and is simply cut
 * off after however many characters fit.
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
    val scrollingEnabled = LocalScrollLongTitles.current

    val overflowPx = (textWidthPx - containerWidthPx).coerceAtLeast(0)
    // Animated on the frame clock, so it moves in step with the display and stops entirely while
    // the app isn't on screen - a timer-driven loop kept running in the background.
    val offsetPx = remember { Animatable(0f) }

    LaunchedEffect(text, overflowPx, scrollingEnabled) {
        // Reset unconditionally: turning scrolling off mid-scroll has to bring the text back to
        // the start rather than leaving it frozen somewhere in the middle.
        offsetPx.snapTo(0f)
        if (!scrollingEnabled || overflowPx <= 0) return@LaunchedEffect
        val scrollMs = (overflowPx * 8).coerceIn(800, 6000)
        while (true) {
            delay(START_PAUSE_MS)
            offsetPx.animateTo(-overflowPx.toFloat(), tween(scrollMs, easing = LinearEasing))
            delay(END_PAUSE_MS)
            // Ease back to the start instead of cutting to it, so the loop doesn't jolt.
            offsetPx.animateTo(0f, tween(RETURN_MS, easing = FastOutSlowInEasing))
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
                .offset { IntOffset(offsetPx.value.toInt(), 0) },
        )
    }
}

private const val START_PAUSE_MS = 1000L
private const val END_PAUSE_MS = 1400L
private const val RETURN_MS = 500
