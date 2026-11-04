package io.github.parthibdebnath.openplayer.ui.skin.aqua

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.min
import kotlin.random.Random

/**
 * The appearance picked in System Preferences: Blue, or Graphite, which turned every gel, the
 * traffic lights and the selection a cool grey. Set by AquaSkin's frame before anything under it
 * is drawn; held as state so whatever's already drawn redraws when it changes.
 */
internal object AquaAppearance {
    var graphite by mutableStateOf(false)
}

/** Aqua's materials: gel, pinstripes and brushed metal. */
internal object AquaColors {
    val text = Color(0xFF000000)
    val textDim = Color(0xFF4D4D4D)
    val selection get() = if (AquaAppearance.graphite) Color(0xFF6F7D93) else Color(0xFF3875D7)
    val stripe get() = if (AquaAppearance.graphite) Color(0xFFF0F1F3) else Color(0xFFEDF3FE)
    val wellBorder = Color(0xFF8C8C8C)
    val lcdTop = Color(0xFFF5F7E4)
    val lcdBottom = Color(0xFFDDE3C3)
    val lcdBorder = Color(0xFF7D806C)
    val lcdText = Color(0xFF1E1F18)
    val focus get() = if (AquaAppearance.graphite) Color(0xFF8E9BB0) else Color(0xFF6E9DE6)

    /** The desktop, seen past the window's rounded top corners. */
    val desktop get() = if (AquaAppearance.graphite) listOf(Color(0xFF5B6470), Color(0xFF9BA3AE)) else listOf(Color(0xFF3A6EC4), Color(0xFF6FA0E0))

    /** The engraved glyph on the Play gel. */
    val gelInk get() = if (AquaAppearance.graphite) Color(0xFF1F2630) else Color(0xFF0D2E6E)
}

/** One gel's colours, top to bottom, with its rim and the glow that pools at its base. */
internal class Gel(
    val body: List<Pair<Float, Color>>,
    val rim: Color,
    val glow: Color,
    val highlight: Float = 0.95f,
)

internal object Gels {
    /** Whatever is default or on: blue, or graphite. */
    val blue get() = if (AquaAppearance.graphite) graphiteGel else blueGel
    val bluePressed get() = if (AquaAppearance.graphite) graphitePressedGel else bluePressedGel

    /** The traffic lights; in Graphite all three went grey. */
    val red get() = if (AquaAppearance.graphite) graphiteLight else redGel
    val yellow get() = if (AquaAppearance.graphite) graphiteLight else yellowGel
    val green get() = if (AquaAppearance.graphite) graphiteLight else greenGel

    private val blueGel = Gel(
        body = listOf(0f to Color(0xFF3577D9), 0.46f to Color(0xFF1F5FCC), 0.78f to Color(0xFF4F98EC), 1f to Color(0xFF9AD8FF)),
        rim = Color(0xFF173F8F),
        glow = Color(0xFFC6F1FF),
    )
    private val bluePressedGel = Gel(
        body = listOf(0f to Color(0xFF1D4FA8), 0.46f to Color(0xFF143F94), 0.78f to Color(0xFF2E6FCB), 1f to Color(0xFF6EB4F2)),
        rim = Color(0xFF0E2A63),
        glow = Color(0xFF9FD8F7),
        highlight = 0.75f,
    )
    val white = Gel(
        body = listOf(0f to Color(0xFFE2E2E2), 0.46f to Color(0xFFCBCBCB), 0.8f to Color(0xFFEAEAEA), 1f to Color(0xFFFFFFFF)),
        rim = Color(0xFF7B7B7B),
        glow = Color(0xFFFFFFFF),
    )
    val whitePressed = Gel(
        body = listOf(0f to Color(0xFFB4B4B4), 0.46f to Color(0xFFA2A2A2), 0.8f to Color(0xFFC7C7C7), 1f to Color(0xFFE6E6E6)),
        rim = Color(0xFF5E5E5E),
        glow = Color(0xFFF2F2F2),
        highlight = 0.7f,
    )
    private val graphiteGel = Gel(
        body = listOf(0f to Color(0xFF9AA3B0), 0.46f to Color(0xFF7C8796), 0.78f to Color(0xFF9BA6B5), 1f to Color(0xFFD2DAE3)),
        rim = Color(0xFF4C5563),
        glow = Color(0xFFE9EEF4),
    )
    private val graphitePressedGel = Gel(
        body = listOf(0f to Color(0xFF6E7887), 0.46f to Color(0xFF5B6573), 0.78f to Color(0xFF77828F), 1f to Color(0xFFAFB8C3)),
        rim = Color(0xFF343B46),
        glow = Color(0xFFC9D1DA),
        highlight = 0.75f,
    )
    private val graphiteLight = Gel(
        body = listOf(0f to Color(0xFF8C96A3), 0.5f to Color(0xFF6D7886), 0.8f to Color(0xFF8E99A7), 1f to Color(0xFFC8D0DA)),
        rim = Color(0xFF454D59),
        glow = Color(0xFFE2E8EF),
    )
    private val redGel = Gel(
        body = listOf(0f to Color(0xFFD5473A), 0.5f to Color(0xFFB8261C), 0.8f to Color(0xFFE25C45), 1f to Color(0xFFFFA58A)),
        rim = Color(0xFF7E1610),
        glow = Color(0xFFFFC9B8),
    )
    private val yellowGel = Gel(
        body = listOf(0f to Color(0xFFE5A93A), 0.5f to Color(0xFFD08A16), 0.8f to Color(0xFFF0B640), 1f to Color(0xFFFFE08C)),
        rim = Color(0xFF8A5A08),
        glow = Color(0xFFFFF0B8),
    )
    private val greenGel = Gel(
        body = listOf(0f to Color(0xFF6DB143), 0.5f to Color(0xFF4A9327), 0.8f to Color(0xFF7CC44C), 1f to Color(0xFFC2EF8F)),
        rim = Color(0xFF2C5E12),
        glow = Color(0xFFE2FFC4),
    )
}

