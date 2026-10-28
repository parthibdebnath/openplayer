package io.github.parthibdebnath.openplayer.ui.screens.sfx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.ui.components.GlassPillSwitcher
import io.github.parthibdebnath.openplayer.ui.components.NonHomePlayerBar
import io.github.parthibdebnath.openplayer.ui.components.ToggleRow
import io.github.parthibdebnath.openplayer.ui.components.TopHeader
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel

private val TAB_LABELS = listOf("Speed", "Pitch", "Reverb", "3D")

/** The SFX (effects) screen: tabbed speed/pitch/reverb/3D controls plus the global "All Effects" toggle. */
@Composable
fun SfxScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val effects by viewModel.effectsState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val playbackRestored by viewModel.playbackRestored.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TopHeader(
            playlistLabel = playback.playlistLabel,
            twentyFourHour = settings.twentyFourHourClock,
        )
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            GlassPillSwitcher(
                items = TAB_LABELS,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            // Most of the gap is inside the scroll, not above it: a scroll clips at its edges,
            // and each tab's first toggle sat right on that edge, so its glass swell and shadow
            // were sliced off at the top whenever it was pressed.
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                when (selectedTab) {
                    0 -> SpeedTab(state = effects.speed, onUpdate = { viewModel.updateSpeed(it) })
                    1 -> PitchTab(state = effects.pitch, onUpdate = { viewModel.updatePitch(it) })
                    2 -> ReverbTab(
                        state = effects.reverb,
                        onUpdate = { viewModel.updateReverb(it) },
                        onApplyPreset = { viewModel.applyReverbPreset(it) },
                    )
                    3 -> ThreeDTab(state = effects.threeD, onUpdate = { viewModel.updateThreeD(it) })
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Reset ${TAB_LABELS[selectedTab].lowercase()} to default",
                    style = interTextStyle(16, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            when (selectedTab) {
                                0 -> viewModel.resetSpeed()
                                1 -> viewModel.resetPitch()
                                2 -> viewModel.resetReverb()
                                3 -> viewModel.resetThreeD()
                            }
                        },
                )
                Spacer(Modifier.height(12.dp))
            }
            ToggleRow(
                label = "All effects",
                checked = effects.globalEnabled,
                onToggle = { viewModel.setGlobalEffectsEnabled(it) },
            )
        }
        Spacer(Modifier.height(8.dp))
        NonHomePlayerBar(
            currentSong = playback.currentSong,
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.skipNext() },
            onPrevious = { viewModel.skipPrevious() },
            onSeek = { viewModel.seekTo(it) },
            playbackRestored = playbackRestored,
        )
    }
}
