package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.audio.OutputDevice

/**
 * The shuffle/favourite/output-device row, left-aligned above the time position text on the Play
 * screen. There's no repeat button: it was never used, and playback always runs with repeat off.
 */
@Composable
fun SmallControlsRow(
    shuffleOn: Boolean,
    outputDevice: OutputDevice,
    /** Only true when a Bluetooth media output is actually connected - with nothing to switch to, there's no button. */
    outputSwitchAvailable: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleOutput: () -> Unit,
    /** Whether the current song is a favourite; null when it can't be one (nothing loaded, or a file from another app). */
    isFavourite: Boolean?,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle",
            onClick = onToggleShuffle,
            circleSize = 35.dp,
            iconSize = 20.dp,
            opacity = if (shuffleOn) 1f else 0.5f,
        )
        if (isFavourite != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavourite) "Unfavourite" else "Favourite",
                onClick = onToggleFavourite,
                circleSize = 35.dp,
                iconSize = 20.dp,
                opacity = if (isFavourite) 1f else 0.5f,
            )
        }
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
