package com.musicplayer.app.ui.screens.file

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.ui.components.NonHomePlayerBar
import com.musicplayer.app.ui.components.TopHeader
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.viewmodel.MainViewModel

/** The File tab: playlist list <-> multi-playlist <-> in-playlist songs <-> settings, plus the shared mini player. */
@Composable
fun FileScreen(
    viewModel: MainViewModel,
    authGateManager: AuthGateManager,
    resetSignal: Int,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable(stateSaver = FileDestination.Saver) {
        mutableStateOf<FileDestination>(FileDestination.PlaylistList)
    }
    val playback by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()

    BackHandler(enabled = destination != FileDestination.PlaylistList) {
        destination = FileDestination.PlaylistList
    }

    // Re-tapping the File tab backs out of Settings (or any other sub-screen).
    //
    // Compared against the last value acted on, not just tested for being non-zero: this screen
    // leaves composition on every tab switch, so the effect is created afresh each time it comes
    // back and would re-fire on a signal it had already handled, throwing away wherever the user
    // had navigated to.
    var lastHandledReset by rememberSaveable { mutableIntStateOf(resetSignal) }
    LaunchedEffect(resetSignal) {
        if (resetSignal != lastHandledReset) {
            lastHandledReset = resetSignal
            destination = FileDestination.PlaylistList
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopHeader(
            playlistLabel = playback.playlistLabel,
            twentyFourHour = settings.twentyFourHourClock,
        )
        Column(modifier = Modifier.weight(1f)) {
            when (val dest = destination) {
                is FileDestination.PlaylistList -> PlaylistListScreen(
                    viewModel = viewModel,
                    authGateManager = authGateManager,
                    onOpenPlaylist = { id -> destination = FileDestination.PlaylistSongs(id) },
                    onOpenMultiPlaylist = { destination = FileDestination.MultiPlaylist },
                )
                is FileDestination.MultiPlaylist -> MultiPlaylistScreen(
                    viewModel = viewModel,
                    onBackToSingle = { destination = FileDestination.PlaylistList },
                    onPlayed = { destination = FileDestination.PlaylistList },
                )
                is FileDestination.PlaylistSongs -> PlaylistSongsScreen(
                    playlistId = dest.playlistId,
                    viewModel = viewModel,
                    authGateManager = authGateManager,
                )
                is FileDestination.Settings -> SettingsScreen(viewModel = viewModel, authGateManager = authGateManager)
            }
        }
        NonHomePlayerBar(
            currentSong = playback.currentSong,
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.skipNext() },
            onPrevious = { viewModel.skipPrevious() },
            onSeek = { viewModel.seekTo(it) },
            // Toggles: tapping the gear while already in Settings goes back.
            onOpenSettings = {
                destination = if (destination is FileDestination.Settings) {
                    FileDestination.PlaylistList
                } else {
                    FileDestination.Settings
                }
            },
        )
    }
}
