package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import io.github.parthibdebnath.openplayer.util.LocalHaptics

/**
 * Calls [onTap] for a quick tap that doesn't move, and [onHold] (with a haptic click) once a touch
 * has been held still for the long-press timeout.
 *
 * Nothing is consumed until a hold fires. A detector that took the touch from the start would mark
 * it as claimed by a child, which stops a row's drag-to-reorder and the tab swipe from starting on
 * a title; this one only watches. Once a hold has fired, the rest of that touch is consumed, so
 * moving the finger afterwards doesn't also drag the row or switch tabs.
 *
 * Moving past touch slop, a second finger, or a touch someone else consumed is neither.
 */
fun Modifier.onTapOrHold(
    onTap: (() -> Unit)? = null,
    onHold: (() -> Unit)? = null,
    /** Read out by TalkBack for the hold action, which it offers as a long press. */
    holdLabel: String? = null,
): Modifier = composed {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHold by rememberUpdatedState(onHold)
    val haptics = LocalHaptics.current
    val hasHold = onHold != null
    this
        .then(
            if (hasHold) {
                Modifier.semantics {
                    onLongClick(label = holdLabel) {
                        currentOnHold?.invoke()
                        true
                    }
                }
            } else {
                Modifier
            },
        )
        .pointerInput(hasHold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                var tapped = false
                val settled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull true
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (change.isConsumed || (change.position - down.position).getDistance() > slop) {
                            return@withTimeoutOrNull true
                        }
                        if (!change.pressed) {
                            tapped = true
                            return@withTimeoutOrNull true
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    true
                }
                if (tapped) {
                    currentOnTap?.invoke()
                    return@awaitEachGesture
                }
                // Ended some other way before the timeout, or there's nothing to do on a hold.
                if (settled != null || !hasHold) return@awaitEachGesture

                haptics?.click()
                currentOnHold?.invoke()
                // The touch is spoken for from here: whatever the finger does next isn't a drag
                // or a swipe as well.
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { if (it.id == down.id) it.consume() }
                    if (event.changes.none { it.id == down.id && it.pressed }) break
                }
            }
        }
}
