package com.musicplayer.app.ui.screens.sfx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicplayer.app.data.settings.PitchEffectState
import com.musicplayer.app.ui.components.EffectSlider
import com.musicplayer.app.ui.components.ToggleRow

private fun signed(value: Int): String = if (value <= 0) "-${-value}" else "+$value"

/** @param onUpdate see [ReverbTab] - transforms the current stored state, never a stale snapshot. */
@Composable
fun PitchTab(
    state: PitchEffectState,
    onUpdate: ((PitchEffectState) -> PitchEffectState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ToggleRow(label = "Shift Pitch", checked = state.enabled, onToggle = { on -> onUpdate { it.copy(enabled = on) } })
        Spacer(Modifier.height(24.dp))
        EffectSlider(
            label = "Semitones",
            valueText = "${signed(state.semitones)} st",
            sliderValue = state.semitones.toFloat(),
            onSliderValueChange = { v -> onUpdate { it.copy(semitones = v.toInt()) } },
            valueRange = -12f..12f,
            steps = 23,
            onIncrement = { onUpdate { it.copy(semitones = (it.semitones + 1).coerceAtMost(12)) } },
            onDecrement = { onUpdate { it.copy(semitones = (it.semitones - 1).coerceAtLeast(-12)) } },
        )
        Spacer(Modifier.height(24.dp))
        EffectSlider(
            label = "Fine Tuning",
            valueText = "${signed(state.cents)} cents",
            sliderValue = state.cents.toFloat(),
            onSliderValueChange = { v -> onUpdate { it.copy(cents = v.toInt()) } },
            valueRange = -100f..100f,
            steps = 199,
            onIncrement = { onUpdate { it.copy(cents = (it.cents + 1).coerceAtMost(100)) } },
            onDecrement = { onUpdate { it.copy(cents = (it.cents - 1).coerceAtLeast(-100)) } },
        )
    }
}
