package com.musicplayer.app.ui.screens.sfx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicplayer.app.data.settings.ThreeDEffectState
import com.musicplayer.app.data.settings.formatCenterValue
import com.musicplayer.app.ui.components.EffectSlider
import com.musicplayer.app.ui.components.ToggleRow
import kotlin.math.roundToInt

/** @param onUpdate see [ReverbTab] - transforms the current stored state, never a stale snapshot. */
@Composable
fun ThreeDTab(
    state: ThreeDEffectState,
    onUpdate: ((ThreeDEffectState) -> ThreeDEffectState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ToggleRow(
            label = "Use 3D (Spatial Audio)",
            checked = state.enabled,
            onToggle = { on -> onUpdate { it.copy(enabled = on) } },
        )
        Spacer(Modifier.height(20.dp))
        EffectSlider(
            label = "Intensity",
            valueText = "${state.intensityPercent}%",
            sliderValue = state.intensityPercent.toFloat(),
            onSliderValueChange = { v -> onUpdate { it.copy(intensityPercent = v.roundToInt()) } },
            valueRange = 0f..100f,
            steps = 99,
            onIncrement = { onUpdate { it.copy(intensityPercent = (it.intensityPercent + 1).coerceAtMost(100)) } },
            onDecrement = { onUpdate { it.copy(intensityPercent = (it.intensityPercent - 1).coerceAtLeast(0)) } },
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EffectSlider(
                label = "Width",
                valueText = "${state.widthPercent}%",
                sliderValue = state.widthPercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(widthPercent = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(widthPercent = (it.widthPercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(widthPercent = (it.widthPercent - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
            EffectSlider(
                label = "Distance",
                valueText = "${state.distancePercent}%",
                sliderValue = state.distancePercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(distancePercent = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(distancePercent = (it.distancePercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(distancePercent = (it.distancePercent - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EffectSlider(
                label = "Room",
                valueText = "${state.roomPercent}%",
                sliderValue = state.roomPercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(roomPercent = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(roomPercent = (it.roomPercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(roomPercent = (it.roomPercent - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
            EffectSlider(
                label = "Center",
                valueText = formatCenterValue(state.centerPercent),
                sliderValue = state.centerPercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(centerPercent = v.roundToInt()) } },
                valueRange = -100f..100f,
                steps = 199,
                onIncrement = { onUpdate { it.copy(centerPercent = (it.centerPercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(centerPercent = (it.centerPercent - 1).coerceAtLeast(-100)) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
