package io.github.parthibdebnath.openplayer.ui.skin.channels

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.parthibdebnath.openplayer.ui.skin.SeekBar
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
import java.text.SimpleDateFormat
import java.util.Locale

private object Wii {
    val blue = Color(0xFF34BEED)
    val text = Color(0xFF5A5A5A)
    val textSoft = Color(0xFF9A9A9A)
    val clock = Color(0xFFA9A9A9)
    val frame = Color(0xFFCFCFCF)
    val empty = Color(0xFFE4E4E4)
}

/** Channel banners take one of these soft tints, chosen by the playlist. */
private val Tints = listOf(
    Color(0xFF7FC8F0), Color(0xFF8ED48A), Color(0xFFF3B36B), Color(0xFFB79BE6),
    Color(0xFFF08FAE), Color(0xFF6FD4C8), Color(0xFFF2D35E), Color(0xFF9DB4F2),
)

/**
 * The Wii Menu: a white page of rounded channels, a grey dock along the bottom with the clock and
 * the two round buttons, soft shadows, and Wii blue wherever something is chosen. Playlists are
 * channels; the playing song is a channel preview, with Wii Menu and Start buttons.
 */
object ChannelsSkin : Skin() {
    override val theme = UiTheme.CHANNELS

    /** The menu's white above; the dock's below. */
    override val edgeTop = Color(0xFFF8F8F8)
    override val edgeBottom = Color(0xFFF2F2F2)
    override val colors = AppColors(background = Color(0xFFF7F7F7), onBackground = Wii.text)
    override val showsHeader = false
    override val gutter: Dp = 16.dp

