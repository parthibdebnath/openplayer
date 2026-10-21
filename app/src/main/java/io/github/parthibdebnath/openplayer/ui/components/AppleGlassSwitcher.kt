package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.parthibdebnath.openplayer.ui.theme.InterFontFamily
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** How far the selection lifts while held: Apple's tab bar takes a 56pt pill to 78pt. */
private const val PRESSED_SCALE = 78f / 56f

/** How long the pill takes to fade in or out when the selection appears or clears. */
private const val SHOW_MS = 160

/** The labels seen through a lifted lens are enlarged by this much. */
private const val LENS_LABEL_SCALE = 1.2f

/**
 * The selection's motion: where it is (in items), how far it's lifted, and how much it has grown.
 * The springs are the ones Apple's tab bar moves on, as measured in Kyant's recreation.
 */
@Stable
private class SelectionMotion(private val scope: CoroutineScope, initial: Float, private val count: Int, visible: Boolean) {
    val value = Animatable(initial, 0.001f)

    /**
     * 1 while something is selected, 0 while nothing is - a reverb that matches none of the
     * presets. The pill fades out where it is rather than going anywhere: placed at "item -1" it
     * sat off to the left of the bar, selecting nothing, until a preset was picked again.
     */
    val shown = Animatable(if (visible) 1f else 0f, 0.001f)
    val lift = Animatable(0f, 0.001f)
    /**
     * How far it has grown as it lifts. One value for both directions, on a spring that doesn't
     * overshoot: two springs, one per direction, let it wobble and squash like jelly.
     */
    val scale = Animatable(1f, 0.001f)
    private val scaleSpec = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = 0.001f)
    val panel = Animatable(0f)
    var dragging by mutableStateOf(false)

    private val valueSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)

    fun press() {
        scope.launch { lift.animateTo(1f, spring(1f, 1000f, 0.001f)) }
        scope.launch { scale.animateTo(PRESSED_SCALE, scaleSpec) }
    }

    fun release() {
        scope.launch { lift.animateTo(0f, spring(1f, 1000f, 0.001f)) }
        scope.launch { scale.animateTo(1f, scaleSpec) }
    }

    fun follow(target: Float) {
        scope.launch { value.animateTo(target.coerceIn(0f, (count - 1).toFloat()), valueSpec) }
    }

    /** Moves to [index], lifting on the way and setting down once it's nearly there. */
    fun settle(index: Int, lifted: Boolean) {
        if (!lifted) press()
        scope.launch { value.animateTo(index.toFloat(), valueSpec) }
        scope.launch {
            val threshold = (count - 1).coerceAtLeast(1) * 0.025f
            snapshotFlow { abs(value.value - index) }.first { it < threshold }
            release()
        }
    }

    fun hide() {
        scope.launch { shown.animateTo(0f, tween(SHOW_MS)) }
    }

    /** Brings the pill back on [index] - appearing there, not sliding in from where it was hidden. */
    fun showAt(index: Int) {
        scope.launch { value.snapTo(index.toFloat()) }
        scope.launch { shown.animateTo(1f, tween(SHOW_MS)) }
    }

    val hidden: Boolean get() = shown.targetValue < 0.5f

    fun nudgePanel(dx: Float) {
        scope.launch { panel.snapTo(panel.value + dx) }
    }

    fun releasePanel() {
        scope.launch { panel.animateTo(0f, spring(1f, 300f, 0.5f)) }
    }
}

/**
 * Apple's segmented selection, as its tab bar and segmented controls do it.
 *
 * At rest the selected item sits in a faint pill. Touch it - or drag across - and the pill lifts
 * off the bar into a clear glass lens: it grows, refracts and slightly splits the colour of what
 * passes under its rim, and casts a shadow, while the label inside it is
 * enlarged. Let go and it settles into the nearest item. A drag past either end pulls the whole bar
 * a few points after it.
 *
 * The selected label reads bold through the pill because the pill shows a second, bold copy of the
 * labels underneath - which is also what the lens refracts.
 *
 * @param floating whether the bar floats over the screen (the tab bar), in which case its track is
 *   glass refracting the screen behind; otherwise the track is a plain fill, as a segmented
 *   control in the content is.
 */
