package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.rotate
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import kotlin.math.roundToInt

/** Opacity levels for the expanded next-5 list, earlier songs more opaque, per spec. */
private val ROW_OPACITIES = listOf(1.0f, 0.9f, 0.8f, 0.7f, 0.6f)

private const val EXPAND_DURATION_MS = 260

/** How many songs the expanded list shows. */
private const val VISIBLE_ROWS = 5

/**
 * Every row is exactly this tall, whatever its title. Left to size itself, a row took the height
 * of its text, and titles in scripts the app's font doesn't cover (Japanese, Arabic, emoji) come
 * from fallback fonts with taller lines - so the whole bottom-anchored list shifted a few pixels
 * every time one of them came into view.
 */
private val ROW_HEIGHT = 40.dp

/**
 * The collapsible "next up" list on the Play screen: collapsed shows just the immediate next
 * song with a swap arrow; expanded shows the next 5, each individually swappable, and
 * drag-reorderable.
 */
@Composable
fun UpcomingSongsList(
    upcoming: List<Song>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSwapSlot: (Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drag = remember { UpcomingDragState() }

    // One icon rotated rather than two swapped, so the chevron turns over as the list opens
    // instead of popping between glyphs.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(EXPAND_DURATION_MS),
        label = "chevron",
    )

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // With nothing left to come (the end of the queue) the chevron's space is still kept, so
        // the list's height - and everything anchored to it - doesn't change.
        if (upcoming.isEmpty()) {
            Spacer(Modifier.height(28.dp))
        } else {
            GlassIconButton(
                // The list grows upward, so the chevron points the way it's about to move: up to
                // expand, and flipped over to collapse again.
                icon = Icons.Filled.ExpandLess,
                contentDescription = if (expanded) "Collapse upcoming songs" else "Expand upcoming songs",
                onClick = onToggleExpanded,
                circleSize = 28.dp,
                iconSize = 18.dp,
                modifier = Modifier.rotate(chevronRotation),
            )
        }

        // The immediate next song is always present, so it keeps its place as the list opens and
        // closes rather than being torn down and rebuilt around the animation.
        UpcomingDraggableRow(
            index = 0,
            song = upcoming.getOrNull(0),
            opacity = ROW_OPACITIES[0],
            dragEnabled = expanded && upcoming.isNotEmpty(),
            drag = drag,
            upcomingSize = upcoming.size,
            lastIndex = upcoming.lastIndex.coerceAtMost(VISIBLE_ROWS - 1),
            onSwap = { onSwapSlot(0) },
            onReorder = onReorder,
        )

        // Expands from the bottom edge so the rows unfurl upward, matching where the list sits on
        // screen and which way the chevron points.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(EXPAND_DURATION_MS), expandFrom = Alignment.Bottom) +
                fadeIn(animationSpec = tween(EXPAND_DURATION_MS)),
            exit = shrinkVertically(animationSpec = tween(EXPAND_DURATION_MS), shrinkTowards = Alignment.Bottom) +
                fadeOut(animationSpec = tween(EXPAND_DURATION_MS / 2)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Always the full five rows, blank ones included: near the end of the queue there
                // are fewer songs left, and a shorter list moved everything above it down.
                for (index in 1 until VISIBLE_ROWS) {
                    val song = upcoming.getOrNull(index)
                    UpcomingDraggableRow(
                        index = index,
                        song = song,
                        opacity = ROW_OPACITIES.getOrElse(index) { 0.6f },
                        dragEnabled = song != null,
                        drag = drag,
                        upcomingSize = upcoming.size,
                        lastIndex = upcoming.lastIndex.coerceAtMost(VISIBLE_ROWS - 1),
                        onSwap = { onSwapSlot(index) },
                        onReorder = onReorder,
                    )
                }
            }
        }
    }
}

