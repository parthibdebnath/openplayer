package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
    /** A title held: its tags are edited. */
    onHoldSong: (Song) -> Unit = {},
) {
    val drag = remember { UpcomingDragState() }
    val scope = rememberCoroutineScope()
    // Read through these from the gesture callbacks, which are created once and would otherwise
    // see the list as it was when the drag began.
    val currentUpcoming by rememberUpdatedState(upcoming)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val lastIndex = upcoming.lastIndex.coerceAtMost(VISIBLE_ROWS - 1)

    // A dropped row stays drawn in its new place until the list comes back in the new order; the
    // offsets then go in the same frame that draws it, so nothing jumps. Clearing them a frame
    // early or late showed the old order for a moment.
    val reorderLanded = drag.awaitingList != null && upcoming != drag.awaitingList
    val moving = drag.index >= 0 && !reorderLanded
    SideEffect { if (reorderLanded) drag.reset() }

    fun drop(cancelled: Boolean) {
        val from = drag.index
        if (from < 0 || drag.dropping) return
        val to = if (cancelled) from else drag.targetIndex()
        drag.dropping = true
        scope.launch {
            // The row glides the rest of the way into its slot instead of snapping there.
            animate(drag.offsetY, (to - from) * drag.rowHeightPx, animationSpec = rowSpring()) { value, _ ->
                drag.offsetY = value
            }
            if (to == from) {
                drag.reset()
                return@launch
            }
            val before = currentUpcoming
            drag.awaitingList = before
            currentOnReorder(from, to)
            // A move the queue turned down never changes the list; don't hold the rows forever.
            delay(REORDER_TIMEOUT_MS)
            if (drag.awaitingList === before) drag.reset()
        }
    }

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
            dragEnabled = expanded && upcoming.isNotEmpty(),
            moving = moving,
            drag = drag,
            lastIndex = lastIndex,
            onSwap = { onSwapSlot(0) },
            onDrop = ::drop,
            onHold = onHoldSong,
        )

        // Expands from the bottom edge so the rows unfurl upward, matching where the list sits on
        // screen and which way the chevron points.
        val expandState = remember { MutableTransitionState(expanded) }
        expandState.targetState = expanded
        AnimatedVisibility(
            visibleState = expandState,
            // Clipped only while opening or closing. The built-in clip stays on all the time,
            // which cut a row off as it was dragged up past the first one.
            modifier = Modifier
                .then(if (expandState.isIdle) Modifier else Modifier.clipToBounds())
                // A row dragged out of these four draws over the first row, not under it.
                .zIndex(if (moving && drag.index >= 1) 1f else 0f),
            enter = expandVertically(animationSpec = tween(EXPAND_DURATION_MS), expandFrom = Alignment.Bottom, clip = false) +
                fadeIn(animationSpec = tween(EXPAND_DURATION_MS)),
            exit = shrinkVertically(animationSpec = tween(EXPAND_DURATION_MS), shrinkTowards = Alignment.Bottom, clip = false) +
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
                        dragEnabled = song != null,
                        moving = moving,
                        drag = drag,
                        lastIndex = lastIndex,
                        onSwap = { onSwapSlot(index) },
                        onDrop = ::drop,
                        onHold = onHoldSong,
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
    private var lastIndex by mutableIntStateOf(0)

    /** Set once the finger is lifted, while the row glides into place. */
    var dropping by mutableStateOf(false)

    /** The list as it was when a move was handed to the queue; cleared when the new order lands. */
    var awaitingList by mutableStateOf<List<Song>?>(null)

    fun start(index: Int, lastIndex: Int) {
        this.index = index
        this.lastIndex = lastIndex
        offsetY = 0f
    }

    /** The slot the dragged row would land in if it were let go now. */
    fun targetIndex(): Int = (index + (offsetY / rowHeightPx).roundToInt()).coerceIn(0, lastIndex)

    fun reset() {
        index = -1
        offsetY = 0f
        dropping = false
        awaitingList = null
    }
}

