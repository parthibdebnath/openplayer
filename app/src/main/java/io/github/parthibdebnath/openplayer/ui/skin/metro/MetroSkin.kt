package io.github.parthibdebnath.openplayer.ui.skin.metro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.parthibdebnath.openplayer.ui.skin.batteryPercent
import io.github.parthibdebnath.openplayer.ui.skin.pressedState
import io.github.parthibdebnath.openplayer.ui.skin.skinClick
import io.github.parthibdebnath.openplayer.ui.skin.skinTap
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.formatClockTime
import io.github.parthibdebnath.openplayer.util.formatDurationMs

private object M {
    /** Windows Phone's magenta, the Zune's pink. */
    val accent = Color(0xFFFF0097)
    val subtle = Color(0xFF999999)
    val chrome = Color(0xFF1F1F1F)
    val inactive = Color(0xFF4D4D4D)
}

/**
 * Metro, as on the Zune HD and Windows Phone 7: black, white and one accent colour, type set huge
 * and lowercase so it runs off the edge, a panorama title that drifts as you move between the
 * pivots, round app-bar icons and flat square tiles. Nothing is drawn that isn't type or a flat block.
 */
object MetroSkin : Skin() {
    override val theme = UiTheme.METRO
    override val colors = AppColors(background = Color.Black, onBackground = Color.White)
    override val showsHeader = false
    override val gutter: Dp = 18.dp

