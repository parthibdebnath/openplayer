package io.github.parthibdebnath.openplayer.ui.screens.play

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import io.github.parthibdebnath.openplayer.audio.OutputDevice
import io.github.parthibdebnath.openplayer.ui.components.ArcPlayerCluster
import io.github.parthibdebnath.openplayer.ui.components.BatteryIndicator
import io.github.parthibdebnath.openplayer.ui.components.GestureType
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.ui.components.MarqueeText
import io.github.parthibdebnath.openplayer.ui.components.SleepTimerOverlay
import io.github.parthibdebnath.openplayer.ui.components.SmallControlsRow
import io.github.parthibdebnath.openplayer.ui.components.TopHeader
import io.github.parthibdebnath.openplayer.ui.components.UpcomingSongsList
import io.github.parthibdebnath.openplayer.ui.components.multiFingerVerticalGesture
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.BrightnessVolumeController
import io.github.parthibdebnath.openplayer.util.formatDurationMs
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel

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
    onOpenLibrary: () -> Unit,
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

    // Held only while this screen is showing and music is playing: leaving the tab, pausing, or
    // the screen going away all let it time out again.
    val view = LocalView.current
    val keepAwake = settings.keepScreenOn && playbackState.isPlaying
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

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
                            title = "Switch output device?",
                            message = "Play through ${if (target == OutputDevice.BLUETOOTH) "Bluetooth" else "the phone speaker"} instead?",
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
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    // With nothing loaded, the prompt itself leads to the playlists.
                    .then(
                        if (hasSong) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "Open playlists",
                                onClick = onOpenLibrary,
                            )
                        },
                    ),
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

        // Only while playback has actually failed, and gone again once something plays.
        // An error outranks a notice; both clear once something plays.
        (playbackState.error?.let { "Can't play this song: $it" } ?: playbackState.notice)?.let { message ->
            Text(
                text = message,
                style = interTextStyle(12, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = SCREEN_GUTTER, top = 2.dp),
            )
        }

        // Sits between the small buttons on the left and the seek arc on the right, clear of both.
        if (settings.shuffleSeedLocked) {
            Text(
                text = "Shuffle locked",
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
