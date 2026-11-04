package io.github.parthibdebnath.openplayer.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import io.github.parthibdebnath.openplayer.util.BatteryMonitor
import io.github.parthibdebnath.openplayer.util.LocalHaptics

/** Shared pieces the skins build from. */

/** A tap with no ripple - every skin draws its own pressed state - plus an optional hold. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.skinClick(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = if (onLongClick == null) {
    clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
} else {
    combinedClickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/** A tap with a haptic tick, as the app's own buttons give. */
fun Modifier.skinTap(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    skinClick(interaction, enabled, role) {
        haptics?.click()
        onClick()
    }
}

/** Whether [interaction] is held down right now. */
@Composable
fun pressedState(interaction: MutableInteractionSource): State<Boolean> = interaction.collectIsPressedAsState()

/** The battery level, 0-100. */
@Composable
fun batteryPercent(): Int {
    val percent by BatteryMonitor.percent.collectAsState()
    return percent
}

/**
 * A bar that seeks: tap or drag along it, and the seek is made once, on release (each seek
 * restarts buffering). [draw] paints it at the fraction shown - the finger's while dragging.
 */
@Composable
fun SeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier,
    draw: DrawScope.(Float) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shown = drag ?: fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    drag = (down.position.x / widthPx).coerceIn(0f, 1f)
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        drag = (change.position.x / widthPx).coerceIn(0f, 1f)
                    }
                    drag?.let { currentOnSeek(it) }
                    drag = null
                }
            },
    ) { draw(shown) }
}

/**
 * How far type in a skin's fixed-size chrome - menu bars, taskbars, tabs, tiles - grows with the
 * phone's font size. The chrome is drawn to its original's proportions and doesn't grow; past
 * this the text would spill out of it. Content (lists, titles, settings) scales in full.
 */
private const val CHROME_MAX_FONT_SCALE = 1.15f

/** [size] sp for chrome text, growing with the font size setting only up to [CHROME_MAX_FONT_SCALE]. */
internal fun chromeSp(size: Float, density: androidx.compose.ui.unit.Density): androidx.compose.ui.unit.TextUnit =
    androidx.compose.ui.unit.TextUnit(size * minOf(density.fontScale, CHROME_MAX_FONT_SCALE) / density.fontScale, androidx.compose.ui.unit.TextUnitType.Sp)

/**
 * [content] with its type growing with the font size setting only up to [CHROME_MAX_FONT_SCALE]:
 * for a skin's fixed-size controls - its switchers and mini player - drawn as a whole.
 */
@androidx.compose.runtime.Composable
fun ChromeType(content: @androidx.compose.runtime.Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    if (density.fontScale <= CHROME_MAX_FONT_SCALE) {
        content()
    } else {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, CHROME_MAX_FONT_SCALE),
            content = content,
        )
    }
}
