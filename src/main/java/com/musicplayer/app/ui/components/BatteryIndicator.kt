package com.musicplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.ui.theme.LocalAppColors
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.BatteryMonitor

/** Small battery glyph + percentage text, visible at all times on all screens (bottom-right). */
@Composable
fun BatteryIndicator(modifier: Modifier = Modifier, opacity: Float = 0.5f, fontSizeSp: Int = 14) {
    // Reads a process-wide value rather than owning a receiver: see BatteryMonitor for why.
    val percent by BatteryMonitor.percent.collectAsStateWithLifecycle()
    // Read outside the draw lambda: a DrawScope isn't a composable, so it can't read the local.
    val contentColor = LocalAppColors.current.onBackground
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$percent%",
            style = interTextStyle(fontSizeSp, bold = false),
            color = contentColor.copy(alpha = opacity),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Canvas(modifier = Modifier.size(width = 20.dp, height = 11.dp)) {
            val bodyWidth = size.width * 0.85f
            val nubWidth = size.width - bodyWidth
            val color = contentColor.copy(alpha = opacity)
            drawRoundRect(
                color = color,
                size = Size(bodyWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                style = Stroke(width = 1.5f),
            )
            val nubHeight = size.height * 0.5f
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(bodyWidth + 1f, (size.height - nubHeight) / 2f),
                size = Size(nubWidth - 1f, nubHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
            )
            val inset = 2.5f
            val fillWidth = ((bodyWidth - inset * 2) * (percent.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(fillWidth, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
            )
        }
    }
}
