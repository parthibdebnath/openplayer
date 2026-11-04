package io.github.parthibdebnath.openplayer.ui.skin.platinum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassRole
import io.github.parthibdebnath.openplayer.ui.components.MarqueeText
import io.github.parthibdebnath.openplayer.ui.components.onTapOrHold
import io.github.parthibdebnath.openplayer.ui.components.rememberCurrentTime
import io.github.parthibdebnath.openplayer.ui.skin.ButtonKind
import io.github.parthibdebnath.openplayer.ui.skin.CollectionEntry
import io.github.parthibdebnath.openplayer.ui.skin.FrameModel
import io.github.parthibdebnath.openplayer.ui.skin.MiniPlayerModel
import io.github.parthibdebnath.openplayer.ui.skin.NowPlayingModel
import io.github.parthibdebnath.openplayer.ui.skin.Skin
import io.github.parthibdebnath.openplayer.ui.skin.SkinFonts
import io.github.parthibdebnath.openplayer.ui.skin.SliderLook
import io.github.parthibdebnath.openplayer.ui.skin.SongRowModel
import io.github.parthibdebnath.openplayer.ui.skin.pressedState
import io.github.parthibdebnath.openplayer.ui.skin.skinClick
import io.github.parthibdebnath.openplayer.ui.skin.skinTap
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.formatClockTime
import io.github.parthibdebnath.openplayer.util.formatDurationMs
import kotlin.math.max
import kotlin.math.roundToInt

/** Mac OS 8 and 9's Platinum appearance. */
private object Pt {
    val face = Color(0xFFDDDDDD)
    val light = Color(0xFFEEEEEE)
    val shadow = Color(0xFF999999)
    val darkShadow = Color(0xFF777777)
    val frame = Color(0xFF000000)
    val highlight = Color(0xFFCCCCFF)
    val menuHighlight = Color(0xFF3333CC)
    val thumb = Color(0xFFCCCCFF)
    val thumbEdge = Color(0xFF666699)
    val desktop = Color(0xFF6666AA)
}

private fun DrawScope.u(): Float = max(1f, density.roundToInt().toFloat())

/** A Platinum bevel: a black (or grey) line round the edge, lit white inside top-left, grey bottom-right. */
private fun DrawScope.drawPlatinum(rect: Rect, pressed: Boolean = false, fill: Color = Pt.face, border: Color? = Pt.frame, radius: Float = 0f) {
    val p = u()
    val corner = CornerRadius(radius)
    drawRoundRect(if (pressed) Color(0xFF888888) else fill, rect.topLeft, rect.size, corner)
    val inner = Rect(rect.left + p, rect.top + p, rect.right - p, rect.bottom - p)
    if (!pressed) {
        drawRect(Color.White, inner.topLeft, Size(inner.width - p, p))
        drawRect(Color.White, inner.topLeft, Size(p, inner.height - p))
        drawRect(Pt.shadow, Offset(inner.left + p, inner.bottom - p), Size(inner.width - p, p))
        drawRect(Pt.shadow, Offset(inner.right - p, inner.top + p), Size(p, inner.height - p))
    } else {
        drawRect(Color(0xFF555555), inner.topLeft, Size(inner.width, p))
        drawRect(Color(0xFF555555), inner.topLeft, Size(p, inner.height))
    }
    if (border != null) drawRoundRect(border, Offset(rect.left + p / 2f, rect.top + p / 2f), Size(rect.width - p, rect.height - p), corner, style = Stroke(p))
}

/** A sunken well: grey above and left, white below and right. */
private fun DrawScope.drawWell(rect: Rect, fill: Color = Color.White) {
    val p = u()
    drawRect(fill, rect.topLeft, rect.size)
    drawRect(Pt.shadow, rect.topLeft, Size(rect.width, p))
    drawRect(Pt.shadow, rect.topLeft, Size(p, rect.height))
    drawRect(Color.White, Offset(rect.left, rect.bottom - p), Size(rect.width, p))
    drawRect(Color.White, Offset(rect.right - p, rect.top), Size(p, rect.height))
    drawRect(Color.Black, Offset(rect.left + p, rect.top + p), Size(rect.width - 2 * p, p))
    drawRect(Color.Black, Offset(rect.left + p, rect.top + p), Size(p, rect.height - 2 * p))
    drawRect(Pt.face, Offset(rect.left + p, rect.bottom - 2 * p), Size(rect.width - 2 * p, p))
    drawRect(Pt.face, Offset(rect.right - 2 * p, rect.top + p), Size(p, rect.height - 2 * p))
}

/** The ridges across a Platinum title bar. */
private fun DrawScope.drawRidges(rect: Rect) {
    val p = u()
    var y = rect.top
    while (y + 2 * p <= rect.bottom) {
        drawRect(Color.White, Offset(rect.left, y), Size(rect.width, p))
        drawRect(Pt.shadow, Offset(rect.left, y + p), Size(rect.width, p))
        y += 2 * p
    }
}

