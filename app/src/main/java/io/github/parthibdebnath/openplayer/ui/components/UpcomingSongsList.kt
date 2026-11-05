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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
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
 *
 * Swiping a row to the right undoes its swap arrow: the row's song slides off to the right,
 * uncovering the song the arrow pushed out, which takes the row back once let go far enough.
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
    /** For each row of [upcoming], the song a swipe to the right brings back, or null. */
    swapBacks: List<Song?> = emptyList(),
    /** A row swiped to the right far enough: the song it showed, to check the list hasn't moved on. */
    onSwapBack: (index: Int, songId: Long) -> Unit = { _, _ -> },
    /**
     * How far the rows reach past the list's own sides - the screen's gutter - so a swipe started
     * right at the edge of the screen lands on a row rather than the margin beside it. The songs
     * are still drawn within the list's own width.
     */
    edgeReach: Dp = 0.dp,
) {
    val drag = remember { UpcomingDragState() }
    val swipe = remember { SwipeBackState() }
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

    // The same for a swipe: the row stays slid out, the song coming back drawn in its place, until
    // the list has it there, and in that frame the row is drawn as it is again.
    val swipeLanded = swipe.awaitingSongId != null && upcoming.getOrNull(swipe.index)?.id != swipe.awaitingSongId
    val swipingIndex = if (swipe.index >= 0 && !swipeLanded) swipe.index else -1
    SideEffect { if (swipeLanded) swipe.reset() }
    val currentOnSwapBack by rememberUpdatedState(onSwapBack)

    fun releaseSwipe(commit: Boolean) {
        if (swipe.index < 0 || swipe.settling) return
        swipe.settling = true
        scope.launch {
            if (!commit) {
                animate(swipe.offsetX, 0f, animationSpec = rowSpring()) { value, _ -> swipe.offsetX = value }
                swipe.reset()
                return@launch
            }
            // Carried on the rest of the way, so the song coming back ends exactly where the row
            // will draw it.
            animate(swipe.offsetX, swipe.rowWidthPx, animationSpec = rowSpring()) { value, _ -> swipe.offsetX = value }
            val songId = swipe.songId
            swipe.awaitingSongId = songId
            currentOnSwapBack(swipe.index, songId)
            // Turned down (the song played or went in the meantime): the row slides back.
            delay(REORDER_TIMEOUT_MS)
            if (swipe.awaitingSongId == songId && swipe.index >= 0) {
                swipe.awaitingSongId = null
                animate(swipe.offsetX, 0f, animationSpec = rowSpring()) { value, _ -> swipe.offsetX = value }
                swipe.reset()
            }
        }
    }

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

    Column(modifier = modifier.fillMaxWidth().reachPastSides(edgeReach), horizontalAlignment = Alignment.CenterHorizontally) {
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
            swapBack = swapBacks.getOrNull(0),
            dragEnabled = expanded && upcoming.isNotEmpty(),
            moving = moving,
            drag = drag,
            swipe = swipe,
            swiping = swipingIndex == 0,
            lastIndex = lastIndex,
            onSwap = { onSwapSlot(0) },
            onDrop = ::drop,
            onSwipeEnd = ::releaseSwipe,
            onSwapBack = { currentOnSwapBack(0, it) },
            onHold = onHoldSong,
            edgeReach = edgeReach,
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
                        swapBack = swapBacks.getOrNull(index),
                        dragEnabled = song != null,
                        moving = moving,
                        drag = drag,
                        swipe = swipe,
                        swiping = swipingIndex == index,
                        lastIndex = lastIndex,
                        onSwap = { onSwapSlot(index) },
                        onDrop = ::drop,
                        onSwipeEnd = ::releaseSwipe,
                        onSwapBack = { currentOnSwapBack(index, it) },
                        onHold = onHoldSong,
                        edgeReach = edgeReach,
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

/** A row being swiped to the right to bring back the song its swap arrow pushed out. */
@Stable
private class SwipeBackState {
    /** The row being swiped, or -1. */
    var index by mutableIntStateOf(-1)
    var offsetX by mutableFloatStateOf(0f)
    var rowWidthPx by mutableFloatStateOf(1f)

    /** The song the row showed when the swipe began. */
    var songId = 0L

    /** Let go, and gliding back or the rest of the way out. */
    var settling by mutableStateOf(false)

    /** Handed to the queue; cleared once the row shows another song. */
    var awaitingSongId by mutableStateOf<Long?>(null)

    fun start(index: Int, songId: Long) {
        this.index = index
        this.songId = songId
        offsetX = 0f
        settling = false
        awaitingSongId = null
    }

    fun reset() {
        index = -1
        offsetX = 0f
        settling = false
        awaitingSongId = null
    }
}

@Composable
private fun UpcomingDraggableRow(
    index: Int,
    song: Song?,
    swapBack: Song?,
    dragEnabled: Boolean,
    moving: Boolean,
    drag: UpcomingDragState,
    swipe: SwipeBackState,
    swiping: Boolean,
    lastIndex: Int,
    onSwap: () -> Unit,
    onDrop: (cancelled: Boolean) -> Unit,
    onSwipeEnd: (commit: Boolean) -> Unit,
    onSwapBack: (songId: Long) -> Unit,
    onHold: (Song) -> Unit,
    edgeReach: Dp,
) {
    // The gesture is set up once per row, and the row is a slot whose song changes under it.
    val currentSong by rememberUpdatedState(song)
    val currentSwapBack by rememberUpdatedState(swapBack)
    val currentDragEnabled by rememberUpdatedState(dragEnabled)
    val currentLastIndex by rememberUpdatedState(lastIndex)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnSwipeEnd by rememberUpdatedState(onSwipeEnd)
    val haptics = LocalHaptics.current
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
            .onGloballyPositioned {
                drag.rowHeightPx = it.size.height.toFloat().coerceAtLeast(1f)
                swipe.rowWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
            }
            .graphicsLayer { translationY = offsetY }
            .then(
                if (song == null) {
                    Modifier
                } else {
                    Modifier
                        // On gesture navigation a swipe in from the left edge is Back. Where the
                        // swipe brings a song back, the row takes it instead - only those rows,
                        // so Back still works from everywhere else.
                        .then(if (swapBack != null) Modifier.systemGestureExclusion() else Modifier)
                        .semantics {
                            // TalkBack can't swipe a row; it gets the same as an action.
                            if (swapBack != null) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Bring back ${swapBack.displayName}") {
                                        onSwapBack(song.id)
                                        true
                                    },
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            val slop = viewConfiguration.touchSlop
                            val flingPx = SWIPE_FLING_VELOCITY.toPx()
                            val resistPx = SWIPE_RESIST.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startSong = currentSong ?: return@awaitEachGesture
                                // Which way it's going decides what it is, once the finger has
                                // moved far enough to tell. Until then nothing is consumed, and a
                                // drag that's none of this row's business - sideways to the left,
                                // or with nothing to bring back - is left for the tab swipe.
                                var travel: Offset
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (change.isConsumed || !change.pressed) return@awaitEachGesture
                                    travel = change.position - down.position
                                    if (travel.getDistance() > slop) break
                                }
                                val sideways = abs(travel.x) > abs(travel.y)
                                if (sideways) {
                                    // One gesture at a time across the list.
                                    if (travel.x <= 0f || drag.index >= 0 || swipe.index >= 0) return@awaitEachGesture
                                    swipeBack(
                                        swipe, index, down.id, startSong, travel.x - slop, flingPx, resistPx,
                                        canBringBack = { currentSwapBack != null && currentSong?.id == startSong.id },
                                        onArmed = { haptics?.tick() },
                                        onEnd = { currentOnSwipeEnd(it) },
                                    )
                                } else {
                                    if (!currentDragEnabled || drag.index >= 0 || swipe.index >= 0) return@awaitEachGesture
                                    // The reorder is applied once, on release, rather than
                                    // continuously during the drag. Mutating the list mid-drag
                                    // reshuffled the rows out from under the finger on every
                                    // crossing, which is what made the motion flicker; now the
                                    // dragged row follows the finger, the others make room, and the
                                    // list takes the new order once the row has settled into it.
                                    drag.start(index, currentLastIndex)
                                    val overSlop = travel.y - slop * sign(travel.y)
                                    drag.moveBy(index, currentLastIndex, overSlop)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || change.isConsumed) {
                                            if (drag.index == index) currentOnDrop(true)
                                            return@awaitEachGesture
                                        }
                                        if (!change.pressed) {
                                            if (drag.index == index) currentOnDrop(false)
                                            return@awaitEachGesture
                                        }
                                        // Read before consuming: a consumed change reports no movement.
                                        val dy = change.positionChange().y
                                        change.consume()
                                        drag.moveBy(index, currentLastIndex, dy)
                                    }
                                }
                            }
                        }
                },
            ),
    ) {
        // A slot with no song left to fill it stays as blank space of the same height.
        if (song != null) {
            Box(
                // Clipped only while it's being swiped: the songs slide in and out at the row's
                // edges, but at rest the swap button's press swell mustn't be cut off.
                Modifier.fillMaxSize().then(if (swiping) Modifier.clipToBounds() else Modifier),
            ) {
                if (swiping && swapBack != null) {
                    // The song coming back, already in its place and uncovered as the one leaving
                    // slides off it, so its name shows from the start of the swipe. Cut off where
                    // the leaving row begins: neither has a background to hide the other.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent { clipRect(right = swipe.offsetX) { this@drawWithContent.drawContent() } }
                            .clearAndSetSemantics {}
                            .padding(horizontal = edgeReach),
                    ) {
                        UpcomingRow(song = swapBack, opacity = opacity, onSwap = {}, onHold = {}, preview = true)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = if (swiping) swipe.offsetX else 0f }
                        .padding(horizontal = edgeReach),
                ) {
                    UpcomingRow(song = song, opacity = opacity, onSwap = onSwap, onHold = { onHold(song) })
                }
            }
        }
    }
}

