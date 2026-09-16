package com.musicplayer.app.ui.screens.sfx

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicplayer.app.data.settings.SPEED_STEPS
import com.musicplayer.app.data.settings.SpeedEffectState
import com.musicplayer.app.ui.components.CustomSlider
import com.musicplayer.app.ui.components.GlassIconButton
import com.musicplayer.app.ui.components.ToggleRow
import com.musicplayer.app.ui.components.glassSurface
import com.musicplayer.app.ui.theme.interTextStyle
import kotlin.math.roundToInt

/** @param onUpdate see [ReverbTab] - transforms the current stored state, never a stale snapshot. */
@Composable
fun SpeedTab(
    state: SpeedEffectState,
    onUpdate: ((SpeedEffectState) -> SpeedEffectState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = SPEED_STEPS.indexOf(state.speed).let { if (it < 0) 2 else it }

    Column(modifier = modifier.fillMaxWidth()) {
        ToggleRow(label = "Change Speed", checked = state.enabled, onToggle = { on -> onUpdate { it.copy(enabled = on) } })
        Spacer(Modifier.height(20.dp))
        ToggleRow(
            label = "Preserve Pitch",
            checked = state.preservePitch,
            onToggle = { on -> onUpdate { it.copy(preservePitch = on) } },
            bold = false,
        )
        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SPEED_STEPS.forEachIndexed { index, speed ->
                val isDefault = index == 2
                SpeedStepButton(
                    label = formatSpeedLabel(speed),
                    size = if (isDefault) 50.dp else 40.dp,
                    verticalOffset = if (isDefault) 0.dp else 14.dp,
                    onClick = { onUpdate { it.copy(speed = speed) } },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        CustomSlider(
            value = currentIndex.toFloat(),
            onValueChange = { v -> onUpdate { it.copy(speed = SPEED_STEPS[v.roundToInt().coerceIn(0, SPEED_STEPS.lastIndex)]) } },
            valueRange = 0f..(SPEED_STEPS.size - 1).toFloat(),
            steps = SPEED_STEPS.size - 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(text = "${formatSpeedLabel(state.speed)}x", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
            Spacer(Modifier.size(16.dp))
            GlassIconButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Decrease speed",
                onClick = { onUpdate { it.copy(speed = SPEED_STEPS[(SPEED_STEPS.indexOf(it.speed) - 1).coerceIn(0, SPEED_STEPS.lastIndex)]) } },
                circleSize = 28.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.size(12.dp))
            GlassIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Increase speed",
                onClick = { onUpdate { it.copy(speed = SPEED_STEPS[(SPEED_STEPS.indexOf(it.speed) + 1).coerceIn(0, SPEED_STEPS.lastIndex)]) } },
                circleSize = 28.dp,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun SpeedStepButton(label: String, size: Dp, verticalOffset: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(y = verticalOffset)
            .size(size)
            .glassSurface(shape = CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = interTextStyle(if (size >= 50.dp) 16 else 14, bold = true), color = LocalAppColors.current.onBackground)
    }
}

private fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toLong().toFloat()) speed.toLong().toString() else speed.toString()
