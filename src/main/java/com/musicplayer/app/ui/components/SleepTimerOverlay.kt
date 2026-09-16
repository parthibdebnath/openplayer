package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle

/**
 * The sleep timer entry overlay, opened by double-tap/long-press on the clock. hr/min use the
 * numeric keyboard; confirming with unchanged values resumes the running timer from its exact
 * second, changed values discard and restart it, and 0hr/0min cancels it.
 */
@Composable
fun SleepTimerOverlay(
    initialHours: Int,
    initialMinutes: Int,
    initialPlayLastSongToEnd: Boolean,
    onConfirm: (hours: Int, minutes: Int, playLastSongToEnd: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hoursText by remember { mutableStateOf(initialHours.toString()) }
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }
    var playLastSongToEnd by remember { mutableStateOf(initialPlayLastSongToEnd) }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Sleep Timer", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
            GlassIconButton(
                icon = Icons.Filled.RestartAlt,
                contentDescription = "Dismiss without setting a timer",
                onClick = onDismiss,
                circleSize = 24.dp,
                iconSize = 16.dp,
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = hoursText,
                onValueChange = { if (it.length <= 2) hoursText = it.filter(Char::isDigit) },
                modifier = Modifier.width(56.dp),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "hr", style = interTextStyle(16, bold = false), color = LocalAppColors.current.onBackground)
            Spacer(Modifier.width(16.dp))
            GlassTextField(
                value = minutesText,
                onValueChange = { if (it.length <= 2) minutesText = it.filter(Char::isDigit) },
                modifier = Modifier.width(56.dp),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "min", style = interTextStyle(16, bold = false), color = LocalAppColors.current.onBackground)
            // Confirm sits at the right edge, level with the hr/min fields, per the mockup.
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Confirm sleep timer",
                tint = LocalAppColors.current.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onConfirm(
                            hoursText.toIntOrNull() ?: 0,
                            minutesText.toIntOrNull() ?: 0,
                            playLastSongToEnd,
                        )
                    },
            )
        }
        Spacer(Modifier.size(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Play last song to end?", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
            GlassIconButton(
                icon = if (playLastSongToEnd) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = "Toggle play last song to end",
                onClick = { playLastSongToEnd = !playLastSongToEnd },
                circleSize = 30.dp,
                iconSize = 20.dp,
            )
        }
    }
}
