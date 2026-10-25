package io.github.parthibdebnath.openplayer.data.model

/** A user playlist, backed by a folder on-device selected via the Storage Access Framework. */
data class Playlist(
    val id: Long,
    val displayName: String,
    val folderUri: String,
    val number: Int,
    val createdAt: Long,
    val songCount: Int = 0,
    val includeSubfolders: Boolean = false,
)

/**
 * The Favourites list's id where a playlist id is expected - in the File tab's navigation and the
 * viewed playlist. Database ids are positive, and -1 is taken by files opened from other apps.
 */
const val FAVOURITES_PLAYLIST_ID = -2L

/** The Favourites list's name, also shown as the queue label while it plays. */
const val FAVOURITES_LABEL = "Favourites"
