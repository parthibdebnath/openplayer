package io.github.parthibdebnath.openplayer.ui.skin

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.parthibdebnath.openplayer.R

/**
 * The typefaces the skins use. None of the originals can be shipped, so each is the closest
 * openly licensed stand-in (see the licences screen):
 *
 * - Lucida Grande (Aqua) and Tahoma (Luna) - DejaVu Sans and its condensed cut, which share their
 *   humanist proportions; Luna's italic "start" is Libre Franklin, a Franklin Gothic revival.
 * - MS Sans Serif (Classic) - "Pixel 95", an 8 px bitmap face drawn for openplayer.
 * - Chicago (iPod, Platinum) - ChicagoFLF.
 * - Segoe (Metro, Blades) - Selawik, Microsoft's own open Segoe stand-in.
 * - Rodin (XMB, Channels) - M PLUS 1p, and M PLUS Rounded for the Wii's clock.
 * - Nokia Sans (S60) - Fira Sans, from the same type designer's lineage.
 */
object SkinFonts {
    val aqua = FontFamily(
        Font(R.font.aqua_sans, FontWeight.Normal),
        Font(R.font.aqua_sans_bold, FontWeight.Bold),
    )
    val luna = FontFamily(
        Font(R.font.luna_sans, FontWeight.Normal),
        Font(R.font.luna_sans_bold, FontWeight.Bold),
    )
    val lunaStart = FontFamily(Font(R.font.luna_start, FontWeight.ExtraBold, FontStyle.Italic))
    val pixel = FontFamily(
        Font(R.font.classic_pixel, FontWeight.Normal),
        Font(R.font.classic_pixel_bold, FontWeight.Bold),
    )
    val chicago = FontFamily(Font(R.font.chicago, FontWeight.Normal))
    val metro = FontFamily(
        Font(R.font.metro_light, FontWeight.Light),
        Font(R.font.metro_semilight, FontWeight.ExtraLight),
        Font(R.font.metro_regular, FontWeight.Normal),
        Font(R.font.metro_semibold, FontWeight.SemiBold),
        Font(R.font.metro_bold, FontWeight.Bold),
    )
    val rodin = FontFamily(
        Font(R.font.rodin_light, FontWeight.Light),
        Font(R.font.rodin_regular, FontWeight.Normal),
        Font(R.font.rodin_medium, FontWeight.Medium),
        Font(R.font.rodin_bold, FontWeight.Bold),
    )
    val rounded = FontFamily(
        Font(R.font.rounded_light, FontWeight.Light),
        Font(R.font.rounded_regular, FontWeight.Normal),
        Font(R.font.rounded_bold, FontWeight.Bold),
    )
    val s60 = FontFamily(
        Font(R.font.s60_regular, FontWeight.Normal),
        Font(R.font.s60_medium, FontWeight.Medium),
        Font(R.font.s60_bold, FontWeight.Bold),
    )
}
