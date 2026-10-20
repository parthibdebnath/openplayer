package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.parthibdebnath.openplayer.ui.theme.InterFontFamily
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalGlassEnabled
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The segmented switcher used for File/Play/SFX, the 4 effect tabs, the reverb presets and the
 * other-apps setting: items split the full width into equal segments, each label centered in its
 * own, with the selected one marked by a pill.
 *
 * With glass theming on, that pill is a liquid-glass lens: it magnifies and refracts the labels it
 * passes over, slides on a spring, stretches with its speed like a drop of water, swells while
 * pressed, and can be dragged from one item to another. With it off, it's the flat pill it always
 * was.
 *
 * @param floating whether the switcher floats over the screen (the tab bar). Floating glass
 *   refracts the screen content behind it; the rest sit inside the content and can't.
 */
@Composable
fun GlassPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    fontSizeSp: Float = 13.33f,
    floating: Boolean = false,
    /** Shown in place of the labels when given, which then serve as their descriptions. */
    icons: List<ImageVector>? = null,
) {
    val haptics = LocalHaptics.current
    val select: (Int) -> Unit = { index ->
        if (index != selectedIndex) haptics?.tick()
        onSelect(index)
    }
    if (LocalGlassEnabled.current && USE_APPLE_GLASS) {
        AppleGlassSwitcher(items, selectedIndex, select, modifier, height, fontSizeSp, floating, icons)
    } else if (LocalGlassEnabled.current) {
        LiquidPillSwitcher(items, selectedIndex, select, modifier, height, fontSizeSp, floating, icons)
    } else {
        FlatPillSwitcher(items, selectedIndex, select, modifier, height, fontSizeSp, icons)
    }
}

@Composable
private fun FlatPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    height: Dp,
    fontSizeSp: Float,
    icons: List<ImageVector>?,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .glassSurface(shape = RoundedCornerShape(height / 2), opacity = 0.5f),
    ) {
        val segmentWidth = maxWidth / items.size
        if (selectedIndex in items.indices) {
            // The highlight spans its whole segment - one full 1/N share of the switcher - rather
            // than hugging the label's own text width, which left it looking undersized and
            // inconsistent between short and long labels.
            // Inset on both sides so the highlight never runs flush into the rounded outer edge
            // at the first and last segments.
            val inset = 4.dp
            val animatedX by animateDpAsState(segmentWidth * selectedIndex + inset, tween(220), label = "pillX")
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(height - 8.dp)
                    // Lambda form: the animation then only re-places the pill each frame.
                    .offset { IntOffset(animatedX.roundToPx(), 0) }
                    .width(segmentWidth - inset * 2)
                    .glassSurface(shape = RoundedCornerShape((height - 8.dp) / 2), opacity = 1f),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    PillLabel(label, bold = index == selectedIndex, fontSizeSp = fontSizeSp, icon = icons?.getOrNull(index))
                }
            }
        }
    }
}

/** Icons in a switcher: the size iOS draws tab bar symbols at. */
internal val SWITCHER_ICON_SIZE = 26.dp

@Composable
internal fun PillLabel(label: String, bold: Boolean, fontSizeSp: Float, icon: ImageVector? = null) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = LocalAppColors.current.onBackground,
            modifier = Modifier.size(SWITCHER_ICON_SIZE),
        )
        return
    }
    Text(
        text = label,
        color = LocalAppColors.current.onBackground,
        fontFamily = InterFontFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        maxLines = 1,
    )
}

/** Lens settings for the selector: a magnifier with a strong rim and no blur, so labels stay sharp. */
private val LensStyle = GlassStyle(
    bezel = 9.dp,
    refraction = 10.dp,
    dispersion = 0.16f,
    magnify = 1.06f,
    highlight = 0.75f,
)

/** The floating tab bar refracts and softly blurs the screen behind it. */
private val FloatingTrackStyle = GlassStyle(
    bezel = 12.dp,
    refraction = 16.dp,
    dispersion = 0.1f,
    highlight = 0.7f,
    blur = 6.dp,
)