/**
 * Lickable gel: a glossy body with a darker band across the middle, a rim, a bright highlight
 * across the upper half and light pooling at the bottom, over a soft shadow.
 */
internal fun DrawScope.drawGel(rect: Rect, radius: Float, gel: Gel, shadow: Boolean = true) {
    val corner = CornerRadius(radius.coerceAtMost(min(rect.width, rect.height) / 2f))
    if (shadow) {
        val s = px(1f)
        drawRoundRect(Color.Black.copy(alpha = 0.10f), rect.topLeft + Offset(0f, s * 2.2f), rect.size, corner)
        drawRoundRect(Color.Black.copy(alpha = 0.14f), rect.topLeft + Offset(0f, s * 1.1f), rect.size, corner)
    }
    val path = Path().apply { addRoundRect(RoundRect(rect, corner)) }
    clipPath(path) {
        drawRect(
            Brush.verticalGradient(*gel.body.toTypedArray(), startY = rect.top, endY = rect.bottom),
            rect.topLeft,
            rect.size,
        )
        // Light pooling at the base, the way it does in a drop of water.
        drawOval(
            Brush.radialGradient(
                listOf(gel.glow.copy(alpha = 0.75f), gel.glow.copy(alpha = 0f)),
                center = Offset(rect.center.x, rect.bottom),
                radius = rect.width * 0.55f,
            ),
            topLeft = Offset(rect.left - rect.width * 0.1f, rect.top + rect.height * 0.45f),
            size = Size(rect.width * 1.2f, rect.height * 1.1f),
        )
    }
    // The highlight: a pill of white across the upper half, inset from the rim.
    val hInsetX = min(rect.height * 0.32f, rect.width * 0.18f)
    val hTop = rect.top + rect.height * 0.06f
    val hHeight = rect.height * 0.46f
    val hRect = Rect(rect.left + hInsetX, hTop, rect.right - hInsetX, hTop + hHeight)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = gel.highlight),
            1f to Color.White.copy(alpha = 0.12f),
            startY = hRect.top,
            endY = hRect.bottom,
        ),
        hRect.topLeft,
        hRect.size,
        CornerRadius(min(hRect.height / 2f, corner.x)),
    )
    drawRoundRect(gel.rim, rect.topLeft, rect.size, corner, style = Stroke(px(1f)))
}

/** Just a gel's body and glow, for a part of a larger control that shares one highlight. */
internal fun DrawScope.drawGelBody(rect: Rect, gel: Gel) {
    drawRect(Brush.verticalGradient(*gel.body.toTypedArray(), startY = rect.top, endY = rect.bottom), rect.topLeft, rect.size)
    drawOval(
        Brush.radialGradient(
            listOf(gel.glow.copy(alpha = 0.75f), gel.glow.copy(alpha = 0f)),
            center = Offset(rect.center.x, rect.bottom),
            radius = rect.width * 0.55f,
        ),
        topLeft = Offset(rect.left - rect.width * 0.1f, rect.top + rect.height * 0.45f),
        size = Size(rect.width * 1.2f, rect.height * 1.1f),
    )
}

