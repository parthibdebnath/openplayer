package io.github.parthibdebnath.openplayer.ui.skin.aqua

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawOutline
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
import io.github.parthibdebnath.openplayer.ui.skin.batteryPercent
import io.github.parthibdebnath.openplayer.ui.skin.pressedState
import io.github.parthibdebnath.openplayer.ui.skin.skinClick
import io.github.parthibdebnath.openplayer.ui.skin.skinTap
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.formatClockTime
import io.github.parthibdebnath.openplayer.util.formatDurationMs
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Aqua, as on Mac OS X 10.0-10.2 with iTunes 2 and 3: a global menu bar, a brushed-metal window
 * with gel traffic lights, pinstriped content, blue gel for whatever's default or on, and an
 * iTunes status display for what's playing. Play is a lickable blue gel pill.
 */
object AquaSkin : Skin() {
    override val theme = UiTheme.AQUA
    override val colors = AppColors(background = Color(0xFFEDEDED), onBackground = AquaColors.text)
    override val gutter: Dp = 12.dp
    override val showsHeader = false

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        // Lucida Grande ran small and wide; the app's sizes come down a little to match it.
        val size = when {
            sizeSp >= 20 -> 16.5f
            sizeSp >= 18 -> 15f
            sizeSp >= 16 -> 14f
            sizeSp >= 15 -> 13.5f
            sizeSp >= 14 -> 12.5f
            sizeSp >= 13 -> 12f
            else -> 11f
        }
        return TextStyle(
            fontFamily = SkinFonts.aqua,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = size.sp,
            lineHeight = (size * 1.25f).sp,
        )
    }

    // --- frame ---------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        val density = LocalDensity.current
        var metal by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        val stripes = remember(density.density) { pinstripeBrush(density.density) }
        Box(
            Modifier
                .fillMaxSize()
                // The desktop, seen only past the window's rounded top corners.
                .background(Brush.verticalGradient(listOf(Color(0xFF3A6EC4), Color(0xFF6FA0E0))))
                .onSizeChanged { size ->
                    if (metal == null || metal!!.width != size.width || metal!!.height != size.height) {
                        metal = brushedMetal(size.width, size.height)
                    }
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                MenuBar(model)
                // The window: brushed metal, rounded at the top.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .drawBehind { metal?.let { drawImage(it, topLeft = Offset(0f, -px(24f))) } },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TitleBar(title = "openplayer")
                        Toolbar(model)
                        // The content well: pinstripes, sunk into the metal.
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 7.dp)
                                .drawBehind {
                                    drawRect(stripes)
                                    // Sunk: a darker line along the top and left, light at the foot.
                                    drawLine(Color(0xFF6E6E6E), Offset(0f, 0f), Offset(size.width, 0f), px(1f))
                                    drawLine(Color(0xFF9A9A9A), Offset(0f, 0f), Offset(0f, size.height), px(1f))
                                    drawLine(Color(0xFF9A9A9A), Offset(size.width, 0f), Offset(size.width, size.height), px(1f))
                                    drawLine(Color(0xFFF7F7F7), Offset(0f, size.height), Offset(size.width, size.height), px(1f))
                                },
                        ) {
                            content(Modifier.fillMaxSize())
                        }
                        StatusStrip(model)
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuBar(model: FrameModel) {
        val density = LocalDensity.current
        val stripes = remember(density.density) { menuStripeBrush(density.density) }
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        val day = remember(now) { SimpleDateFormat("EEE", Locale.getDefault()).format(now) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .drawBehind {
                    drawRect(stripes)
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)))
                    drawLine(Color(0xFFA0A0A0), Offset(0f, size.height - px(0.5f)), Offset(size.width, size.height - px(0.5f)), px(1f))
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A gel drop in place of the Apple: this is openplayer's own menu.
            Canvas(Modifier.size(14.dp)) { drawGelCircle(center, size.minDimension / 2f, Gels.blue, shadow = false) }
            Spacer(Modifier.width(8.dp))
            MenuTitle("openplayer", bold = true, tag = "frame:menu", items = listOf(
                ContextMenuItem("Preferences…", onClick = model.onOpenSettings),
            ))
            MenuTitle("Controls", items = listOf(
                ContextMenuItem(if (model.playback.isPlaying) "Pause" else "Play", onClick = model.onPlayPause),
                ContextMenuItem("Next Song", onClick = model.onNext),
                ContextMenuItem("Previous Song", onClick = model.onPrevious),
            ))
            MenuTitle("Window", items = model.tabs.mapIndexed { i, tab ->
                ContextMenuItem(windowName(tab), selected = i == model.selectedTab, onClick = { model.onSelectTab(i) })
            })
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.Black, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "$day ${time.time}${time.amPm?.let { " $it" }.orEmpty()}",
                style = menuText(false),
                color = Color.Black,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    private fun windowName(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    private fun menuText(bold: Boolean) = TextStyle(
        fontFamily = SkinFonts.aqua,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 13.5.sp,
    )

    @Composable
    private fun MenuTitle(title: String, items: List<ContextMenuItem>, bold: Boolean = false, tag: String? = null) {
        var open by remember { mutableStateOf(false) }
        Box {
            Text(
                title,
                style = menuText(bold),
                color = if (open) Color.White else Color.Black,
                modifier = Modifier
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                    .then(if (open) Modifier.background(AquaColors.selection) else Modifier)
                    .skinTap { open = true }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            if (open) {
                Popup(
                    offset = IntOffset(0, with(LocalDensity.current) { 21.dp.roundToPx() }),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val haptics = LocalHaptics.current
                    MenuPanel(items, dropdown = true, onPick = { item ->
                        open = false
                        haptics?.click()
                        item.onClick()
                    })
                }
            }
        }
    }

    @Composable
    private fun TitleBar(title: String) {
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Gels.red, Gels.yellow, Gels.green).forEach { gel ->
                    Canvas(Modifier.size(14.dp)) { drawGelCircle(center, size.minDimension / 2f - px(0.5f), gel) }
                }
            }
            Text(
                title,
                style = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 13.5.sp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    @Composable
    private fun Toolbar(model: FrameModel) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Segmented(
                items = model.tabs.map { windowName(it) },
                selectedIndex = model.selectedTab,
                onSelect = model.onSelectTab,
                tags = model.tabs.map { "tab:$it" },
                modifier = Modifier.width(300.dp).height(30.dp),
            )
        }
    }

    @Composable
    private fun StatusStrip(model: FrameModel) {
        Box(Modifier.fillMaxWidth().height(26.dp)) {
            Text(
                text = model.playback.playlistLabel.ifBlank { "openplayer" },
                style = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 11.sp),
                color = Color(0xFF2E2E2E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
            )
            // The resize grip, scored into the corner.
            Canvas(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(12.dp)) {
                for (i in 1..3) {
                    val d = size.width * i / 3.4f
                    drawLine(Color(0xFF7A7A7A), Offset(size.width - d, size.height), Offset(size.width, size.height - d), px(1f))
                    drawLine(Color.White, Offset(size.width - d + px(1f), size.height), Offset(size.width, size.height - d + px(1f)), px(1f))
                }
            }
        }
    }

    // --- segmented control ---------------------------------------------------------------------

    @Composable
    private fun Segmented(
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier,
        tags: List<String>? = null,
        icons: List<ImageVector>? = null,
    ) {
        val haptics = LocalHaptics.current
        Box(
            modifier.drawBehind {
                val h = size.height
                val r = h / 2f
                drawGel(Rect(Offset.Zero, size), r, Gels.white)
                if (selectedIndex in items.indices) {
                    val w = size.width / items.size
                    // The chosen segment's blue body, clipped to the capsule's ends...
                    clipCapsule(size) {
                        drawGelBody(Rect(w * selectedIndex, 0f, w * (selectedIndex + 1), h), Gels.blue)
                    }
                }
                // ...under one gloss across the whole control, as Aqua drew it.
                drawGelHighlight(Rect(Offset.Zero, size), r, 0.9f)
                drawRoundRect(Color(0xFF4F4F4F), Offset.Zero, size, CornerRadius(r), style = Stroke(px(1f)))
                val w = size.width / items.size
                for (i in 1 until items.size) {
                    drawLine(Color(0xFF6E6E6E), Offset(w * i, px(1f)), Offset(w * i, h - px(1f)), px(1f))
                }
            },
        ) {
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(tags?.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier)
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                            }
                            .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                                if (index != selectedIndex) haptics?.tick()
                                onSelect(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val icon = icons?.getOrNull(index)
                        if (icon != null) {
                            Icon(icon, label, tint = if (selected) Color.White else Color(0xFF262626), modifier = Modifier.size(18.dp))
                        } else {
                            Text(
                                label,
                                style = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 12.5.sp),
                                color = if (selected) Color.White else Color(0xFF1A1A1A),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun DrawScope.clipCapsule(size: Size, block: DrawScope.() -> Unit) {
        val path = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), CornerRadius(size.height / 2f)))
        }
        clipPath(path) { block() }
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
        Box(modifier.fillMaxWidth().height(height.coerceAtLeast(34.dp)), contentAlignment = Alignment.Center) {
            Segmented(items, selectedIndex, onSelect, Modifier.fillMaxWidth().height(28.dp), icons = icons)
        }
    }

    // --- buttons -------------------------------------------------------------------------------

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
            ButtonKind.Play -> PlayPill(
                playing = icon == Icons.Filled.Pause,
                pressed = pressed,
                modifier = modifier.size(width = size * 1.7f, height = size * 0.72f).then(click),
            )
            ButtonKind.Toggle -> Checkbox(
                checked = icon == Icons.Filled.Check,
                modifier = modifier.size(size).then(click),
                pressed = pressed,
            )
            ButtonKind.Confirm -> PushButton(
                label = if (icon == Icons.Filled.Add) "Add" else "OK",
                default = true,
                pressed = pressed,
                modifier = modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f).then(click),
            )
            ButtonKind.Chevron -> Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
                // A disclosure triangle; the list's own rotation turns it.
                Canvas(Modifier.size(12.dp)) {
                    val area = drawContext.size
                    val path = Path().apply {
                        moveTo(area.width * 0.1f, area.height * 0.78f)
                        lineTo(area.width * 0.9f, area.height * 0.78f)
                        lineTo(area.width * 0.5f, area.height * 0.18f)
                        close()
                    }
                    drawPath(path, Color(0xFF4A4A4A))
                }
            }
            else -> RoundButton(
                icon = icon,
                pressed = pressed,
                enabled = enabled,
                glyphAlpha = opacity,
                // Small buttons stay at Aqua's own sizes; very small ones just grow a little.
                modifier = modifier.size(size.coerceIn(26.dp, 56.dp)).then(click),
                iconSize = iconSize.coerceIn(14.dp, 26.dp),
            )
        }
    }

    @Composable
    private fun RoundButton(icon: ImageVector, pressed: Boolean, enabled: Boolean, glyphAlpha: Float, modifier: Modifier, iconSize: Dp) {
        Box(
            modifier.drawBehind {
                drawGelCircle(center, size.minDimension / 2f - px(1.5f), if (pressed) Gels.whitePressed else Gels.white)
            },
            contentAlignment = Alignment.Center,
        ) {
            EtchedIcon(icon, Color(0xFF2B2B2B).copy(alpha = (if (enabled) 1f else 0.4f) * glyphAlpha.coerceAtLeast(0.35f)), iconSize)
        }
    }

    /** A glyph with the 1-pixel white emboss Aqua gave its button art. */
    @Composable
    private fun EtchedIcon(icon: ImageVector, tint: Color, size: Dp) {
        Box {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f * tint.alpha), modifier = Modifier.size(size).offset(y = 1.dp))
            Icon(icon, null, tint = tint, modifier = Modifier.size(size))
        }
    }

    @Composable
    private fun PlayPill(playing: Boolean, pressed: Boolean, modifier: Modifier) {
        Box(
            modifier.drawBehind {
                drawGel(Rect(Offset.Zero, size).deflate(px(1.5f)), size.height / 2f, if (pressed) Gels.bluePressed else Gels.blue)
            },
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
            Box {
                Icon(icon, null, tint = Color(0xFF0D2E6E).copy(alpha = 0.55f), modifier = Modifier.size(30.dp).offset(y = (-1).dp))
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }

    @Composable
    private fun Checkbox(checked: Boolean, modifier: Modifier, pressed: Boolean = false) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(19.dp)) {
                val gel = when {
                    checked && pressed -> Gels.bluePressed
                    checked -> Gels.blue
                    pressed -> Gels.whitePressed
                    else -> Gels.white
                }
                drawGel(Rect(Offset.Zero, size).deflate(px(1f)), px(4f), gel)
                if (checked) {
                    val path = Path().apply {
                        moveTo(size.width * 0.22f, size.height * 0.52f)
                        lineTo(size.width * 0.43f, size.height * 0.74f)
                        lineTo(size.width * 0.80f, size.height * 0.22f)
                    }
                    drawPath(path, Color(0xFF0D2A66).copy(alpha = 0.5f), style = Stroke(px(2.6f)), alpha = 1f)
                    drawPath(path, Color.Black, style = Stroke(px(2.2f)))
                }
            }
        }
    }

    @Composable
    private fun PushButton(label: String, default: Boolean, pressed: Boolean, modifier: Modifier) {
        Box(
            modifier
                .heightIn(min = 30.dp)
                .widthIn(min = 72.dp)
                .drawBehind {
                    val h = px(24f)
                    val top = (size.height - h) / 2f
                    val gel = when {
                        default && pressed -> Gels.bluePressed
                        default -> Gels.blue
                        pressed -> Gels.whitePressed
                        else -> Gels.white
                    }
                    drawGel(Rect(px(1f), top, size.width - px(1f), top + h), h / 2f, gel)
                }
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 13.sp), color = Color.Black)
        }
    }

    // --- panels, fields, dividers ---------------------------------------------------------------

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier {
        val density = LocalDensity.current
        val stripes = remember(density.density) { pinstripeBrush(density.density) }
        return when (role) {
            GlassRole.Control -> modifier.drawBehind {
                drawGelCircle(center, size.minDimension / 2f - px(1.5f), Gels.white)
            }
            GlassRole.Floating -> modifier
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .drawBehind {
                    drawRect(stripes)
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, Color(0xFF8A8A8A), style = Stroke(px(1f)))
                }
            else -> modifier.clip(shape).drawBehind {
                // A group box: a faint dark wash with an etched edge.
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Color.Black.copy(alpha = 0.045f))
                drawOutline(outline, Color(0xFFA6A6A6), style = Stroke(px(1f)))
            }
        }
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier.drawBehind {
        if (focused) {
            // The focus ring: a soft blue halo just outside the field.
            drawRect(AquaColors.focus.copy(alpha = 0.55f), Offset(-px(3f), -px(3f)), Size(size.width + px(6f), size.height + px(6f)))
            drawRect(AquaColors.focus.copy(alpha = 0.35f), Offset(-px(1.5f), -px(1.5f)), Size(size.width + px(3f), size.height + px(3f)))
        }
        drawRect(Color.White)
        drawLine(Color(0xFF636363), Offset(0f, px(0.5f)), Offset(size.width, px(0.5f)), px(1f))
        drawLine(Color(0xFFA9A9A9), Offset(px(0.5f), 0f), Offset(px(0.5f), size.height), px(1f))
        drawLine(Color(0xFFA9A9A9), Offset(size.width - px(0.5f), 0f), Offset(size.width - px(0.5f), size.height), px(1f))
        drawLine(Color(0xFFC9C9C9), Offset(0f, size.height - px(0.5f)), Offset(size.width, size.height - px(0.5f)), px(1f))
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Canvas(modifier.fillMaxWidth().height(2.dp)) {
            drawLine(Color(0xFFA8A8A8), Offset(0f, px(0.5f)), Offset(size.width, px(0.5f)), px(1f))
            drawLine(Color.White, Offset(0f, px(1.5f)), Offset(size.width, px(1.5f)), px(1f))
        }
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(11.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            val track = Rect(0f, 0f, size.width, size.height)
            drawRect(
                Brush.horizontalGradient(listOf(Color(0xFFBDBDBD), Color(0xFFEFEFEF), Color(0xFFF7F7F7), Color(0xFFD2D2D2))),
                track.topLeft,
                track.size,
            )
            drawLine(Color(0xFF9C9C9C), Offset(0f, 0f), Offset(0f, size.height), px(1f))
            val thumbH = (size.height * visible / total).coerceAtLeast(px(28f))
            val fraction = (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            val top = (size.height - thumbH) * fraction
            drawGel(Rect(px(1.5f), top + px(1f), size.width - px(1f), top + thumbH - px(1f)), size.width / 2f, Gels.blue, shadow = false)
        }
    }

    // --- sliders and toggles --------------------------------------------------------------------

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 26.dp
        override val thumbWidth: Dp = 20.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val thumbR = thumbWidth.toPx() / 2f
            val cy = size.height / 2f - if (steps in 1..12) px(3f) else 0f
            val trackH = px(5f)
            drawTrough(Rect(thumbR - px(3f), cy - trackH / 2f, size.width - thumbR + px(3f), cy + trackH / 2f), trackH / 2f)
            if (steps in 1..12) {
                for (i in 0..steps + 1) {
                    val x = thumbR + (size.width - thumbR * 2f) * i / (steps + 1)
                    drawLine(Color(0xFF6A6A6A), Offset(x, cy + px(9f)), Offset(x, cy + px(14f)), px(1f))
                }
            }
            val x = thumbR + (size.width - thumbR * 2f) * fraction
            val gel = if (pressed) Gels.bluePressed else if (enabled) Gels.blue else Gels.white
            drawGelCircle(Offset(x, cy), thumbR - px(1f), gel)
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
                .semantics { role = Role.Checkbox; this.selected = checked }
                .skinClick(interaction, role = Role.Checkbox) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, Modifier.size(24.dp), pressed)
            Spacer(Modifier.width(8.dp))
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = Color.Black, modifier = Modifier.weight(1f))
        }
    }

    // --- menus and alerts -----------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) = MenuPanel(items, onPick, dropdown = false)

    /**
     * A menu: translucent white with a soft shadow. Pulled down from the menu bar it hangs square
     * from the bar; as a contextual menu it's rounded all round.
     */
    @Composable
    private fun MenuPanel(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit, dropdown: Boolean) {
        val shape = if (dropdown) {
            androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
        } else {
            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        }
        Column(
            Modifier
                .padding(bottom = 10.dp, start = 6.dp, end = 6.dp)
                .shadow(8.dp, shape)
                .background(Color.White.copy(alpha = 0.96f), shape)
                .border(1.dp, Color(0xFFB4B4B4).copy(alpha = 0.7f), shape)
                .width(IntrinsicSize.Max)
                .widthIn(min = 180.dp)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (pressed) Modifier.background(AquaColors.selection) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(start = 6.dp, end = 22.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (item.selected) "\u2713" else "",
                        style = menuText(false),
                        color = if (pressed) Color.White else Color.Black,
                        modifier = Modifier.width(16.dp),
                    )
                    Text(item.label, style = menuText(false), color = if (pressed) Color.White else Color.Black, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        Column(
            modifier
                .fillMaxWidth()
                .then(panel(Modifier, shape, GlassRole.Floating, 1f))
                .padding(16.dp),
        ) {
            Row {
                // The application icon, as every alert carried.
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawGelCircle(center, size.minDimension / 2f - px(2f), Gels.blue) }
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = textStyle(16, true, LocalDensity.current), color = Color.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = textStyle(14, false, LocalDensity.current), color = Color.Black)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val cancel = remember { MutableInteractionSource() }
                val ok = remember { MutableInteractionSource() }
                val cancelPressed by pressedState(cancel)
                val okPressed by pressedState(ok)
                PushButton("Cancel", default = false, pressed = cancelPressed, modifier = Modifier.testTag("dialog:dismiss").skinClick(cancel) { onDismiss() })
                PushButton("OK", default = true, pressed = okPressed, modifier = Modifier.skinClick(ok) { onConfirm() })
            }
        }
    }

    // --- lists ----------------------------------------------------------------------------------

    @Composable
    private fun ColumnHeader(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
        Row(
            modifier
                .fillMaxWidth()
                .height(22.dp)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFE4E4E4), Color(0xFFD6D6D6))))
                    drawLine(Color(0xFF8E8E8E), Offset(0f, size.height - px(0.5f)), Offset(size.width, size.height - px(0.5f)), px(1f))
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }

    private val headerText = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 11.5.sp)
    private val rowText = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 13.5.sp)
    private val rowTextBold = rowText.copy(fontWeight = FontWeight.Bold)

    /** A white list well with a one-pixel frame, as Aqua's tables and source lists sat in. */
    private fun Modifier.well(): Modifier = this
        .background(Color.White)
        .drawBehind {
            drawRect(AquaColors.wellBorder, style = Stroke(px(1f)))
        }
        .padding(1.dp)

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Column(modifier.well()) {
            ColumnHeader {
                Spacer(Modifier.width(20.dp))
                Text("Song Name", style = headerText, color = Color.Black, modifier = Modifier.weight(1f))
                Text("Artist", style = headerText, color = Color.Black, modifier = Modifier.width(117.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .background(if (model.index % 2 == 1) AquaColors.stripe else Color.White)
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(20.dp)) {
                    if (model.isCurrent) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color(0xFF2B2B2B), modifier = Modifier.size(14.dp))
                }
                MarqueeText(
                    text = model.title,
                    style = if (model.isCurrent) rowTextBold else rowText,
                    color = Color.Black,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    model.artist,
                    style = rowText,
                    color = Color(0xFF262626),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp),
                )
            }
            model.menu()
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        // iTunes' Source list.
        Column(modifier.well()) {
            ColumnHeader { Text("Source", style = headerText, color = Color.Black) }
            if (emptyMessage != null) {
                Text(emptyMessage, style = rowText, color = Color(0xFF6A6A6A), modifier = Modifier.padding(12.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(entries, key = { _, e -> e.key }) { _, entry ->
                    val selected = entry.emphasized
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("playlist:${entry.title}")
                            .background(if (selected) AquaColors.selection else Color.Transparent)
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SourceIcon(entry.kind)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                entry.title,
                                style = rowText,
                                color = if (selected) Color.White else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                entry.subtitle.substringBefore(" ·"),
                                style = headerText,
                                color = if (selected) Color.White.copy(alpha = 0.85f) else Color(0xFF7A7A7A),
                            )
                        }
                        entry.menu()
                    }
                }
            }
        }
    }

    @Composable
    private fun SourceIcon(kind: CollectionEntry.Kind) {
        val (icon, tint) = when (kind) {
            CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite to Color(0xFFD9455F)
            CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label to Color(0xFF8A5BD0)
            CollectionEntry.Kind.Playlist -> Icons.AutoMirrored.Filled.QueueMusic to Color(0xFF3F74C8)
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }

    // --- the iTunes status display ----------------------------------------------------------------

    @Composable
    private fun StatusDisplay(
        title: String,
        subtitle: String,
        positionMs: Long,
        durationMs: Long,
        onSeek: ((Float) -> Unit)?,
        modifier: Modifier,
        onTitleHold: (() -> Unit)? = null,
        onTitleTap: (() -> Unit)? = null,
        compact: Boolean = false,
    ) {
        Column(
            modifier
                .drawBehind {
                    val r = CornerRadius(px(8f))
                    drawRoundRect(Brush.verticalGradient(listOf(AquaColors.lcdTop, AquaColors.lcdBottom)), cornerRadius = r)
                    // Sunk into the metal: a shadow along the top edge inside.
                    drawRoundRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.12f to Color.Transparent),
                        cornerRadius = r,
                    )
                    drawRoundRect(AquaColors.lcdBorder, cornerRadius = r, style = Stroke(px(1f)))
                }
                .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val lcd = TextStyle(fontFamily = SkinFonts.aqua, fontSize = if (compact) 12.5.sp else 14.sp, textAlign = TextAlign.Center)
            MarqueeText(
                text = title,
                style = lcd.copy(fontWeight = FontWeight.Bold),
                color = AquaColors.lcdText,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            onTitleHold != null -> Modifier.onTapOrHold(onHold = onTitleHold, holdLabel = "Edit tags")
                            onTitleTap != null -> Modifier.skinTap(onClick = onTitleTap)
                            else -> Modifier
                        },
                    ),
            )
            if (subtitle.isNotEmpty()) {
                MarqueeText(text = subtitle, style = lcd.copy(fontSize = lcd.fontSize * 0.9f), color = AquaColors.lcdText.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth())
            }
            if (durationMs > 0) {
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                LcdProgress(positionMs, durationMs, onSeek)
            }
        }
    }

    /** The display's progress bar: an outlined channel with a diamond at the playhead. */
    @Composable
    private fun LcdProgress(positionMs: Long, durationMs: Long, onSeek: ((Float) -> Unit)?) {
        var drag by remember { mutableStateOf<Float?>(null) }
        val currentOnSeek by rememberUpdatedState(onSeek)
        val fraction = drag ?: (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val small = TextStyle(fontFamily = SkinFonts.aqua, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDurationMs((fraction * durationMs).toLong()), style = small, color = AquaColors.lcdText, modifier = Modifier.width(40.dp))
            var widthPx by remember { mutableFloatStateOf(1f) }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(22.dp)
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .then(
                        if (onSeek == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    drag = (down.position.x / widthPx).coerceIn(0f, 1f)
                                    horizontalDrag(down.id) { change ->
                                        change.consume()
                                        drag = (change.position.x / widthPx).coerceIn(0f, 1f)
                                    }
                                    drag?.let { currentOnSeek?.invoke(it) }
                                    drag = null
                                }
                            }
                        },
                    ),
            ) {
                val h = px(9f)
                val top = (size.height - h) / 2f
                drawRect(AquaColors.lcdText.copy(alpha = 0.22f), Offset(0f, top), Size(size.width * fraction, h))
                drawRect(AquaColors.lcdText.copy(alpha = 0.85f), Offset(0f, top), Size(size.width, h), style = Stroke(px(1f)))
                val x = size.width * fraction
                val d = px(6f)
                val cy = size.height / 2f
                val diamond = Path().apply {
                    moveTo(x, cy - d)
                    lineTo(x + d * 0.75f, cy)
                    lineTo(x, cy + d)
                    lineTo(x - d * 0.75f, cy)
                    close()
                }
                drawPath(diamond, AquaColors.lcdText)
            }
            Text(
                "-" + formatDurationMs(((1f - fraction) * durationMs).toLong()),
                style = small.copy(textAlign = TextAlign.End),
                color = AquaColors.lcdText,
                modifier = Modifier.width(44.dp),
            )
        }
    }

    // --- Now Playing ------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // The output confirmation (over where a header would be), when there is one.
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel ->
                panel(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .then(panel(Modifier, androidx.compose.foundation.shape.RoundedCornerShape(8.dp), GlassRole.Container, 1f))
                        .padding(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            StatusDisplay(
                title = song?.displayName ?: if (model.playbackRestored) "openplayer" else "",
                subtitle = when {
                    song != null -> song.artist
                    model.playbackRestored -> "Choose a playlist to start"
                    else -> ""
                },
                positionMs = model.playback.positionMs,
                durationMs = if (song != null) model.playback.durationMs else 0L,
                onSeek = model.onSeek,
                onTitleHold = if (song != null) model.onHoldTitle else null,
                onTitleTap = if (song == null) model.onOpenLibrary else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            // The transport: round gel either side of the blue Play pill.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 50.dp, 26.dp, 1f, true, ButtonKind.Previous)
                Spacer(Modifier.width(14.dp))
                IconButton(
                    if (model.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (model.playback.isPlaying) "Pause" else "Play",
                    model.onPlayPause,
                    Modifier,
                    76.dp,
                    30.dp,
                    1f,
                    true,
                    ButtonKind.Play,
                )
                Spacer(Modifier.width(14.dp))
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 50.dp, 26.dp, 1f, true, ButtonKind.Next)
            }
            Spacer(Modifier.height(16.dp))
            // iTunes' bottom-left buttons: shuffle and the rest, small and square-ish.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallToggle(Icons.Filled.Shuffle, "Shuffle", model.shuffleOn, model.onToggleShuffle)
                model.isFavourite?.let { fav ->
                    Spacer(Modifier.width(8.dp))
                    SmallToggle(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Unfavourite" else "Favourite", fav, model.onToggleFavourite)
                }
                if (model.outputSwitchAvailable) {
                    Spacer(Modifier.width(8.dp))
                    SmallToggle(
                        if (model.outputDevice == io.github.parthibdebnath.openplayer.audio.OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker,
                        "Output device",
                        false,
                        model.onToggleOutput,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SmallToggle(Icons.Filled.Timer, "Sleep timer", model.sleepTimerActive, model.onOpenSleepTimer)
                Spacer(Modifier.weight(1f))
                val notes = listOfNotNull(
                    "Shuffle locked".takeIf { model.shuffleLocked },
                    "FX on".takeIf { model.fxOn },
                    "Timer on".takeIf { model.sleepTimerActive },
                )
                Text(notes.joinToString(" · "), style = headerText, color = Color(0xFF333333))
            }
            model.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = headerText, color = Color(0xFFB0281A))
            }
            Spacer(Modifier.height(14.dp))
            UpNext(model, Modifier.weight(1f).fillMaxWidth().padding(bottom = 10.dp))
        }
    }

    @Composable
    private fun SmallToggle(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val haptics = LocalHaptics.current
        Box(
            Modifier
                .size(width = 40.dp, height = 30.dp)
                .semantics { contentDescription = description; selected = on }
                .skinClick(interaction) {
                    haptics?.click()
                    onClick()
                }
                .drawBehind {
                    drawGel(Rect(Offset.Zero, size).deflate(px(1.5f)), px(6f), if (on) (if (pressed) Gels.bluePressed else Gels.blue) else if (pressed) Gels.whitePressed else Gels.white)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) Color.White else Color(0xFF2B2B2B), modifier = Modifier.size(17.dp))
        }
    }

    @Composable
    private fun UpNext(model: NowPlayingModel, modifier: Modifier) {
        val upcoming = model.playback.upcoming.take(5)
        Column(modifier.well()) {
            ColumnHeader {
                Text("Up Next", style = headerText, color = Color.Black, modifier = Modifier.weight(1f))
                Text("Time", style = headerText, color = Color.Black)
                Spacer(Modifier.width(34.dp))
            }
            if (upcoming.isEmpty()) {
                Text(
                    if (model.hasSong) "Nothing else is queued." else "",
                    style = rowText,
                    color = Color(0xFF7A7A7A),
                    modifier = Modifier.padding(10.dp),
                )
            }
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(upcoming, key = { i, s -> "${s.id}-$i" }) { index, s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(if (index % 2 == 1) AquaColors.stripe else Color.White)
                                .onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags")
                                .padding(start = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", style = headerText, color = Color(0xFF7A7A7A), modifier = Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.displayName, style = rowText.copy(fontSize = 13.sp), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, style = headerText.copy(fontSize = 10.5.sp), color = Color(0xFF5A5A5A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatDurationMs(s.durationMs), style = headerText, color = Color(0xFF333333))
                            Spacer(Modifier.width(6.dp))
                            IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(index) }, Modifier, 28.dp, 14.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                }
            }
        }
    }

    // --- the mini player ----------------------------------------------------------------------------

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        Column(modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Divider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (model.onOpenSettings != null) {
                    Box(Modifier.testTag("nav:settings")) {
                        IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 34.dp, 18.dp, 1f, true, ButtonKind.Plain)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 36.dp, 20.dp, 1f, true, ButtonKind.Previous)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (model.isPlaying) "Pause" else "Play",
                    model.onPlayPause,
                    Modifier,
                    52.dp,
                    24.dp,
                    1f,
                    true,
                    ButtonKind.Play,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 36.dp, 20.dp, 1f, true, ButtonKind.Next)
                Spacer(Modifier.width(8.dp))
                val song = model.song
                StatusDisplay(
                    title = song?.displayName ?: if (model.playbackRestored) "openplayer" else "",
                    subtitle = song?.artist.orEmpty(),
                    positionMs = model.positionMs,
                    durationMs = if (song != null) model.durationMs else 0L,
                    onSeek = { f -> model.onSeek((f * model.durationMs).toLong()) },
                    onTitleHold = if (song != null && model.onHoldTitle != null) ({ model.onHoldTitle.invoke(song) }) else null,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

