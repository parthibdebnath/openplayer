package com.musicplayer.app.ui.screens.play

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.audio.OutputDevice
import com.musicplayer.app.ui.components.ArcPlayerCluster
import com.musicplayer.app.ui.components.BatteryIndicator
import com.musicplayer.app.ui.components.GestureType
import com.musicplayer.app.ui.components.GlassNotification
import com.musicplayer.app.ui.components.MarqueeText
import com.musicplayer.app.ui.components.SleepTimerOverlay
import com.musicplayer.app.ui.components.SmallControlsRow
import com.musicplayer.app.ui.components.TopHeader
import com.musicplayer.app.ui.components.UpcomingSongsList
import com.musicplayer.app.ui.components.multiFingerVerticalGesture
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.BrightnessVolumeController
import com.musicplayer.app.util.formatDurationMs
import com.musicplayer.app.viewmodel.MainViewModel

/**
 * Horizontal inset for the screen's text column, matching the gutter the nav pill leaves at the
 * bottom so the song title, time readout and small buttons all share one left alignment line.
 */
private val SCREEN_GUTTER = 18.dp

/**
 * The home/main play screen.
 *
 * Structurally this is a text column layered *under* the arc transport cluster, not stacked above
 * it: on the mockup the arc occupies the bottom-right quadrant at the same height as the time
 * readout and the repeat/shuffle/output buttons on the left. Laying the two out as siblings in one
 * Column was what pushed everything upward and left a dead band across the bottom of the screen.
 *
 * Vertically the column is anchored from the bottom - a single weighted spacer below the playlist
 * name absorbs the whole difference between screen sizes, which is exactly where the mockup's
 * large empty region sits.
 */
