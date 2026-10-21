package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * A slider thumb that turns to glass while it's held, as Apple's does since iOS 26.
 *
 * At rest it's the solid pill it always was. Touched, it swells (to about one and a half times its
 * size), the solid fill drains away and it becomes clear glass: the track and its filled part show
 * through it, magnified and bent at the rim, with the rim lit and a slight inner shadow. Let go, it
 * settles back into the solid pill. The construction follows the slider in Kyant's
 * AndroidLiquidGlass catalogue (scales and lens settings), sized down to this app's thinner
 * sliders - without its squash and stretch, which made the thumb feel soft rather than solid.
 *
 * What it refracts is the slider's own track, recorded into a layer as it's drawn - the page
 * behind is a flat colour, so the track is all there is to see.
 */

/** Press and size of one glass thumb. */
@Stable
internal class GlassThumbState(private val scope: CoroutineScope, private val pressedScale: Float) {
    private val pressSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)

    // One scale for both directions, on a spring that doesn't overshoot. It grows when held and
    // shrinks back when let go, but never squashes, stretches or wobbles - that read as jelly
    // rather than glass.
    private val scaleSpec = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = 0.001f)

    val press = Animatable(0f, 0.001f)
    val scale = Animatable(1f, 0.001f)

    private var held = false

    fun press() {
        held = true
        scope.launch { press.animateTo(1f, pressSpec) }
        scope.launch { scale.animateTo(pressedScale, scaleSpec) }
    }

    fun release() {
        held = false
        scope.launch { press.animateTo(0f, pressSpec) }
        scope.launch { scale.animateTo(1f, scaleSpec) }
    }

    /** A tap on the track: the thumb lifts, glides there as glass, then settles. */
    fun tap(glideMs: Long) {
        press()
        scope.launch {
            delay(glideMs)
            if (held) release()
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
 */
@Composable
internal fun GlassSliderThumb(
    state: GlassThumbState,
    track: GraphicsLayer,
    center: () -> Offset,
    restSize: DpSize,
    color: Color,
    pageColor: Color,
) {
    val res = rememberGlassResources()
    val shape = remember { RoundedCornerShape(50) }
    val density = LocalDensity.current
    val source = remember(track, pageColor, density, restSize) {
        GlassSource(layer = { track }, offset = { topLeft(center(), restSize, density) }, flatColor = pageColor)
    }

    Box(
        Modifier
            .offset {
                val c = center()
                IntOffset((c.x - restSize.width.toPx() / 2f).roundToInt(), (c.y - restSize.height.toPx() / 2f).roundToInt())
            }
            .size(restSize)
            .graphicsLayer {
                scaleX = state.scale.value
                scaleY = state.scale.value
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