@Composable
private fun UpcomingDraggableRow(
    index: Int,
    song: Song?,
    dragEnabled: Boolean,
    moving: Boolean,
    drag: UpcomingDragState,
    lastIndex: Int,
    onSwap: () -> Unit,
    onDrop: (cancelled: Boolean) -> Unit,
    onHold: (Song) -> Unit,
) {
    val isDragged = moving && drag.index == index
    // The rows between where the dragged one started and where it's headed step aside by one
    // row, so the gap it will drop into opens up under the finger as it goes.
    val makeRoom = if (!moving || isDragged) {
        0f
    } else {
        val from = drag.index
        val to = drag.targetIndex()
        when (index) {
            in (from + 1)..to -> -drag.rowHeightPx
            in to until from -> drag.rowHeightPx
            else -> 0f
        }
    }
    // Snapped, not animated, once nothing is moving: the offsets have to be gone in the very
    // frame the list is drawn in its new order.
    val roomOffset by animateFloatAsState(
        targetValue = makeRoom,
        animationSpec = if (moving) rowSpring() else snap(),
        label = "makeRoom",
    )
    val offsetY = when {
        isDragged -> drag.offsetY
        moving -> roomOffset
        else -> 0f
    }
    // Faded to the slot it's passing through, so a song doesn't change brightness all at once
    // when the move lands.
    val opacity = opacityAt(index + offsetY / drag.rowHeightPx)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .zIndex(if (isDragged) 1f else 0f)
            .onGloballyPositioned { drag.rowHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = offsetY }
            .then(
                if (!dragEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(lastIndex) {
                        // The reorder is applied once, on release, rather than continuously during
                        // the drag. Mutating the list mid-drag reshuffled the rows out from under
                        // the finger on every crossing, which is what made the motion flicker; now
                        // the dragged row follows the finger, the others make room, and the list
                        // takes the new order once the row has settled into it.
                        detectDragGestures(
                            onDragStart = {
                                // One row at a time, and not while the last move is landing.
                                if (drag.index < 0) drag.start(index, lastIndex)
                            },
                            onDragEnd = { if (drag.index == index) onDrop(false) },
                            onDragCancel = { if (drag.index == index) onDrop(true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (drag.index == index && !drag.dropping) {
                                    // Clamped to the ends of the list, so dragging past the first
                                    // or last row holds the row there instead of building up
                                    // offset that never unwinds.
                                    val minOffset = -index * drag.rowHeightPx
                                    val maxOffset = (lastIndex - index) * drag.rowHeightPx
                                    drag.offsetY = (drag.offsetY + dragAmount.y).coerceIn(minOffset, maxOffset)
                                }
                            },
                        )
                    }
                },
            ),
    ) {
        // A slot with no song left to fill it stays as blank space of the same height.
        if (song != null) UpcomingRow(song = song, opacity = opacity, onSwap = onSwap, onHold = { onHold(song) })
    }
}

/** A row's opacity at a (fractional) slot, blending between the slots either side. */
private fun opacityAt(position: Float): Float {
    val clamped = position.coerceIn(0f, (ROW_OPACITIES.size - 1).toFloat())
    val lower = clamped.toInt()
    val upper = (lower + 1).coerceAtMost(ROW_OPACITIES.lastIndex)
    return ROW_OPACITIES[lower] + (ROW_OPACITIES[upper] - ROW_OPACITIES[lower]) * (clamped - lower)
}

/** Quick and settled, with no overshoot: the rows should feel solid, not springy. */
private fun <T> rowSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

/** How long a dropped row waits for the queue to take the move before giving up on it. */
private const val REORDER_TIMEOUT_MS = 500L

@Composable
private fun UpcomingRow(song: Song, opacity: Float, onSwap: () -> Unit, onHold: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Static by default: a long title stays cut off rather than scrolling. These are
        // glanceable "what's next" entries, and several lines of text animating at once is more
        // distracting than the information is worth. Tapping one scrolls it a few times, so a
        // name that's cut off can still be read; holding one edits its tags. Keyed on the song, because the rows are slots:
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
                    .onTapOrHold(onTap = { scrollRequest++ }, onHold = onHold, holdLabel = "Edit tags"),
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
