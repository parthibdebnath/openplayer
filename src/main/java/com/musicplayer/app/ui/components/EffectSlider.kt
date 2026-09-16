package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle

/**
 * A labeled, stepped slider with a value readout and +/- fine-adjust buttons - the one design
 * used for every effect parameter on the SFX screens (mix, decay, pre-delay, damping, diffusion,
 * semitones, cents, intensity, width, distance, room, center).
 */
@Composable
fun EffectSlider(
    label: String,
    valueText: String,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        CustomSlider(
            value = sliderValue,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = valueText, style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
            Spacer(Modifier.size(16.dp))
            GlassIconButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Decrease $label",
                onClick = onDecrement,
                circleSize = 28.dp,
                iconSize = 20.dp,
                enabled = enabled,
            )
            Spacer(Modifier.size(12.dp))
            GlassIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Increase $label",
                onClick = onIncrement,
                circleSize = 28.dp,
                iconSize = 20.dp,
                enabled = enabled,
            )
        }
    }
}