@Composable
fun PlayScreen(
    viewModel: MainViewModel,
    brightnessController: BrightnessVolumeController,
    onGestureChanged: (GestureType?, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val effects by viewModel.effectsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val outputDevice by viewModel.outputDevice.collectAsStateWithLifecycle()
    val bluetoothConnected by viewModel.bluetoothConnected.collectAsStateWithLifecycle()

    var upcomingExpanded by remember { mutableStateOf(false) }
    var sleepTimerOpen by remember { mutableStateOf(false) }
    var pendingOutputSwitch by remember { mutableStateOf<OutputDevice?>(null) }

    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gesturePercent by remember { mutableIntStateOf(0) }

    val hasSong = playbackState.currentSong != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .multiFingerVerticalGesture(
                twoFingerEnabled = settings.twoFingerBrightnessGesture,
                threeFingerEnabled = settings.threeFingerVolumeGesture,
                onGestureActiveChanged = { type ->
                    gestureType = type
                    gesturePercent = when (type) {
                        GestureType.BRIGHTNESS -> brightnessController.currentBrightnessPercent()
                        GestureType.VOLUME -> brightnessController.currentVolumePercent()
                        null -> gesturePercent
                    }
                    onGestureChanged(type, gesturePercent)
                },
                onDeltaPercent = { type, delta ->
                    gesturePercent = (gesturePercent + delta).coerceIn(0, 100)
                    when (type) {
                        GestureType.BRIGHTNESS -> brightnessController.setBrightnessPercent(gesturePercent)
                        GestureType.VOLUME -> brightnessController.setVolumePercent(gesturePercent)
                    }
                    onGestureChanged(type, gesturePercent)
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = SCREEN_GUTTER)) {
            TopHeader(
                playlistLabel = playbackState.playlistLabel,
                twentyFourHour = settings.twentyFourHourClock,
                onOpenSleepTimer = { sleepTimerOpen = true },
                overlay = {
                    pendingOutputSwitch?.let { target ->
                        GlassNotification(
                            title = "Switch Output Device?",
                            message = "Are you sure you want to switch to ${if (target == OutputDevice.BLUETOOTH) "bluetooth" else "speaker"}?",
                            onConfirm = {
                                viewModel.switchOutputDevice(target)
                                pendingOutputSwitch = null
                            },
                            onDismiss = { pendingOutputSwitch = null },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                },
            )

            // Per the mockup the sleep timer is a panel directly beneath the playlist name, not a
            // replacement for the next-up list further down the screen.
            if (sleepTimerOpen) {
                val prefill = viewModel.sleepTimerPrefill()
                SleepTimerOverlay(
                    initialHours = if (sleepTimer.isActive) prefill.first else 0,
                    initialMinutes = if (sleepTimer.isActive) prefill.second else 0,
                    initialPlayLastSongToEnd = sleepTimer.playLastSongToEnd,
                    onConfirm = { h, m, playToEnd ->
                        viewModel.confirmSleepTimer(h, m, playToEnd)
                        sleepTimerOpen = false
                    },
                    // Reset cancels any running timer, it doesn't just close the panel.
                    onDismiss = {
                        viewModel.confirmSleepTimer(0, 0, sleepTimer.playLastSongToEnd)
                        sleepTimerOpen = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            // The mockup's large empty region. Everything below this point is bottom-anchored.
            Spacer(Modifier.weight(1f))

            if (hasSong) {
                UpcomingSongsList(
                    upcoming = playbackState.upcoming,
                    expanded = upcomingExpanded,
                    onToggleExpanded = { upcomingExpanded = !upcomingExpanded },
                    onSwapSlot = { viewModel.replaceUpcomingSlot(it) },
                    onReorder = { from, to -> viewModel.reorderUpcoming(from, to) },
                )
            }

            Spacer(Modifier.height(55.dp))

            // Width-limited so a long name marquees within the clear part of the row instead of
            // running underneath the play/next buttons on the arc.
            MarqueeText(
                text = playbackState.currentSong?.displayName ?: "Start a playlist",
                style = interTextStyle(20, bold = true),
                modifier = Modifier.fillMaxWidth(0.66f),
            )

            if (hasSong) {
                MarqueeText(
                    text = playbackState.currentSong?.artist.orEmpty(),
                    style = interTextStyle(16, bold = false),
                    modifier = Modifier.fillMaxWidth(0.50f),
                )
                Spacer(Modifier.height(100.dp))
                Text(
                    text = "${formatDurationMs(playbackState.positionMs)} / ${formatDurationMs(playbackState.durationMs)}",
                    style = interTextStyle(16, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                SmallControlsRow(
                    repeatMode = playbackState.repeatMode,
                    shuffleOn = playbackState.shuffleOn,
                    outputDevice = outputDevice,
                    outputSwitchAvailable = bluetoothConnected,
                    onCycleRepeat = { viewModel.cycleRepeatMode() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleOutput = {
                        pendingOutputSwitch = if (outputDevice == OutputDevice.SPEAKER) OutputDevice.BLUETOOTH else OutputDevice.SPEAKER
                    },
                )
            } else {
                Spacer(Modifier.height(100.dp))
            }

            Spacer(Modifier.height(22.dp))
        }

        ArcPlayerCluster(
            progress = if (playbackState.durationMs > 0) playbackState.positionMs.toFloat() / playbackState.durationMs else 0f,
            onSeek = { fraction -> viewModel.seekTo((fraction * playbackState.durationMs).toLong()) },
            isPlaying = playbackState.isPlaying,
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.skipNext() },
            onPrevious = { viewModel.skipPrevious() },
            modifier = Modifier.fillMaxSize(),
        )

        // Only surfaces when playback has actually failed, so a failure can be read straight off
        // the screen instead of inferred, without cluttering the UI the rest of the time.
        if (playbackState.diagnostics.contains("err=")) {
            Text(
                text = playbackState.diagnostics,
                style = interTextStyle(11, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopStart).padding(top = 2.dp),
            )
        }

        // Sits between the small buttons on the left and the seek arc on the right, clear of both.
        if (settings.shuffleSeedLocked) {
            Text(
                text = "Shuffle Locked",
                style = interTextStyle(14, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = SCREEN_GUTTER, bottom = 56.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (sleepTimer.isActive) {
                Text(text = "Timer on", style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground)
            }
            if (effects.isFxIndicatorOn) {
                Text(text = "FX on", style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground)
            }
            Spacer(Modifier.height(4.dp))
            BatteryIndicator()
        }
    }
}
