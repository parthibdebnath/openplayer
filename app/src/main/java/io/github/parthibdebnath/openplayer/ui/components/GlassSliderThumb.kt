package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * A slider thumb that turns to glass while it's held, as Apple's does since iOS 26.
 *
 * At rest it's the solid pill it always was. Touched, it swells (to about one and a half times its
 * size), the solid fill drains away and it becomes clear glass: the track and its filled part show
 * through it, magnified and bent at the rim, with the rim lit and a slight inner shadow. Dragged,
 * it stretches along the direction it's moving and wobbles back when it stops; let go, it settles
 * back into the solid pill. The construction follows the slider in Kyant's AndroidLiquidGlass
 * catalogue (springs, scales and lens settings), sized down to this app's thinner sliders.
 *
 * What it refracts is the slider's own track, recorded into a layer as it's drawn - the page
 * behind is a flat colour, so the track is all there is to see.
 */

/** Press, wobble and speed of one glass thumb. */
@Stable
internal class GlassThumbState(private val scope: CoroutineScope, private val pressedScale: Float) {
    private val pressSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleXSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpec = spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val velocitySpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.01f)

    val press = Animatable(0f, 0.001f)
    val scaleX = Animatable(1f, 0.001f)
    val scaleY = Animatable(1f, 0.001f)

    /** Thumb speed, in slider lengths per second. */
    val velocity = Animatable(0f, 0.01f)

    private val tracker = VelocityTracker()
    private var held = false

    fun press() {
        held = true
        tracker.resetTracking()
        scope.launch { press.animateTo(1f, pressSpec) }
        scope.launch { scaleX.animateTo(pressedScale, scaleXSpec) }
        scope.launch { scaleY.animateTo(pressedScale, scaleYSpec) }
    }

    fun release() {
        held = false
        scope.launch { velocity.animateTo(0f, velocitySpec) }
        scope.launch { press.animateTo(0f, pressSpec) }
        scope.launch { scaleX.animateTo(1f, scaleXSpec) }
        scope.launch { scaleY.animateTo(1f, scaleYSpec) }
    }

    /** A tap on the track: the thumb lifts, glides there as glass, then settles. */
    fun tap(glideMs: Long) {
        press()
        scope.launch {
            delay(glideMs)
            if (held) release()
        }
    }

    /** Feeds where the thumb is drawn, as a fraction of the slider, so its speed can be read. */
    suspend fun followPosition(fraction: () -> Float) {
        snapshotFlow(fraction).collectLatest { f ->
            if (!held) return@collectLatest
            tracker.addPosition(System.nanoTime() / 1_000_000L, Offset(f, 0f))
            scope.launch { velocity.animateTo(tracker.calculateVelocity().x, velocitySpec) }
            // No movement for a moment means it's being held still: let the stretch go.
            delay(60)
            tracker.resetTracking()
            velocity.animateTo(0f, velocitySpec)
        }
    }
}

@Composable
internal fun rememberGlassThumbState(pressedScale: Float): GlassThumbState {
    val scope = rememberCoroutineScope()
    return remember(scope, pressedScale) { GlassThumbState(scope, pressedScale) }
}

/**
 * Draws the thumb, centred on [center] in the coordinates of the track it sits on. Place it in
 * the same box as the track, so the two share coordinates.
 *
 * @param track the track as drawn, refracted through the thumb while it's held.
 * @param fraction how far along the track the thumb is, for its speed.
 * @param stretch whether it stretches along its motion; off for a thumb travelling a curve, where
 *   a sideways stretch would point the wrong way.
 */
@Composable
internal fun GlassSliderThumb(
    state: GlassThumbState,
    track: GraphicsLayer,
    center: () -> Offset,
    fraction: () -> Float,
    restSize: DpSize,
    color: Color,
    pageColor: Color,
    stretch: Boolean = true,
) {
    val res = rememberGlassResources()
    val shape = remember { RoundedCornerShape(50) }
    val density = LocalDensity.current
    val source = remember(track, pageColor, density, restSize) {
        GlassSource(layer = { track }, offset = { topLeft(center(), restSize, density) }, flatColor = pageColor)
    }
    LaunchedEffect(state) { state.followPosition(fraction) }

    Box(
        Modifier
            .offset {
                val c = center()
                IntOffset((c.x - restSize.width.toPx() / 2f).roundToInt(), (c.y - restSize.height.toPx() / 2f).roundToInt())
            }
            .size(restSize)
            .graphicsLayer {
                var sx = state.scaleX.value
                var sy = state.scaleY.value
                if (stretch) {
                    // Longer along its motion and a touch thinner across it, either way it moves.
                    val v = abs(state.velocity.value) / 10f
                    sx /= 1f - (v * 0.75f).coerceIn(0f, 0.2f)
                    sy *= 1f - (v * 0.25f).coerceIn(0f, 0.2f)
                }
                scaleX = sx
                scaleY = sy
            }
            .drawBehind {
                val p = state.press.value
                val radius = CornerRadius(size.minDimension / 2f)
                if (p < 0.002f) {
                    // At rest it's a plain pill; skipping the glass keeps idle sliders cheap.
                    drawRoundRect(color = color, cornerRadius = radius)
                    return@drawBehind
                }
                val spec = GlassSpec(
                    blur = 0.dp,
                    lensHeight = (size.minDimension * 0.42f).toDp() * p,
                    lensAmount = (size.minDimension * 0.6f).toDp() * p,
                    dispersion = true,
                    // The solid fill drains away as it lifts, leaving clear glass.
                    surface = color.copy(alpha = color.alpha * (1f - p)),
                    highlightAlpha = p,
                    shadowRadius = 4.dp,
                    shadowAlpha = 0.12f * p,
                    innerShadowRadius = (size.minDimension * 0.18f).toDp() * p,
                    innerShadowAlpha = p,
                )
                drawGlassLayers(res, shape, spec, source, press = 0f, pressPosition = Offset.Zero) {}
            },
    )
}

/** The thumb's top-left in track coordinates, at its resting size (the glass scales about its centre). */
private fun topLeft(center: Offset, restSize: DpSize, density: Density): Offset =
    with(density) { Offset(center.x - restSize.width.toPx() / 2f, center.y - restSize.height.toPx() / 2f) }
