package io.github.parthibdebnath.openplayer.data.settings

/**
 * The look of the whole app. [DEFAULT] is openplayer's own design (with or without glass); every
 * other entry is an experimental skin modelled on a classic interface. Stored by [name], so the
 * order here can change freely.
 */
enum class UiTheme(val label: String, val inspiration: String) {
    DEFAULT("openplayer", "The app's own look"),
    AQUA("Aqua", "Mac OS X, early 2000s"),
    LUNA("Luna", "Windows XP"),
    CLASSIC("Classic", "Windows 95/98"),
    PLATINUM("Platinum", "Mac OS 9"),
    IPOD("iPod", "Early iPod"),
    METRO("Metro", "Zune HD / Windows Phone 7"),
    XMB("XMB", "PSP / PS3"),
    BLADES("Blades", "Xbox 360"),
    CHANNELS("Channels", "Wii menu"),
    S60("S60", "Nokia Symbian"),
    ;

    companion object {
        fun fromName(name: String?): UiTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
