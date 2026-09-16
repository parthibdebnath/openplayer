package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.LocalAppColors
import com.musicplayer.app.ui.theme.LocalGlassEnabled
import com.musicplayer.app.ui.theme.LocalHideIconBackgrounds
import com.musicplayer.app.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Wraps a screen's root content as the source Glass components blur into. Every top-level
 * screen composable should be wrapped in this once, near its outermost Box.
 */
@Composable
fun GlassScreenRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = modifier.fillMaxSize().hazeSource(hazeState), content = content)
    }
}

/** The gray a frosted surface reads as against this app's black background. */
private val GLASS_GRAY = Color(0xFF9A9A9E)

/**
 * The frosted "liquid glass" surface used by every button/pill/panel/dialog/notification in the
 * app: a gray-tinted blur of whatever's behind it when glass theming is on, or pure flat OLED
 * black when it's off (per spec, glass-off replaces glass with flat black rather than any other
 * flat color).
 */
/**
 * @param hideable whether the "hide icon backgrounds" setting applies here. Only the circle behind
 *   an icon button opts in - panels, text fields, context menus and the switcher pills keep their
 *   surface and border either way, since those outlines are what make them legible as controls.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(50),
    opacity: Float = 1f,
    hideable: Boolean = false,
): Modifier = composed {
    val glassEnabled = LocalGlassEnabled.current
    val hazeState = LocalHazeState.current
    val colors = LocalAppColors.current
    // Frosted glass reads as a lift off the page, so it tints toward the content colour: a light
    // grey on a dark background, a dark grey on a light one.
    val glassTint = if (colors.isLightBackground) Color(0xFF3A3A3C) else GLASS_GRAY
    if (glassEnabled) {
        val tint = HazeTint(glassTint.copy(alpha = 0.28f * opacity))
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    tint = tint,
                    blurRadius = 18.dp,
                    noiseFactor = 0.12f,
                    fallbackTint = tint,
                ),
            )
            // Painted unconditionally on top of the blur rather than left to Haze's tint alone.
            // Against this app's pure-black background there is nothing behind a glass surface for
            // the blur to pick up, so wherever the backdrop-blur pass produces nothing - which is
            // what happens on real devices where the effect silently no-ops - the surface rendered
            // as an invisible circle with only its hairline border showing. This guarantees the
            // gray frosted body the mockups show, and simply deepens it where the blur does run.
            .background(glassTint.copy(alpha = 0.38f * opacity), shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.22f * opacity), shape)
    } else if (LocalHideIconBackgrounds.current) {
        if (hideable) {
            // An icon button loses its circle entirely - the bare icon is the whole point.
            this.clip(shape)
        } else {
            // Everything else keeps its outline but drops the fill. A panel or text field still
            // has to read as a distinct surface; filling it in solid is what this setting is
            // meant to avoid.
            this
                .clip(shape)
                .border(1.dp, colors.onBackground.copy(alpha = 0.35f * opacity), shape)
        }
    } else {
        this
            .clip(shape)
            .background(
                // "Glass off" means a flat panel in the opposite of the content colour, which on
                // the default palette is the spec's pure OLED black.
                (if (colors.isLightBackground) Color.White else Color.Black).copy(alpha = opacity),
                shape,
            )
    }
}
