package io.github.parthibdebnath.openplayer.ui.components

import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle

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
        modifier = modifier
            .fillMaxWidth()
            // The whole row toggles, not just the small icon - and reads to TalkBack as one switch.
            .toggleable(
                value = checked,
                onValueChange = onToggle,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted, so a long label wraps instead of pushing the toggle off the edge.
        Text(
            text = label,
            style = interTextStyle(fontSizeSp, bold = bold),
            color = LocalAppColors.current.onBackground,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        GlassIconButton(
            icon = if (checked) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = "$label: ${if (checked) "on" else "off"}",
            onClick = { onToggle(!checked) },
            circleSize = iconSize,
            iconSize = iconSize * 0.66f,
        )
    }
}
