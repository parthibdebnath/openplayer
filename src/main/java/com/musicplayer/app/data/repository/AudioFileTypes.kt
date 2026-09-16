package com.musicplayer.app.data.repository

/** Extensions accepted as playable audio when a SAF document provider reports a generic/missing MIME type. */
val KNOWN_AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "wav", "wave", "ogg", "oga", "opus",
    "wma", "aiff", "aif", "amr", "mid", "midi", "3gp", "3gpp", "mka", "ape",
)

/** The subfolder (relative to a playlist's root folder) that removed songs are moved into. */
const val REMOVED_SONGS_FOLDER_NAME = "removedSongs"

fun isAudioFile(name: String, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.startsWith("audio/")) return true
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext in KNOWN_AUDIO_EXTENSIONS
}
