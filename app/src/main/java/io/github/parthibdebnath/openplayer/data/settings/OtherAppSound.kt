package io.github.parthibdebnath.openplayer.data.settings

/**
 * What the music does when another app makes a sound of its own - a game's move sounds, a
 * navigation prompt, a notification chime.
 *
 * Android tells us about these through audio focus, and most of them are "transient": the other
 * app wants the speaker for a moment and then hands it back. Pausing for every one of those, as
 * the player used to unconditionally, turned a game's move sounds into a stutter of pause/resume
 * on every move.
 *
 * Whatever the choice, another app that genuinely takes over - starts its own music or video and
 * keeps it going - still pauses playback; see AudioFocusController.
 */
enum class OtherAppSound(val label: String, val description: String) {
    LOWER_VOLUME("Lower volume", "Music dips while the other sound plays"),
    PAUSE("Pause", "Music pauses, then picks up again afterwards"),
    KEEP_PLAYING("Keep playing", "Music carries on at full volume"),
    ;

    companion object {
        fun fromName(name: String?): OtherAppSound = entries.firstOrNull { it.name == name } ?: LOWER_VOLUME
    }
}
