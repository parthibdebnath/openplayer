package io.github.parthibdebnath.openplayer.ui.skin

import android.os.Build
import android.view.RoundedCorner
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * How much of the display's top and bottom a skin's frame keeps clear: a camera cutout, and the
 * screen's rounded corners. Openplayer's own look runs to the edges and pads its few edge controls;
 * a skin's frame is built like a desktop or a device - menu bars, title bars, taskbars and
 * softkeys hard up against the edges - so it's set inside these bands instead, and the bands are
 * filled to carry on the frame's edge.
 */
@Immutable
data class FrameEdges(val top: Dp, val bottom: Dp)

/** For the render harness, which has no real display to measure. */
@VisibleForTesting
var frameEdgesOverride: FrameEdges? = null

/**
 * A rounded corner of radius r cuts into anything drawn within r of it. Something a couple of dp
 * in from the side (a Start button, a softkey label) is clear of the curve once it's this much of
 * r up from the edge.
 */
private const val CORNER_CLEARANCE = 0.65f

@Composable
fun rememberFrameEdges(): FrameEdges {
    frameEdgesOverride?.let { return it }
    val density = LocalDensity.current
    // Read as composition state, so this comes round again when the window's insets arrive or change.
    val cutout = WindowInsets.displayCutout.union(WindowInsets.statusBars)
    val cutoutTop = with(density) { cutout.getTop(this).toDp() }
    val cutoutBottom = with(density) { WindowInsets.displayCutout.getBottom(this).toDp() }
    var cornerTop = 0.dp
    var cornerBottom = 0.dp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val insets = LocalView.current.rootWindowInsets
        if (insets != null) {
            fun radius(position: Int): Dp = with(density) { (insets.getRoundedCorner(position)?.radius ?: 0).toDp() }
            cornerTop = max(radius(RoundedCorner.POSITION_TOP_LEFT), radius(RoundedCorner.POSITION_TOP_RIGHT)) * CORNER_CLEARANCE
            cornerBottom = max(radius(RoundedCorner.POSITION_BOTTOM_LEFT), radius(RoundedCorner.POSITION_BOTTOM_RIGHT)) * CORNER_CLEARANCE
        }
    }
    return FrameEdges(top = max(cutoutTop, cornerTop), bottom = max(cutoutBottom, cornerBottom))
}
