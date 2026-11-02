package io.github.parthibdebnath.openplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.parthibdebnath.openplayer.R

/**
 * Inter is the only font used throughout the app (per spec). It ships as a single
 * variable-weight TTF; each weight below is the same file loaded with a different
 * FontVariation weight axis setting rather than a separate static font file.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.inter,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val InterFontFamily = FontFamily(
    interWeight(400, FontWeight.Normal),
    interWeight(500, FontWeight.Medium),
    interWeight(600, FontWeight.SemiBold),
    interWeight(700, FontWeight.Bold),
)

/**
 * The app's text style at a size. Under a skin it's the skin's own type for that size instead -
 * every screen asks for its text through here, so a skin changes it all at once.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun interTextStyle(sizeSp: Int, bold: Boolean = false): TextStyle {
    val skin = io.github.parthibdebnath.openplayer.ui.skin.LocalSkin.current
    return skin?.textStyle(sizeSp, bold, androidx.compose.ui.platform.LocalDensity.current) ?: defaultTextStyle(sizeSp, bold)
}

fun defaultTextStyle(sizeSp: Int, bold: Boolean = false) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = sizeSp.sp,
    // Explicit, tight line height tied directly to font size - left unspecified, some contexts
    // fall back to a much taller ambient default, visibly inflating list-row spacing.
    lineHeight = (sizeSp * 1.15f).sp,
)

val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    titleLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
)