/** Just the gloss across a gel's upper half. */
internal fun DrawScope.drawGelHighlight(rect: Rect, radius: Float, alpha: Float) {
    val hInsetX = min(rect.height * 0.32f, rect.width * 0.18f)
    val hTop = rect.top + rect.height * 0.06f
    val hRect = Rect(rect.left + hInsetX, hTop, rect.right - hInsetX, hTop + rect.height * 0.46f)
    drawRoundRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = alpha), 1f to Color.White.copy(alpha = 0.1f), startY = hRect.top, endY = hRect.bottom),
        hRect.topLeft,
        hRect.size,
        CornerRadius(min(hRect.height / 2f, radius)),
    )
}

internal fun DrawScope.drawGelCircle(center: Offset, radius: Float, gel: Gel, shadow: Boolean = true) =
    drawGel(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius), radius, gel, shadow)

internal fun DrawScope.px(dp: Float): Float = dp * density

/** Aqua's window background: fine horizontal pinstripes. */
internal fun pinstripeBrush(density: Float): ShaderBrush {
    val light = (1.2f * density).toInt().coerceAtLeast(1)
    val dark = (0.9f * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(1, light + dark, Bitmap.Config.ARGB_8888)
    for (y in 0 until light) bitmap.setPixel(0, y, 0xFFF4F4F4.toInt())
    for (y in light until light + dark) bitmap.setPixel(0, y, 0xFFE6E6E6.toInt())
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** The menu bar's finer, fainter stripes. */
internal fun menuStripeBrush(density: Float): ShaderBrush {
    val step = (1f * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(1, step * 2, Bitmap.Config.ARGB_8888)
    for (y in 0 until step) bitmap.setPixel(0, y, 0xFFFFFFFF.toInt())
    for (y in step until step * 2) bitmap.setPixel(0, y, 0xFFF3F3F3.toInt())
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Brushed metal, as on iTunes and QuickTime: lighter at the top, with long horizontal grain made
 * of row-by-row noise that drifts slowly along each row.
 */
internal fun brushedMetal(width: Int, height: Int, seed: Int = 7): ImageBitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val random = Random(seed)
    val rowNoise = FloatArray(h) { random.nextFloat() * 2f - 1f }
    // Neighbouring rows a little alike, so the grain reads as streaks rather than static.
    val smooth = FloatArray(h) { y ->
        val a = rowNoise[(y - 1).coerceAtLeast(0)]
        val b = rowNoise[y]
        val c = rowNoise[(y + 1).coerceAtMost(h - 1)]
        a * 0.25f + b * 0.5f + c * 0.25f
    }
    // A slow wave along x per row: the grain fades in and out as it would on real metal.
    val waveCount = 6
    val phases = Array(h) { FloatArray(waveCount) { random.nextFloat() * 6.283f } }
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val t = y.toFloat() / h
        // Lighter band near the top, as the light catches it.
        val base = 222f - 26f * t + 6f * kotlin.math.exp(-((t - 0.06f) * (t - 0.06f)) / 0.004f)
        val grain = smooth[y]
        val ph = phases[y]
        for (x in 0 until w) {
            val fx = x.toFloat() / w
            var drift = 0f
            drift += kotlin.math.sin(fx * 3.1f + ph[0]) * 0.5f
            drift += kotlin.math.sin(fx * 7.3f + ph[1]) * 0.3f
            drift += kotlin.math.sin(fx * 13.7f + ph[2]) * 0.2f
            val v = (base + grain * (6.5f + 3.5f * drift) + (random.nextFloat() - 0.5f) * 2.2f)
                .toInt().coerceIn(0, 255)
            pixels[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or (v + 2).coerceAtMost(255)
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    return bitmap.asImageBitmap()
}

/** A sunken channel, lit from above: the slider track and scroller trough. */
internal fun DrawScope.drawTrough(rect: Rect, radius: Float) {
    val corner = CornerRadius(radius)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color(0xFF8D8D8D),
            0.35f to Color(0xFFC4C4C4),
            1f to Color(0xFFF2F2F2),
            startY = rect.top,
            endY = rect.bottom,
        ),
        rect.topLeft,
        rect.size,
        corner,
    )
    drawRoundRect(Color(0xFF6F6F6F), rect.topLeft, rect.size, corner, style = Stroke(1f * density))
}