/** Widens the layout by [reach] on each side, laid over whatever is beside it. */
private fun Modifier.reachPastSides(reach: Dp): Modifier = if (reach == 0.dp) this else layout { measurable, constraints ->
    val extra = reach.roundToPx() * 2
    val wide = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wide)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Moves the row being reordered, clamped to the ends of the list. */
private fun UpcomingDragState.moveBy(index: Int, lastIndex: Int, dy: Float) {
    if (this.index != index || dropping) return
    // Clamped to the ends of the list, so dragging past the first or last row holds the row there
    // instead of building up offset that never unwinds.
    val minOffset = -index * rowHeightPx
    val maxOffset = (lastIndex - index) * rowHeightPx
    offsetY = (offsetY + dy).coerceIn(minOffset, maxOffset)
}

/**
 * Follows a swipe to the right on one row until the finger lifts. With a song to bring back the
 * row slides as far as the finger goes; without one it only gives a little and springs back, so
 * the swipe is seen to have been noticed but there's nothing there.
 */
private suspend fun AwaitPointerEventScope.swipeBack(
    swipe: SwipeBackState,
    index: Int,
    pointerId: PointerId,
    startSong: Song,
    overSlop: Float,
    flingPx: Float,
    resistPx: Float,
    canBringBack: () -> Boolean,
    onArmed: () -> Unit,
    onEnd: (commit: Boolean) -> Unit,
) {
    swipe.start(index, startSong.id)
    val tracker = VelocityTracker()
    var raw = overSlop
    var armed = false
    fun follow() {
        val width = swipe.rowWidthPx
        swipe.offsetX = if (canBringBack()) {
            raw.coerceIn(0f, width)
        } else {
            // Gives less and less the further it's pulled, never more than resistPx.
            val pulled = raw.coerceAtLeast(0f)
            resistPx * (1f - exp(-pulled / resistPx))
        }
        val nowArmed = canBringBack() && swipe.offsetX >= width * SWIPE_COMMIT_FRACTION
        if (nowArmed && !armed) onArmed()
        armed = nowArmed
    }
    follow()
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId }
        if (change == null || change.isConsumed) {
            onEnd(false)
            return
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!change.pressed) {
            val fling = tracker.calculateVelocity().x > flingPx && swipe.offsetX > 0f
            onEnd(canBringBack() && (armed || fling))
            return
        }
        // Read before consuming: a consumed change reports no movement.
        raw += change.positionChange().x
        change.consume()
        follow()
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

/** A swipe let go past this much of the row's width brings the song back. */
private const val SWIPE_COMMIT_FRACTION = 0.4f

/** A quick flick to the right brings the song back however short it was. */
private val SWIPE_FLING_VELOCITY = 1000.dp

/** How far a row gives when there's nothing to bring back. */
private val SWIPE_RESIST = 20.dp

/** How long a dropped row waits for the queue to take the move before giving up on it. */
private const val REORDER_TIMEOUT_MS = 500L

@Composable
private fun UpcomingRow(
    song: Song,
    opacity: Float,
    onSwap: () -> Unit,
    onHold: () -> Unit,
    /** Only drawn, for the song sliding back in: nothing on it can be tapped or held. */
    preview: Boolean = false,
) {
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
                    .then(if (preview) Modifier else Modifier.onTapOrHold(onTap = { scrollRequest++ }, onHold = onHold, holdLabel = "Edit tags")),
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
            enabled = !preview,
        )
    }
}
