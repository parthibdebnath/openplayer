package com.musicplayer.app.ui.screens.file

import androidx.compose.runtime.saveable.Saver

sealed class FileDestination {
    data object PlaylistList : FileDestination()
    data object MultiPlaylist : FileDestination()
    data class PlaylistSongs(val playlistId: Long) : FileDestination()
    data object Settings : FileDestination()

    companion object {
        /**
         * Lets the File tab remember where it was. Without it, switching away drops the state and
         * the tab reopens on the playlist list, discarding whatever playlist was being browsed.
         */
        val Saver: Saver<FileDestination, List<Any>> = Saver(
            save = {
                when (it) {
                    PlaylistList -> listOf(0)
                    MultiPlaylist -> listOf(1)
                    Settings -> listOf(2)
                    is PlaylistSongs -> listOf(3, it.playlistId)
                }
            },
            restore = {
                when (it.firstOrNull()) {
                    1 -> MultiPlaylist
                    3 -> PlaylistSongs(it[1] as Long)
                    // Settings deliberately doesn't survive. It's a sub-screen reached from the
                    // gear, and coming back to the File tab should land on the file browser.
                    // Dropping it here rather than resetting after the fact matters: restore runs
                    // before the first frame, so settings never appears and flashes away.
                    else -> PlaylistList
                }
            },
        )
    }
}
