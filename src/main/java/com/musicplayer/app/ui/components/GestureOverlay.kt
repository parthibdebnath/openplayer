package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.LocalHazeState
import com.musicplayer.app.ui.theme.interTextStyle
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Full-screen brightness/volume gesture overlay: heavy blur, +/- guides, title, and percent. */
@Composable
fun GestureOverlay(type: GestureType?, percent: Int, modifier: Modifier = Modifier) {
    val hazeState = LocalHazeState.current
    // The content stays composed through the fade-out, by which point `type` is already null.
    // Reading it directly there made a released volume gesture flash the brightness overlay on
    // its way out, so the last real gesture is what gets rendered.
    var lastType by remember { mutableStateOf(GestureType.BRIGHTNESS) }
    LaunchedEffect(type) { if (type != null) lastType = type }
    AnimatedVisibility(
        visible = type != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // A guaranteed-visible solid scrim first, independent of whether the blur behind
                // it renders strongly on this device - then the haze blur layered on top of that.
                .background(Color.Black.copy(alpha = 0.72f))
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        tint = HazeTint(Color.Black.copy(alpha = 0.55f)),
                        blurRadius = 64.dp,
                        fallbackTint = HazeTint(Color.Black.copy(alpha = 0.8f)),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = LocalAppColors.current.onBackground, modifier = Modifier.size(40.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lastType == GestureType.VOLUME) "Volume" else "Brightness",
                        style = interTextStyle(40, bold = true),
                        color = LocalAppColors.current.onBackground,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text(text = "$percent%", style = interTextStyle(40, bold = false), color = LocalAppColors.current.onBackground)
                }
                Icon(Icons.Filled.Remove, contentDescription = null, tint = LocalAppColors.current.onBackground, modifier = Modifier.size(40.dp))
            }
        }
    }
}
