package com.musicplayer.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val SWIPE_THRESHOLD = 72.dp

/** A horizontal swipe has to be this much longer than its vertical travel to count. */
private const val DIRECTION_RATIO = 1.5f

/**
 * Detects a left/right swipe for changing tabs, without ever competing with the content for the
 * gesture.
 *
 * It watches on [PointerEventPass.Final] - after every child has had its say - and consumes
 * nothing. A drag a child claimed (scrolling a song list, dragging the seek bar, moving the seek
 * arc) is seen as already consumed and ignored. Using an ordinary drag detector here meant the
 * parent and the seek bar raced for the same gesture: a horizontal-only detector crosses its touch
 * slop sooner than the slider's two-dimensional one, so the parent sometimes stole the drag
 * mid-seek, which is what made dragging the bar behave erratically.
 *
 * @param onSwipe called with +1 for a swipe towards the next tab, -1 for the previous one.
 */
fun Modifier.tabSwipe(enabled: Boolean = true, onSwipe: (Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val thresholdPx = SWIPE_THRESHOLD.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var totalX = 0f
            var totalY = 0f
            var claimedByChild = down.isConsumed

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) claimedByChild = true
                totalX += change.position.x - change.previousPosition.x
                totalY += change.position.y - change.previousPosition.y
                if (!change.pressed) break
            }

            if (!claimedByChild && abs(totalX) > thresholdPx && abs(totalX) > abs(totalY) * DIRECTION_RATIO) {
                onSwipe(if (totalX < 0) 1 else -1)
            }
        }
    }