    private fun rodin(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.rodin, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.3f).sp)

    private fun rounded(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.rounded, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.25f).sp)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val size = when {
            sizeSp >= 20 -> 19f
            sizeSp >= 18 -> 17f
            sizeSp >= 16 -> 15f
            sizeSp >= 14 -> 13f
            else -> 11.5f
        }
        return rodin(size, if (bold) FontWeight.Bold else FontWeight.Medium)
    }

    // --- the page and the dock ---------------------------------------------------------------------

    private fun stripes(density: Float): ShaderBrush {
        val step = (2f * density).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(1, step * 2, Bitmap.Config.ARGB_8888)
        for (y in 0 until step) bitmap.setPixel(0, y, 0xFFF8F8F8.toInt())
        for (y in step until step * 2) bitmap.setPixel(0, y, 0xFFF1F1F1.toInt())
        return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        val density = LocalDensity.current
        val brush = remember(density.density) { stripes(density.density) }
        Column(Modifier.fillMaxSize().drawBehind { drawRect(brush) }) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 18.dp)) { content(Modifier.fillMaxSize()) }
            Dock(model)
        }
    }

    /** The dock: a grey band that rises in a gentle curve, the clock in the middle, a round button at each end. */
    @Composable
    private fun Dock(model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        val date = remember(now) { SimpleDateFormat("EEE M/d", Locale.getDefault()).format(now) }
        val haptics = LocalHaptics.current
        Box(Modifier.fillMaxWidth().height(138.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val rise = size.height * 0.34f
                val shape = Path().apply {
                    moveTo(0f, rise * 0.4f)
                    cubicTo(size.width * 0.25f, rise * 0.4f, size.width * 0.3f, 0f, size.width * 0.5f, 0f)
                    cubicTo(size.width * 0.7f, 0f, size.width * 0.75f, rise * 0.4f, size.width, rise * 0.4f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(shape, Brush.verticalGradient(listOf(Color(0xFFDCDCDC), Color(0xFFEDEDED), Color(0xFFE2E2E2)), startY = 0f, endY = size.height))
                drawPath(shape, Color.White, style = Stroke(2.dp.toPx()))
                // A blue line along the dock's lip, as the Wii's bottom bar had.
                drawPath(
                    Path().apply {
                        moveTo(0f, rise * 0.4f + 4.dp.toPx())
                        cubicTo(size.width * 0.25f, rise * 0.4f + 4.dp.toPx(), size.width * 0.3f, 4.dp.toPx(), size.width * 0.5f, 4.dp.toPx())
                        cubicTo(size.width * 0.7f, 4.dp.toPx(), size.width * 0.75f, rise * 0.4f + 4.dp.toPx(), size.width, rise * 0.4f + 4.dp.toPx())
                    },
                    Wii.blue.copy(alpha = 0.55f),
                    style = Stroke(2.dp.toPx()),
                )
            }
            // The clock: Now Playing.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .testTag("tab:Play")
                    .semantics { role = Role.Tab; selected = model.selectedTab == 1; contentDescription = "Now Playing" }
                    .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                        if (model.selectedTab != 1) haptics?.tick()
                        model.onSelectTab(1)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time.time, style = rounded(46f, FontWeight.Light), color = if (model.selectedTab == 1) Wii.blue else Wii.clock)
                    time.amPm?.let { Text(" $it", style = rounded(16f), color = Wii.clock, modifier = Modifier.padding(bottom = 9.dp)) }
                }
                Text(date, style = rounded(17f), color = Wii.clock)
            }
            DockButton(Icons.Filled.MusicNote, "Library", selected = model.selectedTab == 0, modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 18.dp).testTag("tab:File")) {
                if (model.selectedTab != 0) haptics?.tick()
                model.onSelectTab(0)
            }
            DockButton(Icons.Filled.Tune, "Effects", selected = model.selectedTab == 2, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp).testTag("tab:SFX")) {
                if (model.selectedTab != 2) haptics?.tick()
                model.onSelectTab(2)
            }
        }
    }

    /** One of the dock's round buttons: white, softly domed, ringed in blue when it's where you are. */
    @Composable
    private fun DockButton(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Box(
            modifier
                .size(74.dp)
                .semantics { role = Role.Tab; this.selected = selected; contentDescription = label }
                .skinClick(interaction, role = Role.Tab, onClick = onClick)
                .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(Color.Black.copy(alpha = 0.10f), r, center + Offset(0f, 3.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEDED)), startY = 0f, endY = size.height), r)
                    drawCircle(if (selected) Wii.blue else Wii.frame, r - 1.5.dp.toPx(), style = Stroke(if (selected) 4.dp.toPx() else 2.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = if (selected) Wii.blue else Color(0xFF8C8C8C), modifier = Modifier.size(28.dp))
                Text(label, style = rodin(10.5f, FontWeight.Medium), color = if (selected) Wii.blue else Color(0xFF8C8C8C))
            }
        }
    }

    // --- controls -------------------------------------------------------------------------------------

    /** The Wii's button: a white rounded slab with a soft shadow, edged in blue when lit. */
    @Composable
    private fun WiiButton(label: String?, icon: ImageVector?, modifier: Modifier, lit: Boolean = false, big: Boolean = false, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        Row(
            modifier
                .graphicsLayer { val s = if (pressed) 0.96f else 1f; scaleX = s; scaleY = s }
                .shadow(if (pressed) 1.dp else 3.dp, RoundedCornerShape(if (big) 18.dp else 14.dp))
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F0F0))), RoundedCornerShape(if (big) 18.dp else 14.dp))
                .border(if (lit || pressed) 3.dp else 1.5.dp, if (lit || pressed) Wii.blue else Wii.frame, RoundedCornerShape(if (big) 18.dp else 14.dp))
                .skinClick(interaction, onClick = onClick)
                .padding(horizontal = if (big) 22.dp else 14.dp, vertical = if (big) 14.dp else 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, tint = if (lit) Wii.blue else Wii.text, modifier = Modifier.size(if (big) 26.dp else 20.dp))
            if (icon != null && label != null) Spacer(Modifier.width(8.dp))
            if (label != null) Text(label, style = rodin(if (big) 18f else 15f, FontWeight.Medium), color = Wii.text, maxLines = 1)
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
            ButtonKind.Toggle -> Box(modifier.then(click), contentAlignment = Alignment.Center) { OnOff(icon == Icons.Filled.Check) }
            ButtonKind.Confirm -> Box(modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)) {
                WiiButton(if (icon == Icons.Filled.Add) "Add" else "OK", null, Modifier.semantics { this.contentDescription = contentDescription }, lit = true, onClick = onClick)
            }
            else -> {
                val side = when (kind) {
                    ButtonKind.Play -> 72.dp
                    ButtonKind.Next, ButtonKind.Previous -> 54.dp
                    else -> size.coerceIn(34.dp, 48.dp)
                }
                Box(
                    modifier
                        .size(side)
                        .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f)
                        .then(click)
                        .graphicsLayer { val s = if (pressed) 0.94f else 1f; scaleX = s; scaleY = s }
                        .drawBehind {
                            val r = this.size.minDimension / 2f
                            drawCircle(Color.Black.copy(alpha = 0.10f), r, center + Offset(0f, 2.dp.toPx()))
                            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFEFEFEF))), r)
                            drawCircle(if (kind == ButtonKind.Play || pressed) Wii.blue else Wii.frame, r - 1.5.dp.toPx(), style = Stroke(if (kind == ButtonKind.Play || pressed) 3.dp.toPx() else 1.5.dp.toPx()))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (kind == ButtonKind.Play) Wii.blue else Wii.text, modifier = Modifier.size(side * 0.48f))
                }
            }
        }
    }

    /** The Wii's two-button choice: On and Off side by side, the chosen one lit. */
    @Composable
    private fun OnOff(on: Boolean) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("On" to true, "Off" to false).forEach { (label, value) ->
                val chosen = on == value
                Box(
                    Modifier
                        .width(48.dp)
                        .height(30.dp)
                        .background(if (chosen) Color(0xFFE6F7FD) else Color.White, RoundedCornerShape(10.dp))
                        .border(if (chosen) 2.5.dp else 1.dp, if (chosen) Wii.blue else Wii.frame, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = rodin(13f, FontWeight.Medium), color = if (chosen) Wii.blue else Wii.textSoft)
                }
            }
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Color.White)
            drawOutline(outline, Wii.frame, style = Stroke(1.5.dp.toPx()))
        }
        else -> modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.5.dp, Wii.frame, RoundedCornerShape(16.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier
        .background(Color.White, RoundedCornerShape(10.dp))
        .border(if (focused) 2.5.dp else 1.5.dp, if (focused) Wii.blue else Wii.frame, RoundedCornerShape(10.dp))

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E2E2)))
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(6.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            drawRoundRect(Color(0xFFE6E6E6), cornerRadius = CornerRadius(size.width / 2f))
            val h = (size.height * visible / total).coerceAtLeast(24.dp.toPx())
            val top = (size.height - h) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawRoundRect(Wii.blue.copy(alpha = 0.8f), Offset(0f, top), Size(size.width, h), CornerRadius(size.width / 2f))
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 34.dp
        override val thumbWidth: Dp = 26.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val h = 10.dp.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(Color(0xFFE3E3E3), Offset(tw / 2f, top), Size(size.width - tw, h), CornerRadius(h / 2f))
            val x = tw / 2f + (size.width - tw) * fraction
            drawRoundRect(if (enabled) Wii.blue else Color(0xFFBDBDBD), Offset(tw / 2f, top), Size(x - tw / 2f, h), CornerRadius(h / 2f))
            val c = Offset(x, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.12f), tw / 2f, c + Offset(0f, 2.dp.toPx()))
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEDED)), startY = c.y - tw / 2f, endY = c.y + tw / 2f), tw / 2f, c)
            drawCircle(Wii.blue, tw / 2f - 1.5.dp.toPx(), c, style = Stroke(if (pressed) 3.5.dp.toPx() else 2.5.dp.toPx()))
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val haptics = LocalHaptics.current
        Row(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Switch; selected = checked }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Switch) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = Wii.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            OnOff(checked)
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
        Row(modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(if (selected) 0.dp else 2.dp, RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFE6F7FD) else Color.White, RoundedCornerShape(12.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Wii.blue else Wii.frame, RoundedCornerShape(12.dp))
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = rodin(13.5f, FontWeight.Medium), color = if (selected) Wii.blue else Wii.text, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 200.dp)
                .padding(8.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, Wii.frame, RoundedCornerShape(18.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                WiiButton(item.label + if (item.selected) "  ✓" else "", null, Modifier.fillMaxWidth(), onClick = { onPick(item) })
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Column(
            modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.5.dp, Wii.frame, RoundedCornerShape(20.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = rodin(17f, FontWeight.Bold), color = Wii.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, style = rodin(14f), color = Wii.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WiiButton("Yes", null, Modifier.width(110.dp), onClick = onConfirm)
                WiiButton("No", null, Modifier.width(110.dp).testTag("dialog:dismiss"), onClick = onDismiss)
            }
        }
    }

    // --- channels -------------------------------------------------------------------------------------

    /** A channel: a rounded frame with a banner inside it. Empty channels are plain grey. */
    @Composable
    private fun Channel(entry: CollectionEntry?, index: Int, modifier: Modifier) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val tint = Tints[index % Tints.size]
        Box(
            modifier
                .aspectRatio(1.45f)
                .graphicsLayer { val s = if (pressed) 1.04f else 1f; scaleX = s; scaleY = s }
                .shadow(if (entry == null) 0.dp else 3.dp, RoundedCornerShape(16.dp))
                .background(if (entry == null) Wii.empty else Color.White, RoundedCornerShape(16.dp))
                .border(
                    if (entry?.emphasized == true || pressed) 3.dp else 2.dp,
                    if (entry?.emphasized == true || pressed) Wii.blue else Color(0xFFD8D8D8),
                    RoundedCornerShape(16.dp),
                )
                .then(
                    if (entry != null) {
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(interaction, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() }
                    } else {
                        Modifier
                    },
                )
                .padding(6.dp),
        ) {
            if (entry == null) {
                // The empty channel's faint inner bevel.
                Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(11.dp)))
                return@Box
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.verticalGradient(listOf(Color.White, tint.copy(alpha = 0.35f)))),
            ) {
                val icon = when (entry.kind) {
                    CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite
                    CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label
                    CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote
                }
                Icon(icon, null, tint = tint, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(entry.title, style = rodin(14.5f, FontWeight.Bold), color = Wii.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle.substringBefore(" ·"), style = rodin(11f, FontWeight.Medium), color = Wii.textSoft, maxLines = 1)
                }
            }
            entry.menu()
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        // A page of channels, filled out with empty ones as the Wii Menu was.
        // Always a few empty channels after the last, and never less than a page of twelve.
        val slots = maxOf(12, ((entries.size + 4 + 1) / 2) * 2)
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = rodin(14f), color = Wii.text, modifier = Modifier.padding(4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(2.dp),
            ) {
                itemsIndexed((0 until slots).toList(), key = { i, _ -> entries.getOrNull(i)?.key ?: "empty-$i" }) { i, _ ->
                    Channel(entries.getOrNull(i), i, Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(
            modifier
                .shadow(3.dp, RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, Wii.frame, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            content = content,
        )
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .padding(vertical = 2.dp)
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .then(if (model.isCurrent) Modifier.background(Color(0xFFE6F7FD), RoundedCornerShape(10.dp)).border(2.dp, Wii.blue, RoundedCornerShape(10.dp)) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = model.title,
                    style = rodin(15f, FontWeight.Medium),
                    color = if (model.isCurrent) Wii.blue else Wii.text,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = rodin(12.5f), color = Wii.textSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    // --- the channel preview ---------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        val tint = Tints[((song?.playlistId ?: 0L) % Tints.size).toInt()]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel -> panel(Modifier.fillMaxWidth().then(panel(Modifier, RoundedCornerShape(16.dp), GlassRole.Container, 1f)).padding(14.dp)); Spacer(Modifier.height(10.dp)) }
            // The channel's banner, filling the preview.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .shadow(4.dp, RoundedCornerShape(22.dp))
                    .background(Color.White, RoundedCornerShape(22.dp))
                    .border(3.dp, Wii.blue, RoundedCornerShape(22.dp))
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color.White, tint.copy(alpha = 0.45f)))),
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = tint, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(64.dp))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(p.playlistLabel, style = rodin(13f, FontWeight.Medium), color = Wii.textSoft, maxLines = 1)
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "No song playing" else "",
                        rounded(26f, FontWeight.Bold),
                        Modifier.fillMaxWidth().then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier),
                        color = Wii.text,
                    )
                    Text(song?.artist.orEmpty(), style = rodin(15f, FontWeight.Medium), color = Wii.text, maxLines = 1)
                }
            }
            if (song != null) {
                Spacer(Modifier.height(10.dp))
                SeekBar(model.progress, model.onSeek, Modifier.fillMaxWidth().height(26.dp)) { f ->
                    with(slider) { drawSlider(f, true, 0, false) }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDurationMs(p.positionMs), style = rounded(14f), color = Wii.clock)
                    Spacer(Modifier.weight(1f))
                    Text(formatDurationMs(p.durationMs), style = rounded(14f), color = Wii.clock)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 54.dp, 26.dp, 1f, true, ButtonKind.Previous)
                IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 72.dp, 36.dp, 1f, true, ButtonKind.Play)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 54.dp, 26.dp, 1f, true, ButtonKind.Next)
            }
            Spacer(Modifier.height(14.dp))
            // The preview's two big buttons.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WiiButton("Wii Menu", null, Modifier.weight(1f), big = true, onClick = model.onOpenLibrary)
                WiiButton(if (p.isPlaying) "Pause" else "Start", null, Modifier.weight(1f), lit = true, big = true, onClick = model.onPlayPause)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WiiButton("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f), lit = model.shuffleOn, onClick = model.onToggleShuffle)
                model.isFavourite?.let { WiiButton("Favourite", if (it) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, Modifier.weight(1f), lit = it, onClick = model.onToggleFavourite) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WiiButton("Sleep Timer", Icons.Filled.Timer, Modifier.weight(1f), lit = model.sleepTimerActive, onClick = model.onOpenSleepTimer)
                if (model.outputSwitchAvailable) {
                    WiiButton(if (model.outputDevice == OutputDevice.BLUETOOTH) "Bluetooth" else "Speaker", if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker, Modifier.weight(1f), onClick = model.onToggleOutput)
                }
            }
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, model.message)
            if (notes.isNotEmpty()) Text(notes.joinToString("  ·  "), style = rodin(13f), color = Wii.textSoft, modifier = Modifier.padding(top = 8.dp))
            if (p.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(18.dp))
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .border(1.5.dp, Wii.frame, RoundedCornerShape(18.dp))
                        .padding(12.dp),
                ) {
                    Text("Up Next", style = rodin(15f, FontWeight.Bold), color = Wii.text)
                    p.upcoming.take(5).forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.displayName, style = rodin(14.5f, FontWeight.Medium), color = Wii.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, style = rodin(11.5f), color = Wii.textSoft, maxLines = 1)
                            }
                            IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 36.dp, 18.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .shadow(3.dp, RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, Wii.frame, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "No song playing" else "",
                        rodin(15f, FontWeight.Bold),
                        Modifier.fillMaxWidth().then(if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier),
                        color = Wii.text,
                    )
                    Text(song?.artist.orEmpty(), style = rodin(12f), color = Wii.textSoft, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (model.onOpenSettings != null) {
                        Box(Modifier.testTag("nav:settings")) {
                            IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 38.dp, 18.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 38.dp, 18.dp, 1f, true, ButtonKind.Plain)
                    IconButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 46.dp, 22.dp, 1f, true, ButtonKind.Plain)
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 38.dp, 18.dp, 1f, true, ButtonKind.Plain)
                }
            }
            if (song != null && model.durationMs > 0) {
                SeekBar(model.positionMs.toFloat() / model.durationMs, { model.onSeek((it * model.durationMs).toLong()) }, Modifier.fillMaxWidth().height(16.dp)) { f ->
                    val h = 6.dp.toPx()
                    val top = (size.height - h) / 2f
                    drawRoundRect(Color(0xFFE3E3E3), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
                    drawRoundRect(Wii.blue, Offset(0f, top), Size(size.width * f, h), CornerRadius(h / 2f))
                }
            }
        }
    }
}
