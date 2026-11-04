package io.github.parthibdebnath.openplayer.ui.skin.classic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoice
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoices
import io.github.parthibdebnath.openplayer.ui.skin.SkinOption
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

/**
 * Windows 95/98: grey two-pixel bevels, a navy-to-blue title bar, the 8-pixel system font,
 * sunken white lists with navy selection, notched trackbars and the Start button. The Play
 * screen is the CD Player, LED display and all. Everything sits on a whole-pixel grid, so edges
 * and text stay crisp.
 */
class ClassicSkin private constructor(private val scheme: Win95Scheme) : Skin() {
    override val theme = UiTheme.CLASSIC
    override val colors = AppColors(background = scheme.face, onBackground = scheme.text)
    override val showsHeader = false
    override val gutter: Dp = 8.dp

    companion object {
        private val Scheme = SkinOption(
            "scheme",
            "Scheme",
            Win95Scheme.All.map { SkinChoice(it.id, it.label, it.caption) },
            SkinOption.Style.List,
        )
        val Options = listOf(Scheme)

        fun create(choices: SkinChoices): ClassicSkin {
            val id = choices.of(Scheme).id
            return ClassicSkin(Win95Scheme.All.first { it.id == id })
        }
    }

    /** One classic pixel, in device pixels. */
    private fun unitPx(density: Density) = max(1, density.density.roundToInt())

    /** [n] classic pixels, as dp that land exactly on whole device pixels. */
    @Composable
    private fun cp(n: Int): Dp = with(LocalDensity.current) { (n * unitPx(this)).toDp() }

