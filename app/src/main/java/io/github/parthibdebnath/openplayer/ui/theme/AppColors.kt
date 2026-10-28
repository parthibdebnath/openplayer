package io.github.parthibdebnath.openplayer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The app's two working colours: the user-chosen page background, and the single foreground colour
 * everything (text, icons, glass tint, dividers) derives from.
 */
@Immutable
data class AppColors(
    val background: Color,
    val onBackground: Color,
) {
    /**
     * True when the background is light enough that content has to be drawn dark to stay readable.
     * Judged by the content colour's luminance rather than by it being exactly black: the content
     * colour glides between white and black when the background changes, and the surfaces keyed off
     * this should switch half way through that, along with it, not only once it has finished.
     */
    val isLightBackground: Boolean get() = onBackground.luminance() < 0.5f
}

val LocalAppColors = compositionLocalOf {
    AppColors(background = Color.Black, onBackground = Color.White)
}

/**
 * Whether icons are drawn bare, with no circle behind them. Glass theming overrides this while it
 * is on - the frosted circle is the whole look - but the user's choice here is kept either way, so
 * turning glass off reveals it again rather than resetting it.
 */
val LocalHideIconBackgrounds = compositionLocalOf { false }

/**
 * Whether a title too long for its space scrolls. When off it stays put, showing however many
 * characters fit.
 */
val LocalScrollLongTitles = compositionLocalOf { true }

/**
 * Parses a user-entered `#RRGGBB` (or `#AARRGGBB`) value, falling back to pure black - which on an
 * OLED panel is genuinely off pixels - for anything unparseable, including a half-typed entry.
 */
fun parseBackgroundColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return Color.Black
    val value = cleaned.toLongOrNull(radix = 16) ?: return Color.Black
    // Always opaque, even when eight digits include an alpha: a see-through background shows the
    // black window behind it while the text colour is still picked for the colour as typed, which
    // could leave light text unreadable, or dark text on black with no way to find the setting.
    return Color(value or 0xFF000000L)
}

/**
 * Picks the foreground colour for a background. The threshold is on perceptual luminance rather
 * than a simple average, so mid-tones like a saturated blue still get white text.
 */
fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Color.Black else Color.White
