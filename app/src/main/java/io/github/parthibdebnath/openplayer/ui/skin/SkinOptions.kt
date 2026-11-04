package io.github.parthibdebnath.openplayer.ui.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.parthibdebnath.openplayer.data.settings.SettingsSnapshot
import io.github.parthibdebnath.openplayer.data.settings.UiTheme

/**
 * A setting a skin offers because its original system offered it: Windows Phone's accent colour,
 * the PS3's background colour, a Windows colour scheme. Shown in Settings under the theme picker
 * while that theme is in use.
 */
@Immutable
class SkinOption(
    /** Stored under this name, per theme; never change it once shipped. */
    val key: String,
    val label: String,
    val choices: List<SkinChoice>,
    val style: Style,
    val note: String? = null,
) {
    init {
        require(choices.isNotEmpty()) { "An option needs at least one choice" }
        require(choices.map { it.id }.distinct().size == choices.size) { "Choice ids must be unique" }
    }

    /** The choice a new install starts with. */
    val default: SkinChoice get() = choices.first()

    enum class Style {
        /** A row of coloured squares, for a single colour. */
        Swatches,

        /** A list to pick one from, each with a colour chip if it has one. */
        List,

        /** Two or three words side by side. */
        Segmented,
    }
}

@Immutable
class SkinChoice(
    /** Stored as this; never change it once shipped. */
    val id: String,
    val label: String,
    val swatch: Color? = null,
)

/** What's been picked for each of a theme's options, by option key. Unknown or missing ids read as the default. */
@Immutable
data class SkinChoices(val values: Map<String, String> = emptyMap()) {
    fun of(option: SkinOption): SkinChoice = option.choices.firstOrNull { it.id == values[option.key] } ?: option.default

    /** Just the values this theme's [options] use, normalised to known ids, so equal looks compare equal. */
    fun normalisedFor(options: List<SkinOption>): SkinChoices = SkinChoices(options.associate { it.key to of(it).id })

    companion object {
        /** A theme's choices out of every theme's ([SettingsSnapshot.skinChoices], keyed "THEME.option"). */
        fun forTheme(theme: UiTheme, stored: Map<String, String>): SkinChoices {
            val prefix = theme.name + "."
            return SkinChoices(stored.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) })
        }
    }
}
