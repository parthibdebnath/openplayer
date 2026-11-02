package io.github.parthibdebnath.openplayer.ui.skin

import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.skin.aqua.AquaSkin
import io.github.parthibdebnath.openplayer.ui.skin.classic.ClassicSkin
import io.github.parthibdebnath.openplayer.ui.skin.luna.LunaSkin

/** The skin for a theme; null is openplayer's own look. */
fun skinFor(theme: UiTheme): Skin? = when (theme) {
    UiTheme.DEFAULT -> null
    UiTheme.AQUA -> AquaSkin
    UiTheme.LUNA -> LunaSkin
    UiTheme.CLASSIC -> ClassicSkin
    else -> null
}
