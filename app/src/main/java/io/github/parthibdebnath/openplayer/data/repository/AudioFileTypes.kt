package io.github.parthibdebnath.openplayer.data.repository

/** Extensions accepted as playable audio when a SAF document provider reports a generic/missing MIME type. */
val KNOWN_AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus",
    "aiff", "aif", "amr", "3gp", "3gpp", "mka",
)

/**
 * Audio the player has no decoder for, and playlist files. Listed, these showed up as songs and
 * then always failed and got skipped, so they're left out of the index instead.
 */
private val UNSUPPORTED_EXTENSIONS = setOf("wma", "ape", "mid", "midi", "kar", "m3u", "m3u8", "pls")
private val UNSUPPORTED_MIME_TYPES = setOf(
    "audio/x-ms-wma", "audio/x-ape", "audio/ape", "audio/midi", "audio/x-midi", "audio/sp-midi",
    // Playlist files report audio types too, but they're lists of songs, not songs.
    "audio/x-mpegurl", "audio/mpegurl", "audio/x-scpls",
)

/** The subfolder (relative to a playlist's root folder) that removed songs are moved into. */
const val REMOVED_SONGS_FOLDER_NAME = "removedSongs"

fun isAudioFile(name: String, mimeType: String?): Boolean {
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (ext in UNSUPPORTED_EXTENSIONS || mimeType?.lowercase() in UNSUPPORTED_MIME_TYPES) return false
    if (mimeType != null && mimeType.startsWith("audio/")) return true
    return ext in KNOWN_AUDIO_EXTENSIONS
}
