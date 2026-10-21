package io.github.parthibdebnath.openplayer.ui.screens.file

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel

/**
 * The library's questions, shown over whichever File screen is up: whether to restore a backup
 * from an earlier install found in a folder just added, then the folder of each restored or
 * imported playlist that can't be read yet. Here rather than in Settings, because a restore
 * starts on the playlist list, where the folder was added.
 */
@Composable
fun LibraryPrompts(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val offer by viewModel.restoreOffer.collectAsStateWithLifecycle()
    val foldersToRepick by viewModel.foldersToRepick.collectAsStateWithLifecycle()
    val repickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onFolderRepicked(uri)
    }
    // Backed with the page colour: these float over the screen, and an outline-only surface (glass
    // off, icon backgrounds hidden) left them unreadable on top of it.
    val promptModifier = modifier
        .padding(horizontal = 18.dp, vertical = 12.dp)
        .background(LocalAppColors.current.background, RoundedCornerShape(10.dp))

    val pending = offer
    if (pending != null) {
        val count = pending.backup.playlists.size
        val what = buildList {
            if (count > 0) add(if (count == 1) "1 playlist" else "$count playlists")
            if (!pending.backup.settings.isNullOrEmpty()) add("your settings")
        }.joinToString(" and ")
        key(pending.folderUri) {
            GlassNotification(
                title = "Restore your library?",
                message = "This folder has a backup from an earlier install of openplayer: $what. Swipe away to start fresh.",
                onConfirm = { viewModel.acceptRestore() },
                onDismiss = { viewModel.declineRestore() },
                modifier = promptModifier,
            )
        }
        return
    }
    foldersToRepick.firstOrNull()?.let { playlist ->
        // Keyed, so each prompt starts afresh: the notification remembers being swiped away, and
        // without this the next prompt arrived already dismissed.
        key(playlist.folderUri) {
            GlassNotification(
                title = "Find “${playlist.name}”",
                message = "Pick its folder so openplayer can read it here. Swipe away to skip it.",
                onConfirm = { repickLauncher.launch(folderPickerStart(playlist.folderUri)) },
                onDismiss = { viewModel.onFolderRepicked(null) },
                modifier = promptModifier,
            )
        }
    }
}

/**
 * Where the folder picker should open for a saved folder. The picker wants the folder's document,
 * not the tree link that was saved, and given the tree it opened at the top of storage instead.
 */
private fun folderPickerStart(folderUri: String): Uri? = runCatching {
    val tree = Uri.parse(folderUri)
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
}.getOrNull()
