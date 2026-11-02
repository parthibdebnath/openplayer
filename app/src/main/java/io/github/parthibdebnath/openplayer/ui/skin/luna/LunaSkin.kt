package io.github.parthibdebnath.openplayer.ui.skin.luna

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.parthibdebnath.openplayer.audio.OutputDevice
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

/**
 * Luna, Windows XP's default blue: a maximised window with the rounded blue title bar, property
 * sheet tabs, Explorer's task panes and Tiles view, and the taskbar with its green start button.
 * Play is a big green round "go" button.
 */
object LunaSkin : Skin() {
    override val theme = UiTheme.LUNA
    override val colors = AppColors(background = Color(0xFFFCFCFE), onBackground = Luna.text)
    override val gutter: Dp = 10.dp
    override val showsHeader = false

    private val font = SkinFonts.luna

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        // Tahoma 8pt was the whole of XP; headings went bold rather than much bigger.
        val size = when {
            sizeSp >= 20 -> 16f
            sizeSp >= 18 -> 15f
            sizeSp >= 16 -> 14f
            sizeSp >= 15 -> 13.5f
            sizeSp >= 14 -> 12.5f
            sizeSp >= 13 -> 12f
            else -> 11f
        }
        return TextStyle(fontFamily = font, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp, lineHeight = (size * 1.22f).sp)
    }

    private fun t(size: Float, bold: Boolean = false, color: Color = Color.Unspecified) =
        TextStyle(fontFamily = font, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp, color = color)

    // --- frame ---------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0xFF3A6EA5))) {
            Column(Modifier.fillMaxSize()) {
                TitleBar("openplayer")
                Column(Modifier.weight(1f).fillMaxWidth().background(Luna.windowBody)) {
                    MenuBar(model)
                    Tabs(model)
                    // The tab's page: a white sheet framed in the tab control's grey.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                            .background(Color(0xFFFCFCFE))
                            .border(1.dp, Luna.tabBorder),
                    ) {
                        content(Modifier.fillMaxSize())
                    }
                }
                Taskbar(model)
            }
        }
    }

    @Composable
    private fun TitleBar(title: String) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Brush.verticalGradient(*Luna.titleBar.toTypedArray()))
                .padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = t(14f, bold = true, color = Color.White).copy(shadow = Shadow(Color(0xFF0F1089), Offset(1.5f, 1.5f), 0f)),
                modifier = Modifier.weight(1f),
            )
            CaptionButton(CaptionKind.Minimise)
            Spacer(Modifier.width(2.dp))
            CaptionButton(CaptionKind.Maximise)
            Spacer(Modifier.width(2.dp))
            CaptionButton(CaptionKind.Close)
        }
    }

    private enum class CaptionKind { Minimise, Maximise, Close }

    @Composable
    private fun CaptionButton(kind: CaptionKind) {
        Canvas(Modifier.size(23.dp)) {
            val r = CornerRadius(px(3f))
            if (kind == CaptionKind.Close) {
                drawRoundRect(
                    Brush.radialGradient(
                        0f to Color(0xFFCC4600), 0.55f to Color(0xFFDC6527), 0.7f to Color(0xFFCD7546), 0.9f to Color(0xFFFFCCB2), 1f to Color.White,
                        center = Offset(size.width * 0.9f, size.height * 0.9f),
                        radius = size.width * 1.3f,
                    ),
                    cornerRadius = r,
                )
            } else {
                drawRoundRect(
                    Brush.radialGradient(
                        0f to Color(0xFF0054E9), 0.55f to Color(0xFF2263D5), 0.7f to Color(0xFF4479E4), 0.9f to Color(0xFFA3BBEC), 1f to Color.White,
                        center = Offset(size.width * 0.9f, size.height * 0.9f),
                        radius = size.width * 1.3f,
                    ),
                    cornerRadius = r,
                )
            }
            drawRoundRect(Color.White, cornerRadius = r, style = Stroke(px(1f)))
            val s = size.width
            when (kind) {
                CaptionKind.Minimise -> drawRect(Color.White, Offset(s * 0.27f, s * 0.64f), Size(s * 0.33f, px(2.6f)))
                CaptionKind.Maximise -> {
                    drawRect(Color.White, Offset(s * 0.25f, s * 0.25f), Size(s * 0.5f, s * 0.5f), style = Stroke(px(1.3f)))
                    drawRect(Color.White, Offset(s * 0.25f, s * 0.25f), Size(s * 0.5f, px(2.6f)))
                }
                CaptionKind.Close -> {
                    drawLine(Color.White, Offset(s * 0.3f, s * 0.3f), Offset(s * 0.7f, s * 0.7f), px(2.2f))
                    drawLine(Color.White, Offset(s * 0.7f, s * 0.3f), Offset(s * 0.3f, s * 0.7f), px(2.2f))
                }
            }
        }
    }

    /** openplayer's window icon: a little blue disc with a white note. */
    @Composable
    private fun AppIcon(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawXpOrb(center, size.minDimension / 2f, green = false, pressed = false) }
            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.62f))
        }
    }

    private fun windowName(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    @Composable
    private fun MenuBar(model: FrameModel) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .drawBehind { drawLine(Color.White, Offset(0f, size.height - px(0.5f)), Offset(size.width, size.height - px(0.5f)), px(1f)) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuTitle("File", listOf(ContextMenuItem("Options…", onClick = model.onOpenSettings)))
            MenuTitle("View", model.tabs.mapIndexed { i, tab ->
                ContextMenuItem(windowName(tab), selected = i == model.selectedTab, onClick = { model.onSelectTab(i) })
            })
            MenuTitle("Play", listOf(
                ContextMenuItem(if (model.playback.isPlaying) "Pause" else "Play", onClick = model.onPlayPause),
                ContextMenuItem("Next", onClick = model.onNext),
                ContextMenuItem("Previous", onClick = model.onPrevious),
            ))
            MenuTitle("Help", listOf(ContextMenuItem("About openplayer", onClick = {})))
        }
    }

    @Composable
    private fun MenuTitle(title: String, items: List<ContextMenuItem>) {
        var open by remember { mutableStateOf(false) }
        Box {
            Text(
                title,
                style = t(13f),
                color = if (open) Color.White else Color.Black,
                modifier = Modifier
                    .then(if (open) Modifier.background(Luna.selection) else Modifier)
                    .skinTap { open = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (open) {
                Popup(
                    offset = IntOffset(0, with(LocalDensity.current) { 22.dp.roundToPx() }),
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

    @Composable
    private fun Tabs(model: FrameModel) {
        Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 8.dp), verticalAlignment = Alignment.Bottom) {
            model.tabs.forEachIndexed { i, tab ->
                XpTab(
                    label = windowName(tab),
                    selected = i == model.selectedTab,
                    onClick = { model.onSelectTab(i) },
                    modifier = Modifier.testTag("tab:$tab"),
                )
            }
        }
    }

    /** A property-sheet tab: the chosen one is white, a little taller, with XP's orange top edge. */
    @Composable
    private fun XpTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
        val haptics = LocalHaptics.current
        Box(
            modifier
                .offset(y = 1.dp)
                .height(if (selected) (if (small) 28.dp else 29.dp) else (if (small) 25.dp else 26.dp))
                .semantics { role = Role.Tab; this.selected = selected }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                    if (!selected) haptics?.tick()
                    onClick()
                }
                .drawBehind {
                    val r = px(3f)
                    val shape = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, r)
                        quadraticTo(0f, 0f, r, 0f)
                        lineTo(size.width - r, 0f)
                        quadraticTo(size.width, 0f, size.width, r)
                        lineTo(size.width, size.height)
                    }
                    if (selected) {
                        drawPath(shape, Color(0xFFFCFCFE))
                    } else {
                        drawPath(shape, Brush.verticalGradient(listOf(Color.White, Color(0xFFECEBE6), Color(0xFFD9D6CB))))
                    }
                    drawPath(shape, Luna.tabBorder, style = Stroke(px(1f)))
                    if (selected) {
                        // XP's orange cap on the chosen tab.
                        drawRoundRect(Color(0xFFE68B2C), Offset(0f, 0f), Size(size.width, px(3f)), CornerRadius(r))
                        drawRect(Color(0xFFFFC83C), Offset(px(1f), px(1.5f)), Size(size.width - px(2f), px(1.5f)))
                        // Open at the foot, joining the page below.
                        drawLine(Color(0xFFFCFCFE), Offset(px(1f), size.height), Offset(size.width - px(1f), size.height), px(2f))
                    }
                }
                .padding(horizontal = if (small) 12.dp else 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = t(if (small) 12.5f else 13f), color = Color.Black, maxLines = 1)
        }
    }

    // --- the taskbar and Start menu --------------------------------------------------------------

    @Composable
    private fun Taskbar(model: FrameModel) {
        var startOpen by remember { mutableStateOf(false) }
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.verticalGradient(*Luna.taskbar.toTypedArray())),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StartButton(open = startOpen, onClick = { startOpen = !startOpen })
                Spacer(Modifier.width(8.dp))
                // The one running program, its button held in.
                Row(
                    Modifier
                        .width(156.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1C47A6), Color(0xFF1E52B7), Color(0xFF2560CB))))
                        .border(1.dp, Color(0xFF0F2F7C), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("openplayer", style = t(12.5f, color = Color.White), maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxHeight()
                        .background(Brush.verticalGradient(*Luna.tray.toTypedArray()))
                        .drawBehind {
                            drawLine(Color(0xFF1042AF), Offset(0f, 0f), Offset(0f, size.height), px(1.2f))
                            drawLine(Color(0xFF6CC3F7), Offset(px(1.2f), 0f), Offset(px(1.2f), size.height), px(1f))
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = t(12.5f, color = Color.White))
                }
            }
            if (startOpen) {
                Popup(
                    alignment = Alignment.BottomStart,
                    offset = IntOffset(0, -with(LocalDensity.current) { 36.dp.roundToPx() }),
                    onDismissRequest = { startOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    StartMenu(model) { startOpen = false }
                }
            }
        }
    }

    @Composable
    private fun StartButton(open: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .width(104.dp)
                .height(36.dp)
                .testTag("frame:menu")
                .semantics { contentDescription = "start" }
                .skinTap(onClick = onClick)
                .drawBehind {
                    val shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp).createOutline(size, layoutDirection, this)
                    drawOutline(shape, Brush.verticalGradient(*Luna.startButton.toTypedArray()))
                    if (open) drawOutline(shape, Color.Black.copy(alpha = 0.18f))
                    // The soft shine down its right edge.
                    drawOutline(shape, Brush.horizontalGradient(0.8f to Color.Transparent, 1f to Color.White.copy(alpha = 0.18f)))
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(start = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(22.dp)) { drawFlag() }
                Spacer(Modifier.width(5.dp))
                Text(
                    "start",
                    style = TextStyle(
                        fontFamily = SkinFonts.lunaStart,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        color = Color.White,
                        shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(2f, 2f), 2f),
                    ),
                )
            }
        }
    }

    /** A little four-colour flag waving, in the start button's spirit. */
    private fun DrawScope.drawFlag() {
        val w = size.width
        val h = size.height
        val colours = listOf(Color(0xFFF65314), Color(0xFF7CBB00), Color(0xFF00A1F1), Color(0xFFFFBB00))
        val cw = w * 0.42f
        val ch = h * 0.40f
        val gap = w * 0.06f
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).forEachIndexed { i, (cx, cy) ->
            val left = w * 0.06f + cx * (cw + gap)
            val top = h * 0.08f + cy * (ch + gap) + if (cx == 1) h * 0.04f else 0f
            val path = Path().apply {
                moveTo(left, top)
                quadraticTo(left + cw * 0.5f, top - h * 0.06f, left + cw, top + h * 0.02f)
                lineTo(left + cw, top + ch + h * 0.02f)
                quadraticTo(left + cw * 0.5f, top + ch - h * 0.06f, left, top + ch)
                close()
            }
            drawPath(path, colours[i])
        }
    }

    @Composable
    private fun StartMenu(model: FrameModel, close: () -> Unit) {
        val haptics = LocalHaptics.current
        fun pick(action: () -> Unit) {
            haptics?.click()
            close()
            action()
        }
        Column(
            Modifier
                .width(316.dp)
                .shadow(10.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Color(0xFF1C64D6)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1868CE), Color(0xFF0E60CB), Color(0xFF0E60CB), Color(0xFF0D5AC1))))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(2.dp, Color(0xFFD8E4F8), RoundedCornerShape(3.dp))
                        .drawBehind { drawHillside(Rect(Offset.Zero, size)) },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "openplayer",
                    style = t(16f, bold = true, color = Color.White).copy(shadow = Shadow(Color(0xFF0A2F7E), Offset(1.5f, 1.5f), 1f)),
                )
            }
            // The orange rule under the header.
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFDA884A), Color.Transparent))),
            )
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.weight(1f).background(Color.White).padding(vertical = 6.dp)) {
                    val icons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlayCircle, Icons.Filled.GraphicEq)
                    val tints = listOf(Color(0xFFE09D21), Color(0xFF2E9B28), Color(0xFF2B5EB8))
                    model.tabs.forEachIndexed { i, tab ->
                        StartItem(icons[i], tints[i], windowName(tab), bold = true) { pick { model.onSelectTab(i) } }
                    }
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color(0xFFD5D3CE)))
                    StartItem(
                        if (model.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        Color(0xFF2E9B28),
                        if (model.playback.isPlaying) "Pause" else "Play",
                        bold = false,
                    ) { pick(model.onPlayPause) }
                }
                Column(
                    Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .background(Color(0xFFD3E5FA))
                        .drawBehind { drawLine(Color(0xFF95BDEE), Offset(0f, 0f), Offset(0f, size.height), px(1f)) }
                        .padding(vertical = 6.dp),
                ) {
                    StartItem(Icons.Filled.LibraryMusic, Color(0xFFE09D21), "My Music", bold = true, color = Color(0xFF0A246A)) { pick { model.onSelectTab(0) } }
                    StartItem(Icons.Filled.Settings, Color(0xFF5A6E8C), "Control Panel", bold = false, color = Color(0xFF0A246A)) { pick(model.onOpenSettings) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1868CE), Color(0xFF0E60CB), Color(0xFF0D5AC1))))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFDF4A2A)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Close menu", style = t(12.5f, color = Color.White), modifier = Modifier.skinTap(onClick = close))
            }
        }
    }

    @Composable
    private fun StartItem(icon: ImageVector, tint: Color, label: String, bold: Boolean, color: Color = Color.Black, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (pressed) Modifier.background(Luna.selection) else Modifier)
                .skinClick(interaction, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = t(13f, bold = bold), color = if (pressed) Color.White else color)
        }
    }

    // --- controls ------------------------------------------------------------------------------

    private fun glyphTint(icon: ImageVector): Color = when (icon) {
        Icons.Filled.Add, Icons.Filled.PlayArrow, Icons.AutoMirrored.Filled.ArrowForward -> Color(0xFF2E9B28)
        Icons.Filled.Favorite, Icons.Filled.FavoriteBorder -> Color(0xFFD3332B)
        Icons.Filled.Search, Icons.Filled.Shuffle -> Color(0xFF2B5EB8)
        else -> Color(0xFF1F3561)
    }

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
        val click = Modifier
            .semantics { this.contentDescription = contentDescription }
            .skinClick(interaction, enabled) { onClick() }
        when (kind) {
            ButtonKind.Play, ButtonKind.Previous, ButtonKind.Next -> {
                val green = kind == ButtonKind.Play
                Box(
                    modifier.size(if (green) size * 0.92f else size.coerceAtMost(44.dp)).then(click).drawBehind {
                        drawXpOrb(center, this.size.minDimension / 2f - px(1f), green, pressed)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (green) iconSize * 0.95f else iconSize.coerceAtMost(24.dp)))
                }
            }
            ButtonKind.Toggle -> Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) { drawXpCheckbox(Rect(Offset.Zero, this.size), icon == Icons.Filled.Check, pressed) }
            }
            ButtonKind.Confirm -> PushButton(
                if (icon == Icons.Filled.Add) "Add" else "OK",
                default = true,
                pressed = pressed,
                modifier = modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f).then(click),
            )
            ButtonKind.Chevron -> Box(modifier.size(size.coerceAtLeast(26.dp)).then(click), contentAlignment = Alignment.Center) {
                PaneChevron(up = true)
            }
            else -> Box(
                modifier
                    .size(size.coerceIn(26.dp, 52.dp))
                    .then(click)
                    .drawBehind { drawXpButton(Rect(Offset.Zero, this.size), pressed) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = glyphTint(icon).copy(alpha = (if (enabled) 1f else 0.4f) * opacity.coerceAtLeast(0.4f)),
                    modifier = Modifier.size(iconSize.coerceIn(14.dp, 26.dp)),
                )
            }
        }
    }

    /** The round button on a task pane header, with its double chevron. */
    @Composable
    private fun PaneChevron(up: Boolean, onDark: Boolean = false) {
        Canvas(Modifier.size(19.dp)) {
            drawCircle(if (onDark) Color.White.copy(alpha = 0.9f) else Color.White, size.minDimension / 2f)
            drawCircle(if (onDark) Color(0xFF8DAEE8) else Color(0xFFB5C6EE), size.minDimension / 2f, style = Stroke(px(1f)))
            val c = Color(0xFF2E56B8)
            val w = size.width
            for (k in 0..1) {
                val y = w * (if (up) 0.42f else 0.36f) + k * w * 0.2f
                val d = if (up) -1f else 1f
                val path = Path().apply {
                    moveTo(w * 0.32f, y - d * w * 0.06f)
                    lineTo(w * 0.5f, y + d * w * 0.1f - d * w * 0.06f)
                    lineTo(w * 0.68f, y - d * w * 0.06f)
                }
                drawPath(path, c, style = Stroke(px(1.6f)))
            }
        }
    }

    @Composable
    private fun PushButton(label: String, default: Boolean, pressed: Boolean, modifier: Modifier) {
        Box(
            modifier
                .heightIn(min = 30.dp)
                .widthIn(min = 76.dp)
                .drawBehind {
                    val h = px(26f)
                    val top = (size.height - h) / 2f
                    drawXpButton(Rect(0f, top, size.width, top + h), pressed, default)
                }
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = t(13f), color = Color.Black)
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            // A round push button face.
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White, Color(0xFFECEBE6), Color(0xFFD6D0C5))))
            drawOutline(outline, Luna.buttonBorder, style = Stroke(px(1f)))
        }
        GlassRole.Floating -> modifier
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(Luna.windowBody, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF0831D9), RoundedCornerShape(4.dp))
        else -> modifier
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFD0D0BF), RoundedCornerShape(4.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier =
        modifier.background(Color.White).border(1.dp, Luna.fieldBorder)

    @Composable
    override fun Divider(modifier: Modifier) {
        Canvas(modifier.fillMaxWidth().height(2.dp)) {
            drawLine(Color(0xFFACA899), Offset(0f, px(0.5f)), Offset(size.width, px(0.5f)), px(1f))
            drawLine(Color.White, Offset(0f, px(1.5f)), Offset(size.width, px(1.5f)), px(1f))
        }
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(14.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            drawRect(Brush.horizontalGradient(listOf(Color(0xFFEEEDE5), Color(0xFFF7F6F0), Color(0xFFEEEDE5))))
            val thumbH = (size.height * visible / total).coerceAtLeast(px(30f))
            val top = (size.height - thumbH) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            val rect = Rect(px(1f), top, size.width - px(1f), top + thumbH)
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFC9D8FC), Color(0xFFBBCEF8), Color(0xFFB0C6F5)), startX = rect.left, endX = rect.right), rect.topLeft, rect.size, CornerRadius(px(3f)))
            drawRoundRect(Color(0xFF9DB6E8), rect.topLeft, rect.size, CornerRadius(px(3f)), style = Stroke(px(1f)))
            // The grip.
            for (i in -2..2) {
                val y = rect.center.y + i * px(2f)
                drawLine(Color(0xFFEEF4FE), Offset(rect.left + px(3f), y), Offset(rect.right - px(3f), y), px(1f))
                drawLine(Color(0xFF8CB0F8), Offset(rect.left + px(3f), y + px(1f)), Offset(rect.right - px(3f), y + px(1f)), px(1f))
            }
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 30.dp
        override val thumbWidth: Dp = 12.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val ticks = steps in 1..20
            val cy = if (ticks) size.height * 0.36f else size.height / 2f
            // The channel: a thin sunken groove.
            drawLine(Color(0xFFACA899), Offset(tw / 2f, cy - px(1f)), Offset(size.width - tw / 2f, cy - px(1f)), px(1.2f))
            drawLine(Color(0xFFE6E4DB), Offset(tw / 2f, cy), Offset(size.width - tw / 2f, cy), px(1.2f))
            drawLine(Color.White, Offset(tw / 2f, cy + px(1f)), Offset(size.width - tw / 2f, cy + px(1f)), px(1.2f))
            if (ticks) {
                for (i in 0..steps + 1) {
                    val x = tw / 2f + (size.width - tw) * i / (steps + 1)
                    drawLine(Color(0xFF8D8A7E), Offset(x, size.height * 0.78f), Offset(x, size.height * 0.92f), px(1f))
                }
            }
            val x = tw / 2f + (size.width - tw) * fraction
            val th = px(20f)
            val top = cy - th / 2f
            val left = x - tw / 2f
            // The thumb: pointed at the foot when there are ticks to point at.
            val path = Path().apply {
                moveTo(left + px(2f), top)
                lineTo(left + tw - px(2f), top)
                quadraticTo(left + tw, top, left + tw, top + px(2f))
                if (ticks) {
                    lineTo(left + tw, top + th - tw / 2f)
                    lineTo(x, top + th)
                    lineTo(left, top + th - tw / 2f)
                } else {
                    lineTo(left + tw, top + th - px(2f))
                    quadraticTo(left + tw, top + th, left + tw - px(2f), top + th)
                    lineTo(left + px(2f), top + th)
                    quadraticTo(left, top + th, left, top + th - px(2f))
                }
                lineTo(left, top + px(2f))
                quadraticTo(left, top, left + px(2f), top)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color.White, Color(0xFFF0EFEA), Color(0xFFDCDAD1)), startY = top, endY = top + th))
            drawPath(path, if (enabled) Color(0xFF1C5180) else Color(0xFFA6A6A6), style = Stroke(px(1f)))
            // XP lit the thumb's edge green under the finger, and kept a hint of it at rest.
            drawLine(
                if (pressed) Color(0xFF21A121) else Color(0xFF7AC07A),
                Offset(left + px(2f), top + th - (if (ticks) tw / 2f else px(2f))),
                Offset(left + tw - px(2f), top + th - (if (ticks) tw / 2f else px(2f))),
                px(1.6f),
            )
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
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(16.dp)) { drawXpCheckbox(Rect(Offset.Zero, size), checked, pressed) }
            Spacer(Modifier.width(8.dp))
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
            // Big switchers are the effects' own pages: a row of property sheet tabs.
            Box(
                modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .drawBehind { drawLine(Luna.tabBorder, Offset(0f, size.height - px(0.5f)), Offset(size.width, size.height - px(0.5f)), px(1f)) },
                contentAlignment = Alignment.BottomStart,
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 2.dp)) {
                    items.forEachIndexed { i, label -> XpTab(label, i == selectedIndex, { onSelect(i) }, small = true) }
                }
            }
        } else {
            // Smaller ones are choices: option buttons in a row.
            val haptics = LocalHaptics.current
            Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton; selected = i == selectedIndex }
                            .skinClick(remember { MutableInteractionSource() }, role = Role.RadioButton) {
                                if (i != selectedIndex) haptics?.tick()
                                onSelect(i)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(15.dp)) { drawXpRadio(center, size.minDimension / 2f - px(0.5f), i == selectedIndex) }
                        Spacer(Modifier.width(5.dp))
                        Text(label, style = t(12.5f), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // --- menus and message boxes ------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .padding(bottom = 8.dp, end = 8.dp)
                .shadow(5.dp, RoundedCornerShape(0.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFACA899))
                .width(IntrinsicSize.Max)
                .widthIn(min = 170.dp)
                .padding(3.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (pressed) Modifier.background(Luna.selection) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(start = 4.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (item.selected) "✓" else "", style = t(13f), color = if (pressed) Color.White else Color.Black, modifier = Modifier.width(20.dp))
                    Text(item.label, style = t(13f, bold = item.destructive), color = if (pressed) Color.White else Color.Black, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        // A message box: a small window of its own.
        Column(modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))) {
            Row(
                Modifier.fillMaxWidth().height(28.dp).background(Brush.verticalGradient(*Luna.titleBar.toTypedArray())).padding(start = 8.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("openplayer", style = t(13f, bold = true, color = Color.White).copy(shadow = Shadow(Color(0xFF0F1089), Offset(1f, 1f), 0f)), modifier = Modifier.weight(1f))
                Box(Modifier.skinTap(onClick = onDismiss)) { CaptionButton(CaptionKind.Close) }
            }
            Column(Modifier.fillMaxWidth().background(Luna.windowBody).border(1.dp, Color(0xFF0831D9)).padding(14.dp)) {
                Row {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { drawXpOrb(center, size.minDimension / 2f, green = false, pressed = false) }
                        Icon(Icons.Filled.Info, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = t(13.5f, bold = true), color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(message, style = t(13f), color = Color.Black)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ok = remember { MutableInteractionSource() }
                    val cancel = remember { MutableInteractionSource() }
                    val okPressed by pressedState(ok)
                    val cancelPressed by pressedState(cancel)
                    PushButton("OK", default = true, pressed = okPressed, modifier = Modifier.skinClick(ok) { onConfirm() })
                    PushButton("Cancel", default = false, pressed = cancelPressed, modifier = Modifier.testTag("dialog:dismiss").skinClick(cancel) { onDismiss() })
                }
            }
        }
    }

    // --- Explorer views ---------------------------------------------------------------------------

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        // Explorer's Tiles view of "My Music".
        Column(modifier) {
            if (emptyMessage != null) {
                Text(emptyMessage, style = t(13f), color = Color(0xFF555555), modifier = Modifier.padding(8.dp))
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) { drawFolder(Rect(Offset.Zero, size)) }
                                val badge = when (entry.kind) {
                                    CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite to Color(0xFFD3332B)
                                    CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label to Color(0xFF6B4BB4)
                                    CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote to Color(0xFF2B5EB8)
                                }
                                Icon(badge.first, null, tint = badge.second, modifier = Modifier.size(20.dp).offset(y = 6.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = t(13f),
                                    color = if (entry.emphasized) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (entry.emphasized) Modifier.background(Luna.selection).padding(horizontal = 2.dp) else Modifier,
                                )
                                Text(entry.subtitle.substringBefore(" ·"), style = t(12f), color = Color(0xFF7A7A7A), maxLines = 1)
                                Text(
                                    when (entry.kind) {
                                        CollectionEntry.Kind.Favourites -> "Favourites"
                                        CollectionEntry.Kind.Tag -> "Tag"
                                        CollectionEntry.Kind.Playlist -> if (entry.emphasized) "Now playing" else "Playlist"
                                    },
                                    style = t(12f),
                                    color = Color(0xFF7A7A7A),
                                    maxLines = 1,
                                )
                            }
                        }
                        entry.menu()
                    }
                }
            }
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Column(modifier.background(Color.White).border(1.dp, Luna.fieldBorder).padding(1.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .drawBehind {
                        drawRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFF1EFE2), Color(0xFFEBEADB))))
                        drawRect(Color(0xFFE2DECD), Offset(0f, size.height - px(3f)), Size(size.width, px(1f)))
                        drawRect(Color(0xFFCBC7B8), Offset(0f, size.height - px(2f)), Size(size.width, px(1f)))
                        drawRect(Color(0xFFD6D2C2), Offset(0f, size.height - px(1f)), Size(size.width, px(1f)))
                        val split = size.width - px(132f)
                        drawLine(Color(0xFFC7C5B2), Offset(split, px(4f)), Offset(split, size.height - px(5f)), px(1f))
                        drawLine(Color.White, Offset(split + px(1f), px(4f)), Offset(split + px(1f), size.height - px(5f)), px(1f))
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Name", style = t(12.5f), color = Color.Black, modifier = Modifier.weight(1f))
                Text("Artist", style = t(12.5f), color = Color.Black, modifier = Modifier.width(118.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        val selected = model.isCurrent
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 4.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF2B5EB8), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f)) {
                    MarqueeText(
                        text = model.title,
                        style = t(13.5f),
                        color = if (selected) Color.White else Color.Black,
                        scrollRequest = model.titleScrollRequest,
                        idleOverflow = TextOverflow.Ellipsis,
                        modifier = if (selected) Modifier.background(Luna.selection).padding(horizontal = 2.dp) else Modifier,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = t(13.5f), color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(108.dp))
            }
            model.menu()
        }
    }

    // --- task panes --------------------------------------------------------------------------------

    /**
     * An Explorer task pane panel: a header with its title in blue (or, for the main one, white
     * on dark blue) and the round chevron, over a pale blue body.
     */
    @Composable
    private fun PanePanel(title: String, special: Boolean = false, modifier: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) {
        Column(modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(
                        if (special) {
                            Brush.horizontalGradient(listOf(Color(0xFF0C32AA), Color(0xFF2D69DD)))
                        } else {
                            Brush.horizontalGradient(listOf(Color.White, Luna.panelHeaderEnd))
                        },
                    )
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = t(13f, bold = true), color = if (special) Color.White else Luna.link, modifier = Modifier.weight(1f))
                PaneChevron(up = true, onDark = special)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (special) Color(0xFFEEF3FA) else Luna.panelBody)
                    .border(1.dp, Color.White)
                    .padding(12.dp),
                content = body,
            )
        }
    }

    @Composable
    private fun TaskLink(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit, checked: Boolean? = null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val haptics = LocalHaptics.current
        Row(
            Modifier
                .fillMaxWidth()
                .skinClick(interaction) {
                    haptics?.click()
                    onClick()
                }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = t(13f).copy(textDecoration = if (pressed) androidx.compose.ui.text.style.TextDecoration.Underline else null),
                color = Luna.link,
                modifier = Modifier.weight(1f),
            )
            if (checked == true) Text("✓", style = t(13f, bold = true), color = Luna.checkGreen)
        }
    }

    /** XP's green progress bar, seekable by tapping or dragging along it. */
    @Composable
    private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit, modifier: Modifier) {
        var drag by remember { mutableStateOf<Float?>(null) }
        val currentOnSeek by rememberUpdatedState(onSeek)
        var widthPx by remember { mutableFloatStateOf(1f) }
        val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Canvas(
            modifier
                .height(18.dp)
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
            drawXpProgress(Rect(Offset.Zero, size), fraction)
        }
    }

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Luna.paneTop, Luna.paneBottom)))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                model.header(Modifier.fillMaxWidth())
                model.sleepTimerPanel?.let { panel ->
                    PanePanel("Sleep Timer") { panel(Modifier.fillMaxWidth()) }
                }
                PanePanel("Now Playing", special = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(104.dp)
                                .shadow(3.dp)
                                .background(Color.White)
                                .padding(3.dp)
                                .drawBehind { drawHillside(Rect(Offset.Zero, size)) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            MarqueeText(
                                text = song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                                style = t(16f, bold = true),
                                color = Color.Black,
                                modifier = Modifier.fillMaxWidth().then(
                                    if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier,
                                ),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(song?.artist.orEmpty(), style = t(13.5f), color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            if (song == null && model.playbackRestored) {
                                Text("Open My Music", style = t(13f), color = Luna.link, modifier = Modifier.skinTap(onClick = model.onOpenLibrary))
                            } else {
                                Text(model.playback.playlistLabel, style = t(12.5f), color = Luna.link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (song != null) {
                        Spacer(Modifier.height(14.dp))
                        SeekBar(model.playback.positionMs, model.playback.durationMs, model.onSeek, Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                            Text(formatDurationMs(model.playback.positionMs), style = t(12f), color = Color(0xFF333333))
                            Spacer(Modifier.weight(1f))
                            Text(formatDurationMs(model.playback.durationMs), style = t(12f), color = Color(0xFF333333))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 46.dp, 26.dp, 1f, true, ButtonKind.Previous)
                        Spacer(Modifier.width(18.dp))
                        IconButton(
                            if (model.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (model.playback.isPlaying) "Pause" else "Play",
                            model.onPlayPause,
                            Modifier,
                            78.dp,
                            46.dp,
                            1f,
                            true,
                            ButtonKind.Play,
                        )
                        Spacer(Modifier.width(18.dp))
                        IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 46.dp, 26.dp, 1f, true, ButtonKind.Next)
                    }
                }
                PanePanel("Playback Tasks") {
                    TaskLink(Icons.Filled.Shuffle, Color(0xFF2B5EB8), if (model.shuffleOn) "Turn shuffle off" else "Shuffle the songs", model.onToggleShuffle, checked = model.shuffleOn)
                    model.isFavourite?.let { fav ->
                        TaskLink(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, Color(0xFFD3332B), if (fav) "Remove from Favourites" else "Add to Favourites", model.onToggleFavourite)
                    }
                    TaskLink(Icons.Filled.Timer, Color(0xFF2B5EB8), if (model.sleepTimerActive) "Change the sleep timer" else "Set a sleep timer", model.onOpenSleepTimer)
                    if (model.outputSwitchAvailable) {
                        TaskLink(
                            if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Speaker else Icons.Filled.Bluetooth,
                            Color(0xFF2B5EB8),
                            if (model.outputDevice == OutputDevice.BLUETOOTH) "Play through the phone speaker" else "Play through Bluetooth",
                            model.onToggleOutput,
                        )
                    }
                }
                if (model.playback.upcoming.isNotEmpty()) {
                    PanePanel("Up Next") {
                        model.playback.upcoming.take(5).forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF2B5EB8), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.displayName, style = t(13f), color = Luna.link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.artist, style = t(11.5f), color = Color(0xFF555555), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 28.dp, 16.dp, 1f, true, ButtonKind.Plain)
                            }
                        }
                    }
                }
                val details = listOfNotNull(
                    "Shuffle is locked to its seed".takeIf { model.shuffleLocked },
                    "Effects are on".takeIf { model.fxOn },
                    "The sleep timer is on".takeIf { model.sleepTimerActive },
                    model.message,
                )
                if (details.isNotEmpty()) {
                    PanePanel("Details") {
                        details.forEach { Text(it, style = t(12.5f), color = Color.Black, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Column(
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Luna.paneTop, Luna.paneBottom)))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            PanePanel("Now Playing", special = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.onOpenSettings != null) {
                        Box(Modifier.testTag("nav:settings")) {
                            IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 32.dp, 18.dp, 1f, true, ButtonKind.Plain)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        MarqueeText(
                            text = song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                            style = t(13.5f, bold = true),
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth().then(
                                if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier,
                            ),
                        )
                        Text(song?.artist.orEmpty(), style = t(12f), color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 32.dp, 18.dp, 1f, true, ButtonKind.Previous)
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (model.isPlaying) "Pause" else "Play",
                        model.onPlayPause,
                        Modifier,
                        46.dp,
                        26.dp,
                        1f,
                        true,
                        ButtonKind.Play,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 32.dp, 18.dp, 1f, true, ButtonKind.Next)
                }
                if (song != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDurationMs(model.positionMs), style = t(11.5f), color = Color(0xFF333333))
                        Spacer(Modifier.width(6.dp))
                        SeekBar(model.positionMs, model.durationMs, { f -> model.onSeek((f * model.durationMs).toLong()) }, Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Text(formatDurationMs(model.durationMs), style = t(11.5f), color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}