    /**
     * Pixel 95 at whole multiples of its pixel: 11 px to the em, so a scale of 3 puts each of its
     * pixels on three of the screen's. Large headings take the next size up.
     */
    private fun pixelStyle(scale: Int, bold: Boolean, density: Density): TextStyle {
        val px = 11f * scale
        val sp = px / (density.density * density.fontScale)
        return TextStyle(
            fontFamily = SkinFonts.pixel,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = sp.sp,
            lineHeight = (sp * 13f / 11f).sp,
        )
    }

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val u = unitPx(density)
        return pixelStyle(if (sizeSp >= 20) u + 1 else u, bold, density)
    }

    @Composable
    private fun body(bold: Boolean = false) = pixelStyle(unitPx(LocalDensity.current), bold, LocalDensity.current)

    // --- frame ---------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        // Everything below draws in this skin's scheme; set before any of it composes.
        if (W95.scheme !== scheme) W95.scheme = scheme
        Column(Modifier.fillMaxSize().background(W95.desktop)) {
            // The window, maximised: frame, title bar, menu bar, tabs, page and status bar.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Window) }
                    .padding(cp(4)),
            ) {
                TitleBar("openplayer - ${windowName(model.tabs[model.selectedTab])}", active = true)
                MenuBar(model)
                Tabs(model)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = cp(2))
                        .drawBehind {
                            // The tab page: raised, open at the top where the chosen tab joins it.
                            drawBevel(Rect(Offset.Zero, size), Bevel.Raised)
                        }
                        .padding(cp(4)),
                ) {
                    content(Modifier.fillMaxSize())
                }
                StatusBar(model)
            }
            Taskbar(model)
        }
    }

    private fun windowName(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "CD Player"
        else -> "Effects"
    }

    private fun tabName(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    @Composable
    private fun TitleBar(title: String, active: Boolean, onClose: (() -> Unit)? = null) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(cp(20))
                .background(
                    if (active) Brush.horizontalGradient(listOf(W95.caption, W95.captionEnd)) else Brush.horizontalGradient(listOf(W95.scheme.inactiveCaption, W95.scheme.inactiveCaptionEnd)),
                )
                .padding(horizontal = cp(2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(cp(11) + cp(1), cp(9))) { drawPixels(Glyphs.disc, Offset(0f, 0f)) }
            Spacer(Modifier.width(cp(4)))
            Text(title, style = body(bold = true), color = if (active) W95.scheme.captionText else W95.scheme.inactiveCaptionText, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
            if (onClose == null) {
                CaptionButton(Glyphs.minimise)
                CaptionButton(Glyphs.maximise)
                Spacer(Modifier.width(cp(2)))
            }
            CaptionButton(Glyphs.close, onClose)
        }
    }

    @Composable
    private fun CaptionButton(glyph: List<String>, onClick: (() -> Unit)? = null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Canvas(
            Modifier
                .size(cp(16), cp(14))
                .then(if (onClick != null) Modifier.testTag("dialog:dismiss").skinClick(interaction) { onClick() } else Modifier),
        ) {
            drawBevel(Rect(Offset.Zero, size), if (pressed) Bevel.Pressed else Bevel.Raised)
            val u = unit()
            val gw = glyph.maxOf { it.length } * u
            val gh = glyph.size * u
            val shift = if (pressed) u else 0f
            drawPixels(glyph, Offset(((size.width - gw) / 2f / u).roundToInt() * u + shift, ((size.height - gh) / 2f / u).roundToInt() * u + shift))
        }
    }

    /** Menu titles with their access keys underlined, as Windows drew them. */
    private fun accessKeyed(label: String) = buildAnnotatedString {
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(label.first()) }
        append(label.drop(1))
    }

    @Composable
    private fun MenuBar(model: FrameModel) {
        Row(Modifier.fillMaxWidth().height(cp(20)), verticalAlignment = Alignment.CenterVertically) {
            MenuTitle("File", listOf(ContextMenuItem("Options...", onClick = model.onOpenSettings)))
            MenuTitle("View", model.tabs.mapIndexed { i, tab ->
                ContextMenuItem(tabName(tab), selected = i == model.selectedTab, onClick = { model.onSelectTab(i) })
            })
            MenuTitle("Disc", listOf(
                ContextMenuItem(if (model.playback.isPlaying) "Pause" else "Play", onClick = model.onPlayPause),
                ContextMenuItem("Next Track", onClick = model.onNext),
                ContextMenuItem("Previous Track", onClick = model.onPrevious),
            ))
            MenuTitle("Help", listOf(ContextMenuItem("About openplayer", onClick = {})))
        }
    }

    @Composable
    private fun MenuTitle(title: String, items: List<ContextMenuItem>) {
        var open by remember { mutableStateOf(false) }
        Box {
            Text(
                accessKeyed(title),
                style = body(),
                color = if (open) W95.selectionText else W95.text,
                modifier = Modifier
                    .then(if (open) Modifier.background(W95.selection) else Modifier)
                    .skinTap { open = true }
                    .padding(horizontal = cp(6), vertical = cp(2)),
            )
            if (open) {
                Popup(
                    offset = IntOffset(0, with(LocalDensity.current) { cp(18).roundToPx() }),
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
        Row(Modifier.fillMaxWidth().height(cp(22)).padding(start = cp(4)), verticalAlignment = Alignment.Bottom) {
            model.tabs.forEachIndexed { i, tab ->
                Win95Tab(tabName(tab), i == model.selectedTab, { model.onSelectTab(i) }, Modifier.testTag("tab:$tab"))
            }
        }
    }

    /** A tab: raised on three sides, the chosen one taller and running into the page. */
    @Composable
    private fun Win95Tab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
        val haptics = LocalHaptics.current
        Box(
            modifier
                .offset(y = if (selected) cp(2) else cp(0))
                .height(if (selected) cp(22) else cp(18))
                .semantics { role = Role.Tab; this.selected = selected }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                    if (!selected) haptics?.tick()
                    onClick()
                }
                .drawBehind {
                    val u = unit()
                    drawRect(W95.face)
                    drawRect(W95.highlight, Offset(u, 0f), Size(size.width - 3 * u, u))
                    drawRect(W95.highlight, Offset(0f, u), Size(u, size.height - u))
                    drawRect(W95.light, Offset(u, u), Size(u, size.height - u))
                    drawRect(W95.dark, Offset(size.width - u, u), Size(u, size.height - u))
                    drawRect(W95.shadow, Offset(size.width - 2 * u, u), Size(u, size.height - u))
                    if (!selected) drawRect(W95.highlight, Offset(0f, size.height - u), Size(size.width, u))
                }
                .padding(horizontal = cp(8)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = body(), color = W95.text, maxLines = 1)
        }
    }

    @Composable
    private fun StatusBar(model: FrameModel) {
        Row(Modifier.fillMaxWidth().padding(top = cp(2)).height(cp(20)), horizontalArrangement = Arrangement.spacedBy(cp(2))) {
            val p = model.playback
            Box(Modifier.weight(1f).fillMaxHeight().drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Sunken, null) }.padding(horizontal = cp(4)), contentAlignment = Alignment.CenterStart) {
                Text(
                    if (p.currentSong != null) "Total Play: ${formatDurationMs(p.durationMs)} m:s" else "Ready",
                    style = body(),
                    color = W95.text,
                    maxLines = 1,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight().drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Sunken, null) }.padding(horizontal = cp(4)), contentAlignment = Alignment.CenterStart) {
                Text(
                    if (p.currentSong != null) "Track: ${formatDurationMs(p.positionMs)} m:s" else "",
                    style = body(),
                    color = W95.text,
                    maxLines = 1,
                )
            }
        }
    }

    // --- taskbar and Start menu ------------------------------------------------------------------

    @Composable
    private fun Taskbar(model: FrameModel) {
        var startOpen by remember { mutableStateOf(false) }
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(cp(28))
                    .drawBehind {
                        drawRect(W95.face)
                        drawRect(W95.light, Offset.Zero, Size(size.width, unit()))
                        drawRect(W95.highlight, Offset(0f, unit()), Size(size.width, unit()))
                    }
                    .padding(start = cp(2), end = cp(2), top = cp(4), bottom = cp(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val startInteraction = remember { MutableInteractionSource() }
                val startPressed by pressedState(startInteraction)
                Row(
                    Modifier
                        .fillMaxHeight()
                        .testTag("frame:menu")
                        .semantics { contentDescription = "Start" }
                        .skinClick(startInteraction) { startOpen = !startOpen }
                        .drawBehind { drawBevel(Rect(Offset.Zero, size), if (startOpen || startPressed) Bevel.Pressed else Bevel.Raised) }
                        .padding(horizontal = cp(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(cp(16), cp(14))) { drawFlag() }
                    Spacer(Modifier.width(cp(3)))
                    Text("Start", style = body(bold = true), color = W95.text)
                }
                Spacer(Modifier.width(cp(4)))
                // The running program's button, held in: Windows filled it with the checkerboard.
                val density = LocalDensity.current
                val dither = remember(density.density, W95.scheme) { ditherBrush(density.density) }
                Row(
                    Modifier
                        .width(cp(140))
                        .fillMaxHeight()
                        .drawBehind {
                            drawRect(dither)
                            drawBevel(Rect(Offset.Zero, size), Bevel.Pressed, null)
                        }
                        .padding(horizontal = cp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(cp(12), cp(9))) { drawPixels(Glyphs.disc, Offset.Zero) }
                    Spacer(Modifier.width(cp(3)))
                    Text("openplayer", style = body(bold = true), color = W95.text, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxHeight()
                        .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Sunken, null) }
                        .padding(horizontal = cp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(cp(9), cp(9))) {
                        drawPixels(listOf("    #    ", "   ##  # ", "#### #  #", "#### # # ", "#### # # ", "#### #  #", "   ##  # ", "    #    ").map { it.take(9) }, Offset.Zero)
                    }
                    Spacer(Modifier.width(cp(6)))
                    Text("${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = body(), color = W95.text)
                }
            }
            if (startOpen) {
                Popup(
                    alignment = Alignment.BottomStart,
                    offset = IntOffset(0, -with(LocalDensity.current) { cp(25).roundToPx() }),
                    onDismissRequest = { startOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    StartMenu(model) { startOpen = false }
                }
            }
        }
    }

    /** A four-pane waving flag, in pixels. */
    private fun DrawScope.drawFlag() {
        drawPixels(
            listOf(
                "    rrr  GGG    ",
                "   rrrr GGGG    ",
                "   rrrr GGGG    ",
                "  rrrr GGGG     ",
                "  rrrr GGGG     ",
                "                ",
                "  bbb  yyy      ",
                " bbbb yyyy      ",
                " bbbb yyyy      ",
                "bbbb yyyy       ",
                "bbbb yyyy       ",
            ),
            Offset(0f, unit()),
        )
    }

    @Composable
    private fun StartMenu(model: FrameModel, close: () -> Unit) {
        val haptics = LocalHaptics.current
        fun pick(action: () -> Unit) {
            haptics?.click()
            close()
            action()
        }
        Box(
            Modifier
                .width(IntrinsicSize.Max)
                .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Window) }
                .padding(cp(3)),
        ) {
            // The band down the side, the product name reading upwards; as tall as the items.
            Box(Modifier.matchParentSize()) {
                Box(Modifier.width(cp(21)).fillMaxHeight().background(Brush.verticalGradient(listOf(W95.caption, W95.captionEnd)))) {
                    val style = pixelStyle(unitPx(LocalDensity.current) + 1, true, LocalDensity.current)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) { append("open") }
                            withStyle(SpanStyle(color = Color(0xFFC0C0C0), fontWeight = FontWeight.Normal)) { append("player") }
                        },
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = cp(4))
                            .wrapUnboundedRotated(),
                    )
                }
            }
            Column(Modifier.padding(start = cp(21)).widthIn(min = cp(150))) {
                val items = listOf(
                    Triple("Library", Glyphs.folder, { pick { model.onSelectTab(0) } }),
                    Triple("Now Playing", Glyphs.disc, { pick { model.onSelectTab(1) } }),
                    Triple("Effects", Glyphs.note, { pick { model.onSelectTab(2) } }),
                )
                items.forEach { (label, glyph, action) -> StartItem(label, glyph, action) }
                Box(Modifier.fillMaxWidth().padding(vertical = cp(2), horizontal = cp(2)).height(cp(2)).drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Etched, null) })
                StartItem("Settings...", Glyphs.folder) { pick(model.onOpenSettings) }
                StartItem(if (model.playback.isPlaying) "Pause" else "Play", Glyphs.play) { pick(model.onPlayPause) }
                Box(Modifier.fillMaxWidth().padding(vertical = cp(2), horizontal = cp(2)).height(cp(2)).drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Etched, null) })
                StartItem("Shut Down...", Glyphs.close) { pick {} }
            }
        }
    }

    /** Text laid along the band, reading bottom to top. */
    private fun Modifier.wrapUnboundedRotated(): Modifier = this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity, minWidth = 0))
            layout(placeable.height, placeable.width) {
                placeable.placeWithLayer(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = placeable.width / 2 - placeable.height / 2,
                ) {
                    rotationZ = -90f
                }
            }
        },
    )

    @Composable
    private fun StartItem(label: String, glyph: List<String>, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Row(
            Modifier
                .fillMaxWidth()
                .height(cp(32))
                .then(if (pressed) Modifier.background(W95.selection) else Modifier)
                .skinClick(interaction, onClick = onClick)
                .padding(horizontal = cp(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(cp(32), cp(26))) {
                val gw = glyph.maxOf { it.length } * unit() * 2
                drawPixels(glyph, Offset(((size.width - gw) / 2f / unit()).roundToInt() * unit(), 0f), scale = 2)
            }
            Spacer(Modifier.width(cp(6)))
            Text(accessKeyed(label), style = body(), color = if (pressed) W95.selectionText else W95.text)
        }
    }

    // --- controls ------------------------------------------------------------------------------

    private fun glyphFor(icon: ImageVector, kind: ButtonKind): List<String>? = when {
        kind == ButtonKind.Play && icon == Icons.Filled.Pause -> Glyphs.pause
        kind == ButtonKind.Play -> Glyphs.play
        kind == ButtonKind.Next || icon == Icons.Filled.SkipNext -> Glyphs.next
        kind == ButtonKind.Previous || icon == Icons.Filled.SkipPrevious -> Glyphs.prev
        icon == Icons.Filled.Add -> listOf("   #   ", "   #   ", "   #   ", "#######", "   #   ", "   #   ", "   #   ")
        icon == Icons.Filled.Remove -> listOf("#######")
        icon == Icons.Filled.Search -> listOf("  ###    ", " #   #   ", "#     #  ", "#     #  ", "#     #  ", " #   #   ", "  #####  ", "      ## ", "       ##")
        icon == Icons.AutoMirrored.Filled.ArrowForward -> Glyphs.skip
        icon == Icons.Filled.PlayArrow -> Glyphs.play
        icon == Icons.Filled.Favorite -> Glyphs.heart
        icon == Icons.Filled.FavoriteBorder -> listOf(" ## ## ", "#  #  #", "#     #", " #   # ", "  # #  ", "   #   ")
        icon == Icons.Filled.Timer -> listOf("  #####  ", " #     # ", "#   #   #", "#   #   #", "#   ### #", "#       #", "#       #", " #     # ", "  #####  ")
        else -> null
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
        val click = Modifier.semantics { this.contentDescription = contentDescription }.skinClick(interaction, enabled) { onClick() }
        when (kind) {
            ButtonKind.Toggle -> Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
                Checkbox(icon == Icons.Filled.Check, pressed)
            }
            ButtonKind.Confirm -> PushButton(if (icon == Icons.Filled.Add) "Add" else "OK", default = true, pressed = pressed, modifier = modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f).then(click))
            ButtonKind.Chevron -> Box(modifier.size(cp(22)).then(click).drawBehind { drawBevel(Rect(Offset.Zero, this.size), if (pressed) Bevel.Pressed else Bevel.Raised) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(cp(7), cp(4))) { drawPixels(Glyphs.up, Offset.Zero) }
            }
            else -> {
                val side = when (kind) {
                    ButtonKind.Play -> cp(46)
                    ButtonKind.Next, ButtonKind.Previous -> cp(34)
                    else -> if (size >= 40.dp) cp(28) else cp(23)
                }
                val glyph = glyphFor(icon, kind)
                Box(
                    modifier
                        .size(width = side, height = if (kind == ButtonKind.Play) cp(28) else if (kind == ButtonKind.Next || kind == ButtonKind.Previous) cp(28) else side)
                        .then(click)
                        .drawBehind { drawBevel(Rect(Offset.Zero, this.size), if (pressed) Bevel.Pressed else Bevel.Raised) },
                    contentAlignment = Alignment.Center,
                ) {
                    val ink = if (enabled) W95.text.copy(alpha = opacity.coerceAtLeast(0.45f)) else W95.shadow
                    if (glyph != null) {
                        Canvas(Modifier.size(cp(glyph.maxOf { it.length }), cp(glyph.size)).offset(x = if (pressed) cp(1) else cp(0), y = if (pressed) cp(1) else cp(0))) {
                            drawPixels(glyph, Offset.Zero, ink = ink)
                        }
                    } else {
                        Icon(icon, null, tint = ink, modifier = Modifier.size(cp(12)).offset(x = if (pressed) cp(1) else cp(0), y = if (pressed) cp(1) else cp(0)))
                    }
                }
            }
        }
    }

    @Composable
    private fun Checkbox(checked: Boolean, pressed: Boolean) {
        Canvas(Modifier.size(cp(13))) {
            drawBevel(Rect(Offset.Zero, size), Bevel.Field, if (pressed) W95.face else W95.window)
            if (checked) drawPixels(Glyphs.check, Offset(unit() * 3, unit() * 3))
        }
    }

    @Composable
    private fun PushButton(label: String, default: Boolean, pressed: Boolean, modifier: Modifier) {
        Box(
            modifier
                .heightIn(min = cp(23))
                .widthIn(min = cp(75))
                .drawBehind {
                    val u = unit()
                    if (default) {
                        drawRect(W95.dark, Offset.Zero, size)
                        drawBevel(Rect(u, u, size.width - u, size.height - u), if (pressed) Bevel.Pressed else Bevel.Raised)
                    } else {
                        drawBevel(Rect(Offset.Zero, size), if (pressed) Bevel.Pressed else Bevel.Raised)
                    }
                }
                .padding(horizontal = cp(10), vertical = cp(5)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = body(), color = W95.text)
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        // Windows 95 had no round buttons: they're square and raised here.
        GlassRole.Control -> modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Raised) }
        GlassRole.Floating -> modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Window) }
        else -> modifier.drawBehind {
            // A group box's etched frame.
            drawBevel(Rect(Offset.Zero, size), Bevel.Etched, W95.face)
        }
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier =
        modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Field, W95.window) }

    @Composable
    override fun Divider(modifier: Modifier) {
        Canvas(modifier.fillMaxWidth().height(cp(2))) {
            drawRect(W95.shadow, Offset.Zero, Size(size.width, unit()))
            drawRect(W95.highlight, Offset(0f, unit()), Size(size.width, unit()))
        }
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        val density = LocalDensity.current
        val dither = remember(density.density, W95.scheme) { ditherBrush(density.density) }
        Canvas(modifier.width(cp(16)).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            val u = unit()
            val button = 16 * u
            drawRect(dither)
            // The arrow buttons at each end.
            drawBevel(Rect(0f, 0f, size.width, button), Bevel.Raised)
            drawPixels(Glyphs.up, Offset(((size.width - 7 * u) / 2f / u).roundToInt() * u, 6 * u))
            drawBevel(Rect(0f, size.height - button, size.width, size.height), Bevel.Raised)
            drawPixels(Glyphs.down, Offset(((size.width - 7 * u) / 2f / u).roundToInt() * u, size.height - button + 6 * u))
            val track = size.height - button * 2
            val thumbH = (track * visible / total).coerceAtLeast(button)
            val top = button + (track - thumbH) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawBevel(Rect(0f, (top / u).roundToInt() * u, size.width, ((top + thumbH) / u).roundToInt() * u), Bevel.Raised)
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 30.dp
        override val thumbWidth: Dp = 13.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val u = unit()
            val tw = thumbWidth.toPx()
            val cy = ((size.height * 0.38f) / u).roundToInt() * u
            // The channel: a sunken slot four pixels deep.
            drawBevel(Rect(tw / 2f - 2 * u, cy - 2 * u, size.width - tw / 2f + 2 * u, cy + 2 * u), Bevel.Sunken, W95.window)
            // Notches below: one per step when there are few enough to read, else every tenth.
            val notches = if (steps in 1..24) steps + 1 else 10
            for (i in 0..notches) {
                val x = ((tw / 2f + (size.width - tw) * i / notches) / u).roundToInt() * u
                drawRect(W95.text, Offset(x, size.height - 6 * u), Size(u, 3 * u))
            }
            val x = ((tw / 2f + (size.width - tw) * fraction) / u).roundToInt() * u
            // The thumb: a raised slab with a point at its foot, toward the notches.
            val half = ((tw / 2f) / u).roundToInt() * u
            val top = cy - 10 * u
            val bodyBottom = cy + 5 * u
            drawRect(W95.face, Offset(x - half, top), Size(half * 2, bodyBottom - top))
            for (k in 0 until (half / u).toInt()) {
                drawRect(W95.face, Offset(x - half + k * u, bodyBottom + k * u), Size(half * 2 - 2 * k * u, u))
            }
            drawRect(W95.highlight, Offset(x - half, top), Size(half * 2, u))
            drawRect(W95.highlight, Offset(x - half, top), Size(u, bodyBottom - top))
            drawRect(W95.dark, Offset(x + half - u, top), Size(u, bodyBottom - top))
            drawRect(W95.shadow, Offset(x + half - 2 * u, top + u), Size(u, bodyBottom - top - u))
            for (k in 0 until (half / u).toInt()) {
                drawRect(W95.highlight, Offset(x - half + k * u, bodyBottom + k * u), Size(u, u))
                drawRect(W95.dark, Offset(x + half - (k + 1) * u, bodyBottom + k * u), Size(u, u))
            }
            if (pressed) {
                // Focus: Windows dotted a rectangle round the control in use.
                var dx = 0f
                while (dx < size.width) {
                    drawRect(W95.text, Offset(dx, 0f), Size(u, u))
                    dx += 2 * u
                }
            }
            if (!enabled) drawRect(W95.face.copy(alpha = 0.5f))
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
                .padding(vertical = cp(3)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, pressed)
            Spacer(Modifier.width(cp(6)))
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = W95.text, modifier = Modifier.weight(1f))
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
            Row(
                modifier
                    .fillMaxWidth()
                    .height(cp(24))
                    .drawBehind { drawRect(W95.highlight, Offset(0f, size.height - unit()), Size(size.width, unit())) },
                verticalAlignment = Alignment.Bottom,
            ) {
                items.forEachIndexed { i, label -> Win95Tab(label, i == selectedIndex, { onSelect(i) }) }
            }
        } else {
            val haptics = LocalHaptics.current
            Row(modifier.fillMaxWidth().heightIn(min = cp(24)), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton; selected = i == selectedIndex }
                            .skinClick(remember { MutableInteractionSource() }, role = Role.RadioButton) {
                                if (i != selectedIndex) haptics?.tick()
                                onSelect(i)
                            }
                            .padding(vertical = cp(4)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioMark(i == selectedIndex)
                        Spacer(Modifier.width(cp(4)))
                        Text(label, style = body(), color = W95.text, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }

    /** The round option button: a sunken white circle, with a black dot when chosen. */
    @Composable
    private fun RadioMark(selected: Boolean) {
        Canvas(Modifier.size(cp(12))) {
            drawPixels(
                listOf(
                    "    gggg    ", "  gg####ww  ", " g##wwwwllw ", " g#wwwwwwlw ", "g#wwwwwwwwlw", "g#wwwwwwwwlw",
                    "g#wwwwwwwwlw", "g#wwwwwwwwlw", " glwwwwwwlw ", " gwllwwllww ", "  wwllllww  ", "    wwww    ",
                ),
                Offset.Zero,
            )
            if (selected) drawPixels(listOf(" ## ", "####", "####", " ## "), Offset(unit() * 4, unit() * 4))
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
                .padding(vertical = cp(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioMark(selected)
            Spacer(Modifier.width(cp(6)))
            if (swatch != null) {
                Canvas(Modifier.size(cp(18), cp(12))) { drawBevel(Rect(Offset.Zero, size), Bevel.Field, swatch) }
                Spacer(Modifier.width(cp(5)))
            }
            Text(label, style = body(), color = W95.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }

    // --- menus and message boxes ------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = cp(130))
                .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Window) }
                .padding(cp(3)),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(cp(24))
                        .then(if (pressed) Modifier.background(W95.selection) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(end = cp(20)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(cp(20)), contentAlignment = Alignment.Center) {
                        if (item.selected) Canvas(Modifier.size(cp(7))) { drawPixels(Glyphs.check, Offset.Zero, ink = if (pressed) W95.selectionText else W95.text) }
                    }
                    Text(item.label, style = body(), color = if (pressed) W95.selectionText else W95.text, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Column(modifier.fillMaxWidth().drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Window) }.padding(cp(3))) {
            TitleBar("openplayer", active = true, onClose = onDismiss)
            Column(Modifier.fillMaxWidth().padding(cp(10))) {
                Row {
                    Canvas(Modifier.size(cp(30), cp(26))) { drawPixels(Glyphs.info, Offset.Zero, scale = 2) }
                    Spacer(Modifier.width(cp(10)))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = body(bold = true), color = W95.text)
                        Spacer(Modifier.height(cp(4)))
                        Text(message, style = body(), color = W95.text)
                    }
                }
                Spacer(Modifier.height(cp(10)))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(cp(6))) {
                    val ok = remember { MutableInteractionSource() }
                    val cancel = remember { MutableInteractionSource() }
                    val okPressed by pressedState(ok)
                    val cancelPressed by pressedState(cancel)
                    PushButton("OK", default = true, pressed = okPressed, modifier = Modifier.skinClick(ok) { onConfirm() })
                    PushButton("Cancel", default = false, pressed = cancelPressed, modifier = Modifier.skinClick(cancel) { onDismiss() })
                }
            }
        }
    }

    // --- lists --------------------------------------------------------------------------------------

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        // Explorer's Large Icons view, in a sunken white window.
        Box(modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Field, W95.window) }.padding(cp(2))) {
            if (emptyMessage != null) Text(emptyMessage, style = body(), color = W95.windowText, modifier = Modifier.padding(cp(6)))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(cp(4))) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() }
                            .padding(vertical = cp(6)),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(Modifier.size(cp(32), cp(26))) {
                                drawPixels(Glyphs.folder, Offset.Zero, scale = 2)
                                val badge = when (entry.kind) {
                                    CollectionEntry.Kind.Favourites -> Glyphs.heart.map { it.replace('#', 'r') }
                                    CollectionEntry.Kind.Tag -> Glyphs.tag.map { it.replace('#', 'n') }
                                    CollectionEntry.Kind.Playlist -> Glyphs.note.map { it.replace('#', 'n') }
                                }
                                drawPixels(badge, Offset(unit() * 13, unit() * 11), ink = W95.windowText)
                            }
                            Spacer(Modifier.height(cp(3)))
                            Text(
                                entry.title,
                                style = body(),
                                color = if (entry.emphasized) W95.selectionText else W95.windowText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = if (entry.emphasized) Modifier.background(W95.selection).padding(horizontal = cp(1)) else Modifier,
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
        Column(modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Field, W95.window) }.padding(cp(2))) {
            Row(Modifier.fillMaxWidth().height(cp(18))) {
                HeaderButton("Name", Modifier.weight(1f))
                HeaderButton("Artist", Modifier.width(cp(110) + cp(16)))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }

    @Composable
    private fun HeaderButton(label: String, modifier: Modifier) {
        Box(modifier.fillMaxHeight().drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Raised) }.padding(horizontal = cp(4)), contentAlignment = Alignment.CenterStart) {
            Text(label, style = body(), color = W95.text, maxLines = 1)
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.5f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = cp(3), end = cp(18)), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(cp(8))) { drawPixels(Glyphs.note.map { it.replace('#', 'n') }, Offset.Zero) }
                Spacer(Modifier.width(cp(4)))
                Box(Modifier.weight(1f)) {
                    MarqueeText(
                        text = model.title,
                        style = body(),
                        color = if (model.isCurrent) W95.selectionText else W95.windowText,
                        scrollRequest = model.titleScrollRequest,
                        idleOverflow = TextOverflow.Ellipsis,
                        modifier = if (model.isCurrent) Modifier.background(W95.selection).padding(horizontal = cp(1)) else Modifier,
                    )
                }
                Spacer(Modifier.width(cp(6)))
                Text(model.artist, style = body(), color = W95.windowText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(cp(104)))
            }
            model.menu()
        }
    }

    // --- the CD Player ------------------------------------------------------------------------------

    /** A group box: an etched frame with its caption sitting in a gap in the top edge. */
    @Composable
    private fun GroupBox(caption: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Box(modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = cp(7))
                    .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Etched, null) }
                    .padding(start = cp(8), end = cp(8), top = cp(12), bottom = cp(8)),
                content = content,
            )
            Text(caption, style = body(), color = W95.text, modifier = Modifier.padding(start = cp(8)).background(W95.face).padding(horizontal = cp(2)))
        }
    }

    @Composable
    private fun LedDisplay(trackNumber: Int, positionMs: Long, modifier: Modifier) {
        val seconds = (positionMs / 1000).coerceAtLeast(0)
        val text = "[%02d] %02d:%02d".format(trackNumber.coerceIn(0, 99), (seconds / 60) % 100, seconds % 60)
        Canvas(modifier.drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Sunken, Color.Black) }) {
            val h = size.height * 0.5f
            val w = sevenSegmentWidth(text, h)
            drawSevenSegment(text, Offset((size.width - w) / 2f, (size.height - h) / 2f), h, W95.ledOn, W95.ledOff)
        }
    }

    /** The CD Player's Artist / Title / Track rows: a label and a sunken, drop-down-looking field. */
    @Composable
    private fun FieldRow(label: String, value: String, onHold: (() -> Unit)? = null) {
        Row(Modifier.fillMaxWidth().padding(vertical = cp(2)), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = body(), color = W95.text, modifier = Modifier.width(cp(40)))
            Row(
                Modifier
                    .weight(1f)
                    .height(cp(22))
                    .drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Field, W95.window) }
                    .then(if (onHold != null) Modifier.onTapOrHold(onHold = onHold, holdLabel = "Edit tags") else Modifier)
                    .padding(start = cp(4), end = cp(2), top = cp(2), bottom = cp(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeText(text = value, style = body(), color = W95.windowText, modifier = Modifier.weight(1f))
                Box(Modifier.size(cp(16)).drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Raised) }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(cp(7), cp(4))) { drawPixels(Glyphs.down, Offset.Zero) }
                }
            }
        }
    }

    /** A notched trackbar for seeking; the seek happens when it's let go. */
    @Composable
    private fun SeekTrackbar(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit, modifier: Modifier) {
        var drag by remember { mutableStateOf<Float?>(null) }
        val currentOnSeek by rememberUpdatedState(onSeek)
        var widthPx by remember { mutableFloatStateOf(1f) }
        val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Canvas(
            modifier
                .height(30.dp)
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
            with(slider) { drawSlider(fraction, true, 9, drag != null) }
        }
    }

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(cp(4))) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel ->
                GroupBox("Sleep timer", Modifier.padding(bottom = cp(6))) { panel(Modifier.fillMaxWidth()) }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                LedDisplay(
                    trackNumber = if (song != null) (song.sortIndex + 1).toInt() else 0,
                    positionMs = p.positionMs,
                    modifier = Modifier.weight(1f).height(cp(58)),
                )
                Spacer(Modifier.width(cp(6)))
                // The CD Player's button block: Play wide on top, track skips below.
                Column(verticalArrangement = Arrangement.spacedBy(cp(2))) {
                    IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier.width(cp(70)), 70.dp, 30.dp, 1f, true, ButtonKind.Play)
                    Row(horizontalArrangement = Arrangement.spacedBy(cp(2))) {
                        IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Previous)
                        IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Next)
                    }
                }
            }
            Spacer(Modifier.height(cp(8)))
            FieldRow("Artist:", song?.artist ?: if (model.playbackRestored) "Data or no disc loaded" else "")
            FieldRow("Title:", song?.displayName ?: "", onHold = if (song != null) model.onHoldTitle else null)
            FieldRow("Track:", p.playlistLabel)
            if (song == null && model.playbackRestored) {
                Spacer(Modifier.height(cp(6)))
                PushButton("Open Library...", default = false, pressed = false, modifier = Modifier.skinTap(onClick = model.onOpenLibrary))
            }
            if (song != null) {
                Spacer(Modifier.height(cp(4)))
                SeekTrackbar(p.positionMs, p.durationMs, model.onSeek, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(cp(6)))
            GroupBox("Options") {
                ToggleRow("Random order", model.shuffleOn, { model.onToggleShuffle() }, Modifier, false, 16)
                model.isFavourite?.let { fav -> ToggleRow("Favourite", fav, { model.onToggleFavourite() }, Modifier, false, 16) }
                Spacer(Modifier.height(cp(4)))
                Row(horizontalArrangement = Arrangement.spacedBy(cp(6))) {
                    PushButton("Sleep Timer...", default = false, pressed = false, modifier = Modifier.skinTap(onClick = model.onOpenSleepTimer))
                    if (model.outputSwitchAvailable) {
                        PushButton(
                            if (model.outputDevice == io.github.parthibdebnath.openplayer.audio.OutputDevice.BLUETOOTH) "Speaker" else "Bluetooth",
                            default = false,
                            pressed = false,
                            modifier = Modifier.skinTap(onClick = model.onToggleOutput),
                        )
                    }
                }
            }
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "FX on".takeIf { model.fxOn }, "Timer on".takeIf { model.sleepTimerActive }, model.message)
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(cp(4)))
                Text(notes.joinToString("   "), style = body(), color = W95.text)
            }
            if (p.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(cp(8)))
                Text("Up next:", style = body(), color = W95.text)
                Spacer(Modifier.height(cp(2)))
                Column(Modifier.fillMaxWidth().drawBehind { drawBevel(Rect(Offset.Zero, size), Bevel.Field, W95.window) }.padding(cp(3))) {
                    p.upcoming.take(5).forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().height(cp(24)).onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags"), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 1), style = body(), color = W95.windowText, modifier = Modifier.width(cp(18)))
                            Text(s.displayName, style = body(), color = W95.windowText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatDurationMs(s.durationMs), style = body(), color = W95.windowText)
                            Spacer(Modifier.width(cp(4)))
                            IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 20.dp, 12.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Column(modifier.fillMaxWidth().padding(top = cp(4))) {
            Divider(Modifier)
            Spacer(Modifier.height(cp(4)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDisplay(if (song != null) (song.sortIndex + 1).toInt() else 0, model.positionMs, Modifier.width(cp(116)).height(cp(28)))
                Spacer(Modifier.width(cp(4)))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = song?.displayName ?: if (model.playbackRestored) "No disc" else "",
                        style = body(bold = true),
                        color = W95.text,
                        modifier = Modifier.fillMaxWidth().then(
                            if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier,
                        ),
                    )
                    Text(song?.artist.orEmpty(), style = body(), color = W95.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(cp(4)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cp(2))) {
                if (model.onOpenSettings != null) {
                    PushButton("Options...", default = false, pressed = false, modifier = Modifier.testTag("nav:settings").skinTap(onClick = model.onOpenSettings))
                    Spacer(Modifier.width(cp(4)))
                }
                IconButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 50.dp, 24.dp, 1f, true, ButtonKind.Play)
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Previous)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Next)
            }
            if (song != null) SeekTrackbar(model.positionMs, model.durationMs, { f -> model.onSeek((f * model.durationMs).toLong()) }, Modifier.fillMaxWidth())
        }
    }
}
