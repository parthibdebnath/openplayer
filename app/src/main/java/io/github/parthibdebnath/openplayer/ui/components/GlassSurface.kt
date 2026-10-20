package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalGlassEnabled
import io.github.parthibdebnath.openplayer.ui.theme.LocalHideIconBackgrounds

/**
 * Wraps a screen's root content as the backdrop that floating glass - the tab bar, the gesture
 * overlay - refracts. Every top-level screen composable should be wrapped in this once, near its
 * outermost Box.
 */
@Composable
fun GlassScreenRoot(backdrop: GlassBackdrop, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().glassBackdropSource(backdrop), content = content)
}

/**
 * The surface used by every button/pill/panel/dialog/notification in the app: liquid glass when
 * glass theming is on, or pure flat OLED black when it's off (per spec, glass-off replaces glass
 * with flat black rather than any other flat color).
 *
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
    val colors = LocalAppColors.current
    if (glassEnabled) {
        // Glass inside the content can't refract that same content - it's part of it - so it's
        // drawn as a translucent material over whatever is underneath. See LiquidGlass.
        this
            .liquidGlassMaterial(shape = shape, lightBackground = colors.isLightBackground, opacity = opacity)
            .clip(shape)
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