    private fun seg(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.metro, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.2f).sp)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle = when {
        sizeSp >= 20 -> seg(24f, if (bold) FontWeight.SemiBold else FontWeight.ExtraLight)
        sizeSp >= 18 -> seg(20f, if (bold) FontWeight.SemiBold else FontWeight.Normal)
        sizeSp >= 16 -> seg(17f, if (bold) FontWeight.SemiBold else FontWeight.Normal)
        sizeSp >= 14 -> seg(15f, if (bold) FontWeight.SemiBold else FontWeight.Normal)
        else -> seg(13f, if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }

    private fun pivotName(tab: String) = when (tab) {
        "File" -> "music"
        "Play" -> "now playing"
        else -> "effects"
    }

    // --- the panorama ----------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        val density = LocalDensity.current
        // The title drifts a little as the pivots move, as a panorama's background does.
        val drift by animateFloatAsState(model.selectedTab.toFloat(), tween(500, easing = FastOutSlowInEasing), label = "panorama")
        // Each page slides in from the side it comes from, fading up.
        val slide = remember { Animatable(0f) }
        var lastTab by remember { mutableIntStateOf(model.selectedTab) }
        LaunchedEffect(model.selectedTab) {
            if (model.selectedTab != lastTab) {
                val from = if (model.selectedTab > lastTab) 1f else -1f
                lastTab = model.selectedTab
                slide.snapTo(from)
                slide.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            }
        }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            StatusBar(model)
            // The title: enormous, light, lowercase, and too wide for the screen.
            Box(Modifier.fillMaxWidth().height(104.dp).clipToBounds()) {
                Text(
                    "openplayer",
                    style = seg(84f, FontWeight.Light).copy(lineHeight = 100.sp),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .unboundedWidth()
                        .graphicsLayer { translationX = with(density) { (8.dp - 70.dp * drift).toPx() } }
                        .offset(y = (-6).dp),
                )
            }
            PivotHeaders(model)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = slide.value * with(density) { 80.dp.toPx() }
                        alpha = 1f - kotlin.math.abs(slide.value) * 0.9f
                    },
            ) {
                content(Modifier.fillMaxSize())
            }
        }
    }

    private fun Modifier.unboundedWidth(): Modifier = layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity, minWidth = 0))
        layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
    }

    @Composable
    private fun StatusBar(model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Signal bars, rising.
            Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
                for (i in 0 until 5) {
                    val h = size.height * (i + 1) / 5f
                    drawRect(Color.White, Offset(i * size.width / 5f, size.height - h), Size(size.width / 5f - 1.dp.toPx(), h))
                }
            }
            Spacer(Modifier.weight(1f))
            val percent = batteryPercent()
            Canvas(Modifier.size(width = 22.dp, height = 11.dp)) {
                val nub = 2.dp.toPx()
                drawRect(Color.White, Offset.Zero, Size(size.width - nub, size.height), style = Stroke(1.dp.toPx()))
                drawRect(Color.White, Offset(size.width - nub, size.height * 0.3f), Size(nub, size.height * 0.4f))
                drawRect(Color.White, Offset(2.dp.toPx(), 2.dp.toPx()), Size((size.width - nub - 4.dp.toPx()) * percent / 100f, size.height - 4.dp.toPx()))
            }
            Spacer(Modifier.width(8.dp))
            Text(time.time, style = seg(14f), color = Color.White)
        }
    }

    /** The pivot headers: the current one first and white, the rest following it in grey. */
    @Composable
    private fun PivotHeaders(model: FrameModel) {
        val haptics = LocalHaptics.current
        val order = model.tabs.indices.map { (model.selectedTab + it) % model.tabs.size }
        Row(
            Modifier.fillMaxWidth().height(56.dp).clipToBounds().padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            order.forEach { i ->
                val selected = i == model.selectedTab
                Text(
                    pivotName(model.tabs[i]),
                    style = seg(38f, FontWeight.ExtraLight),
                    color = if (selected) Color.White else M.inactive,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .testTag("tab:${model.tabs[i]}")
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            model.onSelectTab(i)
                        }
                        .padding(end = 22.dp),
                )
            }
        }
    }

    // --- controls ----------------------------------------------------------------------------------

    /** The round app bar icon: a white ring, filled white when pressed. */
    @Composable
    private fun RoundIcon(icon: ImageVector, size: Dp, pressed: Boolean, alpha: Float = 1f, ringColor: Color = Color.White) {
        Box(
            Modifier
                .size(size)
                .alpha(alpha)
                .drawBehind {
                    val r = this.size.minDimension / 2f - 1.dp.toPx()
                    if (pressed) drawCircle(ringColor, r) else drawCircle(ringColor, r, style = Stroke(2.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (pressed) Color.Black else ringColor, modifier = Modifier.size(size * 0.5f))
        }
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
            ButtonKind.Toggle -> Box(modifier.then(click), contentAlignment = Alignment.Center) { ToggleSwitch(icon == Icons.Filled.Check) }
            ButtonKind.Confirm -> Box(
                modifier
                    .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)
                    .then(click)
                    .background(if (pressed) Color.White else Color.Transparent)
                    .border(3.dp, Color.White)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (icon == Icons.Filled.Add) "add" else "ok", style = seg(17f), color = if (pressed) Color.Black else Color.White)
            }
            else -> Box(modifier.then(click), contentAlignment = Alignment.Center) {
                val side = when (kind) {
                    ButtonKind.Play -> 72.dp
                    ButtonKind.Next, ButtonKind.Previous -> 52.dp
                    else -> size.coerceIn(30.dp, 48.dp)
                }
                RoundIcon(icon, side, pressed, alpha = if (enabled) opacity.coerceAtLeast(0.4f) else 0.35f)
            }
        }
    }

    /** The Windows Phone toggle switch: a white-edged track, accent-filled when on, and a white block. */
    @Composable
    private fun ToggleSwitch(on: Boolean) {
        val position by animateFloatAsState(if (on) 1f else 0f, tween(150), label = "toggle")
        Canvas(Modifier.size(width = 66.dp, height = 28.dp)) {
            val border = 2.dp.toPx()
            val thumbW = 16.dp.toPx()
            drawRect(Color.White, Offset(0f, border * 1.5f), Size(size.width - thumbW / 2f, size.height - border * 3), style = Stroke(border))
            val inset = border * 3
            if (position > 0f) {
                drawRect(M.accent, Offset(inset, border * 3), Size((size.width - thumbW / 2f - inset * 2) * position, size.height - border * 6))
            }
            val x = (size.width - thumbW) * position
            drawRect(Color.White, Offset(x, 0f), Size(thumbW, size.height))
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            drawOutline(shape.createOutline(size, layoutDirection, this), Color.White, style = Stroke(2.dp.toPx()))
        }
        else -> modifier.background(M.chrome)
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier =
        modifier.background(if (focused) Color.White else Color(0xFFBFBFBF)).border(2.dp, if (focused) M.accent else Color(0xFFBFBFBF))

    override fun textFieldInk(focused: Boolean): Color = Color.Black

    @Composable
    override fun Divider(modifier: Modifier) {
        Spacer(modifier.fillMaxWidth().height(1.dp))
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(4.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            val h = (size.height * visible / total).coerceAtLeast(24.dp.toPx())
            val top = (size.height - h) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawRect(Color(0xFF6B6B6B), Offset(0f, top), Size(size.width, h))
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 36.dp
        override val thumbWidth: Dp = 10.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val trackH = 12.dp.toPx()
            val top = (size.height - trackH) / 2f
            drawRect(Color(0xFF3B3B3B), Offset(0f, top), Size(size.width, trackH))
            val x = tw / 2f + (size.width - tw) * fraction
            drawRect(if (enabled) M.accent else M.inactive, Offset(0f, top), Size(x, trackH))
            drawRect(Color.White, Offset(x - tw / 2f, 0f), Size(tw, size.height))
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val haptics = LocalHaptics.current
        // The phone's own toggle: the setting's name above, the state large below it, and the switch.
        Column(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Switch; selected = checked }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Switch) {
                    haptics?.click()
                    onToggle(!checked)
                },
        ) {
            Text(label.lowercase(), style = seg(16f), color = if (bold) Color.White else M.subtle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (checked) "On" else "Off", style = seg(26f, FontWeight.ExtraLight), color = Color.White, modifier = Modifier.weight(1f))
                ToggleSwitch(checked)
            }
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
        // A pivot: lowercase words, the chosen one white.
        Row(
            modifier.fillMaxWidth().heightIn(min = 40.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Text(
                    label.lowercase(),
                    style = seg(if (height >= 48.dp) 28f else 20f, FontWeight.ExtraLight),
                    color = if (selected) Color.White else M.inactive,
                    maxLines = 1,
                    modifier = Modifier
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        }
                        .padding(end = 18.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        // Windows Phone's context menu: white, edge to edge, in large black type.
        Column(Modifier.fillMaxWidth().widthIn(min = 300.dp).background(Color.White).padding(vertical = 10.dp)) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Text(
                    item.label.lowercase() + if (item.selected) "  ✓" else "",
                    style = seg(24f, FontWeight.Normal),
                    color = if (pressed) M.accent else Color.Black,
                    modifier = Modifier.fillMaxWidth().skinClick(interaction) { onPick(item) }.padding(horizontal = 24.dp, vertical = 9.dp),
                )
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        // A message box: a dark band, the title large, two outlined buttons.
        Column(modifier.fillMaxWidth().background(M.chrome).padding(18.dp)) {
            Text(title, style = seg(24f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(message, style = seg(17f), color = Color.White)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("ok" to onConfirm, "cancel" to onDismiss).forEach { (label, action) ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by pressedState(interaction)
                    Box(
                        Modifier
                            .weight(1f)
                            .then(if (label == "cancel") Modifier.testTag("dialog:dismiss") else Modifier)
                            .background(if (pressed) Color.White else Color.Transparent)
                            .border(3.dp, Color.White)
                            .skinClick(interaction) { action() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = seg(18f), color = if (pressed) Color.Black else Color.White)
                    }
                }
            }
        }
    }

    // --- lists ---------------------------------------------------------------------------------------

    @Composable
    private fun Tile(icon: ImageVector, size: Dp, color: Color = M.accent, label: String? = null, modifier: Modifier = Modifier) {
        Box(modifier.size(size).background(color)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.align(if (label == null) Alignment.Center else Alignment.TopCenter).padding(top = if (label == null) 0.dp else size * 0.2f).size(size * 0.4f))
            if (label != null) {
                Text(label, style = seg(13f), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), maxLines = 1)
            }
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = seg(17f), color = M.subtle)
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() }
                            .padding(vertical = 7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tile(
                                when (entry.kind) {
                                    CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite
                                    CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label
                                    CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote
                                },
                                62.dp,
                                color = if (entry.kind == CollectionEntry.Kind.Playlist) M.accent else Color(0xFF3B3B3B),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title.lowercase(),
                                    style = seg(26f, FontWeight.ExtraLight),
                                    color = if (entry.emphasized) M.accent else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(entry.subtitle, style = seg(15f), color = M.subtle, maxLines = 1)
                            }
                        }
                        entry.menu()
                    }
                }
            }
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.4f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = model.title.lowercase(),
                    style = seg(22f, FontWeight.ExtraLight),
                    color = if (model.isCurrent) M.accent else Color.White,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(model.artist.lowercase(), style = seg(15f), color = M.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(modifier, content = content)
    }

    // --- now playing ------------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Box(Modifier.fillMaxSize().clipToBounds()) {
            // The Zune HD's backdrop: the artist's name, enormous and faint, drifting by.
            if (song != null) {
                val drift = rememberInfiniteTransition(label = "backdrop")
                val shift by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
                Text(
                    song.artist.lowercase(),
                    style = seg(170f, FontWeight.Light).copy(lineHeight = 170.sp),
                    color = Color.White.copy(alpha = 0.08f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .unboundedWidth()
                        .offset(y = 150.dp)
                        .graphicsLayer { translationX = -shift * 700.dp.toPx() + 20.dp.toPx() },
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                model.header(Modifier.fillMaxWidth())
                model.sleepTimerPanel?.let { panel ->
                    panel(Modifier.fillMaxWidth().background(M.chrome).padding(14.dp))
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Tile(Icons.Filled.MusicNote, 132.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.playlistLabel.lowercase(), style = seg(15f), color = M.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song?.artist?.uppercase() ?: "",
                            style = seg(15f, FontWeight.SemiBold),
                            color = M.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                MarqueeText(
                    song?.displayName?.lowercase() ?: if (model.playbackRestored) "nothing playing" else "",
                    seg(40f, FontWeight.ExtraLight).copy(lineHeight = 46.sp),
                    Modifier.fillMaxWidth().then(
                        if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier.skinTap(onClick = model.onOpenLibrary),
                    ),
                    color = Color.White,
                )
                if (song != null) {
                    Spacer(Modifier.height(8.dp))
                    io.github.parthibdebnath.openplayer.ui.components.CustomSlider(
                        value = model.progress,
                        onValueChange = model.onSeek,
                        valueRange = 0f..1f,
                        commitOnRelease = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatDurationMs(p.positionMs), style = seg(15f), color = M.subtle)
                        Spacer(Modifier.weight(1f))
                        Text(formatDurationMs((p.durationMs - p.positionMs).coerceAtLeast(0)), style = seg(15f), color = M.subtle)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 52.dp, 26.dp, 1f, true, ButtonKind.Previous)
                    IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 72.dp, 36.dp, 1f, true, ButtonKind.Play)
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 52.dp, 26.dp, 1f, true, ButtonKind.Next)
                }
                Spacer(Modifier.height(18.dp))
                // Flat tiles for the switches: lit in the accent when on.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Filled.Shuffle, "shuffle", model.shuffleOn, model.onToggleShuffle)
                    model.isFavourite?.let { ActionTile(if (it) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "favourite", it, model.onToggleFavourite) }
                    ActionTile(Icons.Filled.Timer, "sleep", model.sleepTimerActive, model.onOpenSleepTimer)
                    if (model.outputSwitchAvailable) {
                        ActionTile(if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker, "output", false, model.onToggleOutput)
                    }
                }
                val notes = listOfNotNull("shuffle locked".takeIf { model.shuffleLocked }, "effects on".takeIf { model.fxOn }, model.message?.lowercase())
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(notes.joinToString("   "), style = seg(15f), color = M.subtle)
                }
                if (p.upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("up next", style = seg(32f, FontWeight.ExtraLight), color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    p.upcoming.take(5).forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.displayName.lowercase(), style = seg(22f, FontWeight.ExtraLight), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist.lowercase(), style = seg(14f), color = M.subtle, maxLines = 1)
                            }
                            IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun ActionTile(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
        val haptics = LocalHaptics.current
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Tile(
            icon,
            78.dp,
            color = if (on) M.accent else Color(0xFF333333),
            label = label,
            modifier = Modifier
                .semantics { contentDescription = label; selected = on }
                .graphicsLayer {
                    // Tiles tilt in a little when pressed.
                    val s = if (pressed) 0.94f else 1f
                    scaleX = s
                    scaleY = s
                }
                .skinClick(interaction) {
                    haptics?.click()
                    onClick()
                },
        )
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        // The application bar: a dark strip of round icons, with the song beside them.
        Column(modifier.fillMaxWidth().background(M.chrome).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName?.lowercase() ?: if (model.playbackRestored) "nothing playing" else "",
                        seg(20f, FontWeight.ExtraLight),
                        Modifier.fillMaxWidth().then(
                            if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier,
                        ),
                        color = Color.White,
                    )
                    Text(song?.artist?.lowercase().orEmpty(), style = seg(14f), color = M.subtle, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Plain)
                    IconButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 44.dp, 22.dp, 1f, true, ButtonKind.Plain)
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 40.dp, 20.dp, 1f, true, ButtonKind.Plain)
                    if (model.onOpenSettings != null) {
                        Box(Modifier.testTag("nav:settings").semantics { contentDescription = "Settings" }.skinTap(onClick = model.onOpenSettings).padding(4.dp)) {
                            Icon(Icons.Filled.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            if (song != null && model.durationMs > 0) {
                // A thin progress line, with a taller strip to touch for seeking.
                io.github.parthibdebnath.openplayer.ui.skin.SeekBar(
                    fraction = model.positionMs.toFloat() / model.durationMs,
                    onSeek = { model.onSeek((it * model.durationMs).toLong()) },
                    modifier = Modifier.fillMaxWidth().height(18.dp),
                ) { f ->
                    val h = 3.dp.toPx()
                    val top = (size.height - h) / 2f
                    drawRect(Color(0xFF3B3B3B), Offset(0f, top), Size(size.width, h))
                    drawRect(M.accent, Offset(0f, top), Size(size.width * f, h))
                }
            }
        }
    }
}
