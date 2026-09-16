package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle

/**
 * A labeled toggle showing a checkmark (on) or cross (off), tapping flips the state - the
 * pattern used for every on/off setting and every effect's local/global enable toggle.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = true,
    fontSizeSp: Int = 20,
    iconSize: androidx.compose.ui.unit.Dp = 30.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = interTextStyle(fontSizeSp, bold = bold), color = LocalAppColors.current.onBackground)
        GlassIconButton(
            icon = if (checked) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = "$label: ${if (checked) "on" else "off"}",
            onClick = { onToggle(!checked) },
            circleSize = iconSize,
            iconSize = iconSize * 0.66f,
        )
    }
}