/** Mac OS 9's folder: blue-lavender, with its tab and a lighter face. */
private fun DrawScope.drawOs9Folder(rect: Rect) {
    val w = rect.width
    val h = rect.height
    val p = u()
    val tab = Path().apply {
        moveTo(rect.left + w * 0.06f, rect.top + h * 0.18f)
        lineTo(rect.left + w * 0.40f, rect.top + h * 0.18f)
        lineTo(rect.left + w * 0.48f, rect.top + h * 0.28f)
        lineTo(rect.left + w * 0.94f, rect.top + h * 0.28f)
        lineTo(rect.left + w * 0.94f, rect.top + h * 0.88f)
        lineTo(rect.left + w * 0.06f, rect.top + h * 0.88f)
        close()
    }
    drawPath(tab, Color(0xFF9999FF))
    drawPath(tab, Color(0xFF333399), style = Stroke(p))
    val face = Rect(rect.left + w * 0.06f, rect.top + h * 0.36f, rect.left + w * 0.94f, rect.top + h * 0.88f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCCCCFF), Color(0xFFAAAAFF)), startY = face.top, endY = face.bottom), face.topLeft, face.size)
    drawRect(Color(0xFF333399), face.topLeft, face.size, style = Stroke(p))
    drawRect(Color.White.copy(alpha = 0.7f), Offset(face.left + p, face.top + p), Size(face.width - 2 * p, p))
}

/**
 * Platinum, the look of Mac OS 8 and 9: a grey menu bar with the clock and the application menu,
 * windows with ridged title bars and square boxes, Charcoal everywhere, lavender scroll thumbs and
 * progress, and the Finder's icon and list views. Calm, square and dense.
 */
object PlatinumSkin : Skin() {
    override val theme = UiTheme.PLATINUM

    /** The menu bar above; the desktop below. */
    override val edgeTop = Color(0xFFDDDDDD)
    override val edgeBottom = Color(0xFF6666AA)
    override val colors = AppColors(background = Pt.face, onBackground = Color.Black)
    override val showsHeader = false
    override val gutter: Dp = 10.dp

    @Composable
    private fun px(n: Int): Dp = with(LocalDensity.current) { (n * max(1, density.roundToInt())).toDp() }

    /** Charcoal, the system font: menus, buttons, titles. ChicagoFLF stands in. */
    private fun system(size: Float) = TextStyle(fontFamily = SkinFonts.chicago, fontSize = size.sp, lineHeight = (size * 1.3f).sp)

    /** Geneva, for small text. */
    private fun small(size: Float, bold: Boolean = false) = TextStyle(
        fontFamily = SkinFonts.aqua,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = (size * 1.28f).sp,
    )

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle = when {
        // Headings, and anything bold, are in the system font; the rest is Geneva.
        sizeSp >= 18 || bold -> system(if (sizeSp >= 20) 15.5f else 13.5f)
        sizeSp >= 16 -> small(13.5f)
        sizeSp >= 14 -> small(12f)
        else -> small(11f)
    }

