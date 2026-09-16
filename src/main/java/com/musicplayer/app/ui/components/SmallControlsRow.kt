package com.musicplayer.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicplayer.app.audio.OutputDevice
import com.musicplayer.app.audio.RepeatMode

/** The repeat/shuffle/output-device row, left-aligned above the time position text on the Play screen. */
@Composable
fun SmallControlsRow(
    repeatMode: RepeatMode,
    shuffleOn: Boolean,
    outputDevice: OutputDevice,
    /** Only true when a Bluetooth media output is actually connected - with nothing to switch to, there's no button. */
    outputSwitchAvailable: Boolean,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = "Repeat: $repeatMode",
            onClick = onCycleRepeat,
            circleSize = 35.dp,
            iconSize = 20.dp,
            opacity = if (repeatMode == RepeatMode.OFF) 0.5f else 1f,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        GlassIconButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle",
            onClick = onToggleShuffle,
            circleSize = 35.dp,
            iconSize = 20.dp,
            opacity = if (shuffleOn) 1f else 0.5f,
        )
        if (outputSwitchAvailable) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = if (outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker,
                contentDescription = "Output device",
                onClick = onToggleOutput,
                circleSize = 35.dp,
                iconSize = 20.dp,
            )
        }
    }
}
