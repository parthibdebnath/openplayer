package com.musicplayer.app.ui.screens.file

import com.musicplayer.app.ui.theme.LocalAppColors
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.ui.components.ContextMenuItem
import com.musicplayer.app.ui.components.GlassContextMenu
import com.musicplayer.app.ui.components.GlassIconButton
import com.musicplayer.app.ui.components.GlassNotification
import com.musicplayer.app.ui.components.NamingDialog
import com.musicplayer.app.ui.components.glassSurface
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListScreen(
    viewModel: MainViewModel,
    authGateManager: AuthGateManager,
    onOpenPlaylist: (Long) -> Unit,
    onOpenMultiPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pendingFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var contextMenuPlaylistId by remember { mutableStateOf<Long?>(null) }
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylistId by remember { mutableStateOf<Long?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingFolderUri = uri
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Choose Playlist", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(16.dp))

        // In the column's flow, sharing its gutter, and only ever one at a time - as full-bleed
        // overlays at a fixed offset these drew at a different width to everything else and
        // stacked on top of each other.
        val playlistBeingRenamed = renamingPlaylist
        val folderAwaitingName = pendingFolderUri
        if (playlistBeingRenamed != null) {
            NamingDialog(
                title = "Name Playlist",
                initialValue = playlistBeingRenamed.displayName,
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.renamePlaylist(playlistBeingRenamed.id, name)
                    renamingPlaylist = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else if (folderAwaitingName != null) {
            NamingDialog(
                title = "Name Playlist",
                initialValue = "",
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.createPlaylist(folderAwaitingName, name)
                    pendingFolderUri = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .pointerInput(playlist.id) {
                                detectTapGestures(
                                    onTap = { onOpenPlaylist(playlist.id) },
                                    onLongPress = { contextMenuPlaylistId = playlist.id },
                                )
                            },
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
                        Text(text = playlist.displayName, style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)

                        if (contextMenuPlaylistId == playlist.id) {
                            GlassContextMenu(
                                expanded = true,
                                onDismissRequest = { contextMenuPlaylistId = null },
                                offset = IntOffset(0, 0),
                                items = listOf(
                                    ContextMenuItem(label = "Rename", onClick = { renamingPlaylist = playlist }),
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
                title = "Delete Playlist?",
                message = "This action cannot be reversed",
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
                icon = Icons.Filled.CallMerge,
                contentDescription = "Play multiple playlists",
                onClick = onOpenMultiPlaylist,
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
        }
    }

    } // outer Box
}
