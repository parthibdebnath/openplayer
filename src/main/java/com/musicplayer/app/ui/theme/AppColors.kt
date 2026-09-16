package com.musicplayer.app.ui.theme

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
    /** True when the background is light enough that content has to be drawn dark to stay readable. */
    val isLightBackground: Boolean get() = onBackground == Color.Black
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
 * Parses a user-entered `#RRGGBB` (or `#AARRGGBB`) value, falling back to pure black - which on an
 * OLED panel is genuinely off pixels - for anything unparseable, including a half-typed entry.
 */
fun parseBackgroundColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return Color.Black
    val value = cleaned.toLongOrNull(radix = 16) ?: return Color.Black
    return if (cleaned.length == 6) Color(value or 0xFF000000L) else Color(value)
}

/**
 * Picks the foreground colour for a background. The threshold is on perceptual luminance rather
 * than a simple average, so mid-tones like a saturated blue still get white text.
 *
 * @param inverted flips the automatic choice. Mid-tone backgrounds can legitimately read either
 *   way, and which one looks right is a matter of taste, so the user can override it.
 */
fun contentColorFor(background: Color, inverted: Boolean = false): Color {
    val luminance = background.luminance()
    // Inversion is only honoured in the mid-range. Against something near-black or near-white the
    // inverted choice has almost no contrast, so everything - including the toggle that turned it
    // on - fades into the page and there's no way back. There, the automatic choice always wins.
    val canInvert = luminance > INVERSION_SAFE_MIN && luminance < INVERSION_SAFE_MAX
    val light = luminance > 0.45f
    return if (light != (inverted && canInvert)) Color.Black else Color.White
}

private const val INVERSION_SAFE_MIN = 0.10f
private const val INVERSION_SAFE_MAX = 0.80f
