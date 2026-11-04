package io.github.parthibdebnath.openplayer.ui.skin

import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.skin.aqua.AquaSkin
import io.github.parthibdebnath.openplayer.ui.skin.blades.BladesSkin
import io.github.parthibdebnath.openplayer.ui.skin.channels.ChannelsSkin
import io.github.parthibdebnath.openplayer.ui.skin.classic.ClassicSkin
import io.github.parthibdebnath.openplayer.ui.skin.ipod.IpodSkin
import io.github.parthibdebnath.openplayer.ui.skin.luna.LunaSkin
import io.github.parthibdebnath.openplayer.ui.skin.metro.MetroSkin
import io.github.parthibdebnath.openplayer.ui.skin.platinum.PlatinumSkin
import io.github.parthibdebnath.openplayer.ui.skin.s60.S60Skin
import io.github.parthibdebnath.openplayer.ui.skin.xmb.XmbSkin

/** The options a theme offers, in the order Settings shows them; empty for most. */
fun skinOptions(theme: UiTheme): List<SkinOption> = when (theme) {
    UiTheme.AQUA -> AquaSkin.Options
    UiTheme.CLASSIC -> ClassicSkin.Options
    UiTheme.METRO -> MetroSkin.Options
    UiTheme.XMB -> XmbSkin.Options
    else -> emptyList()
}

/**
 * Skins already built, by theme and choices. A skin is built once per look and then reused, so the
 * frame keeps the same skin object (and everything remembered under it) from one frame to the next.
 */
private val built = HashMap<Pair<UiTheme, SkinChoices>, Skin>()

/** The skin for a theme with these choices made; null is openplayer's own look. Call on the main thread. */
fun skinFor(theme: UiTheme, choices: SkinChoices = SkinChoices()): Skin? {
    if (theme == UiTheme.DEFAULT) return null
    val normalised = choices.normalisedFor(skinOptions(theme))
    return built.getOrPut(theme to normalised) {
        when (theme) {
            UiTheme.DEFAULT -> error("openplayer's own look has no skin")
            UiTheme.AQUA -> AquaSkin.create(normalised)
            UiTheme.LUNA -> LunaSkin
            UiTheme.CLASSIC -> ClassicSkin.create(normalised)
            UiTheme.PLATINUM -> PlatinumSkin
            UiTheme.IPOD -> IpodSkin
            UiTheme.METRO -> MetroSkin.create(normalised)
            UiTheme.XMB -> XmbSkin.create(normalised)
            UiTheme.BLADES -> BladesSkin
            UiTheme.CHANNELS -> ChannelsSkin
            UiTheme.S60 -> S60Skin
        }
    }
}
