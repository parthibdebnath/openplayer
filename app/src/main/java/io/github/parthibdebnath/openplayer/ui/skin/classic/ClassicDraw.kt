package io.github.parthibdebnath.openplayer.ui.skin.classic

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max
import kotlin.math.roundToInt

/** Windows 95's colours. */
internal object W95 {
    val face = Color(0xFFC0C0C0)
    val light = Color(0xFFDFDFDF)
    val highlight = Color(0xFFFFFFFF)
    val shadow = Color(0xFF808080)
    val dark = Color(0xFF000000)
    val navy = Color(0xFF000080)
    val titleEnd = Color(0xFF1084D0)
    val desktop = Color(0xFF008080)
    val ledOn = Color(0xFF00FF00)
    /** Unlit segments: the CD Player showed none, just black. */
    val ledOff = Color.Transparent
}

/** One classic pixel, in device pixels: a whole number, so every edge lands on a real pixel. */
internal fun DrawScope.unit(): Float = max(1f, density.roundToInt().toFloat())

internal enum class Bevel { Raised, Pressed, Sunken, Window, Field, Etched }

/**
 * The two-pixel 3D edges everything in Windows 95 was made of. [rect] is filled with [fill] (if
 * given) and edged: raised things lit from the top left, sunken things the other way round.
 */
internal fun DrawScope.drawBevel(rect: Rect, kind: Bevel, fill: Color? = W95.face) {
    val u = unit()
    val l = rect.left.roundToInt().toFloat()
    val t = rect.top.roundToInt().toFloat()
    val r = rect.right.roundToInt().toFloat()
    val b = rect.bottom.roundToInt().toFloat()
    if (fill != null) drawRect(fill, Offset(l, t), Size(r - l, b - t))
    fun edge(outerTL: Color, outerBR: Color, innerTL: Color?, innerBR: Color?) {
        // Top and left, then bottom and right, one pixel wide each, outer then inner.
        drawRect(outerTL, Offset(l, t), Size(r - l, u))
        drawRect(outerTL, Offset(l, t), Size(u, b - t))
        drawRect(outerBR, Offset(l, b - u), Size(r - l, u))
        drawRect(outerBR, Offset(r - u, t), Size(u, b - t))
        if (innerTL != null && innerBR != null) {
            drawRect(innerTL, Offset(l + u, t + u), Size(r - l - 2 * u, u))
            drawRect(innerTL, Offset(l + u, t + u), Size(u, b - t - 2 * u))
            drawRect(innerBR, Offset(l + u, b - 2 * u), Size(r - l - 2 * u, u))
            drawRect(innerBR, Offset(r - 2 * u, t + u), Size(u, b - t - 2 * u))
        }
    }
    when (kind) {
        Bevel.Raised -> edge(W95.highlight, W95.dark, W95.light, W95.shadow)
        Bevel.Window -> edge(W95.light, W95.dark, W95.highlight, W95.shadow)
        Bevel.Pressed -> edge(W95.dark, W95.highlight, W95.shadow, W95.light)
        Bevel.Sunken, Bevel.Field -> edge(W95.shadow, W95.highlight, W95.dark, W95.light)
        Bevel.Etched -> edge(W95.shadow, W95.highlight, W95.highlight, W95.shadow)
    }
}

/** A grey-and-white checkerboard, as scrollbar tracks and held-down toggle buttons were filled. */
internal fun ditherBrush(density: Float): ShaderBrush {
    val u = max(1, density.roundToInt())
    val bitmap = Bitmap.createBitmap(u * 2, u * 2, Bitmap.Config.ARGB_8888)
    for (y in 0 until u * 2) for (x in 0 until u * 2) {
        val on = ((x / u) + (y / u)) % 2 == 0
        bitmap.setPixel(x, y, if (on) 0xFFFFFFFF.toInt() else 0xFFC0C0C0.toInt())
    }
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Draws a little bitmap: each row of [rows] is a string where '#' is [ink], 'w' white, 'g' grey,
 * 'y' yellow, 'd' dark yellow, 'r' red, 'b' blue, and anything else is clear. Scaled so one bitmap
 * pixel is [scale] classic pixels.
 */
internal fun DrawScope.drawPixels(rows: List<String>, origin: Offset, scale: Int = 1, ink: Color = W95.dark) {
    val p = unit() * scale
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = when (c) {
                '#' -> ink
                'w' -> Color.White
                'g' -> W95.shadow
                'l' -> W95.face
                'y' -> Color(0xFFFFFF00)
                'd' -> Color(0xFF808000)
                'r' -> Color(0xFFFF0000)
                'm' -> Color(0xFF800000)
                'b' -> Color(0xFF0000FF)
                'n' -> W95.navy
                'c' -> Color(0xFF00FFFF)
                't' -> Color(0xFF008080)
                'G' -> Color(0xFF008000)
                else -> null
            }
            if (color != null) drawRect(color, Offset(origin.x + x * p, origin.y + y * p), Size(p, p))
        }
    }
}