/**
 * Shared, observable drag state.
 *
 * It has to be a state holder passed by reference, not values passed as parameters: a
 * `pointerInput` block captures its parameters once, when the block is keyed, so plain values read
 * inside the gesture callbacks stay frozen at whatever they were then. Reading them through this
 * object means the callbacks see the live values.
 */
@Stable
private class UpcomingDragState {
    var index by mutableIntStateOf(-1)
    var offsetY by mutableFloatStateOf(0f)
    var rowHeightPx by mutableFloatStateOf(1f)

    fun reset() {
        index = -1
        offsetY = 0f
    }
}

@Composable
private fun UpcomingDraggableRow(
    index: Int,
    song: Song?,
    opacity: Float,
    dragEnabled: Boolean,
    drag: UpcomingDragState,
    upcomingSize: Int,
    lastIndex: Int,
    onSwap: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .onGloballyPositioned { drag.rowHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = if (drag.index == index) drag.offsetY else 0f }
            .then(
                if (!dragEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(upcomingSize, lastIndex) {
                        // The reorder is applied once, on release, rather than continuously during
                        // the drag. Mutating the list mid-drag reshuffled the rows out from under
                        // the finger on every crossing, which is what made the motion flicker; now
                        // the dragged row simply follows the finger and the list settles when it's
                        // let go.
                        detectDragGestures(
                            onDragStart = { drag.index = index },
                            onDragEnd = {
                                val from = drag.index
                                if (from >= 0) {
                                    val to = (from + (drag.offsetY / drag.rowHeightPx).roundToInt())
                                        .coerceIn(0, lastIndex)
                                    if (to != from) onReorder(from, to)
                                }
                                drag.reset()
                            },
                            onDragCancel = { drag.reset() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val current = drag.index
                                if (current >= 0) {
                                    // Clamped to the ends of the list, so dragging past the first
                                    // or last row holds the row there instead of building up
                                    // offset that never unwinds.
                                    val minOffset = -current * drag.rowHeightPx
                                    val maxOffset = (lastIndex - current) * drag.rowHeightPx
                                    drag.offsetY = (drag.offsetY + dragAmount.y).coerceIn(minOffset, maxOffset)
                                }
                            },
                        )
                    }
                },
            ),
    ) {
        // A slot with no song left to fill it stays as blank space of the same height.
        if (song != null) UpcomingRow(song = song, opacity = opacity, onSwap = onSwap)
    }
}

@Composable
private fun UpcomingRow(song: Song, opacity: Float, onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Static by default: a long title stays cut off rather than scrolling. These are
        // glanceable "what's next" entries, and several lines of text animating at once is more
        // distracting than the information is worth. Tapping one scrolls it a few times, so a
        // name that's cut off can still be read. Keyed on the song, because the rows are slots:
        // without it a scroll asked for one song carried on when another moved into its place.
        key(song.id) {
            var scrollRequest by remember { mutableIntStateOf(0) }
            MarqueeText(
                text = song.displayName,
                style = interTextStyle(16, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = opacity),
                scrollRequest = scrollRequest,
                idleOverflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .onPlainTap { scrollRequest++ },
            )
        }
        Spacer(Modifier.width(16.dp))
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Swap for next available",
            onClick = onSwap,
            circleSize = 24.dp,
            iconSize = 16.dp,
            opacity = opacity,
        )
    }
}

/**
 * Calls [onTap] for a quick tap that doesn't move, without consuming anything. A tap detector that
 * consumed the touch would mark it as claimed by a child, which stops the row's drag-to-reorder and
 * the tab swipe from starting on the title; this one only watches. Moving past touch slop, a
 * gesture someone else consumed, or holding past the long-press timeout isn't a tap.
 */
private fun Modifier.onPlainTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (change.isConsumed || (change.position - down.position).getDistance() > slop) {
                return@awaitEachGesture
            }
            if (!change.pressed) {
                if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) onTap()
                return@awaitEachGesture
            }
        }
    }
}
