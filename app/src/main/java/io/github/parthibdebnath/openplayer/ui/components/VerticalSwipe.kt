package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How far a finger travels before it counts as a swipe; well short of [TabSwipe]'s, since it's vertical. */
private val SWIPE_DISTANCE = 40.dp

/** Mostly up or down rather than sideways, so it can never be mistaken for a tab swipe. */
private const val DIRECTION_RATIO = 1.5f

/**
 * One-finger swipes up or down, reported once per touch as soon as the finger has gone far enough.
 *
 * Nothing is consumed: the tab swipe and the two- and three-finger brightness and volume gestures
 * on the same area keep working exactly as before. A second finger, or a touch something else has
 * already taken, isn't a swipe.
 */
fun Modifier.verticalSwipe(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    /** Whether a touch landing here (in this element's coordinates) may become a swipe. */
    startsInside: (Offset) -> Boolean = { true },
): Modifier = composed {
    val currentUp by rememberUpdatedState(onSwipeUp)
    val currentDown by rememberUpdatedState(onSwipeDown)
    val currentStartsInside by rememberUpdatedState(startsInside)
    pointerInput(Unit) {
        val distancePx = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentStartsInside(down.position)) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed || !change.pressed) return@awaitEachGesture
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dy) > distancePx && abs(dy) > abs(dx) * DIRECTION_RATIO) {
                    if (dy < 0) currentUp() else currentDown()
                    // Once per touch: the rest of it is ignored until the finger lifts.
                    do {
                        val rest = awaitPointerEvent()
                    } while (rest.changes.any { it.pressed })
                    return@awaitEachGesture
                }
            }
        }
    }
}
