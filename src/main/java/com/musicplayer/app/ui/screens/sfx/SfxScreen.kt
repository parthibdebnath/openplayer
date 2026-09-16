package com.musicplayer.app.ui.screens.sfx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.ui.components.GlassPillSwitcher
import com.musicplayer.app.ui.components.NonHomePlayerBar
import com.musicplayer.app.ui.components.ToggleRow
import com.musicplayer.app.ui.components.TopHeader
import com.musicplayer.app.viewmodel.MainViewModel

private val TAB_LABELS = listOf("Speed", "Pitch", "Reverb", "3D")

/** The SFX (effects) screen: tabbed speed/pitch/reverb/3D controls plus the global "All Effects" toggle. */
@Composable
fun SfxScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val effects by viewModel.effectsState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

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
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
            }
            ToggleRow(
                label = "All Effects",
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
        )
    }
}