    // --- frame ---------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        // The screen's rounded corners, as on every Mac, with the desktop behind the window.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Pt.desktop),
            ) {
                MenuBar(model)
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = px(4), end = px(4), top = px(4), bottom = px(10))) {
                    Window(
                        title = "openplayer",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Tabs(model)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = px(6))
                                .padding(bottom = px(6))
                                .drawBehind { drawPlatinum(Rect(Offset.Zero, size), border = Pt.darkShadow) }
                                .padding(px(6)),
                        ) {
                            content(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Window(title: String, modifier: Modifier, onClose: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier.drawBehind {
                val p = u()
                drawRect(Pt.face)
                // A one-pixel black frame, a white highlight inside it, and a shadow down the right and foot.
                drawRect(Color.Black, Offset.Zero, size, style = Stroke(p * 2))
                drawRect(Color.White, Offset(p, p), Size(size.width - 2 * p, p))
                drawRect(Color.White, Offset(p, p), Size(p, size.height - 2 * p))
                drawRect(Pt.shadow, Offset(size.width - 2 * p, p), Size(p, size.height - 2 * p))
                drawRect(Pt.shadow, Offset(p, size.height - 2 * p), Size(size.width - 2 * p, p))
            },
        ) {
            TitleBar(title, onClose)
            content()
        }
    }

    @Composable
    private fun TitleBar(title: String, onClose: (() -> Unit)?) {
        Box(Modifier.fillMaxWidth().height(px(22)).padding(horizontal = px(3)).padding(top = px(3))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRidges(Rect(0f, u() * 3, size.width, size.height - u() * 3))
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(px(6)))
                Box(Modifier.background(Pt.face).padding(horizontal = px(3))) {
                    WindowBox(BoxKind.Close, onClose)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.background(Pt.face).padding(horizontal = px(3))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(px(4))) {
                        WindowBox(BoxKind.Zoom, null)
                        WindowBox(BoxKind.Collapse, null)
                    }
                }
                Spacer(Modifier.width(px(6)))
            }
            Text(
                title,
                style = system(13.5f),
                color = Color.Black,
                modifier = Modifier.align(Alignment.Center).background(Pt.face).padding(horizontal = px(8)),
            )
        }
    }

    private enum class BoxKind { Close, Zoom, Collapse }

    @Composable
    private fun WindowBox(kind: BoxKind, onClick: (() -> Unit)?) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Canvas(
            Modifier
                .size(px(13))
                .then(if (onClick != null) Modifier.testTag("dialog:dismiss").skinClick(interaction) { onClick() } else Modifier),
        ) {
            val p = u()
            drawRect(Color(0xFF555555), Offset.Zero, size)
            drawRect(if (pressed) Color(0xFF888888) else Pt.light, Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p))
            drawRect(Color.White, Offset(p, p), Size(size.width - 3 * p, p))
            drawRect(Color.White, Offset(p, p), Size(p, size.height - 3 * p))
            drawRect(Pt.shadow, Offset(2 * p, size.height - 2 * p), Size(size.width - 3 * p, p))
            drawRect(Pt.shadow, Offset(size.width - 2 * p, 2 * p), Size(p, size.height - 3 * p))
            when (kind) {
                BoxKind.Zoom -> drawRect(Color(0xFF555555), Offset(p * 2, p * 2), Size(size.width * 0.5f, size.height * 0.5f), style = Stroke(p))
                BoxKind.Collapse -> {
                    drawRect(Color(0xFF555555), Offset(p * 2, size.height / 2f - p * 1.5f), Size(size.width - 4 * p, p))
                    drawRect(Color(0xFF555555), Offset(p * 2, size.height / 2f + p * 0.5f), Size(size.width - 4 * p, p))
                }
                BoxKind.Close -> Unit
            }
        }
    }

    private fun windowName(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    @Composable
    private fun MenuBar(model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        Row(
            Modifier
                .fillMaxWidth()
                .height(px(22))
                .drawBehind {
                    drawRect(Pt.face)
                    drawRect(Color.White, Offset.Zero, Size(size.width, u()))
                    drawRect(Color.Black, Offset(0f, size.height - u()), Size(size.width, u()))
                }
                .padding(horizontal = px(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A little rainbow note where the Apple menu would be: this is openplayer's own menu bar.
            Box(Modifier.testTag("frame:menu")) {
                MenuTitle(null, listOf(ContextMenuItem("About openplayer…", onClick = {}), ContextMenuItem("Control Panels…", onClick = model.onOpenSettings)))
            }
            MenuTitle("File", listOf(ContextMenuItem("Preferences…", onClick = model.onOpenSettings)))
            MenuTitle("Controls", listOf(
                ContextMenuItem(if (model.playback.isPlaying) "Pause" else "Play", onClick = model.onPlayPause),
                ContextMenuItem("Next Track", onClick = model.onNext),
                ContextMenuItem("Previous Track", onClick = model.onPrevious),
            ))
            MenuTitle("Window", model.tabs.mapIndexed { i, tab ->
                ContextMenuItem(windowName(tab), selected = i == model.selectedTab, onClick = { model.onSelectTab(i) })
            })
            Spacer(Modifier.weight(1f))
            Text("${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = system(13f), color = Color.Black)
            Spacer(Modifier.width(px(10)))
            // The application menu, at the far right.
            Box(Modifier.size(px(1), px(16)).background(Pt.shadow))
            Spacer(Modifier.width(px(8)))
            // Collapsed to its icon, as Mac OS 9 let it be on a narrow screen.
            NoteIcon(Modifier.size(px(16)))
        }
    }

    @Composable
    private fun NoteIcon(modifier: Modifier) {
        Canvas(modifier) {
            val stripes = listOf(Color(0xFF61BB46), Color(0xFFFDB827), Color(0xFFF5821F), Color(0xFFE03A3E), Color(0xFF963D97), Color(0xFF009DDC))
            val band = size.height / stripes.size
            val note = Path().apply {
                moveTo(size.width * 0.42f, size.height * 0.08f)
                lineTo(size.width * 0.92f, size.height * 0.0f)
                lineTo(size.width * 0.92f, size.height * 0.72f)
                cubicTo(size.width * 0.92f, size.height * 0.95f, size.width * 0.55f, size.height * 0.95f, size.width * 0.58f, size.height * 0.74f)
                cubicTo(size.width * 0.6f, size.height * 0.6f, size.width * 0.78f, size.height * 0.58f, size.width * 0.8f, size.height * 0.62f)
                lineTo(size.width * 0.8f, size.height * 0.22f)
                lineTo(size.width * 0.54f, size.height * 0.27f)
                lineTo(size.width * 0.54f, size.height * 0.82f)
                cubicTo(size.width * 0.54f, size.height * 1.05f, size.width * 0.12f, size.height * 1.05f, size.width * 0.16f, size.height * 0.82f)
                cubicTo(size.width * 0.18f, size.height * 0.68f, size.width * 0.38f, size.height * 0.66f, size.width * 0.42f, size.height * 0.7f)
                close()
            }
            clipPath(note) {
                stripes.forEachIndexed { i, c -> drawRect(c, Offset(0f, band * i), Size(size.width, band + 1f)) }
            }
        }
    }

    @Composable
    private fun MenuTitle(title: String?, items: List<ContextMenuItem>) {
        var open by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier
                    .then(if (open) Modifier.background(Pt.menuHighlight) else Modifier)
                    .skinTap { open = true }
                    .padding(horizontal = px(8), vertical = px(2)),
            ) {
                if (title == null) NoteIcon(Modifier.size(px(16))) else Text(title, style = system(13f), color = if (open) Color.White else Color.Black)
            }
            if (open) {
                Popup(
                    offset = IntOffset(0, with(LocalDensity.current) { px(20).roundToPx() }),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val haptics = LocalHaptics.current
                    Menu(items) { item ->
                        open = false
                        haptics?.click()
                        item.onClick()
                    }
                }
            }
        }
    }

    /** Appearance Manager tabs: a centred row, the chosen one lighter and joined to its pane. */
    @Composable
    private fun Tabs(model: FrameModel) {
        Box(Modifier.fillMaxWidth().height(px(26)).padding(top = px(4)), contentAlignment = Alignment.BottomCenter) {
            Row(verticalAlignment = Alignment.Bottom) {
                model.tabs.forEachIndexed { i, tab ->
                    PlatinumTab(windowName(tab), i == model.selectedTab, { model.onSelectTab(i) }, Modifier.testTag("tab:$tab"))
                }
            }
        }
    }

    @Composable
    private fun PlatinumTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
        val haptics = LocalHaptics.current
        Box(
            modifier
                .offset(y = px(1))
                .height(px(21))
                .semantics { role = Role.Tab; this.selected = selected }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                    if (!selected) haptics?.tick()
                    onClick()
                }
                .drawBehind {
                    val p = u()
                    val r = p * 4
                    val shape = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, r)
                        quadraticTo(0f, 0f, r, 0f)
                        lineTo(size.width - r, 0f)
                        quadraticTo(size.width, 0f, size.width, r)
                        lineTo(size.width, size.height)
                    }
                    drawPath(shape, if (selected) Pt.light else Color(0xFFBBBBBB))
                    drawLine(Color.White, Offset(p * 1.5f, size.height), Offset(p * 1.5f, r), p)
                    drawLine(Color.White, Offset(r, p * 1.5f), Offset(size.width - r, p * 1.5f), p)
                    drawPath(shape, Pt.darkShadow, style = Stroke(p))
                    if (selected) drawRect(Pt.light, Offset(p, size.height - p), Size(size.width - 2 * p, p * 2))
                }
                .padding(horizontal = px(12)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = system(13f), color = Color.Black, maxLines = 1)
        }
    }

    // --- controls ------------------------------------------------------------------------------

    @Composable
    override fun IconButton(
        icon: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        size: Dp,
        iconSize: Dp,
        opacity: Float,
        enabled: Boolean,
        kind: ButtonKind,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val click = Modifier.semantics { this.contentDescription = contentDescription }.skinClick(interaction, enabled) { onClick() }
        when (kind) {
            ButtonKind.Toggle -> Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) { Checkbox(icon == Icons.Filled.Check, pressed) }
            ButtonKind.Confirm -> PushButton(if (icon == Icons.Filled.Add) "Add" else "OK", default = true, pressed = pressed, modifier = modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f).then(click))
            ButtonKind.Chevron -> Box(modifier.size(size.coerceAtLeast(24.dp)).then(click), contentAlignment = Alignment.Center) {
                // A disclosure triangle.
                Canvas(Modifier.size(px(9), px(9))) {
                    val area = drawContext.size
                    val path = Path().apply {
                        moveTo(0f, area.height * 0.8f)
                        lineTo(area.width, area.height * 0.8f)
                        lineTo(area.width / 2f, area.height * 0.15f)
                        close()
                    }
                    drawPath(path, Color(0xFF555555))
                    drawPath(path, Color.Black, style = Stroke(u()))
                }
            }
            else -> {
                // Bevel buttons: square, with the icon in the middle.
                val side = when (kind) {
                    ButtonKind.Play -> px(48)
                    ButtonKind.Next, ButtonKind.Previous -> px(38)
                    else -> if (size >= 40.dp) px(30) else px(24)
                }
                Box(
                    modifier.size(side).then(click).drawBehind { drawPlatinum(Rect(Offset.Zero, this.size), pressed, border = Color(0xFF555555)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        null,
                        tint = (if (pressed) Color.White else Color.Black).copy(alpha = if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f),
                        modifier = Modifier.size(if (kind == ButtonKind.Play) px(28) else if (kind == ButtonKind.Plain) iconSize.coerceAtMost(px(16)) else px(20)),
                    )
                }
            }
        }
    }

    @Composable
    private fun Checkbox(checked: Boolean, pressed: Boolean) {
        Canvas(Modifier.size(px(12))) {
            val p = u()
            drawRect(Color.Black, Offset.Zero, size)
            drawRect(if (pressed) Pt.shadow else Pt.light, Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p))
            drawRect(Color.White, Offset(p, p), Size(size.width - 3 * p, p))
            drawRect(Color.White, Offset(p, p), Size(p, size.height - 3 * p))
            if (checked) {
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.5f)
                    lineTo(size.width * 0.42f, size.height * 0.75f)
                    lineTo(size.width * 1.0f, size.height * 0.0f)
                }
                drawPath(path, Color.Black, style = Stroke(p * 1.6f))
            }
        }
    }

    @Composable
    private fun PushButton(label: String, default: Boolean, pressed: Boolean, modifier: Modifier) {
        Box(
            modifier
                .heightIn(min = px(26))
                .widthIn(min = px(70))
                .drawBehind {
                    val p = u()
                    if (default) {
                        // The default button's ring: thick, rounded, with a gap.
                        drawRoundRect(Color.Black, Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p), CornerRadius(p * 6), style = Stroke(p * 2.5f))
                        drawPlatinum(Rect(p * 4, p * 4, size.width - p * 4, size.height - p * 4), pressed, radius = p * 3)
                    } else {
                        drawPlatinum(Rect(p * 4, p * 4, size.width - p * 4, size.height - p * 4), pressed, radius = p * 3)
                    }
                }
                .padding(horizontal = px(14), vertical = px(7)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = system(13f), color = if (pressed) Color.White else Color.Black)
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind { drawPlatinum(Rect(Offset.Zero, size), border = Color(0xFF555555)) }
        GlassRole.Floating -> modifier.drawBehind {
            // A movable modal dialog: a double-edged frame.
            drawRect(Color.Black, Offset(u() * 2, u() * 2), size)
            drawPlatinum(Rect(Offset.Zero, size))
        }
        else -> modifier.drawBehind {
            // A secondary group box: an etched, rounded frame.
            val p = u()
            drawRoundRect(Pt.shadow, Offset.Zero, Size(size.width - p, size.height - p), CornerRadius(p * 3), style = Stroke(p))
            drawRoundRect(Color.White, Offset(p, p), Size(size.width - p, size.height - p), CornerRadius(p * 3), style = Stroke(p))
        }
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier.drawBehind {
        if (focused) {
            // The keyboard focus ring: a lavender band round the field.
            drawRect(Pt.thumb, Offset(-u() * 3, -u() * 3), Size(size.width + u() * 6, size.height + u() * 6))
        }
        drawWell(Rect(Offset.Zero, size))
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Canvas(modifier.fillMaxWidth().height(px(2))) {
            drawRect(Pt.shadow, Offset.Zero, Size(size.width, u()))
            drawRect(Color.White, Offset(0f, u()), Size(size.width, u()))
        }
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(px(15)).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            val p = u()
            drawRect(Color(0xFFBBBBBB))
            drawRect(Color(0xFF888888), Offset.Zero, Size(p, size.height))
            drawRect(Color(0xFF888888), Offset(0f, 0f), Size(size.width, p))
            val thumbH = (size.height * visible / total).coerceAtLeast(p * 24)
            val top = (size.height - thumbH) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            val rect = Rect(p, top, size.width, top + thumbH)
            // The lavender thumb, with its grip ridges.
            drawRect(Pt.thumb, rect.topLeft, rect.size)
            drawRect(Color.White, rect.topLeft, Size(rect.width, p))
            drawRect(Color.White, rect.topLeft, Size(p, rect.height))
            drawRect(Pt.thumbEdge, rect.topLeft, rect.size, style = Stroke(p))
            for (i in -2..2) {
                val y = (rect.center.y / p).roundToInt() * p + i * 2 * p
                drawRect(Color.White, Offset(rect.left + p * 3, y), Size(rect.width - p * 6, p))
                drawRect(Pt.thumbEdge, Offset(rect.left + p * 4, y + p), Size(rect.width - p * 6, p))
            }
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 30.dp
        override val thumbWidth: Dp = 14.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val p = u()
            val tw = thumbWidth.toPx()
            val cy = (size.height * 0.42f / p).roundToInt() * p
            // A thin sunken groove.
            drawWell(Rect(tw / 2f, cy - p * 3, size.width - tw / 2f, cy + p * 3), Color(0xFFAAAAAA))
            if (steps in 1..20) {
                for (i in 0..steps + 1) {
                    val x = ((tw / 2f + (size.width - tw) * i / (steps + 1)) / p).roundToInt() * p
                    drawRect(Color.Black, Offset(x, size.height - p * 5), Size(p, p * 3))
                }
            }
            val x = ((tw / 2f + (size.width - tw) * fraction) / p).roundToInt() * p
            val half = (tw / 2f / p).roundToInt() * p
            val top = cy - p * 9
            val bottom = cy + p * 6
            // The thumb: a little house pointing down, lavender with ridges.
            val path = Path().apply {
                moveTo(x - half, top)
                lineTo(x + half, top)
                lineTo(x + half, bottom)
                lineTo(x, bottom + half)
                lineTo(x - half, bottom)
                close()
            }
            drawPath(path, if (pressed) Color(0xFF9999EE) else if (enabled) Pt.thumb else Pt.face)
            drawLine(Color.White, Offset(x - half + p, top + p), Offset(x - half + p, bottom), p)
            drawPath(path, if (enabled) Pt.thumbEdge else Pt.shadow, style = Stroke(p))
            for (i in 0..2) {
                val y = top + p * 4 + i * p * 3
                drawRect(Color.White, Offset(x - half + p * 3, y), Size(half * 2 - p * 6, p))
                drawRect(Pt.thumbEdge, Offset(x - half + p * 3, y + p), Size(half * 2 - p * 6, p))
            }
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val haptics = LocalHaptics.current
        Row(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Checkbox; selected = checked }
                .skinClick(interaction, role = Role.Checkbox) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = px(3)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, pressed)
            Spacer(Modifier.width(px(6)))
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = Color.Black, modifier = Modifier.weight(1f))
        }
    }

    @Composable
    override fun Switcher(
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier,
        height: Dp,
        fontSizeSp: Float,
        icons: List<ImageVector>?,
    ) {
        if (height >= 48.dp) {
            Box(
                modifier.fillMaxWidth().height(px(24)).drawBehind { drawRect(Pt.darkShadow, Offset(0f, size.height - u()), Size(size.width, u())) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    items.forEachIndexed { i, label -> PlatinumTab(label, i == selectedIndex, { onSelect(i) }) }
                }
            }
        } else {
            val haptics = LocalHaptics.current
            Row(modifier.fillMaxWidth().heightIn(min = px(24)), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton; selected = i == selectedIndex }
                            .skinClick(remember { MutableInteractionSource() }, role = Role.RadioButton) {
                                if (i != selectedIndex) haptics?.tick()
                                onSelect(i)
                            }
                            .padding(vertical = px(4)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioMark(i == selectedIndex)
                        Spacer(Modifier.width(px(4)))
                        Text(label, style = small(12f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }

    /** The Appearance Manager's radio button: a grey bevelled circle, with a black dot when chosen. */
    @Composable
    private fun RadioMark(selected: Boolean) {
        Canvas(Modifier.size(px(12))) {
            val r = size.minDimension / 2f
            drawCircle(Pt.light, r)
            drawArc(Color.White, 135f, 180f, false, Offset(u(), u()), Size(size.width - 2 * u(), size.height - 2 * u()), style = Stroke(u()))
            drawCircle(Color.Black, r - u() / 2f, style = Stroke(u()))
            if (selected) drawCircle(Color.Black, r * 0.42f)
        }
    }

    @Composable
    override fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier, swatch: Color?) {
        val haptics = LocalHaptics.current
        Row(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.RadioButton; this.selected = selected }
                .skinClick(remember { MutableInteractionSource() }, role = Role.RadioButton) {
                    if (!selected) haptics?.tick()
                    onSelect()
                }
                .padding(vertical = px(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioMark(selected)
            Spacer(Modifier.width(px(6)))
            if (swatch != null) {
                Box(Modifier.size(px(18), px(12)).background(swatch).border(1.dp, Color.Black))
                Spacer(Modifier.width(px(5)))
            }
            Text(label, style = small(12f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }

    // --- menus and alerts -----------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .padding(end = px(3), bottom = px(3))
                .drawBehind {
                    // A one-pixel frame with a hard shadow, as Mac menus had.
                    drawRect(Color(0x66000000), Offset(u() * 2, u() * 2), size)
                    drawRect(Pt.face)
                    drawRect(Color.Black, Offset.Zero, size, style = Stroke(u()))
                    drawRect(Color.White, Offset(u(), u()), Size(size.width - 2 * u(), u()))
                }
                .width(IntrinsicSize.Max)
                .widthIn(min = px(150))
                .padding(vertical = px(4)),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(px(24))
                        .then(if (pressed) Modifier.background(Pt.menuHighlight) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(end = px(18)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (item.selected) "✓" else "", style = system(13f), color = if (pressed) Color.White else Color.Black, modifier = Modifier.width(px(18)), textAlign = TextAlign.Center)
                    Text(item.label, style = system(13f), color = if (pressed) Color.White else Color.Black, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Box(modifier.fillMaxWidth()) {
            Window(title = "openplayer", modifier = Modifier.fillMaxWidth(), onClose = onDismiss) {
                Column(Modifier.fillMaxWidth().padding(px(12))) {
                    Row {
                        // The note icon, as the alert's application icon.
                        Box(Modifier.size(px(32)).drawBehind { drawPlatinum(Rect(Offset.Zero, size), border = Color(0xFF555555)) }, contentAlignment = Alignment.Center) {
                            NoteIcon(Modifier.size(px(22)))
                        }
                        Spacer(Modifier.width(px(12)))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = system(13.5f), color = Color.Black)
                            Spacer(Modifier.height(px(4)))
                            Text(message, style = small(12f), color = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(px(10)))
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(px(10))) {
                        val ok = remember { MutableInteractionSource() }
                        val cancel = remember { MutableInteractionSource() }
                        val okPressed by pressedState(ok)
                        val cancelPressed by pressedState(cancel)
                        PushButton("Cancel", default = false, pressed = cancelPressed, modifier = Modifier.skinClick(cancel) { onDismiss() })
                        PushButton("OK", default = true, pressed = okPressed, modifier = Modifier.skinClick(ok) { onConfirm() })
                    }
                }
            }
        }
    }

    // --- Finder views ----------------------------------------------------------------------------

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        // The Finder's icon view, in a white window pane.
        Box(modifier.drawBehind { drawWell(Rect(Offset.Zero, size)) }.padding(px(3))) {
            if (emptyMessage != null) Text(emptyMessage, style = small(12f), color = Color.Black, modifier = Modifier.padding(px(8)))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(px(4))) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() }
                            .padding(vertical = px(8)),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(px(40), px(34)), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) { drawOs9Folder(Rect(Offset.Zero, size)) }
                                val badge = when (entry.kind) {
                                    CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite
                                    CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label
                                    CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote
                                }
                                Icon(badge, null, tint = Color(0xFF333399), modifier = Modifier.size(px(14)).offset(y = px(4)))
                            }
                            Spacer(Modifier.height(px(3)))
                            Text(
                                entry.title,
                                style = small(11.5f),
                                color = if (entry.emphasized) Color.White else Color.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = if (entry.emphasized) Modifier.background(Color.Black).padding(horizontal = px(2)) else Modifier,
                            )
                        }
                        entry.menu()
                    }
                }
            }
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Column(modifier.drawBehind { drawWell(Rect(Offset.Zero, size)) }.padding(px(2))) {
            ColumnHeaders(listOf("Name" to null, "Artist" to px(118)))
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }

    @Composable
    private fun ColumnHeaders(columns: List<Pair<String, Dp?>>) {
        Row(Modifier.fillMaxWidth().height(px(18))) {
            columns.forEachIndexed { i, (label, width) ->
                Box(
                    (if (width == null) Modifier.weight(1f) else Modifier.width(width))
                        .fillMaxHeight()
                        .drawBehind {
                            // The sorted column is the darker one, as the Finder showed it.
                            drawPlatinum(Rect(Offset.Zero, size), fill = if (i == 0) Color(0xFFBBBBBB) else Pt.face, border = null)
                            drawRect(Pt.darkShadow, Offset(0f, size.height - u()), Size(size.width, u()))
                            drawRect(Pt.darkShadow, Offset(size.width - u(), 0f), Size(u(), size.height))
                        }
                        .padding(horizontal = px(5)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label, style = small(11.5f, bold = i == 0), color = Color.Black, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.5f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = px(4), end = px(18)), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF333399), modifier = Modifier.size(px(12)))
                Spacer(Modifier.width(px(5)))
                Box(Modifier.weight(1f)) {
                    MarqueeText(
                        text = model.title,
                        style = small(12.5f),
                        color = Color.Black,
                        scrollRequest = model.titleScrollRequest,
                        idleOverflow = TextOverflow.Ellipsis,
                        modifier = if (model.isCurrent) Modifier.background(Pt.highlight).padding(horizontal = px(2)) else Modifier,
                    )
                }
                Spacer(Modifier.width(px(6)))
                Text(model.artist, style = small(12.5f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(px(104)))
            }
            model.menu()
        }
    }

    // --- Now Playing -----------------------------------------------------------------------------

    @Composable
    private fun GroupBox(caption: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Box(modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = px(8))
                    .drawBehind {
                        val p = u()
                        drawRoundRect(Pt.shadow, Offset.Zero, Size(size.width - p, size.height - p), CornerRadius(p * 3), style = Stroke(p))
                        drawRoundRect(Color.White, Offset(p, p), Size(size.width - p, size.height - p), CornerRadius(p * 3), style = Stroke(p))
                    }
                    .padding(start = px(10), end = px(10), top = px(12), bottom = px(10)),
                content = content,
            )
            Text(caption, style = system(13f), color = Color.Black, modifier = Modifier.padding(start = px(10)).background(Pt.face).padding(horizontal = px(3)))
        }
    }

    /** Platinum's progress bar: a well filled with lavender-blue. Tap or drag along it to seek. */
    @Composable
    private fun Progress(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit, modifier: Modifier) {
        var drag by remember { mutableStateOf<Float?>(null) }
        val currentOnSeek by rememberUpdatedState(onSeek)
        var widthPx by remember { mutableFloatStateOf(1f) }
        val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Canvas(
            modifier
                .height(px(14))
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        drag = (down.position.x / widthPx).coerceIn(0f, 1f)
                        horizontalDrag(down.id) { change ->
                            change.consume()
                            drag = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                        drag?.let { currentOnSeek(it) }
                        drag = null
                    }
                },
        ) {
            val p = u()
            drawRect(Color(0xFFEEEEEE))
            val w = ((size.width - 2 * p) * fraction / p).roundToInt() * p
            drawRect(Brush.verticalGradient(listOf(Color(0xFFCCCCFF), Color(0xFF9999FF), Color(0xFF6666CC), Color(0xFF9999FF)), startY = p, endY = size.height - p), Offset(p, p), Size(w, size.height - 2 * p))
            drawRect(Color(0xFF333333), Offset.Zero, size, style = Stroke(p))
        }
    }

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(px(4)), verticalArrangement = Arrangement.spacedBy(px(8))) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel -> GroupBox("Sleep Timer") { panel(Modifier.fillMaxWidth()) } }
            GroupBox("Now Playing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A square CD icon, as the Finder would show a disc.
                    Box(Modifier.size(px(56)).drawBehind { drawPlatinum(Rect(Offset.Zero, size), border = Color(0xFF555555)) }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(px(40))) {
                            drawCircle(Brush.sweepGradient(listOf(Color(0xFFE6E6FF), Color(0xFFFFE6F0), Color(0xFFE6FFF0), Color(0xFFFFF6D6), Color(0xFFE6E6FF))), size.minDimension / 2f)
                            drawCircle(Color(0xFF555555), size.minDimension / 2f, style = Stroke(u()))
                            drawCircle(Pt.face, size.minDimension * 0.14f)
                            drawCircle(Color(0xFF555555), size.minDimension * 0.14f, style = Stroke(u()))
                        }
                    }
                    Spacer(Modifier.width(px(10)))
                    Column(Modifier.weight(1f)) {
                        MarqueeText(
                            text = song?.displayName ?: if (model.playbackRestored) "No song" else "",
                            style = system(15f),
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth().then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier),
                        )
                        Text(song?.artist.orEmpty(), style = small(12.5f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.playlistLabel, style = small(11.5f), color = Color(0xFF444444), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (song != null) {
                    Spacer(Modifier.height(px(10)))
                    Progress(p.positionMs, p.durationMs, model.onSeek, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = px(3))) {
                        Text(formatDurationMs(p.positionMs), style = small(11f), color = Color.Black)
                        Spacer(Modifier.weight(1f))
                        Text("-" + formatDurationMs((p.durationMs - p.positionMs).coerceAtLeast(0)), style = small(11f), color = Color.Black)
                    }
                } else if (model.playbackRestored) {
                    Spacer(Modifier.height(px(8)))
                    PushButton("Open Library…", default = false, pressed = false, modifier = Modifier.skinTap(onClick = model.onOpenLibrary))
                }
                Spacer(Modifier.height(px(10)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Previous)
                    Spacer(Modifier.width(px(6)))
                    IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 56.dp, 30.dp, 1f, true, ButtonKind.Play)
                    Spacer(Modifier.width(px(6)))
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Next)
                }
            }
            GroupBox("Options") {
                ToggleRow("Shuffle", model.shuffleOn, { model.onToggleShuffle() }, Modifier, false, 16)
                model.isFavourite?.let { fav -> ToggleRow("Favourite", fav, { model.onToggleFavourite() }, Modifier, false, 16) }
                Spacer(Modifier.height(px(6)))
                Row(horizontalArrangement = Arrangement.spacedBy(px(8))) {
                    PushButton("Sleep Timer…", default = false, pressed = false, modifier = Modifier.skinTap(onClick = model.onOpenSleepTimer))
                    if (model.outputSwitchAvailable) {
                        PushButton(
                            if (model.outputDevice == io.github.parthibdebnath.openplayer.audio.OutputDevice.BLUETOOTH) "Speaker" else "Bluetooth",
                            default = false,
                            pressed = false,
                            modifier = Modifier.skinTap(onClick = model.onToggleOutput),
                        )
                    }
                }
                val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, "Timer on".takeIf { model.sleepTimerActive }, model.message)
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(px(6)))
                    Text(notes.joinToString(" • "), style = small(11.5f), color = Color.Black)
                }
            }
            if (p.upcoming.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().drawBehind { drawWell(Rect(Offset.Zero, size)) }.padding(px(2))) {
                    ColumnHeaders(listOf("Up Next" to null, "Time" to px(76)))
                    p.upcoming.take(5).forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().height(px(24)).onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(start = px(5)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF333399), modifier = Modifier.size(px(12)))
                            Spacer(Modifier.width(px(5)))
                            Text(s.displayName, style = small(12.5f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatDurationMs(s.durationMs), style = small(12f), color = Color.Black, modifier = Modifier.width(px(40)))
                            IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 20.dp, 12.dp, 1f, true, ButtonKind.Plain)
                            Spacer(Modifier.width(px(4)))
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Column(modifier.fillMaxWidth().padding(top = px(4))) {
            Divider(Modifier)
            Spacer(Modifier.height(px(6)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 30.dp, 16.dp, 1f, true, ButtonKind.Previous)
                Spacer(Modifier.width(px(3)))
                IconButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 30.dp, 16.dp, 1f, true, ButtonKind.Play)
                Spacer(Modifier.width(px(3)))
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 30.dp, 16.dp, 1f, true, ButtonKind.Next)
                Spacer(Modifier.width(px(8)))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = song?.displayName ?: if (model.playbackRestored) "No song" else "",
                        style = system(13.5f),
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth().then(
                            if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier,
                        ),
                    )
                    Text(song?.artist.orEmpty(), style = small(11.5f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (model.onOpenSettings != null) {
                    PushButton("Settings…", default = false, pressed = false, modifier = Modifier.testTag("nav:settings").skinTap(onClick = model.onOpenSettings))
                }
            }
            if (song != null) {
                Spacer(Modifier.height(px(6)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDurationMs(model.positionMs), style = small(11f), color = Color.Black)
                    Spacer(Modifier.width(px(6)))
                    Progress(model.positionMs, model.durationMs, { f -> model.onSeek((f * model.durationMs).toLong()) }, Modifier.weight(1f))
                    Spacer(Modifier.width(px(6)))
                    Text(formatDurationMs(model.durationMs), style = small(11f), color = Color.Black)
                }
            }
            Spacer(Modifier.height(px(2)))
        }
    }
}
