package io.github.parthibdebnath.openplayer.ui.screens.file

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.model.SongSort
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassContextMenu
import io.github.parthibdebnath.openplayer.ui.components.GlassIconButton
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.ui.components.ListScrollbar
import io.github.parthibdebnath.openplayer.ui.components.NamingDialog
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel
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
    val playlistName = playlist?.displayName.orEmpty()
    // Held by the ViewModel so it survives leaving this tab - see MainViewModel.viewedSongs.
    LaunchedEffect(playlistId) { viewModel.onViewPlaylist(playlistId) }
    val songs by viewModel.viewedSongs.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val currentSongId = currentSong?.id
    val scope = rememberCoroutineScope()

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuSongId by remember { mutableStateOf<Long?>(null) }
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    var removingSong by remember { mutableStateOf<Song?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Several files at once, rather than one trip through the picker per song.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addSongs(playlistId, uris)
    }

    // Back closes whichever dialog is open; rename had no way out other than confirming.
    BackHandler(enabled = renamingSong != null) { renamingSong = null }
    BackHandler(enabled = searchOpen && renamingSong == null) {
        searchOpen = false
        searchQuery = ""
    }

    // Remembered: a large playlist is otherwise filtered again on every recomposition, including
    // the ones the position ticker causes. Artists match too.
    val visibleSongs = remember(songs, searchOpen, searchQuery) {
        val query = searchQuery.trim()
        if (searchOpen && query.isNotEmpty()) {
            songs.filter { it.displayName.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        } else {
            songs
        }
    }

    // One scroll position per playlist. A single shared state carried the last playlist's scroll
    // into the next one opened, which then started part-way down for no reason.
    val listState = rememberSaveable(playlistId, saver = LazyListState.Saver) { LazyListState() }
    // A new sort order starts from the top, rather than somewhere arbitrary in the new order.
    var shownSort by rememberSaveable(playlistId) { mutableStateOf(settings.songSort) }
    LaunchedEffect(settings.songSort) {
        if (settings.songSort != shownSort) {
            shownSort = settings.songSort
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = playlistName,
            style = interTextStyle(20, bold = true),
            color = LocalAppColors.current.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Says what it's doing while indexing, so a folder that takes a moment to read doesn't
            // look like an empty playlist.
            Text(
                text = when {
                    scanning && songs.isEmpty() -> "Scanning…"
                    searchOpen && searchQuery.isNotBlank() -> "${visibleSongs.size} of ${songs.size} songs"
                    songs.size == 1 -> "1 song"
                    else -> "${songs.size} songs"
                },
                style = interTextStyle(16, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
            )
            Box {
                Text(
                    text = "Sort: ${settings.songSort.label} ▾",
                    style = interTextStyle(16, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { sortMenuOpen = true },
                )
                GlassContextMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    items = SongSort.entries.map { sort ->
                        ContextMenuItem(
                            label = sort.label,
                            selected = sort == settings.songSort,
                            onClick = { viewModel.setSongSort(sort) },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Both dialogs live here, in the column's flow and sharing its gutter, and only ever one
        // at a time. Rename used to be a full-bleed overlay pinned at an arbitrary offset, so
        // renaming while search was open drew the two on top of each other at different widths.
        val songBeingRenamed = renamingSong
        if (songBeingRenamed != null) {
            NamingDialog(
                title = "Rename song",
                initialValue = songBeingRenamed.displayName,
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.renameSong(songBeingRenamed.id, name.trim())
                    renamingSong = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else if (searchOpen) {
            NamingDialog(
                title = "Search within playlist",
                initialValue = searchQuery,
                // Filters as you type; the tick just puts the keyboard away.
                onTextChange = { searchQuery = it },
                onConfirm = { searchQuery = it },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            val emptyMessage = when {
                songs.isEmpty() && scanning -> null
                songs.isEmpty() -> "No audio files in this folder yet. Tap + to copy some in."
                visibleSongs.isEmpty() -> "No songs match “${searchQuery.trim()}”"
                else -> null
            }
            if (emptyMessage != null) {
                Text(
                    text = emptyMessage,
                    style = interTextStyle(16, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(visibleSongs, key = { it.id }) { song ->
                    val isCurrent = song.id == currentSongId
                    // A file that couldn't be read when it was indexed - empty, damaged, or not audio
                    // at all - is dimmed and says so, rather than looking like any other song and then
                    // being silently skipped when it comes up.
                    val unreadable = song.durationMs < 0
                    Box(modifier = if (unreadable) Modifier.alpha(0.4f) else Modifier) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // A fixed row height, not padding around text: letting rows size
                                // themselves let sub-pixel differences between glyph runs change
                                // the list's height as it scrolled, which nudged the separator
                                // below it up and down.
                                .height(SONG_ROW_HEIGHT)
                                // combinedClickable rather than raw tap detection: it also exposes
                                // the row's tap and long-press to TalkBack and switch access.
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClickLabel = "Song options",
                                    onLongClick = { contextMenuSongId = song.id },
                                    // Plays the list as shown - filtered and sorted - from here.
                                    onClick = { viewModel.playSpecificSong(visibleSongs, playlistName, song) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Both names are held to one line. Previously the row let the title
                            // take whatever width it wanted, which squeezed the artist into a
                            // sliver that wrapped one character per line and stretched the row to
                            // an enormous height.
                            Text(
                                text = song.displayName,
                                // The song that's playing reads bold, so it can be found at a glance.
                                style = interTextStyle(20, bold = isCurrent),
                                color = LocalAppColors.current.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (unreadable) "Can't be read" else song.artist,
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
                                    ContextMenuItem(label = "Play next", onClick = { viewModel.playNext(song) }),
                                    ContextMenuItem(label = "Add to queue", onClick = { viewModel.queueSong(song) }),
                                    ContextMenuItem(label = "Rename", onClick = { renamingSong = song }),
                                    ContextMenuItem(label = "Remove", destructive = true, onClick = {
                                        scope.launch {
                                            val authorized = if (settings.requireAuthToDelete) {
                                                authGateManager.authenticate("Authenticate to remove song")
                                            } else true
                                            if (authorized) removingSong = song
                                        }
                                    }),
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
                message = "It's moved into the folder's removedSongs folder, not deleted.",
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
                contentDescription = "Add songs",
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
                onClick = { viewModel.playPlaylistInOrder(visibleSongs, playlistName) },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                icon = Icons.Filled.Shuffle,
                contentDescription = "Shuffle play",
                onClick = { viewModel.playPlaylistShuffled(visibleSongs, playlistName) },
                circleSize = 50.dp,
                iconSize = 25.dp,
            )
        }
    }
}
