package io.github.parthibdebnath.openplayer.ui.screens.file

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.ui.components.MarqueeText
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassContextMenu
import io.github.parthibdebnath.openplayer.ui.components.GlassTextField
import io.github.parthibdebnath.openplayer.ui.components.ListScrollbar
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel

/**
 * Search across every playlist at once, by song name or artist. Results say which playlist each
 * song is in; tapping one plays that playlist from it, so playback carries on through the
 * playlist the song came from rather than stopping after it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val favouriteIds by viewModel.favouriteIds.collectAsStateWithLifecycle()
    val playlistNames = remember(playlists) { playlists.associate { it.id to it.displayName } }
    var query by rememberSaveable { mutableStateOf("") }
    var menuSongId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    // An empty box shows no results straight away, rather than the last search's for a moment.
    val results = if (query.isBlank()) emptyList() else searchResults.songs
    // What the results shown actually answer. Describing them against the box instead said
    // "Nothing matches" for a moment on every new search, while it was still being looked up.
    val answeredQuery = searchResults.query

    // The query lives in the ViewModel so results survive recomposition; it's cleared on the way
    // out, so the next search starts empty rather than showing a stale list.
    DisposableEffect(Unit) {
        viewModel.setSearchQuery(query)
        onDispose { viewModel.setSearchQuery("") }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Search", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.setSearchQuery(it)
            },
            autoFocus = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                query.isBlank() || answeredQuery.isEmpty() -> "Song names and artists, in every playlist"
                results.isEmpty() -> "Nothing matches “$answeredQuery”"
                results.size == 1 -> "1 song"
                else -> "${results.size} songs"
            },
            style = interTextStyle(14, bold = false),
            color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { song ->
                    // Holding a result scrolls its name a few times, as well as opening the menu.
                    var titleScrollRequest by remember { mutableIntStateOf(0) }
                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClickLabel = "Song options",
                                    onLongClick = {
                                        menuSongId = song.id
                                        titleScrollRequest++
                                    },
                                    onClick = { viewModel.playSearchResult(song) },
                                )
                                .padding(vertical = 8.dp),
                        ) {
                            MarqueeText(
                                text = song.displayName,
                                style = interTextStyle(18, bold = song.id == currentSong?.id),
                                color = LocalAppColors.current.onBackground,
                                scrollRequest = titleScrollRequest,
                                idleOverflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(song.artist, playlistNames[song.playlistId]).joinToString(" · "),
                                style = interTextStyle(14, bold = false),
                                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (menuSongId == song.id) {
                            GlassContextMenu(
                                expanded = true,
                                onDismissRequest = { menuSongId = null },
                                offset = IntOffset(0, 0),
                                items = listOf(
                                    ContextMenuItem(label = "Play next", onClick = { viewModel.playNext(song) }),
                                    ContextMenuItem(label = "Add to queue", onClick = { viewModel.queueSong(song) }),
                                    ContextMenuItem(
                                        label = if (song.id in favouriteIds) "Unfavourite" else "Favourite",
                                        onClick = { viewModel.toggleFavourite(song) },
                                    ),
                                    ContextMenuItem(label = "Tags\u2026", onClick = { viewModel.openTagEditor(song) }),
                                ),
                            )
                        }
                    }
                }
            }
            ListScrollbar(state = listState, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
