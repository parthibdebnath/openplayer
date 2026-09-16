package com.musicplayer.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

enum class GestureType { BRIGHTNESS, VOLUME }

/** How long a candidate finger-count must persist before a gesture is considered "started". */
private const val SETTLE_WINDOW_MS = 150L

/** After a gesture ends, how long before a new one is allowed to start. */
private const val RESTART_COOLDOWN_MS = 250L

/**
 * Detects a sustained 2-finger (brightness) or 3-finger (volume) vertical drag, active only
 * where this modifier is applied (the Play screen). Reports 1%-quantized deltas as the fingers
 * move; up increases, down decreases.
 *
 * Fingers touching down (or lifting) for a multi-finger gesture don't all move in the same
 * frame - they land, and later lift, one at a time a few milliseconds apart. Two things follow:
 *  - On the way down, a real 3-finger touch briefly reports a pointer count of 1, then 2, then 3
 *    - so this only commits to a gesture type once a candidate count has held steady for
 *      [SETTLE_WINDOW_MS], rather than latching onto the first (2-finger) reading it sees.
 *  - On the way up, averaging "whatever's currently pressed" as fingers drop out one by one
 *    (3 -> 2 -> 1 -> 0) makes the tracked position jump to whichever finger happens to be last,
 *    producing a sudden spurious value change right as the user lets go - so once active, a
 *    change in finger count resyncs the tracked position instead of diffing against it. The
 *    gesture ends the instant all fingers lift (count == 0 must finalize immediately - waiting
 *    for a "held for N ms" grace period here would mean waiting for a *subsequent* touch event
 *    to even check the clock, and once every finger is off the screen no more events arrive at
 *    all, so that approach leaves the gesture permanently "stuck active"). To still avoid a
 *    staggered release being misread as the start of a new (wrong) gesture, a brand new gesture
 *    can't begin until [RESTART_COOLDOWN_MS] after the last one ended - safe to check because it
 *    only matters once a new touch event actually arrives.
 *
 * Deliberately avoids `withTimeout` around `awaitPointerEvent` - cancelling that suspension
 * point mid-flight can leave the pointer input pipeline in a broken state for the rest of the
 * gesture stream.
 */
fun Modifier.multiFingerVerticalGesture(
    twoFingerEnabled: Boolean,
    threeFingerEnabled: Boolean,
    onGestureActiveChanged: (GestureType?) -> Unit,
    onDeltaPercent: (GestureType, Int) -> Unit,
): Modifier = pointerInput(twoFingerEnabled, threeFingerEnabled) {
    val pxPerPercent = 4.dp.toPx()

    var activeType: GestureType? = null
    var lastAvgY = 0f
    var lastCount = 0
    var accumulatedPx = 0f

    var pendingType: GestureType? = null
    var pendingSinceMs = 0L
    var lastEndedAtMs = 0L

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            val count = pressed.size
            val nowMs = System.currentTimeMillis()

            when {
                activeType != null && count == 0 -> {
                    // End immediately - see the class doc for why this can't wait for a grace
                    // period (there may be no further events to re-check a timer against).
                    onGestureActiveChanged(null)
                    activeType = null
                    pendingType = null
                    lastEndedAtMs = nowMs
                }

                activeType != null -> {
                    // Non-null assertion: the branch guard above already confirmed this, but a
                    // captured `var` mutated elsewhere in this closure can't be smart-cast.
                    val currentType = activeType!!
                    val avgY = pressed.map { it.position.y }.average().toFloat()
                    if (count != lastCount) {
                        // Finger count just changed (one lifted or landed): resync without
                        // diffing against the old average, which was computed over a different
                        // number of fingers and would otherwise produce a spurious jump.
                        lastAvgY = avgY
                        lastCount = count
                    } else {
                        val deltaY = lastAvgY - avgY
                        lastAvgY = avgY
                        accumulatedPx += deltaY
                        while (accumulatedPx >= pxPerPercent) {
                            onDeltaPercent(currentType, 1)
                            accumulatedPx -= pxPerPercent
                        }
                        while (accumulatedPx <= -pxPerPercent) {
                            onDeltaPercent(currentType, -1)
                            accumulatedPx += pxPerPercent
                        }
                    }
                    pressed.forEach { it.consume() }
                }

                count == 0 -> {
                    pendingType = null
                }

                nowMs - lastEndedAtMs < RESTART_COOLDOWN_MS -> {
                    // Too soon after the last gesture ended - likely the tail end of a
                    // staggered release, not a deliberate new touch. Ignore until it passes.
                }

                else -> {
                    val candidate = when {
                        count == 2 && twoFingerEnabled -> GestureType.BRIGHTNESS
                        count >= 3 && threeFingerEnabled -> GestureType.VOLUME
                        else -> null
                    }
                    if (candidate != pendingType) {
                        pendingType = candidate
                        pendingSinceMs = nowMs
                    } else if (candidate != null && nowMs - pendingSinceMs >= SETTLE_WINDOW_MS) {
                        activeType = candidate
                        lastAvgY = pressed.map { it.position.y }.average().toFloat()
                        lastCount = count
                        accumulatedPx = 0f
                        onGestureActiveChanged(candidate)
                    }
                }
            }
        }
    }
}