/** Pixel glyphs for the transport and window buttons, 7 to 9 pixels tall. */
internal object Glyphs {
    val play = listOf("#    ", "##   ", "###  ", "#### ", "#####", "#### ", "###  ", "##   ", "#    ")
    val pause = listOf("## ##", "## ##", "## ##", "## ##", "## ##", "## ##", "## ##", "## ##", "## ##")
    val next = listOf("#   #   #", "##  ##  #", "### ### #", "#########", "### ### #", "##  ##  #", "#   #   #")
    val prev = listOf("#   #   #", "#  ##  ##", "# ### ###", "#########", "# ### ###", "#  ##  ##", "#   #   #")
    val close = listOf("##    ##", " ##  ## ", "  ####  ", "   ##   ", "  ####  ", " ##  ## ", "##    ##")
    val minimise = listOf("      ", "      ", "      ", "      ", "      ", "######", "######")
    val maximise = listOf("#########", "#########", "#       #", "#       #", "#       #", "#       #", "#       #", "#########")
    val check = listOf("      #", "     ##", "#   ###", "## ### ", "#####  ", " ###   ", "  #    ")
    val down = listOf("#######", " ##### ", "  ###  ", "   #   ")
    val up = listOf("   #   ", "  ###  ", " ##### ", "#######")
    val right = listOf("#   ", "##  ", "### ", "####", "### ", "##  ", "#   ")
    val skip = listOf("#  #  ", "## ## ", "######", "## ## ", "#  #  ")

    /** A Windows 95 folder, 16 x 13. */
    val folder = listOf(
        "  #####         ",
        " #yyyyy#        ",
        "#yyyyyyy######  ",
        "#wwwwwwwwwwwww# ",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#wyyyyyyyyyyyyd#",
        "#dddddddddddddd#",
        " ############## ",
    )

    /** A note, for songs. */
    val note = listOf("   ####", "   #  #", "   #  #", "   #  #", " ###  #", "#### ##", " ## ###", "     ##")
    val heart = listOf(" ## ## ", "#######", "#######", " ##### ", "  ###  ", "   #   ")
    val tag = listOf("#####  ", "#   ## ", "# #  ##", "#     #", "#    ##", "#####  ")

    /** The information balloon of a message box, 16 x 16 at double size. */
    val info = listOf(
        "    ######     ",
        "  ##wwwwww##   ",
        " #wwwwnnwwww#  ",
        "#wwwwwnnwwwww# ",
        "#wwwwwwwwwwww# ",
        "#wwwwnnnwwwww# ",
        "#wwwwwnnwwwww# ",
        "#wwwwwnnwwwww# ",
        " #wwwwnnwwww#  ",
        "  ##wnnnnw##   ",
        "    ##ww#      ",
        "      #w#      ",
        "       ##      ",
    )

    /** The CD Player's disc, for the window icon. */
    val disc = listOf(
        "   #####   ",
        "  #ccwww#  ",
        " #cwwwwwg# ",
        "#cwww#wwwg#",
        "#www#l#wwg#",
        "#cwww#wwgg#",
        " #wwwwwgg# ",
        "  #ggggg#  ",
        "   #####   ",
    )
}

