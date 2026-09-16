package com.musicplayer.app.ui.screens.file

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.components.ContextMenuItem
import com.musicplayer.app.ui.components.GlassContextMenu
import com.musicplayer.app.ui.components.GlassIconButton
import com.musicplayer.app.ui.components.GlassNotification
import com.musicplayer.app.ui.components.ListScrollbar
import com.musicplayer.app.ui.components.NamingDialog
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private val SONG_ROW_HEIGHT = 36.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongsScreen(
    playlistId: Long,
    viewModel: MainViewModel,
    authGateManager: AuthGateManager,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    // Held by the ViewModel so it survives leaving this tab - see MainViewModel.viewedSongs.
    LaunchedEffect(playlistId) { viewModel.onViewPlaylist(playlistId) }
    val songs by viewModel.viewedSongs.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuSongId by remember { mutableStateOf<Long?>(null) }
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    var removingSong by remember { mutableStateOf<Song?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addSongFromDocument(playlistId, uri)
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchQuery = ""
    }

    val visibleSongs = if (searchOpen && searchQuery.isNotBlank()) {
        songs.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    } else {
        songs
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = playlist?.displayName.orEmpty(), style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
            Text(text = "${songs.size} songs", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(20.dp))

        // Both dialogs live here, in the column's flow and sharing its gutter, and only ever one
        // at a time. Rename used to be a full-bleed overlay pinned at an arbitrary offset, so
        // renaming while search was open drew the two on top of each other at different widths.
        val songBeingRenamed = renamingSong
        if (songBeingRenamed != null) {
            NamingDialog(
                title = "Name Song",
                initialValue = songBeingRenamed.displayName,
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.renameSong(songBeingRenamed.id, name)
                    renamingSong = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else if (searchOpen) {
            NamingDialog(
                title = "Search within playlist",
                initialValue = searchQuery,
                onConfirm = { searchQuery = it },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(visibleSongs, key = { it.id }) { song ->
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // A fixed row height, not padding around text: letting rows size
                                // themselves let sub-pixel differences between glyph runs change
                                // the list's height as it scrolled, which nudged the separator
                                // below it up and down.
                                .height(SONG_ROW_HEIGHT)
                                .pointerInput(song.id) {
                                    detectTapGestures(
                                        onTap = { viewModel.playSpecificSong(songs, playlist?.displayName.orEmpty(), song) },
                                        onLongPress = { contextMenuSongId = song.id },
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Both names are held to one line. Previously the row let the title
                            // take whatever width it wanted, which squeezed the artist into a
                            // sliver that wrapped one character per line and stretched the row to
                            // an enormous height.
                            Text(
                                text = song.displayName,
                                style = interTextStyle(20, bold = false),
                                color = LocalAppColors.current.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = song.artist,
                                style = interTextStyle(20, bold = false),
                                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 130.dp),
                            )
                        }

                        if (contextMenuSongId == song.id) {
                            GlassContextMenu(
                                expanded = true,
                                onDismissRequest = { contextMenuSongId = null },
                                offset = IntOffset(0, 0),
                                items = listOf(
                                    ContextMenuItem(label = "Rename", onClick = { renamingSong = song }),
                                    ContextMenuItem(label = "Remove", destructive = true, onClick = {
                                        scope.launch {
                                            val authorized = if (settings.requireAuthToDelete) {
                                                authGateManager.authenticate("Authenticate to remove song")
                                            } else true
                                            if (authorized) removingSong = song
                                        }
                                    }),
                                    ContextMenuItem(label = "Queue", onClick = { viewModel.queueSong(song) }),
                                ),
                            )
                        }
                    }
                }
            }
            ListScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        removingSong?.let { song ->
            GlassNotification(
                title = "Remove song from playlist?",
                message = "This will move it to a removed songs folder",
                onConfirm = {
                    viewModel.removeSong(song)
                    removingSong = null
                },
                onDismiss = { removingSong = null },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.Center,
            // Without this the row aligns tops, so the larger shuffle button sat lower than the
            // other three.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add song",
                onClick = { filePicker.launch(arrayOf("audio/*")) },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search within playlist",
                onClick = {
                    if (searchOpen) searchQuery = ""
                    searchOpen = !searchOpen
                },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Play in order",
                onClick = { viewModel.playPlaylistInOrder(songs, playlist?.displayName.orEmpty()) },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = Icons.Filled.Shuffle,
                contentDescription = "Shuffle play",
                onClick = { viewModel.playPlaylistShuffled(songs, playlist?.displayName.orEmpty()) },
                circleSize = 50.dp,
                iconSize = 25.dp,
            )
        }
    }

    } // outer Box
}
