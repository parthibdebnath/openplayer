package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.data.model.Song

/** A song with just enough filled in for the queue: uri "content://test/songNN", one per id. */
fun song(id: Long, name: String = "s$id") = Song(
    id = id,
    playlistId = 1L,
    documentUri = "content://test/song%02d".format(id),
    fileName = "$name.mp3",
    displayName = name,
    artist = "",
    durationMs = 1_000L,
    sizeBytes = 0L,
    lastModified = 0L,
    sortIndex = 0L,
)

fun songs(ids: LongRange): List<Song> = ids.map { song(it) }

val List<Song>.ids: List<Long> get() = map { it.id }
