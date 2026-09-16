package com.musicplayer.app.ui.screens.file

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.ui.components.GlassIconButton
import com.musicplayer.app.ui.components.NamingDialog
import com.musicplayer.app.ui.components.glassSurface
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.viewmodel.MainViewModel

@Composable
fun MultiPlaylistScreen(
    viewModel: MainViewModel,
    onBackToSingle: () -> Unit,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingFolderUri = uri
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Play from multiple Playlists", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    val isSelected = playlist.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                selectedIds = if (isSelected) selectedIds - playlist.id else selectedIds + playlist.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).glassSurface(shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = LocalAppColors.current.onBackground,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        Spacer(Modifier.size(16.dp))
                        Text(text = playlist.displayName, style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
                    }
                }
            }
            Text(
                text = "Select All/None",
                style = interTextStyle(20, bold = true),
                color = LocalAppColors.current.onBackground,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        selectedIds = if (selectedIds.size == playlists.size) emptySet() else playlists.map { it.id }.toSet()
                    },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
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
                icon = Icons.AutoMirrored.Filled.List,
                contentDescription = "Back to single playlist view",
                onClick = onBackToSingle,
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.size(10.dp))
            GlassIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Play selected playlists in order",
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        viewModel.playMultiPlaylist(selectedIds.toList(), shuffled = false)
                        onPlayed()
                    }
                },
                circleSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.size(10.dp))
            GlassIconButton(
                icon = Icons.Filled.Shuffle,
                contentDescription = "Shuffle selected playlists",
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        viewModel.playMultiPlaylist(selectedIds.toList(), shuffled = true)
                        onPlayed()
                    }
                },
                circleSize = 50.dp,
                iconSize = 25.dp,
            )
        }
    }

    pendingFolderUri?.let { uri ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            NamingDialog(
                title = "Name Playlist",
                initialValue = "",
                onConfirm = { name ->
                    if (name.isNotBlank()) viewModel.createPlaylist(uri, name)
                    pendingFolderUri = null
                },
                modifier = Modifier.padding(top = 110.dp),
            )
        }
    }
    } // outer Box
}
