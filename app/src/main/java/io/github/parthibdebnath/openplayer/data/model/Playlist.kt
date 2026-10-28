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

/** A tag on songs, which doubles as a playlist of every song carrying it. */
data class Tag(val id: Long, val name: String, val songCount: Int = 0)

/**
 * Tag lists sit below this where a playlist id is expected, one id per tag: the File tab's
 * navigation and the viewed playlist both key on a single number. Far enough down that the
 * fixed ids above (-1 for opened files, -2 for Favourites) never collide with one.
 */
private const val TAG_PLAYLIST_ID_BASE = -1000L

/** The playlist id standing for [tagId]'s list. */
fun tagPlaylistId(tagId: Long): Long = TAG_PLAYLIST_ID_BASE - tagId

/** The tag a playlist id stands for, or null if it's a folder playlist or Favourites. */
fun tagIdOf(playlistId: Long): Long? = if (playlistId < TAG_PLAYLIST_ID_BASE) TAG_PLAYLIST_ID_BASE - playlistId else null

/** The longest a tag's name can be; long enough for a phrase, short enough to fit in a row. */
const val MAX_TAG_LENGTH = 40

/** Tidies a typed tag: inner runs of spaces collapsed, ends trimmed, cut to [MAX_TAG_LENGTH]. */
fun normalizeTagName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_TAG_LENGTH).trim()
