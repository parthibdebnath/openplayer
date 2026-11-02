package io.github.parthibdebnath.openplayer.ui.skin.luna

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Luna's palette, from Windows XP's default blue scheme. */
internal object Luna {
    val windowBody = Color(0xFFECE9D8)
    val text = Color(0xFF000000)
    val link = Color(0xFF215DC6)
    val selection = Color(0xFF316AC5)
    val fieldBorder = Color(0xFF7F9DB9)
    val buttonBorder = Color(0xFF003C74)
    val tabBorder = Color(0xFF919B9C)
    val paneTop = Color(0xFF7BA2E7)
    val paneBottom = Color(0xFF6375D6)
    val panelBody = Color(0xFFD6DFF7)
    val panelHeaderEnd = Color(0xFFC6D3F7)
    val checkGreen = Color(0xFF21A121)

    /** The active title bar, top to bottom. */
    val titleBar = listOf(
        0f to Color(0xFF0997FF), 0.08f to Color(0xFF0053EE), 0.40f to Color(0xFF0050EE),
        0.88f to Color(0xFF0066FF), 0.93f to Color(0xFF0066FF), 0.95f to Color(0xFF005BFF),
        0.96f to Color(0xFF003DD7), 1f to Color(0xFF003DD7),
    )

    val taskbar = listOf(
        0f to Color(0xFF1F2F86), 0.03f to Color(0xFF3165C4), 0.06f to Color(0xFF3682E5), 0.10f to Color(0xFF4490E6),
        0.12f to Color(0xFF3883E5), 0.15f to Color(0xFF2B71E0), 0.18f to Color(0xFF2663DA), 0.20f to Color(0xFF235BD6),
        0.23f to Color(0xFF2258D5), 0.38f to Color(0xFF2157D6), 0.54f to Color(0xFF245DDB), 0.86f to Color(0xFF2562DF),
        0.89f to Color(0xFF245FDC), 0.92f to Color(0xFF2158D4), 0.95f to Color(0xFF1D4EC0), 0.98f to Color(0xFF1941A5),
        1f to Color(0xFF1941A5),
    )

    val startButton = listOf(
        0f to Color(0xFF64B85A), 0.08f to Color(0xFF52AE47), 0.45f to Color(0xFF3A9632),
        0.85f to Color(0xFF2F8B27), 1f to Color(0xFF1F6B18),
    )

    val tray = listOf(0f to Color(0xFF0C59B9), 0.1f to Color(0xFF139EE9), 0.5f to Color(0xFF18B9F0), 0.9f to Color(0xFF139EE9), 1f to Color(0xFF0D61C2))

    /** The progress bar's green blocks, top to bottom. */
    val progress = listOf(
        0f to Color(0xFFACEDAD), 0.14f to Color(0xFF7BE47D), 0.28f to Color(0xFF4CDA50), 0.42f to Color(0xFF2ED330),
        0.57f to Color(0xFF42D845), 0.71f to Color(0xFF76E275), 0.85f to Color(0xFF8FE791), 1f to Color(0xFFACEDAD),
    )
}

internal fun DrawScope.px(dp: Float): Float = dp * density

/** XP's push button: a soft white-to-beige face inside a dark blue, slightly rounded edge. */
internal fun DrawScope.drawXpButton(rect: Rect, pressed: Boolean, default: Boolean = false) {
    val corner = CornerRadius(px(3f))
    val face = if (pressed) {
        listOf(0f to Color(0xFFCDCAC3), 0.1f to Color(0xFFE3E3DB), 0.9f to Color(0xFFE5E5DE), 1f to Color(0xFFF2F2F1))
    } else {
        listOf(0f to Color(0xFFFFFFFF), 0.86f to Color(0xFFECEBE6), 1f to Color(0xFFD6D0C5))
    }
    drawRoundRect(Brush.verticalGradient(*face.toTypedArray(), startY = rect.top, endY = rect.bottom), rect.topLeft, rect.size, corner)
    if (default && !pressed) {
        // The default button's inner blue rim.
        val inner = Rect(rect.left + px(1.5f), rect.top + px(1.5f), rect.right - px(1.5f), rect.bottom - px(1.5f))
        drawRoundRect(Color(0xFF98B8EA), inner.topLeft, inner.size, CornerRadius(px(2f)), style = Stroke(px(1.6f)))
    }
    drawRoundRect(Luna.buttonBorder, rect.topLeft, rect.size, corner, style = Stroke(px(1f)))
}