@Composable
private fun LiquidPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    height: Dp,
    fontSizeSp: Float,
    floating: Boolean,
    icons: List<ImageVector>?,
) {
    val colors = LocalAppColors.current
    val trackShape = RoundedCornerShape(height / 2)
    val lensHeight = height - 8.dp
    val lensShape = RoundedCornerShape(lensHeight / 2)
    val backdrop = LocalGlassBackdrop.current
    var trackOrigin by remember { mutableStateOf(Offset.Zero) }

    // The track: floating over the screen it refracts what's behind; inside the content it's
    // the plain glass material.
    val trackModifier = if (floating && backdrop != null) {
        Modifier
            .onGloballyPositioned { trackOrigin = it.positionInRoot() }
            .liquidGlassLens(
                layer = { backdrop.layer },
                layerOffset = { trackOrigin - backdrop.originInRoot },
                shape = trackShape,
                lightBackground = colors.isLightBackground,
                style = FloatingTrackStyle,
                // Opaque, tinted towards the content colour: the bar is a slab sitting over the
                // page, and must read as one even where there's nothing behind it to bend.
                bodyOverride = lerp(colors.background, colors.onBackground, if (colors.isLightBackground) 0.04f else 0.05f),
            )
    } else {
        Modifier.liquidGlassMaterial(shape = trackShape, lightBackground = colors.isLightBackground, opacity = 0.7f)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height).then(trackModifier)) {
        val density = LocalDensity.current
        val count = items.size.coerceAtLeast(1)
        val segmentPx = with(density) { maxWidth.toPx() } / count
        val insetPx = with(density) { 4.dp.toPx() }
        val lensWidth = maxWidth / count - 8.dp
        fun targetX(index: Int) = segmentPx * index.coerceIn(0, count - 1) + insetPx
        val minX = insetPx
        val maxX = targetX(count - 1)

        val scope = rememberCoroutineScope()
        val lensX = remember { Animatable(targetX(selectedIndex)) }
        var dragging by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }
        LaunchedEffect(selectedIndex, segmentPx) {
            if (!dragging) lensX.animateTo(targetX(selectedIndex), spring(dampingRatio = 0.82f, stiffness = 380f))
        }
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 1.03f else 1f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
            label = "lensPress",
        )

        // The labels are recorded as well as drawn, so the lens can magnify them as it slides over.
        val labels = rememberGraphicsLayer()
        // Opaque, the colour of the track beneath it, so the lens replaces the labels it covers
        // with its own magnified view of them rather than doubling them up.
        val lensBody = lerp(colors.background, Color.White, if (colors.isLightBackground) 0.3f else 0.07f)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    labels.record { this@drawWithContent.drawContent() }
                    drawLayer(labels)
                },
        ) {
            items.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Touch is handled for the whole bar below, so drags can carry the lens
                        // across; this keeps each item reachable as a tab for TalkBack.
                        .semantics {
                            role = Role.Tab
                            selected = index == selectedIndex
                            onClick { onSelect(index); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PillLabel(label, bold = index == selectedIndex, fontSizeSp = fontSizeSp, icon = icons?.getOrNull(index))
                }
            }
        }

        if (selectedIndex in items.indices) {
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(lensHeight)
                    .width(lensWidth)
                    .offset { IntOffset(lensX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        // A moving drop stretches along its path and thins across it; the faster
                        // it goes, the more.
                        val stretch = 1f + (abs(lensX.velocity) / 12000f).coerceAtMost(0.1f)
                        scaleX = pressScale * stretch
                        scaleY = pressScale / sqrt(stretch)
                    }
                    .liquidGlassLens(
                        layer = { labels },
                        layerOffset = { Offset(lensX.value, insetPx) },
                        shape = lensShape,
                        lightBackground = colors.isLightBackground,
                        style = LensStyle,
                        bodyOverride = lensBody,
                    ),
            )
        }

        // Taps select; a drag carries the lens and settles on whichever item it's let go over.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(count, segmentPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        var moved = 0f
                        var isDrag = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.positionChange().x
                            moved += dx
                            if (!isDrag && abs(moved) > viewConfiguration.touchSlop) {
                                isDrag = true
                                dragging = true
                            }
                            if (isDrag) {
                                change.consume()
                                val next = (lensX.value + dx).coerceIn(minX, maxX)
                                scope.launch { lensX.snapTo(next) }
                            }
                        }
                        pressed = false
                        val index = if (isDrag) {
                            ((lensX.value - insetPx) / segmentPx).roundToInt()
                        } else {
                            (down.position.x / segmentPx).toInt()
                        }.coerceIn(0, count - 1)
                        dragging = false
                        if (index == selectedIndex) {
                            scope.launch { lensX.animateTo(targetX(index), spring(dampingRatio = 0.82f, stiffness = 380f)) }
                        }
                        onSelect(index)
                    }
                },
        )
    }
}
