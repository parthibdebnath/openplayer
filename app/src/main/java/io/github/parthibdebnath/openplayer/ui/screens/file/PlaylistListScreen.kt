package io.github.parthibdebnath.openplayer.ui.screens.file

import androidx.compose.material.icons.filled.Search
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.data.model.FAVOURITES_LABEL
import io.github.parthibdebnath.openplayer.data.model.FAVOURITES_PLAYLIST_ID
import io.github.parthibdebnath.openplayer.data.model.Playlist
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassContextMenu
import io.github.parthibdebnath.openplayer.ui.components.GlassIconButton
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.ui.components.NamingDialog
import io.github.parthibdebnath.openplayer.ui.components.rememberFolderDisplayName
import io.github.parthibdebnath.openplayer.ui.components.glassSurface
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListScreen(
    viewModel: MainViewModel,
    authGateManager: AuthGateManager,
    onOpenPlaylist: (Long) -> Unit,
    onOpenMultiPlaylist: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null until playlists and favourites have both been read. Starting from empty lists showed
    // "No playlists yet" for a moment on the first visit, and then dropped the Favourites row in
    // above the playlists once it arrived.
    val overview by viewModel.libraryOverview.collectAsStateWithLifecycle()
    val playlists = overview?.playlists.orEmpty()
    val favourites = overview?.favourites.orEmpty()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val playingPlaylistId = currentSong?.playlistId
    var favouritesMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var pendingFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var contextMenuPlaylistId by remember { mutableStateOf<Long?>(null) }
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylistId by remember { mutableStateOf<Long?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingFolderUri = uri
    }

    // Back cancels naming; these dialogs had no way out other than confirming.
    BackHandler(enabled = renamingPlaylist != null || pendingFolderUri != null) {
        renamingPlaylist = null
        pendingFolderUri = null
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Playlists", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(16.dp))

        // In the column's flow, sharing its gutter, and only ever one at a time - as full-bleed
        // overlays at a fixed offset these drew at a different width to everything else and
        // stacked on top of each other.
        val playlistBeingRenamed = renamingPlaylist
        val folderAwaitingName = pendingFolderUri
        if (playlistBeingRenamed != null) {
            NamingDialog(
                title = "Rename playlist",
                initialValue = playlistBeingRenamed.displayName,
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.renamePlaylist(playlistBeingRenamed.id, name.trim())
                    renamingPlaylist = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else if (folderAwaitingName != null) {
            NamingDialog(
                title = "Name playlist",
                initialValue = rememberFolderDisplayName(folderAwaitingName),
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.createPlaylist(folderAwaitingName, name.trim())
                    pendingFolderUri = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (overview != null && playlists.isEmpty()) {
                // First run: say what a playlist is here and how to make one, instead of a blank page.
                Text(
                    text = "No playlists yet.\n\nTap + below and pick a folder of music - each folder becomes a playlist. " +
                        "Your files stay where they are.",
                    style = interTextStyle(16, bold = false),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn {
                // Only once something has been hearted, so a library that doesn't use favourites
                // isn't given an empty list it never asked for.
                if (favourites.isNotEmpty()) {
                    item(key = "favourites") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClickLabel = "Favourites options",
                                    onLongClick = { favouritesMenuOpen = true },
                                    onClick = { onOpenPlaylist(FAVOURITES_PLAYLIST_ID) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp).glassSurface(shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = LocalAppColors.current.onBackground,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = FAVOURITES_LABEL,
                                    style = interTextStyle(20, bold = false),
                                    color = LocalAppColors.current.onBackground,
                                    maxLines = 1,
                                )
                                Text(
                                    text = if (favourites.size == 1) "1 song" else "${favourites.size} songs",
                                    style = interTextStyle(14, bold = false),
                                    color = LocalAppColors.current.onBackground.copy(alpha = 0.55f),
                                    maxLines = 1,
                                )
                            }
                            if (favouritesMenuOpen) {
                                GlassContextMenu(
                                    expanded = true,
                                    onDismissRequest = { favouritesMenuOpen = false },
                                    offset = IntOffset(0, 0),
                                    items = listOf(
                                        ContextMenuItem(label = "Play", onClick = { viewModel.playFavourites(shuffled = false) }),
                                        ContextMenuItem(label = "Shuffle", onClick = { viewModel.playFavourites(shuffled = true) }),
                                    ),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            // combinedClickable rather than raw tap detection: it also exposes the
                            // row's tap and long-press to TalkBack and switch access.
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClickLabel = "Playlist options",
                                onLongClick = { contextMenuPlaylistId = playlist.id },
                                onClick = { onOpenPlaylist(playlist.id) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).glassSurface(shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Numbered by position in the list, not by a number stored at creation
                            // time - otherwise deleting a playlist leaves a gap in the sequence.
                            Text(text = (index + 1).toString(), style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
                        }
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.displayName,
                                // The playlist that's playing reads bold, so it can be found at a glance.
                                style = interTextStyle(20, bold = playlist.id == playingPlaylistId),
                                color = LocalAppColors.current.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    playlist.songCount == 0 && scanning -> "Scanning\u2026"
                                    playlist.songCount == 1 -> "1 song"
                                    else -> "${playlist.songCount} songs"
                                } + if (playlist.id == playingPlaylistId) " \u00B7 Playing" else "",
                                style = interTextStyle(14, bold = false),
                                color = LocalAppColors.current.onBackground.copy(alpha = 0.55f),
                                maxLines = 1,
                            )
                        }

                        if (contextMenuPlaylistId == playlist.id) {
                            GlassContextMenu(
                                expanded = true,
                                onDismissRequest = { contextMenuPlaylistId = null },
                                offset = IntOffset(0, 0),
                                items = listOf(
                                    // Playing straight from here saves opening the playlist first.
                                    ContextMenuItem(label = "Play", onClick = { viewModel.playPlaylist(playlist.id, shuffled = false) }),
                                    ContextMenuItem(label = "Shuffle", onClick = { viewModel.playPlaylist(playlist.id, shuffled = true) }),
                                    // Picks up files added to the folder since the app was opened.
                                    ContextMenuItem(label = "Refresh", onClick = { viewModel.rescanPlaylist(playlist.id) }),
                                    ContextMenuItem(label = "Rename", onClick = { renamingPlaylist = playlist }),
                                    // Songs in folders inside this one, for a library laid out
                                    // by artist or album. Ticked while it's on.
                                    ContextMenuItem(
                                        label = "Include subfolders",
                                        selected = playlist.includeSubfolders,
                                        onClick = { viewModel.setIncludeSubfolders(playlist.id, !playlist.includeSubfolders) },
                                    ),
                                    ContextMenuItem(label = "Delete", destructive = true, onClick = {
                                        scope.launch {
                                            val authorized = if (settings.requireAuthToDelete) {
                                                authGateManager.authenticate("Authenticate to delete playlist")
                                            } else true
                                            if (authorized) deletingPlaylistId = playlist.id
                                        }
                                    }),
                                ),
                            )
                        }
                    }
                }
            }
        }

        deletingPlaylistId?.let { id ->
            GlassNotification(
                title = "Delete playlist?",
                // Says what actually happens: only the link is removed, never the files.
                message = "It's removed from openplayer. The folder and its songs aren't touched.",
                onConfirm = {
                    viewModel.deletePlaylist(id)
                    deletingPlaylistId = null
                },
                onDismiss = { deletingPlaylistId = null },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add playlist",
                onClick = { folderPicker.launch(null) },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.size(10.dp))
            GlassIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search all playlists",
                onClick = onOpenSearch,
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.size(10.dp))
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.CallMerge,
                contentDescription = "Play multiple playlists",
                onClick = onOpenMultiPlaylist,
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
        }
    }

    } // outer Box
}
