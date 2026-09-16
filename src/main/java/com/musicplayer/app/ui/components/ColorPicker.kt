package com.musicplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.LocalAppColors
import com.musicplayer.app.ui.theme.interTextStyle

/** Handful of ready-made backgrounds, so picking something decent takes one tap. */
private val PRESET_COLORS = listOf(
    "#000000", "#101014", "#1B2430", "#12261F",
    "#2B1B2E", "#3A2318", "#F2F2F7", "#FFFFFF",
)

private val HUE_COLORS = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/**
 * Hue strip plus saturation and brightness sliders, with a row of presets above them.
 *
 * Deliberately three one-dimensional controls rather than the usual hue strip + saturation/value
 * square: dragging inside a square is fiddly on a phone, and each axis here can be nudged exactly.
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color) { color.toHsv() }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    // Follow the colour when something else changes it - the hex field, a preset, the daily
    // randomiser - but ignore it coming back round from our own controls, which would otherwise
    // snap the sliders back under the user's finger. Comparing against what was last emitted
    // distinguishes the two without needing to track whether a drag is in progress.
    var lastEmitted by remember { mutableStateOf<Color?>(null) }
    if (color != lastEmitted) {
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        lastEmitted = color
    }

    fun emit() {
        val picked = hsvColor(hue, saturation, value)
        // Stored as it will come back after the hex round-trip, so the comparison above matches.
        lastEmitted = com.musicplayer.app.ui.theme.parseBackgroundColor(picked.toHexString())
        onColorChange(picked)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESET_COLORS.forEach { hex ->
                val presetColor = com.musicplayer.app.ui.theme.parseBackgroundColor(hex)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(presetColor)
                        .border(1.dp, LocalAppColors.current.onBackground.copy(alpha = 0.25f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onColorChange(presetColor) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "Hue", style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        HueStrip(
            hue = hue,
            onHueChange = {
                hue = it
                emit()
            },
        )

        Spacer(Modifier.height(12.dp))
        Text(text = "Saturation", style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground.copy(alpha = 0.6f))
        CustomSlider(
            value = saturation,
            onValueChange = {
                saturation = it
                emit()
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "Brightness", style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground.copy(alpha = 0.6f))
        CustomSlider(
            value = value,
            onValueChange = {
                value = it
                emit()
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HueStrip(hue: Float, onHueChange: (Float) -> Unit) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val markerColor = LocalAppColors.current.onBackground

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(HUE_COLORS))
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onHueChange((offset.x / widthPx).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / widthPx).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val x = (hue / 360f).coerceIn(0f, 1f) * size.width
            drawCircle(color = markerColor, radius = 7.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(
                color = hsvColor(hue, 1f, 1f),
                radius = 5.dp.toPx(),
                center = Offset(x, size.height / 2f),
            )
        }
    }
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

/** `#RRGGBB` for a colour, as the hex field expects it. */
fun Color.toHexString(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