/**
 * A round glossy button: green for Play, as XP's "Go" and the Back button were, blue for the
 * rest of the transport. A white rim, a gradient body lit from the lower right, a gloss on top.
 */
internal fun DrawScope.drawXpOrb(center: Offset, radius: Float, green: Boolean, pressed: Boolean) {
    drawCircle(Color.Black.copy(alpha = 0.18f), radius, center + Offset(0f, px(1.5f)))
    drawCircle(Color.White, radius, center)
    val body = radius - px(1.6f)
    val (light, mid, dark) = if (green) {
        Triple(Color(0xFFB4EE9A), Color(0xFF45B12A), Color(0xFF1E7A10))
    } else {
        Triple(Color(0xFFB9D8FF), Color(0xFF3D82E8), Color(0xFF1546A8))
    }
    drawCircle(
        Brush.radialGradient(
            0f to (if (pressed) mid else light),
            0.55f to mid,
            1f to dark,
            center = center + Offset(body * 0.35f, body * 0.45f),
            radius = body * 1.55f,
        ),
        body,
        center,
    )
    drawCircle(dark.copy(alpha = 0.9f), body, center, style = Stroke(px(1f)))
    // Gloss.
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (pressed) 0.35f else 0.7f), Color.White.copy(alpha = 0f)), startY = center.y - body, endY = center.y),
        topLeft = Offset(center.x - body * 0.7f, center.y - body * 0.92f),
        size = Size(body * 1.4f, body * 0.95f),
    )
}

/** XP's green progress bar: a white trough with a rounded grey edge, filled with green blocks. */
internal fun DrawScope.drawXpProgress(rect: Rect, fraction: Float) {
    val corner = CornerRadius(px(3f))
    drawRoundRect(Color.White, rect.topLeft, rect.size, corner)
    drawRoundRect(Color(0xFF686868), rect.topLeft, rect.size, corner, style = Stroke(px(1f)))
    val inset = px(2f)
    val inner = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
    val block = px(7f)
    val gap = px(2f)
    val end = inner.left + inner.width * fraction.coerceIn(0f, 1f)
    val brush = Brush.verticalGradient(*Luna.progress.toTypedArray(), startY = inner.top, endY = inner.bottom)
    var x = inner.left
    while (x < end) {
        val w = minOf(block, end - x)
        drawRect(brush, Offset(x, inner.top), Size(w, inner.height))
        x += block + gap
    }
}

/** XP's checkbox: a white box with a dark blue edge and a green tick. */
internal fun DrawScope.drawXpCheckbox(rect: Rect, checked: Boolean, pressed: Boolean) {
    drawRect(
        Brush.linearGradient(
            listOf(if (pressed) Color(0xFFB0B0A7) else Color(0xFFDCDCD7), Color.White),
            start = rect.topLeft,
            end = rect.bottomRight,
        ),
        rect.topLeft,
        rect.size,
    )
    drawRect(Color(0xFF1C5180), rect.topLeft, rect.size, style = Stroke(px(1f)))
    if (checked) {
        val w = rect.width
        val path = Path().apply {
            moveTo(rect.left + w * 0.22f, rect.top + w * 0.48f)
            lineTo(rect.left + w * 0.42f, rect.top + w * 0.70f)
            lineTo(rect.left + w * 0.80f, rect.top + w * 0.26f)
        }
        drawPath(path, Luna.checkGreen, style = Stroke(px(2.4f)))
    }
}

/** XP's option button: a round well with a green dot when chosen. */
internal fun DrawScope.drawXpRadio(center: Offset, radius: Float, checked: Boolean) {
    drawCircle(Brush.linearGradient(listOf(Color(0xFFDCDCD7), Color.White), start = center - Offset(radius, radius), end = center + Offset(radius, radius)), radius, center)
    drawCircle(Color(0xFF1C5180), radius, center, style = Stroke(px(1f)))
    if (checked) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF55D451), Luna.checkGreen, Color(0xFF138A12)), center = center - Offset(radius * 0.15f, radius * 0.15f), radius = radius * 0.55f),
            radius * 0.45f,
            center,
        )
    }
}

