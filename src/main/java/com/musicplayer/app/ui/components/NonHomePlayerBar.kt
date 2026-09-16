package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.formatDurationMs

/**
 * The persistent mini player shown above the nav pill on every non-Play screen: dotted
 * separator, song/artist, centered prev/play/next, and a long seek bar beside the battery icon.
 * No shuffle/FX/timer indicators here per spec.
 */
@Composable
fun NonHomePlayerBar(
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        DottedDivider(modifier = Modifier.padding(vertical = 8.dp))
        // Explicit heights on both text rows. Letting them size to their content meant the bar's
        // height - and with it the divider above it - shifted by a pixel or two as songs with
        // different glyphs scrolled past, which read as the separator drifting.
        MarqueeText(
            text = currentSong?.displayName ?: "Start a playlist",
            style = interTextStyle(18, bold = true),
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 18.dp),
        )
        MarqueeText(
            text = currentSong?.artist.orEmpty(),
            style = interTextStyle(14, bold = false),
            modifier = Modifier.fillMaxWidth().height(19.dp).padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        // The gear (File screen only) and the position readout are real Row siblings reserving
        // their own space rather than overlays, so the transport cluster takes only the width
        // that's actually left over and lands where the mockup puts it - right of centre, ending
        // at the same gutter as everything else. On screens without a gear (SFX) an equal-width
        // blank spacer keeps that cluster in the same place between screens.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenSettings != null) {
                GlassIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    circleSize = 35.dp,
                    iconSize = 20.dp,
                )
            } else {
                Spacer(Modifier.size(35.dp))
            }
            if (currentSong != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}",
                    style = interTextStyle(14, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            CompactMainControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomSlider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                valueRange = 0f..1f,
                // Seeking on every touch move restarts buffering continuously, which is what made
                // dragging this feel stepped; the seek is committed once, on release.
                commitOnRelease = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            BatteryIndicator()
        }
    }
}