@Composable
internal fun AppleGlassSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    height: Dp,
    fontSizeSp: Float,
    floating: Boolean,
    icons: List<ImageVector>? = null,
) {
    val colors = LocalAppColors.current
    val light = colors.isLightBackground
    val backdrop = LocalGlassBackdrop.current
    val inset = 4.dp
    val trackShape = RoundedCornerShape(height / 2)
    val thumbHeight = height - inset * 2
    val thumbShape = RoundedCornerShape(thumbHeight / 2)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current
        val count = items.size.coerceAtLeast(1)
        val insetPx = with(density) { inset.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val itemWidth = (widthPx - insetPx * 2) / count
        val itemWidthDp = with(density) { itemWidth.toDp() }
        val panelMax = with(density) { 4.dp.toPx() }
        val trackSwell = with(density) { 16.dp.toPx() }

        // Read through these in the touch handler, which outlives recompositions: a captured
        // selectedIndex went stale, and a tap on the tab that had since become current was ignored.
        val currentOnSelect by rememberUpdatedState(onSelect)
        val haptics = io.github.parthibdebnath.openplayer.util.LocalHaptics.current
        val scope = rememberCoroutineScope()
        val motion = remember(scope, count) {
            SelectionMotion(scope, selectedIndex.coerceIn(0, count - 1).toFloat(), count, visible = selectedIndex in 0 until count)
        }
        LaunchedEffect(selectedIndex) {
            if (motion.dragging) return@LaunchedEffect
            when {
                selectedIndex !in 0 until count -> motion.hide()
                motion.hidden -> motion.showAt(selectedIndex)
                // Selection changed from elsewhere - a swipe between tabs, say.
                abs(motion.value.targetValue - selectedIndex) > 0.01f -> motion.settle(selectedIndex, lifted = false)
            }
        }

        fun panelOffset(): Float {
            val fraction = (motion.panel.value / widthPx).coerceIn(-1f, 1f)
            return panelMax * fraction.sign * EaseOut.transform(abs(fraction))
        }

        val trackSurface = when {
            floating && light -> Color(0xFFFAFAFA).copy(alpha = 0.4f)
            floating -> Color(0xFF121212).copy(alpha = 0.4f)
            else -> contentFill(light)
        }
        val trackSpec = if (floating) {
            GlassSpec(blur = 8.dp, lensHeight = 24.dp, lensAmount = 24.dp, surface = trackSurface)
        } else {
            GlassSpec(blur = 0.dp, lensHeight = 0.dp, lensAmount = 0.dp, vibrancy = false, surface = trackSurface, highlightAlpha = 0f, shadowAlpha = 0f)
        }
        var trackOrigin by remember { mutableStateOf(Offset.Zero) }
        val trackSource = remember(backdrop, floating, colors.background) {
            if (floating && backdrop != null) {
                GlassSource(layer = { backdrop.layer }, offset = { trackOrigin - backdrop.originInRoot }, flatColor = colors.background)
            } else {
                // In the content the page colour is what's behind; it's drawn into the hidden copy
                // only, where the lens needs something solid to refract.
                GlassSource(layer = { null }, offset = { Offset.Zero }, flatColor = Color.Transparent)
            }
        }

        val trackRes = rememberGlassResources()
        val copyRes = rememberGlassResources()
        val thumbRes = rememberGlassResources()
        val copyLayer = rememberGraphicsLayer()
        val glowAt = { Offset(insetPx + (motion.value.value + 0.5f) * itemWidth + panelOffset(), with(density) { height.toPx() } / 2f) }

        // The bar itself, with the regular labels.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panelOffset()
                    if (floating && size.width > 0f) {
                        val s = lerp(1f, 1f + trackSwell / size.width, motion.lift.value)
                        scaleX = s
                        scaleY = s
                    }
                }
                .onGloballyPositioned { trackOrigin = it.positionInRoot() }
                .drawGlass(trackRes, trackShape, { trackSpec }, trackSource, press = { motion.lift.value }, pressPosition = glowAt)
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = index == selectedIndex
                            onClick { onSelect(index); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PillLabel(label, bold = false, fontSizeSp = fontSizeSp, icon = icons?.getOrNull(index))
                }
            }
        }

        // The same bar again with every label bold - recorded, never shown directly. The selection
        // pill shows its slice of this, which is how the selected label reads bold, and the lens
        // refracts and enlarges it.
        val pagePath = remember { Path() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .graphicsLayer { alpha = 0f }
                .drawWithContent {
                    copyLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(copyLayer)
                }
                .then(
                    if (floating) {
                        Modifier.drawGlass(copyRes, trackShape, { trackSpec.copy(highlightAlpha = 0f, shadowAlpha = 0f) }, trackSource)
                    } else {
                        Modifier.drawBehind {
                            pagePath.reset()
                            pagePath.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            drawPath(pagePath, colors.background)
                            drawPath(pagePath, trackSurface)
                        }
                    },
                )
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = lerp(1f, LENS_LABEL_SCALE, motion.lift.value)
                            scaleX = s
                            scaleY = s
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PillLabel(label, bold = true, fontSizeSp = fontSizeSp, icon = icons?.getOrNull(index))
                }
            }
        }

        // The selection pill / lens.
        val restTint = if (light) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)
        val thumbSource = remember(copyLayer) {
            GlassSource(
                layer = { copyLayer },
                offset = { Offset(insetPx + motion.value.value * itemWidth, insetPx) },
                flatColor = Color.Transparent,
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = inset)
                .offset { IntOffset((insetPx + motion.value.value * itemWidth + panelOffset()).roundToInt(), 0) }
                .width(itemWidthDp)
                .height(thumbHeight)
                .graphicsLayer {
                    // One uniform scale: it grows as it lifts, but doesn't squash or stretch.
                    scaleX = motion.scale.value
                    scaleY = motion.scale.value
                    alpha = motion.shown.value
                }
                .drawGlass(
                    thumbRes,
                    thumbShape,
                    spec = {
                        val p = motion.lift.value
                        GlassSpec(
                            blur = 0.dp,
                            lensHeight = 10.dp * p,
                            lensAmount = 14.dp * p,
                            dispersion = true,
                            vibrancy = false,
                            surface = restTint.copy(alpha = restTint.alpha * (1f - p)),
                            highlightAlpha = p,
                            shadowRadius = 24.dp,
                            shadowAlpha = 0.1f * p,
                            innerShadowRadius = 8.dp * p,
                            innerShadowAlpha = p,
                        )
                    },
                    source = thumbSource,
                ),
        )

        // Touch: a tap selects; holding the selection lifts it; a drag carries it, following the
        // finger, and it settles on whichever item it's let go over.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(count, itemWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val thumbLeft = insetPx + motion.value.value * itemWidth
                        val onThumb = !motion.hidden && down.position.x in thumbLeft..(thumbLeft + itemWidth)
                        if (onThumb) motion.press()
                        var dragging = false
                        var travelled = 0f
                        var detent = motion.value.value.roundToInt()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.positionChange().x
                            travelled += dx
                            if (!dragging && abs(travelled) > viewConfiguration.touchSlop) {
                                dragging = true
                                motion.dragging = true
                                // From nothing selected, the lens appears under the finger.
                                if (motion.hidden) {
                                    motion.showAt(((change.position.x - insetPx) / itemWidth).toInt().coerceIn(0, count - 1))
                                }
                                if (!onThumb) motion.press()
                            }
                            if (dragging) {
                                change.consume()
                                val target = (change.position.x - insetPx - itemWidth / 2f) / itemWidth
                                motion.follow(target)
                                // A detent each time the lens passes onto another item.
                                val over = target.roundToInt().coerceIn(0, count - 1)
                                if (over != detent) {
                                    detent = over
                                    haptics?.tick()
                                }
                                // Past either end the bar itself gives a little.
                                val x = change.position.x
                                if (x < insetPx || x > widthPx - insetPx) motion.nudgePanel(dx)
                            }
                        }
                        motion.releasePanel()
                        if (dragging) {
                            val index = motion.value.targetValue.roundToInt().coerceIn(0, count - 1)
                            motion.dragging = false
                            motion.settle(index, lifted = true)
                            currentOnSelect(index)
                        } else {
                            val index = ((down.position.x - insetPx) / itemWidth).toInt().coerceIn(0, count - 1)
                            if (motion.hidden) motion.showAt(index) else motion.settle(index, lifted = onThumb)
                            // Always reported, even for the current item: re-tapping File is how
                            // the app backs out of Settings.
                            currentOnSelect(index)
                        }
                    }
                },
        )
    }
}
