package io.github.parthibdebnath.openplayer.ui.skin.s60

import android.app.Activity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import io.github.parthibdebnath.openplayer.ui.skin.SeekBar
import io.github.parthibdebnath.openplayer.ui.skin.Skin
import io.github.parthibdebnath.openplayer.ui.skin.SkinFonts
import io.github.parthibdebnath.openplayer.ui.skin.SliderLook
import io.github.parthibdebnath.openplayer.ui.skin.SongRowModel
import io.github.parthibdebnath.openplayer.ui.skin.batteryPercent
import io.github.parthibdebnath.openplayer.ui.skin.pressedState
import io.github.parthibdebnath.openplayer.ui.skin.skinClick
import io.github.parthibdebnath.openplayer.ui.skin.skinTap
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.formatClockTime
import io.github.parthibdebnath.openplayer.util.formatDurationMs
import java.text.SimpleDateFormat
import java.util.Locale

/** S60 3rd Edition's default blue theme. */
private object N {
    val paneTop = Color(0xFF0E3F86)
    val paneBottom = Color(0xFF2B6FC4)
    val mainTop = Color(0xFFF2F7FD)
    val mainBottom = Color(0xFFD8E6F6)
    val ink = Color(0xFF0D1F3A)
    val inkSoft = Color(0xFF4F6585)
    val highlightTop = Color(0xFF4A98EA)
    val highlightBottom = Color(0xFF1A58B2)
    val frame = Color(0xFF7FA5D6)
}

private val HighlightBrush = Brush.verticalGradient(listOf(N.highlightTop, Color(0xFF2C77D3), N.highlightBottom))

/**
 * Nokia S60, 3rd Edition: a status pane crowded with signal and battery bars, the app's title
 * and the clock; a navi pane of tabs with arrows; a themed main pane of chunky icons and a rounded
 * highlight bar; and the softkey labels along the foot - Options on the left, Back on the right,
 * the selection key between them.
 */
object S60Skin : Skin() {
    override val theme = UiTheme.S60

    /** The status pane's blue above; the softkey bar's below. */
    override val edgeTop = Color(0xFF0E3F86)
    override val edgeBottom = Color(0xFF0E3F86)
    override val colors = AppColors(background = N.mainTop, onBackground = N.ink)
    override val showsHeader = false
    override val gutter: Dp = 10.dp

