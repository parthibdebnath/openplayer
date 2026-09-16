package com.musicplayer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.rotate
import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.theme.interTextStyle
import kotlin.math.roundToInt

/** Opacity levels for the expanded next-5 list, earlier songs more opaque, per spec. */
private val ROW_OPACITIES = listOf(1.0f, 0.9f, 0.8f, 0.7f, 0.6f)

private const val EXPAND_DURATION_MS = 260

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
    if (upcoming.isEmpty()) return

    val drag = remember { UpcomingDragState() }

    // One icon rotated rather than two swapped, so the chevron turns over as the list opens
    // instead of popping between glyphs.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(EXPAND_DURATION_MS),
        label = "chevron",
    )

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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

        // The immediate next song is always present, so it keeps its place as the list opens and
        // closes rather than being torn down and rebuilt around the animation.
        UpcomingDraggableRow(
            index = 0,
            song = upcoming[0],
            opacity = ROW_OPACITIES[0],
            dragEnabled = expanded,
            drag = drag,
            upcomingSize = upcoming.size,
            lastIndex = upcoming.lastIndex.coerceAtMost(4),
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
                upcoming.take(5).drop(1).forEachIndexed { offset, song ->
                    val index = offset + 1
                    UpcomingDraggableRow(
                        index = index,
                        song = song,
                        opacity = ROW_OPACITIES.getOrElse(index) { 0.6f },
                        dragEnabled = true,
                        drag = drag,
                        upcomingSize = upcoming.size,
                        lastIndex = upcoming.lastIndex.coerceAtMost(4),
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
    song: Song,
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
        UpcomingRow(song = song, opacity = opacity, onSwap = onSwap)
    }
}

@Composable
private fun UpcomingRow(song: Song, opacity: Float, onSwap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Deliberately static: a long title stays cut off rather than scrolling. These are
        // glanceable "what's next" entries, and several lines of text animating at once is more
        // distracting than the information is worth.
        Text(
            text = song.displayName,
            style = interTextStyle(16, bold = false),
            color = LocalAppColors.current.onBackground.copy(alpha = opacity),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        GlassIconButton(
            icon = Icons.Filled.ArrowForward,
            contentDescription = "Swap for next available",
            onClick = onSwap,
            circleSize = 24.dp,
            iconSize = 16.dp,
            opacity = opacity,
        )
    }
}