/** A yellow Explorer folder, here holding music. */
internal fun DrawScope.drawFolder(rect: Rect) {
    val w = rect.width
    val h = rect.height
    val tab = Path().apply {
        moveTo(rect.left + w * 0.04f, rect.top + h * 0.2f)
        lineTo(rect.left + w * 0.36f, rect.top + h * 0.2f)
        lineTo(rect.left + w * 0.44f, rect.top + h * 0.3f)
        lineTo(rect.left + w * 0.96f, rect.top + h * 0.3f)
        lineTo(rect.left + w * 0.96f, rect.top + h * 0.86f)
        lineTo(rect.left + w * 0.04f, rect.top + h * 0.86f)
        close()
    }
    drawPath(tab, Brush.verticalGradient(listOf(Color(0xFFE9C25A), Color(0xFFC79A2C)), startY = rect.top, endY = rect.bottom))
    val front = Path().apply {
        moveTo(rect.left + w * 0.02f, rect.top + h * 0.40f)
        lineTo(rect.left + w * 0.98f, rect.top + h * 0.40f)
        lineTo(rect.left + w * 0.92f, rect.top + h * 0.90f)
        lineTo(rect.left + w * 0.08f, rect.top + h * 0.90f)
        close()
    }
    drawPath(front, Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFF6CF5E), Color(0xFFE6B13C)), startY = rect.top + h * 0.4f, endY = rect.top + h * 0.9f))
    drawPath(front, Color(0xFFB0812A), style = Stroke(px(0.8f)))
}

/**
 * A sunny hillside under a blue sky with drifting cloud - the spirit of XP's wallpaper, drawn
 * from scratch, used where an album cover would be.
 */
internal fun DrawScope.drawHillside(rect: Rect) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2F6FD3), Color(0xFF5C95E6), Color(0xFFA9CBF2)), startY = rect.top, endY = rect.top + rect.height * 0.7f), rect.topLeft, rect.size)
    val w = rect.width
    val h = rect.height
    fun cloud(cx: Float, cy: Float, s: Float) {
        for ((dx, dy, r) in listOf(Triple(0f, 0f, 1f), Triple(0.8f, 0.15f, 0.8f), Triple(-0.8f, 0.2f, 0.75f), Triple(1.5f, 0.3f, 0.55f), Triple(-1.4f, 0.35f, 0.5f))) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f)), center = Offset(cx + dx * s, cy + dy * s), radius = s * r * 1.3f),
                s * r * 1.3f,
                Offset(cx + dx * s, cy + dy * s),
            )
        }
    }
    cloud(rect.left + w * 0.25f, rect.top + h * 0.2f, w * 0.06f)
    cloud(rect.left + w * 0.72f, rect.top + h * 0.12f, w * 0.05f)
    cloud(rect.left + w * 0.55f, rect.top + h * 0.38f, w * 0.035f)
    val hill = Path().apply {
        moveTo(rect.left, rect.top + h * 0.62f)
        cubicTo(rect.left + w * 0.3f, rect.top + h * 0.44f, rect.left + w * 0.6f, rect.top + h * 0.5f, rect.right, rect.top + h * 0.66f)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }
    drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF7BC33F), Color(0xFF4C9A22), Color(0xFF2E6D12)), startY = rect.top + h * 0.48f, endY = rect.bottom))
    // A softer, lighter swell of hill behind, fading out to the left.
    val back = Path().apply {
        moveTo(rect.left + w * 0.30f, rect.top + h * 0.66f)
        cubicTo(rect.left + w * 0.55f, rect.top + h * 0.52f, rect.left + w * 0.85f, rect.top + h * 0.53f, rect.right, rect.top + h * 0.58f)
        lineTo(rect.right, rect.top + h * 0.68f)
        cubicTo(rect.left + w * 0.8f, rect.top + h * 0.64f, rect.left + w * 0.5f, rect.top + h * 0.66f, rect.left + w * 0.30f, rect.top + h * 0.66f)
        close()
    }
    drawPath(back, Brush.horizontalGradient(listOf(Color(0x009BD45B), Color(0x999BD45B)), startX = rect.left + w * 0.3f, endX = rect.left + w * 0.7f))
}