    private fun nokia(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.s60, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.22f).sp)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val size = when {
            sizeSp >= 20 -> 18.5f
            sizeSp >= 18 -> 17f
            sizeSp >= 16 -> 15.5f
            sizeSp >= 14 -> 13.5f
            else -> 12f
        }
        return nokia(size, if (bold) FontWeight.Bold else FontWeight.Normal)
    }

    private val tabIcons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlayCircle, Icons.Filled.GraphicEq)

    private fun tabTitle(tab: String) = when (tab) {
        "File" -> "Music library"
        "Play" -> "Now playing"
        else -> "Effects"
    }

    private fun tabShort(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Playing"
        else -> "Effects"
    }

    // --- frame ------------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        Column(Modifier.fillMaxSize().background(N.paneTop)) {
            StatusPane(model)
            NaviPane(model)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(N.mainTop, N.mainBottom)))
                    .drawBehind { drawThemePattern() },
            ) {
                content(Modifier.fillMaxSize())
            }
            ControlPane(model)
        }
    }

    /** The theme's faint swoosh across the main pane. */
    private fun DrawScope.drawThemePattern() {
        val path = Path().apply {
            moveTo(0f, size.height * 0.72f)
            cubicTo(size.width * 0.3f, size.height * 0.6f, size.width * 0.7f, size.height * 0.9f, size.width, size.height * 0.68f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.35f))
        drawCircle(Color.White.copy(alpha = 0.25f), size.width * 0.5f, Offset(size.width * 1.05f, size.height * 0.05f))
    }

    @Composable
    private fun StatusPane(model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, true)
        val date = remember(now) { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Brush.verticalGradient(listOf(N.paneTop, N.paneBottom))),
        ) {
            // Signal: an antenna over a column of bars, longest at the bottom.
            Column(Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 4.dp), horizontalAlignment = Alignment.Start) {
                Canvas(Modifier.size(width = 12.dp, height = 12.dp)) {
                    val c = Color.White
                    drawLine(c, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.5.dp.toPx())
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height * 0.45f); close() }, c)
                }
                Spacer(Modifier.height(2.dp))
                LevelBars(7, 7, alignStart = true)
            }
            // Battery: the same on the right, under a battery glyph.
            val percent = batteryPercent()
            Column(Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 4.dp), horizontalAlignment = Alignment.End) {
                Canvas(Modifier.size(width = 9.dp, height = 12.dp)) {
                    drawRect(Color.White, Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, 2.dp.toPx()))
                    drawRect(Color.White, Offset(0f, 2.dp.toPx()), Size(size.width, size.height - 2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                    drawRect(Color.White, Offset(2.dp.toPx(), size.height * 0.45f), Size(size.width - 4.dp.toPx(), size.height * 0.45f))
                }
                Spacer(Modifier.height(2.dp))
                LevelBars(7, ((percent + 7) / 14).coerceIn(0, 7), alignStart = false)
            }
            // The title pane: the app's icon and name.
            Row(Modifier.align(Alignment.CenterStart).padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                ChunkyIcon(Icons.Filled.MusicNote, 34.dp, Color(0xFFF08A24))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("openplayer", style = nokia(18f, FontWeight.Bold), color = Color.White)
                    Text(tabTitle(model.tabs[model.selectedTab]), style = nokia(13f), color = Color(0xFFCFE0F7))
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 26.dp), horizontalAlignment = Alignment.End) {
                Text(time.time, style = nokia(16f, FontWeight.Bold), color = Color.White)
                Text(date, style = nokia(11.5f), color = Color(0xFFCFE0F7))
            }
        }
    }

    /** A column of level bars, longest at the bottom, as S60 drew signal and battery. */
    @Composable
    private fun LevelBars(count: Int, lit: Int, alignStart: Boolean) {
        Canvas(Modifier.size(width = 14.dp, height = 40.dp)) {
            val gap = 1.5.dp.toPx()
            val h = (size.height - gap * (count - 1)) / count
            for (i in 0 until count) {
                val fromBottom = count - 1 - i
                val w = size.width * (0.35f + 0.65f * (count - i) / count)
                val x = if (alignStart) 0f else size.width - w
                val on = fromBottom < lit
                drawRect(if (on) Color(0xFF8CF06B) else Color.White.copy(alpha = 0.25f), Offset(x, i * (h + gap)), Size(w, h))
            }
        }
    }

    /** S60's chunky, glossy icon: a rounded square of colour with a bold white glyph. */
    @Composable
    private fun ChunkyIcon(icon: ImageVector, size: Dp, colour: Color) {
        Box(
            Modifier
                .size(size)
                .shadow(2.dp, RoundedCornerShape(size * 0.22f))
                .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(colour, Color.White, 0.35f), colour, androidx.compose.ui.graphics.lerp(colour, Color.Black, 0.25f))), RoundedCornerShape(size * 0.22f))
                .drawBehind {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), endY = this.size.height * 0.5f),
                        size = Size(this.size.width, this.size.height * 0.5f),
                        cornerRadius = CornerRadius(this.size.width * 0.22f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
    }

    /** The navi pane: the tab group, with arrows either side to say there's more. */
    @Composable
    private fun NaviPane(model: FrameModel) {
        val haptics = LocalHaptics.current
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Brush.verticalGradient(listOf(N.paneBottom, Color(0xFF1C5AAE))))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Arrow(left = true, enabled = model.selectedTab > 0) { model.onSelectTab(model.selectedTab - 1) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                model.tabs.forEachIndexed { i, tab ->
                    val selected = i == model.selectedTab
                    Row(
                        Modifier
                            .height(if (selected) 32.dp else 28.dp)
                            .padding(horizontal = 1.dp)
                            .testTag("tab:$tab")
                            .semantics { role = Role.Tab; this.selected = selected }
                            .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                                if (!selected) haptics?.tick()
                                model.onSelectTab(i)
                            }
                            .background(
                                if (selected) Brush.verticalGradient(listOf(Color.White, N.mainTop)) else Brush.verticalGradient(listOf(Color(0xFF6F9FDB), Color(0xFF3C77C4))),
                                RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp),
                            )
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tabIcons[i], null, tint = if (selected) N.highlightBottom else Color.White, modifier = Modifier.size(18.dp))
                        if (selected) {
                            Spacer(Modifier.width(5.dp))
                            Text(tabShort(tab), style = nokia(14f, FontWeight.Bold), color = N.ink)
                        }
                    }
                }
            }
            Arrow(left = false, enabled = model.selectedTab < model.tabs.lastIndex) { model.onSelectTab(model.selectedTab + 1) }
        }
    }

    @Composable
    private fun Arrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
        Canvas(
            Modifier
                .size(width = 22.dp, height = 32.dp)
                .then(if (enabled) Modifier.skinTap(onClick = onClick) else Modifier),
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val s = 6.dp.toPx()
            val d = if (left) -1f else 1f
            drawPath(Path().apply { moveTo(c.x - d * s * 0.6f, c.y - s); lineTo(c.x + d * s * 0.6f, c.y); lineTo(c.x - d * s * 0.6f, c.y + s); close() }, Color.White.copy(alpha = if (enabled) 1f else 0.3f))
        }
    }

    /** The control pane: the softkey labels, with the selection key between them. */
    @Composable
    private fun ControlPane(model: FrameModel) {
        val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val context = LocalContext.current
        val haptics = LocalHaptics.current
        var optionsOpen by remember { mutableStateOf(false) }
        // Back where there's somewhere to go back to; otherwise Exit, which leaves the music playing.
        // Whether there is can't be observed, so it's looked at a few times a second.
        var canGoBack by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(back) {
            while (true) {
                canGoBack = back?.hasEnabledCallbacks() == true
                kotlinx.coroutines.delay(250)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF1C5AAE), N.paneTop))),
        ) {
            Text(
                "Options",
                style = nokia(17f, FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("frame:menu")
                    .skinTap { optionsOpen = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 52.dp, height = 34.dp)
                    .semantics { contentDescription = if (model.playback.isPlaying) "Pause" else "Play" }
                    .skinTap(onClick = model.onPlayPause)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6FA6E6), Color(0xFF2A62B4))), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (model.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Text(
                if (canGoBack) "Back" else "Exit",
                style = nokia(17f, FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .skinTap {
                        if (back?.hasEnabledCallbacks() == true) back.onBackPressed() else (context as? Activity)?.moveTaskToBack(true)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (optionsOpen) {
                Popup(
                    alignment = Alignment.BottomStart,
                    offset = IntOffset(0, -with(LocalDensity.current) { 48.dp.roundToPx() }),
                    onDismissRequest = { optionsOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val items = buildList {
                        add(ContextMenuItem(if (model.playback.isPlaying) "Pause" else "Play", onClick = model.onPlayPause))
                        add(ContextMenuItem("Next track", onClick = model.onNext))
                        add(ContextMenuItem("Previous track", onClick = model.onPrevious))
                        model.tabs.forEachIndexed { i, tab -> if (i != model.selectedTab) add(ContextMenuItem("Go to ${tabShort(tab).lowercase()}", onClick = { model.onSelectTab(i) })) }
                        add(ContextMenuItem("Settings", onClick = model.onOpenSettings))
                    }
                    OptionsMenu(items, onCancel = { optionsOpen = false }) { item ->
                        optionsOpen = false
                        haptics?.click()
                        item.onClick()
                    }
                }
            }
        }
    }

    /** The options menu: a themed pop-up list with the highlight bar, and its own Select and Cancel. */
    @Composable
    private fun OptionsMenu(items: List<ContextMenuItem>, onCancel: () -> Unit, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 240.dp)
                .padding(6.dp)
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(N.mainTop, N.mainBottom)), RoundedCornerShape(10.dp))
                .border(2.dp, N.frame, RoundedCornerShape(10.dp))
                .padding(4.dp),
        ) {
            items.forEachIndexed { i, item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                // The first item carries the highlight until another is touched, as the list's focus would.
                val lit = pressed || (i == 0 && items.size > 1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .then(if (lit) Modifier.background(HighlightBrush, RoundedCornerShape(6.dp)) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = nokia(16f), color = if (lit) Color.White else N.ink, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    if (item.selected) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.Check, null, tint = if (lit) Color.White else N.highlightBottom, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).background(N.paneTop, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Select", style = nokia(15f, FontWeight.Bold), color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("Cancel", style = nokia(15f, FontWeight.Bold), color = Color.White, modifier = Modifier.skinTap(onClick = onCancel))
            }
        }
    }

    // --- controls ------------------------------------------------------------------------------------

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
            ButtonKind.Toggle -> Box(modifier.heightIn(min = 30.dp).then(click), contentAlignment = Alignment.Center) {
                CheckBox(icon == Icons.Filled.Check)
            }
            ButtonKind.Confirm -> Box(
                modifier
                    .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)
                    .then(click)
                    .background(HighlightBrush, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(if (icon == Icons.Filled.Add) "Add" else "OK", style = nokia(15f, FontWeight.Bold), color = Color.White)
            }
            else -> {
                val side = when (kind) {
                    ButtonKind.Play -> 64.dp
                    ButtonKind.Next, ButtonKind.Previous -> 48.dp
                    else -> size.coerceIn(32.dp, 46.dp)
                }
                Box(
                    modifier
                        .size(side)
                        .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f)
                        .then(click)
                        .background(
                            if (pressed) HighlightBrush else Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F6))),
                            RoundedCornerShape(side * 0.24f),
                        )
                        .border(1.dp, N.frame, RoundedCornerShape(side * 0.24f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (pressed) Color.White else N.highlightBottom, modifier = Modifier.size(side * 0.56f))
                }
            }
        }
    }

    @Composable
    private fun CheckBox(checked: Boolean) {
        Canvas(Modifier.size(22.dp)) {
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F6))), cornerRadius = CornerRadius(4.dp.toPx()))
            drawRoundRect(N.frame, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            if (checked) {
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.5f)
                        lineTo(size.width * 0.42f, size.height * 0.74f)
                        lineTo(size.width * 0.84f, size.height * 0.22f)
                    },
                    Color(0xFF2E9A1E),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F6))))
            drawOutline(outline, N.frame, style = Stroke(1.dp.toPx()))
        }
        else -> modifier
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .border(1.5.dp, N.frame, RoundedCornerShape(10.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier
        .background(Color.White, RoundedCornerShape(4.dp))
        .border(if (focused) 2.dp else 1.dp, if (focused) N.highlightBottom else N.frame, RoundedCornerShape(4.dp))

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(N.frame.copy(alpha = 0.6f)))
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(7.dp).fillMaxHeight().padding(vertical = 2.dp)) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            drawRoundRect(Color.White.copy(alpha = 0.7f), cornerRadius = CornerRadius(size.width / 2f))
            drawRoundRect(N.frame, cornerRadius = CornerRadius(size.width / 2f), style = Stroke(1.dp.toPx()))
            val h = (size.height * visible / total).coerceAtLeast(20.dp.toPx())
            val top = (size.height - h) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawRoundRect(Brush.horizontalGradient(listOf(N.highlightTop, N.highlightBottom)), Offset(0f, top), Size(size.width, h), CornerRadius(size.width / 2f))
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 30.dp
        override val thumbWidth: Dp = 14.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val h = 10.dp.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(Color.White, Offset(tw / 2f, top), Size(size.width - tw, h), CornerRadius(3.dp.toPx()))
            val x = tw / 2f + (size.width - tw) * fraction
            drawRoundRect(HighlightBrush, Offset(tw / 2f, top), Size(x - tw / 2f, h), CornerRadius(3.dp.toPx()))
            drawRoundRect(N.frame, Offset(tw / 2f, top), Size(size.width - tw, h), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
            // The slider's handle: a small upright block.
            val th = 24.dp.toPx()
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFC9DBF1)), startY = (size.height - th) / 2f, endY = (size.height + th) / 2f), Offset(x - tw / 2f, (size.height - th) / 2f), Size(tw, th), CornerRadius(3.dp.toPx()))
            drawRoundRect(if (pressed) N.highlightBottom else N.frame, Offset(x - tw / 2f, (size.height - th) / 2f), Size(tw, th), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val haptics = LocalHaptics.current
        // A settings list item: the setting's name, and below it its value in a little box.
        Column(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Switch; selected = checked }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Switch) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = 3.dp),
        ) {
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = N.ink)
            Spacer(Modifier.height(3.dp))
            Text(
                if (checked) "On" else "Off",
                style = nokia(14.5f, FontWeight.Medium),
                color = N.highlightBottom,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .border(1.dp, N.frame, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
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
        val haptics = LocalHaptics.current
        Row(modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selected) HighlightBrush else Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F6))), RoundedCornerShape(6.dp))
                        .border(1.dp, N.frame, RoundedCornerShape(6.dp))
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = nokia(14.5f, if (selected) FontWeight.Bold else FontWeight.Normal), color = if (selected) Color.White else N.ink, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }

    // --- menus and queries ------------------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        // A context menu is the options menu, with nothing to cancel but a tap outside.
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 220.dp)
                .padding(6.dp)
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(N.mainTop, N.mainBottom)), RoundedCornerShape(10.dp))
                .border(2.dp, N.frame, RoundedCornerShape(10.dp))
                .padding(4.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .then(if (pressed) Modifier.background(HighlightBrush, RoundedCornerShape(6.dp)) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = nokia(16f), color = if (pressed) Color.White else N.ink, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    if (item.selected) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.Check, null, tint = N.highlightBottom, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        // A confirmation query: the question with its icon, and Yes / No as softkeys at its foot.
        Column(
            modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(N.mainTop, N.mainBottom)))
                .border(2.dp, N.frame, RoundedCornerShape(10.dp)),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).background(Brush.verticalGradient(listOf(Color(0xFF6FA6E6), Color(0xFF2A62B4))), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text("?", style = nokia(24f, FontWeight.Bold), color = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = nokia(16f, FontWeight.Bold), color = N.ink)
                    Text(message, style = nokia(14f), color = N.ink)
                }
            }
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1C5AAE), N.paneTop))).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Yes", style = nokia(16f, FontWeight.Bold), color = Color.White, modifier = Modifier.skinTap(onClick = onConfirm))
                Spacer(Modifier.weight(1f))
                Text("No", style = nokia(16f, FontWeight.Bold), color = Color.White, modifier = Modifier.testTag("dialog:dismiss").skinTap(onClick = onDismiss))
            }
        }
    }

    // --- lists ----------------------------------------------------------------------------------------

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = nokia(15f), color = N.ink)
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                    ) {
                        // A large-icon double-line list item, the playing one under the highlight.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .then(if (entry.emphasized) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)).background(HighlightBrush, RoundedCornerShape(8.dp)) else Modifier)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val (icon, colour) = when (entry.kind) {
                                CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite to Color(0xFFE0393E)
                                CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label to Color(0xFF8A4FD0)
                                CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote to Color(0xFF2E9A1E)
                            }
                            ChunkyIcon(icon, 42.dp, colour)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = nokia(17f, FontWeight.Bold), color = if (entry.emphasized) Color.White else N.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.subtitle, style = nokia(13.5f), color = if (entry.emphasized) Color(0xFFDCEBFB) else N.inkSoft, maxLines = 1)
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
        Box(modifier, content = content)
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .then(if (model.isCurrent) Modifier.background(HighlightBrush, RoundedCornerShape(6.dp)) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = if (model.isCurrent) Color.White else Color(0xFF2E9A1E), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                MarqueeText(
                    text = model.title,
                    style = nokia(16f, if (model.isCurrent) FontWeight.Bold else FontWeight.Normal),
                    color = if (model.isCurrent) Color.White else N.ink,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = nokia(13f), color = if (model.isCurrent) Color(0xFFDCEBFB) else N.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    // --- the Music player ---------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel -> panel(Modifier.fillMaxWidth().padding(top = 8.dp).then(panel(Modifier, RoundedCornerShape(10.dp), GlassRole.Container, 1f)).padding(10.dp)) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The album art box, framed, with a glossy music icon where there's no cover.
                Box(
                    Modifier
                        .size(118.dp)
                        .shadow(3.dp, RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8BC2F2), Color(0xFF2D63B5))), RoundedCornerShape(6.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ChunkyIcon(Icons.Filled.MusicNote, 60.dp, Color(0xFFF08A24))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "(no track)" else "",
                        nokia(19f, FontWeight.Bold),
                        Modifier.fillMaxWidth().then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier.skinTap(onClick = model.onOpenLibrary)),
                        color = N.ink,
                    )
                    Text(song?.artist.orEmpty(), style = nokia(15f), color = N.ink, maxLines = 1)
                    Text(p.playlistLabel, style = nokia(13f), color = N.inkSoft, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (model.shuffleOn) Icon(Icons.Filled.Shuffle, null, tint = N.highlightBottom, modifier = Modifier.size(18.dp))
                        if (model.sleepTimerActive) Icon(Icons.Filled.Timer, null, tint = N.highlightBottom, modifier = Modifier.size(18.dp))
                        if (model.isFavourite == true) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE0393E), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (song != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDurationMs(p.positionMs), style = nokia(13.5f, FontWeight.Bold), color = N.ink, modifier = Modifier.width(46.dp))
                    SeekBar(model.progress, model.onSeek, Modifier.weight(1f).height(26.dp)) { f -> with(slider) { drawSlider(f, true, 0, false) } }
                    Text(formatDurationMs(p.durationMs), style = nokia(13.5f, FontWeight.Bold), color = N.ink, textAlign = TextAlign.End, modifier = Modifier.width(46.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 48.dp, 26.dp, 1f, true, ButtonKind.Previous)
                IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 64.dp, 36.dp, 1f, true, ButtonKind.Play)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 48.dp, 26.dp, 1f, true, ButtonKind.Next)
            }
            Spacer(Modifier.height(12.dp))
            // The settings list beneath, as the player's own options read.
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).border(1.dp, N.frame, RoundedCornerShape(10.dp)).padding(8.dp)) {
                ToggleRow("Shuffle", model.shuffleOn, { model.onToggleShuffle() }, Modifier, true, 16)
                model.isFavourite?.let {
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("Favourite", it, { model.onToggleFavourite() }, Modifier, true, 16)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().skinTap(onClick = model.onOpenSleepTimer).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChunkyIcon(Icons.Filled.Timer, 30.dp, Color(0xFF2C77D3))
                    Spacer(Modifier.width(10.dp))
                    Text("Sleep timer", style = nokia(16f, FontWeight.Bold), color = N.ink, modifier = Modifier.weight(1f))
                    Text(if (model.sleepTimerActive) "On" else "Off", style = nokia(14.5f), color = N.highlightBottom)
                }
                if (model.outputSwitchAvailable) {
                    Row(Modifier.fillMaxWidth().skinTap(onClick = model.onToggleOutput).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChunkyIcon(if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker, 30.dp, Color(0xFF2C77D3))
                        Spacer(Modifier.width(10.dp))
                        Text("Output", style = nokia(16f, FontWeight.Bold), color = N.ink, modifier = Modifier.weight(1f))
                        Text(if (model.outputDevice == OutputDevice.BLUETOOTH) "Bluetooth" else "Loudspeaker", style = nokia(14.5f), color = N.highlightBottom)
                    }
                }
            }
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, model.message)
            if (notes.isNotEmpty()) Text(notes.joinToString("  ·  "), style = nokia(13f), color = N.inkSoft, modifier = Modifier.padding(top = 6.dp))
            if (p.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Next in queue", style = nokia(16f, FontWeight.Bold), color = N.ink)
                p.upcoming.take(5).forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF2E9A1E), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.displayName, style = nokia(15f), color = N.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = nokia(12.5f), color = N.inkSoft, maxLines = 1)
                        }
                        IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 34.dp, 18.dp, 1f, true, ButtonKind.Plain)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Row(
            modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE8F6))), RoundedCornerShape(10.dp))
                .border(1.dp, N.frame, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChunkyIcon(Icons.Filled.MusicNote, 38.dp, Color(0xFFF08A24))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                MarqueeText(
                    song?.displayName ?: if (model.playbackRestored) "(no track)" else "",
                    nokia(15.5f, FontWeight.Bold),
                    Modifier.fillMaxWidth().then(if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier),
                    color = N.ink,
                )
                if (song != null && model.durationMs > 0) {
                    SeekBar(model.positionMs.toFloat() / model.durationMs, { model.onSeek((it * model.durationMs).toLong()) }, Modifier.fillMaxWidth().height(16.dp)) { f ->
                        val h = 6.dp.toPx()
                        val top = (size.height - h) / 2f
                        drawRoundRect(Color.White, Offset(0f, top), Size(size.width, h), CornerRadius(2.dp.toPx()))
                        drawRoundRect(HighlightBrush, Offset(0f, top), Size(size.width * f, h), CornerRadius(2.dp.toPx()))
                        drawRoundRect(N.frame, Offset(0f, top), Size(size.width, h), CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                    Text("${formatDurationMs(model.positionMs)} / ${formatDurationMs(model.durationMs)}", style = nokia(12f), color = N.inkSoft)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (model.onOpenSettings != null) {
                Box(Modifier.testTag("nav:settings")) {
                    IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Plain)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Plain)
            Spacer(Modifier.width(4.dp))
            IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Plain)
        }
    }
}
