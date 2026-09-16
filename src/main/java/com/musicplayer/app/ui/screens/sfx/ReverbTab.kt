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
import com.musicplayer.app.data.settings.ReverbEffectState
import com.musicplayer.app.data.settings.ReverbPreset
import com.musicplayer.app.ui.components.EffectSlider
import com.musicplayer.app.ui.components.GlassPillSwitcher
import com.musicplayer.app.ui.components.ToggleRow
import kotlin.math.roundToInt

/**
 * @param onUpdate applies a transform to the *current* stored state. Controls must not build a
 *   whole new state from the one they were composed with: settings round-trip through DataStore,
 *   so a control acting on a snapshot that predates another control's change silently reverts it -
 *   which showed up as sliders moving each other.
 */
@Composable
fun ReverbTab(
    state: ReverbEffectState,
    onUpdate: ((ReverbEffectState) -> ReverbEffectState) -> Unit,
    onApplyPreset: (ReverbPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = ReverbPreset.entries
    val selectedPresetIndex = presets.indexOf(state.matchingPreset())

    Column(modifier = modifier.fillMaxWidth()) {
        ToggleRow(label = "Use Reverb", checked = state.enabled, onToggle = { on -> onUpdate { it.copy(enabled = on) } })
        Spacer(Modifier.height(16.dp))
        GlassPillSwitcher(
            items = presets.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selectedIndex = selectedPresetIndex,
            onSelect = { onApplyPreset(presets[it]) },
            height = 36.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        EffectSlider(
            label = "Mix",
            valueText = "${state.mixPercent}%",
            sliderValue = state.mixPercent.toFloat(),
            onSliderValueChange = { v -> onUpdate { it.copy(mixPercent = v.roundToInt()) } },
            valueRange = 0f..100f,
            steps = 99,
            onIncrement = { onUpdate { it.copy(mixPercent = (it.mixPercent + 1).coerceAtMost(100)) } },
            onDecrement = { onUpdate { it.copy(mixPercent = (it.mixPercent - 1).coerceAtLeast(0)) } },
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EffectSlider(
                label = "Decay",
                valueText = "%.1fs".format(state.decaySeconds),
                sliderValue = state.decaySeconds.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(decaySeconds = (v * 10).roundToInt() / 10.0) } },
                valueRange = 0f..8f,
                steps = 79,
                onIncrement = { onUpdate { it.copy(decaySeconds = ((it.decaySeconds * 10).roundToInt() + 1).coerceAtMost(80) / 10.0) } },
                onDecrement = { onUpdate { it.copy(decaySeconds = ((it.decaySeconds * 10).roundToInt() - 1).coerceAtLeast(0) / 10.0) } },
                modifier = Modifier.weight(1f),
            )
            EffectSlider(
                label = "Pre-Delay",
                valueText = "${state.preDelayMs}ms",
                sliderValue = state.preDelayMs.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(preDelayMs = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(preDelayMs = (it.preDelayMs + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(preDelayMs = (it.preDelayMs - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EffectSlider(
                label = "Damping",
                valueText = "${state.dampingPercent}%",
                sliderValue = state.dampingPercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(dampingPercent = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(dampingPercent = (it.dampingPercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(dampingPercent = (it.dampingPercent - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
            EffectSlider(
                label = "Diffusion",
                valueText = "${state.diffusionPercent}%",
                sliderValue = state.diffusionPercent.toFloat(),
                onSliderValueChange = { v -> onUpdate { it.copy(diffusionPercent = v.roundToInt()) } },
                valueRange = 0f..100f,
                steps = 99,
                onIncrement = { onUpdate { it.copy(diffusionPercent = (it.diffusionPercent + 1).coerceAtMost(100)) } },
                onDecrement = { onUpdate { it.copy(diffusionPercent = (it.diffusionPercent - 1).coerceAtLeast(0)) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