/** Seven-segment digits, as the CD Player's display drew its time. */
internal fun DrawScope.drawSevenSegment(text: String, origin: Offset, digitHeight: Float, on: Color, off: Color) {
    val h = digitHeight
    val w = h * 0.55f
    val t = h * 0.12f
    var x = origin.x
    val y = origin.y
    for (c in text) {
        when (c) {
            ':' -> {
                drawRect(on, Offset(x + w * 0.15f, y + h * 0.28f), Size(t, t))
                drawRect(on, Offset(x + w * 0.15f, y + h * 0.65f), Size(t, t))
                x += w * 0.5f
                continue
            }
            '[', ']' -> {
                val left = c == '['
                val bx = if (left) x + w * 0.1f else x + w * 0.35f
                drawRect(on, Offset(bx, y), Size(t * 0.8f, h))
                drawRect(on, Offset(if (left) bx else bx - w * 0.2f, y), Size(w * 0.2f + t * 0.8f, t * 0.8f))
                drawRect(on, Offset(if (left) bx else bx - w * 0.2f, y + h - t * 0.8f), Size(w * 0.2f + t * 0.8f, t * 0.8f))
                x += w * 0.7f
                continue
            }
            ' ' -> {
                x += w * 0.6f
                continue
            }
        }
        val segs = SEGMENTS[c] ?: BooleanArray(7)
        // a top, b top-right, c bottom-right, d bottom, e bottom-left, f top-left, g middle
        val shapes = listOf(
            listOf(Offset(x + t, y), Offset(x + w - t, y), Offset(x + w - t * 1.8f, y + t), Offset(x + t * 1.8f, y + t)),
            listOf(Offset(x + w, y + t * 0.6f), Offset(x + w, y + h / 2f - t * 0.4f), Offset(x + w - t, y + h / 2f - t * 0.9f), Offset(x + w - t, y + t * 1.4f)),
            listOf(Offset(x + w, y + h / 2f + t * 0.4f), Offset(x + w, y + h - t * 0.6f), Offset(x + w - t, y + h - t * 1.4f), Offset(x + w - t, y + h / 2f + t * 0.9f)),
            listOf(Offset(x + t * 1.8f, y + h - t), Offset(x + w - t * 1.8f, y + h - t), Offset(x + w - t, y + h), Offset(x + t, y + h)),
            listOf(Offset(x, y + h / 2f + t * 0.4f), Offset(x + t, y + h / 2f + t * 0.9f), Offset(x + t, y + h - t * 1.4f), Offset(x, y + h - t * 0.6f)),
            listOf(Offset(x, y + t * 0.6f), Offset(x + t, y + t * 1.4f), Offset(x + t, y + h / 2f - t * 0.9f), Offset(x, y + h / 2f - t * 0.4f)),
            listOf(Offset(x + t, y + h / 2f - t / 2f), Offset(x + w - t, y + h / 2f - t / 2f), Offset(x + w - t * 0.4f, y + h / 2f), Offset(x + w - t, y + h / 2f + t / 2f), Offset(x + t, y + h / 2f + t / 2f), Offset(x + t * 0.4f, y + h / 2f)),
        )
        shapes.forEachIndexed { i, pts ->
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
                close()
            }
            drawPath(path, if (segs[i]) on else off)
        }
        x += w + h * 0.16f
    }
}

private val SEGMENTS: Map<Char, BooleanArray> = mapOf(
    '0' to booleanArrayOf(true, true, true, true, true, true, false),
    '1' to booleanArrayOf(false, true, true, false, false, false, false),
    '2' to booleanArrayOf(true, true, false, true, true, false, true),
    '3' to booleanArrayOf(true, true, true, true, false, false, true),
    '4' to booleanArrayOf(false, true, true, false, false, true, true),
    '5' to booleanArrayOf(true, false, true, true, false, true, true),
    '6' to booleanArrayOf(true, false, true, true, true, true, true),
    '7' to booleanArrayOf(true, true, true, false, false, false, false),
    '8' to booleanArrayOf(true, true, true, true, true, true, true),
    '9' to booleanArrayOf(true, true, true, true, false, true, true),
    '-' to booleanArrayOf(false, false, false, false, false, false, true),
)

/** The width [drawSevenSegment] takes for [text]. */
internal fun sevenSegmentWidth(text: String, digitHeight: Float): Float {
    val w = digitHeight * 0.55f
    return text.sumOf { c ->
        when (c) {
            ':' -> (w * 0.5f).toDouble()
            '[', ']' -> (w * 0.7f).toDouble()
            ' ' -> (w * 0.6f).toDouble()
            else -> (w + digitHeight * 0.16f).toDouble()
        }
    }.toFloat()
}
