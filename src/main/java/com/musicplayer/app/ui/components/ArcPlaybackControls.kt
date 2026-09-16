package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometry for the Play screen's arc cluster, taken directly off the design mockup by fitting a
 * circle through the three button centers.
 *
 * The key property the mockup has and earlier attempts here didn't: this is one real circle whose
 * centre sits *off-screen*, past the bottom-right corner. The buttons ride its circumference and
 * the seek track is a concentric arc at a smaller radius, exactly as the spec describes ("on one
 * circle with some centre O ... aligned on the circumference of smaller circles with the same
 * centre O"). Anchoring the centre to the bottom-right corner in absolute dp - rather than to
 * fractions of a box whose size varies - is what keeps the curve's shape identical across screen
 * sizes instead of flattening out into a near-straight diagonal.
 */
private object ArcGeometry {
    /** Circle centre, as an offset past the cluster's bottom-right corner. */
    val CENTER_BEYOND_RIGHT = 61.dp
    val CENTER_BEYOND_BOTTOM = 30.dp

    val BUTTON_RADIUS = 263.dp
    val SEEK_RADIUS = 200.dp

    // Degrees, screen convention: 0 points right, negative sweeps upward. Measured spacing
    // between the three buttons on the mockup is a near-uniform ~17.8 degrees.
    const val PREV_ANGLE = -155.7f
    const val PLAY_ANGLE = -138.3f
    const val NEXT_ANGLE = -120.1f

    // The mockup's seek track runs off both the bottom and the right edge. These endpoints keep
    // the full 0%-100% thumb travel on-screen and clear of the nav pill while tracing the same
    // visible curve.
    const val SEEK_START_ANGLE = -166f
    const val SEEK_END_ANGLE = -114f
    const val SEEK_SWEEP = SEEK_END_ANGLE - SEEK_START_ANGLE

    /** How far either side of the track a touch still counts as grabbing the seek thumb. */
    val SEEK_TOUCH_TOLERANCE = 40.dp

    val PREV_SIZE = 38.dp
    val PREV_ICON = 22.dp
    val PLAY_SIZE = 70.dp
    val PLAY_ICON = 42.dp
    val NEXT_SIZE = 56.dp
    val NEXT_ICON = 30.dp
}

private fun pointOnCircle(center: Offset, radiusPx: Float, angleDeg: Float): Offset {
    val radians = Math.toRadians(angleDeg.toDouble())
    return Offset(
        center.x + radiusPx * cos(radians).toFloat(),
        center.y + radiusPx * sin(radians).toFloat(),
    )
}

private fun angleOf(position: Offset, center: Offset): Float =
    Math.toDegrees(atan2((position.y - center.y).toDouble(), (position.x - center.x).toDouble())).toFloat()

private fun progressForTouch(position: Offset, center: Offset): Float =
    ((angleOf(position, center) - ArcGeometry.SEEK_START_ANGLE) / ArcGeometry.SEEK_SWEEP).coerceIn(0f, 1f)

/**
 * The Play screen's transport cluster: the curved seek track with its draggable thumb, plus the
 * previous/play/next buttons riding a concentric outer arc.
 *
 * Fills its parent and is meant to be layered *over* the screen's normal column of text - on the
 * mockup the arc occupies the right side at the same height as the time readout and the small
 * repeat/shuffle/output buttons on the left, rather than sitting below them.
 */
@Composable
fun ArcPlayerCluster(
    progress: Float,
    onSeek: (Float) -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val seekRadiusPx = with(density) { ArcGeometry.SEEK_RADIUS.toPx() }
        val buttonRadiusPx = with(density) { ArcGeometry.BUTTON_RADIUS.toPx() }
        val tolerancePx = with(density) { ArcGeometry.SEEK_TOUCH_TOLERANCE.toPx() }
        val strokePx = with(density) { 2.dp.toPx() }
        val thumbPx = with(density) { 5.5.dp.toPx() }
        // Read outside the draw lambda: a DrawScope isn't a composable, so it can't read the local.
        val contentColor = LocalAppColors.current.onBackground

        val center = with(density) {
            Offset(
                maxWidth.toPx() + ArcGeometry.CENTER_BEYOND_RIGHT.toPx(),
                maxHeight.toPx() + ArcGeometry.CENTER_BEYOND_BOTTOM.toPx(),
            )
        }

        // While dragging, the thumb follows the finger from local state and the player is left
        // alone - seeking on every touch move restarts buffering continuously, which is what made
        // dragging the bar stall playback. The seek is committed once, on release.
        var dragProgress by remember { mutableStateOf<Float?>(null) }
        // Position only arrives a few times a second, so the thumb is interpolated between updates
        // instead of jumping from one report to the next.
        val smoothedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 260, easing = LinearEasing),
            label = "seekProgress",
        )
        val clampedProgress = (dragProgress ?: smoothedProgress).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val topLeft = Offset(center.x - seekRadiusPx, center.y - seekRadiusPx)
            val arcSize = Size(seekRadiusPx * 2, seekRadiusPx * 2)
            drawArc(
                color = contentColor.copy(alpha = 0.28f),
                startAngle = ArcGeometry.SEEK_START_ANGLE,
                sweepAngle = ArcGeometry.SEEK_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = contentColor.copy(alpha = 0.85f),
                startAngle = ArcGeometry.SEEK_START_ANGLE,
                sweepAngle = ArcGeometry.SEEK_SWEEP * clampedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            drawCircle(
                color = contentColor,
                radius = thumbPx,
                center = pointOnCircle(
                    center,
                    seekRadiusPx,
                    ArcGeometry.SEEK_START_ANGLE + ArcGeometry.SEEK_SWEEP * clampedProgress,
                ),
            )
        }

        // Seek touches are handled by a box bounded to the track's own corner of the screen, and
        // only consumed when the touch really is on the track. A full-size handler here would
        // swallow taps meant for the song list and the multi-finger brightness/volume gestures.
        val outerRadius = ArcGeometry.SEEK_RADIUS + ArcGeometry.SEEK_TOUCH_TOLERANCE
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(outerRadius - ArcGeometry.CENTER_BEYOND_RIGHT)
                .height(outerRadius - ArcGeometry.CENTER_BEYOND_BOTTOM)
                .pointerInput(center) {
                    // This region is sized so the circle's centre always lands at exactly
                    // (outerRadius, outerRadius) in the region's own coordinate space.
                    val outerRadiusPx = outerRadius.toPx()
                    val localCenter = Offset(outerRadiusPx, outerRadiusPx)
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val distance = hypot(down.position.x - localCenter.x, down.position.y - localCenter.y)
                            val angle = angleOf(down.position, localCenter)
                            val onTrack = abs(distance - seekRadiusPx) <= tolerancePx &&
                                angle >= ArcGeometry.SEEK_START_ANGLE - 8f &&
                                angle <= ArcGeometry.SEEK_END_ANGLE + 8f
                            if (!onTrack) continue
                            down.consume()
                            dragProgress = progressForTouch(down.position, localCenter)
                            drag(down.id) { change ->
                                change.consume()
                                dragProgress = progressForTouch(change.position, localCenter)
                            }
                            dragProgress?.let(onSeek)
                            dragProgress = null
                        }
                    }
                },
        )

        fun buttonOffset(angleDeg: Float, buttonSize: Dp): Pair<Dp, Dp> {
            val point = pointOnCircle(center, buttonRadiusPx, angleDeg)
            return with(density) { (point.x.toDp() - buttonSize / 2) to (point.y.toDp() - buttonSize / 2) }
        }

        val (prevX, prevY) = buttonOffset(ArcGeometry.PREV_ANGLE, ArcGeometry.PREV_SIZE)
        val (playX, playY) = buttonOffset(ArcGeometry.PLAY_ANGLE, ArcGeometry.PLAY_SIZE)
        val (nextX, nextY) = buttonOffset(ArcGeometry.NEXT_ANGLE, ArcGeometry.NEXT_SIZE)

        GlassIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPrevious,
            circleSize = ArcGeometry.PREV_SIZE,
            iconSize = ArcGeometry.PREV_ICON,
            modifier = Modifier.offset(x = prevX, y = prevY),
        )
        GlassIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            circleSize = ArcGeometry.PLAY_SIZE,
            iconSize = ArcGeometry.PLAY_ICON,
            modifier = Modifier.offset(x = playX, y = playY),
        )
        GlassIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onNext,
            circleSize = ArcGeometry.NEXT_SIZE,
            iconSize = ArcGeometry.NEXT_ICON,
            modifier = Modifier.offset(x = nextX, y = nextY),
        )
    }
}

/** Compact, non-arc variant used on File/SFX/Settings screens: buttons in a straight row. */
@Composable
fun CompactMainControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPrevious,
            circleSize = ArcGeometry.PREV_SIZE,
            iconSize = ArcGeometry.PREV_ICON,
        )
        GlassIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            circleSize = ArcGeometry.PLAY_SIZE,
            iconSize = ArcGeometry.PLAY_ICON,
        )
        GlassIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onNext,
            circleSize = ArcGeometry.NEXT_SIZE,
            iconSize = ArcGeometry.NEXT_ICON,
        )
    }
}
