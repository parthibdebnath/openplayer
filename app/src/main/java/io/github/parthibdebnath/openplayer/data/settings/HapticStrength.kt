package io.github.parthibdebnath.openplayer.data.settings

/** How strongly the app taps back when a control is used; see util/Haptics. */
enum class HapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong"),
    ;

    companion object {
        fun fromName(name: String?): HapticStrength = entries.firstOrNull { it.name == name } ?: LIGHT
    }
}
