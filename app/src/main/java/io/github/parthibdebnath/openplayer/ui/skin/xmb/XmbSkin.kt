package io.github.parthibdebnath.openplayer.ui.skin.xmb

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
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
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoice
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoices
import io.github.parthibdebnath.openplayer.ui.skin.SkinOption
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val Glow = Shadow(Color.White.copy(alpha = 0.6f), Offset.Zero, 14f)
private val Dim = Color.White.copy(alpha = 0.62f)

/** Where the cross sits: the focused category's centre, from the left edge. */
private val FocusX = 104.dp

/** The width an item's icon takes, centred under the cross. */
private val ItemSlot = 58.dp

/**
 * The XrossMediaBar of the PSP and PS3: a deep coloured backdrop with a ribbon of light flowing
 * across it, the categories as a row of white icons, and the chosen category's contents in a
 * column below its icon. Everything is white, soft-edged and glowing.
 */
class XmbSkin private constructor(
    /** The colour picked in the theme settings; null is "Original", which follows the month. */
    private val fixedColour: Color?,
) : Skin() {
    override val theme = UiTheme.XMB
    override val colors = AppColors(background = shade(fixedColour ?: colourForMonth(Calendar.getInstance().get(Calendar.MONTH)), 0.62f), onBackground = Color.White)
    override val showsHeader = false
    override val gutter: Dp = 22.dp

    companion object {
        /**
         * The twelve colours the "Original" setting moves through, January to December. Sony
         * never published the values (the PS3's own theme files say theirs are approximate), so
         * these are matched by eye to the consoles: the PS3 hardware's January silver and
         * February gold, and the month-by-month colours PSP owners recorded.
         */
        private val Months = listOf(
            "Silver" to Color(0xFF8E9299),
            "Gold" to Color(0xFFAF9718),
            "Lime" to Color(0xFF6C9E25),
            "Pink" to Color(0xFFBC5A8F),
            "Green" to Color(0xFF2D8A3C),
            "Purple" to Color(0xFF8858BE),
            "Teal" to Color(0xFF1C93A0),
            "Blue" to Color(0xFF1656B8),
            "Violet" to Color(0xFF5A3DB0),
            "Amber" to Color(0xFFB98321),
            "Brown" to Color(0xFF85582D),
            "Red" to Color(0xFFB02626),
        )
        private val MonthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

        private fun colourForMonth(month: Int): Color = Months[month.coerceIn(0, 11)].second

        private val Colour = SkinOption(
            "colour",
            "Colour",
            listOf(SkinChoice("original", "Original")) +
                Months.mapIndexed { i, (name, colour) -> SkinChoice("${i + 1}", "${MonthNames[i]} \u00B7 $name", colour) },
            SkinOption.Style.List,
            note = "Original changes with the month, as the PS3 and PSP did.",
        )
        val Options = listOf(Colour)

        fun create(choices: SkinChoices) = XmbSkin(choices.of(Colour).swatch)

        /** The colour darkened (below 1) or lightened (above 1), keeping its hue. */
        private fun shade(colour: Color, factor: Float): Color = Color(
            red = (colour.red * factor).coerceIn(0f, 1f),
            green = (colour.green * factor).coerceIn(0f, 1f),
            blue = (colour.blue * factor).coerceIn(0f, 1f),
        )
    }

    /** The backdrop's own darkest shades, so the bands read as more of it. */
    @Composable
    override fun EdgeBand(top: Boolean, modifier: Modifier) {
        val base = baseColour()
        Box(modifier.background(shade(base, if (top) 0.24f else 0.5f)))
    }

    /** The backdrop's colour now: the picked one, or this month's. */
    @Composable
    private fun baseColour(): Color {
        if (fixedColour != null) return fixedColour
        val now = rememberCurrentTime()
        val month = remember(now) { Calendar.getInstance().apply { time = now }.get(Calendar.MONTH) }
        return colourForMonth(month)
    }

    private fun rodin(size: Float, weight: FontWeight = FontWeight.Normal, glow: Boolean = false) = TextStyle(
        fontFamily = SkinFonts.rodin,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        shadow = if (glow) Glow else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.5f), 3f),
    )

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val size = when {
            sizeSp >= 20 -> 19f
            sizeSp >= 18 -> 17f
            sizeSp >= 16 -> 15f
            sizeSp >= 14 -> 13f
            else -> 11.5f
        }
        return rodin(size, if (bold) FontWeight.Bold else FontWeight.Normal)
    }

    private val categoryIcons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlayCircleOutline, Icons.Filled.GraphicEq)

    private fun categoryName(tab: String) = when (tab) {
        "File" -> "Music"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    // --- the backdrop -------------------------------------------------------------------------------

    /** The wave: many fine threads of light, drawn as one ribbon that slowly undulates. */
    private fun DrawScope.drawWave(phase: Float) {
        val w = size.width
        val h = size.height
        val cy = h * 0.62f
        val threads = 22
        for (k in 0 until threads) {
            val t = k / (threads - 1f)
            val path = Path()
            var x = 0f
            val step = w / 48f
            while (x <= w + step) {
                val u = x / w
                val y = cy +
                    sin(u * 2.2f * PI.toFloat() + phase * 2f * PI.toFloat() + t * 1.6f) * h * (0.05f + 0.035f * t) +
                    sin(u * 4.1f * PI.toFloat() - phase * 2f * PI.toFloat() * 0.6f + t * 0.8f) * h * 0.022f +
                    (t - 0.5f) * h * 0.06f * sin(u * PI.toFloat())
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            val edge = 1f - kotlin.math.abs(t - 0.5f) * 2f
            drawPath(path, Color.White.copy(alpha = 0.05f + 0.16f * edge * edge), style = Stroke((0.8f + 1.4f * edge) * density, cap = StrokeCap.Round))
        }
        // The broad glow the threads swim in.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.52f to Color.White.copy(alpha = 0.0f),
                0.62f to Color.White.copy(alpha = 0.07f),
                0.72f to Color.White.copy(alpha = 0.0f),
                1f to Color.Transparent,
            ),
        )
    }

    @Composable
    private fun Backdrop(modifier: Modifier) {
        val time = rememberInfiniteTransition(label = "wave")
        val phase by time.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
        val base = baseColour()
        val gradient = remember(base) { Brush.verticalGradient(listOf(shade(base, 0.24f), shade(base, 0.7f), base, shade(base, 0.5f))) }
        val haze = remember(base) { shade(base, 1.35f).copy(alpha = 0.2f) }
        Canvas(modifier) {
            drawRect(gradient)
            drawRect(Brush.radialGradient(listOf(haze, Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.25f), radius = size.width))
            drawWave(phase)
        }
    }

    // --- frame -----------------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Backdrop(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                StatusArea(model)
                CrossBar(model)
                Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
            }
        }
    }

    @Composable
    private fun StatusArea(model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        val date = remember(now) { SimpleDateFormat("M/d", Locale.getDefault()).format(now) }
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("$date  ${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = rodin(14f), color = Color.White)
            Spacer(Modifier.width(10.dp))
            val percent = batteryPercent()
            Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
                val nub = 2.5.dp.toPx()
                drawRect(Color.White, Offset(nub, 0f), Size(size.width - nub, size.height), style = Stroke(1.2.dp.toPx()))
                drawRect(Color.White, Offset(0f, size.height * 0.3f), Size(nub, size.height * 0.4f))
                val bars = ((percent + 16) / 33).coerceIn(0, 3)
                val bw = (size.width - nub - 4.dp.toPx()) / 3f
                for (i in 0 until bars) drawRect(Color.White, Offset(size.width - 2.dp.toPx() - (i + 1) * bw, 2.dp.toPx()), Size(bw - 1.dp.toPx(), size.height - 4.dp.toPx()))
            }
        }
    }

    /**
     * The categories in a row: the focused one stays at the same spot, big, lit and named; the
     * others slide past it as the focus moves.
     */
    @Composable
    private fun CrossBar(model: FrameModel) {
        val haptics = LocalHaptics.current
        val position by animateFloatAsState(model.selectedTab.toFloat(), tween(320, easing = FastOutSlowInEasing), label = "cross")
        BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp)) {
            val spacing = 96.dp
            model.tabs.forEachIndexed { i, tab ->
                val distance = i - position
                val focus = (1f - kotlin.math.abs(distance)).coerceIn(0f, 1f)
                val size = 44.dp + 22.dp * focus
                val cx = FocusX + spacing * distance + if (distance > 0) 20.dp else 0.dp
                Box(
                    Modifier
                        .offset(x = cx - size / 2, y = 12.dp + (66.dp - size) / 2)
                        .size(size)
                        .testTag("tab:$tab")
                        .semantics { role = Role.Tab; selected = i == model.selectedTab; contentDescription = categoryName(tab) }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (i != model.selectedTab) haptics?.tick()
                            model.onSelectTab(i)
                        }
                        .drawBehind {
                            if (focus > 0f) {
                                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * focus), Color.Transparent)), this.size.minDimension * 0.9f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryIcons[i], null, tint = Color.White.copy(alpha = 0.55f + 0.45f * focus), modifier = Modifier.fillMaxSize(0.82f))
                }
                if (focus > 0.5f) {
                    Text(
                        categoryName(tab),
                        style = rodin(15f, glow = true),
                        color = Color.White.copy(alpha = (focus - 0.5f) * 2f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = cx - 60.dp, y = 84.dp).width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
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
                Text(if (icon == Icons.Filled.Check) "On" else "Off", style = rodin(15f, glow = icon == Icons.Filled.Check), color = Color.White)
            }
            ButtonKind.Confirm -> Box(
                modifier
                    .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)
                    .then(click)
                    .background(Color.White.copy(alpha = if (pressed) 0.35f else 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(if (icon == Icons.Filled.Add) "Add" else "OK", style = rodin(14f), color = Color.White)
            }
            else -> {
                val side = when (kind) {
                    ButtonKind.Play -> 64.dp
                    ButtonKind.Next, ButtonKind.Previous -> 46.dp
                    else -> size.coerceIn(28.dp, 44.dp)
                }
                Box(
                    modifier
                        .size(side)
                        .then(click)
                        .drawBehind {
                            // Lit from behind when pressed, as an XMB item glows when chosen.
                            if (pressed) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)), this.size.minDimension * 0.7f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f), modifier = Modifier.size(side * 0.62f))
                }
            }
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Color.White.copy(alpha = 0.12f))
            drawOutline(outline, Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
        }
        else -> modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier
        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
        .border(1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.45f), RoundedCornerShape(4.dp))

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent))))
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) = Unit

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 28.dp
        override val thumbWidth: Dp = 16.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val cy = size.height / 2f
            val x = tw / 2f + (size.width - tw) * fraction
            drawLine(Color.White.copy(alpha = 0.3f), Offset(tw / 2f, cy), Offset(size.width - tw / 2f, cy), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = if (enabled) 0.95f else 0.4f), Offset(tw / 2f, cy), Offset(x, cy), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), center = Offset(x, cy), radius = tw * 1.1f), tw * 1.1f, Offset(x, cy))
            drawCircle(Color.White, tw * (if (pressed) 0.42f else 0.32f), Offset(x, cy))
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
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = Color.White, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(if (checked) "On" else "Off", style = rodin(15f, glow = checked), color = if (checked) Color.White else Dim)
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
        Row(modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        }
                        .then(
                            if (selected) {
                                Modifier
                                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f))), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = rodin(14f, glow = selected), color = if (selected) Color.White else Dim, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        // The options panel: a dark translucent column with the choices in white.
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 210.dp)
                .background(Brush.horizontalGradient(listOf(shade(baseColour(), 0.3f).copy(alpha = 0.9f), shade(baseColour(), 0.17f).copy(alpha = 0.94f))), RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Text(
                    item.label + if (item.selected) "  ✓" else "",
                    style = rodin(16f, glow = pressed),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (pressed) Modifier.background(Color.White.copy(alpha = 0.2f)) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }

    @Composable
    private fun FaceButton(cross: Boolean) {
        Canvas(Modifier.size(16.dp)) {
            drawCircle(Color.White.copy(alpha = 0.9f), size.minDimension / 2f, style = Stroke(1.2.dp.toPx()))
            val s = size.minDimension * 0.22f
            if (cross) {
                drawLine(Color.White, center - Offset(s, s), center + Offset(s, s), 1.6.dp.toPx())
                drawLine(Color.White, center + Offset(s, -s), center + Offset(-s, s), 1.6.dp.toPx())
            } else {
                drawCircle(Color.White, s * 1.15f, style = Stroke(1.5.dp.toPx()))
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Column(
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(18.dp),
        ) {
            Text(title, style = rodin(17f, FontWeight.Bold), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(message, style = rodin(14f), color = Color.White)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.testTag("dialog:dismiss").skinTap(onClick = onDismiss), verticalAlignment = Alignment.CenterVertically) {
                    FaceButton(cross = false)
                    Spacer(Modifier.width(6.dp))
                    Text("Back", style = rodin(14f), color = Color.White)
                }
                Row(Modifier.skinTap(onClick = onConfirm), verticalAlignment = Alignment.CenterVertically) {
                    FaceButton(cross = true)
                    Spacer(Modifier.width(6.dp))
                    Text("Yes", style = rodin(14f, glow = true), color = Color.White)
                }
            }
        }
    }

    // --- XMB items -------------------------------------------------------------------------------------

    /** An item: an icon under the category's, and its name and detail beside it. Focused ones glow. */
    @Composable
    private fun Item(icon: ImageVector, title: String, subtitle: String?, focused: Boolean, modifier: Modifier = Modifier) {
        Row(modifier.fillMaxWidth().padding(vertical = if (focused) 8.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // The icon sits right under the focused category's, wherever the row starts.
            Spacer(Modifier.width(FocusX - ItemSlot / 2))
            Box(Modifier.width(ItemSlot), contentAlignment = Alignment.Center) {
                val s = if (focused) 58.dp else 40.dp
                Box(
                    Modifier
                        .size(s)
                        .drawBehind {
                            if (focused) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)), size.minDimension * 0.85f)
                        }
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White.copy(alpha = if (focused) 0.8f else 0.35f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(s * 0.55f))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = rodin(if (focused) 19f else 16f, glow = focused), color = if (focused) Color.White else Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && subtitle.isNotEmpty()) Text(subtitle, style = rodin(12.5f), color = Dim, maxLines = 1)
            }
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = rodin(14f), color = Color.White)
            LazyColumn(Modifier.fillMaxSize().padding(end = 6.dp)) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .offset(x = (-22).dp)
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                    ) {
                        Item(
                            when (entry.kind) {
                                CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite
                                CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label
                                CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote
                            },
                            entry.title,
                            entry.subtitle,
                            focused = entry.emphasized,
                        )
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
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = if (model.isCurrent) 1f else 0.6f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                MarqueeText(
                    text = model.title,
                    style = rodin(if (model.isCurrent) 17f else 15f, glow = model.isCurrent),
                    color = Color.White,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = rodin(12.5f), color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(modifier, content = content)
    }

    // --- the music player ------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel -> panel(Modifier.fillMaxWidth().then(panel(Modifier, RoundedCornerShape(6.dp), GlassRole.Container, 1f)).padding(14.dp)) }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.offset(x = (-22).dp)) {
                Item(Icons.Filled.MusicNote, song?.displayName ?: if (model.playbackRestored) "No music" else "", song?.artist, focused = true, modifier = Modifier.then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier.skinTap(onClick = model.onOpenLibrary)))
            }
            if (song != null) {
                Text(p.playlistLabel, style = rodin(13f), color = Dim, modifier = Modifier.padding(start = FocusX + ItemSlot / 2 + 10.dp - 22.dp))
                Spacer(Modifier.height(22.dp))
                // The PSP's player: elapsed and total, a thin bar, and the controls as glowing glyphs.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDurationMs(p.positionMs), style = rodin(15f, glow = true), color = Color.White)
                    Text(" / ${formatDurationMs(p.durationMs)}", style = rodin(15f), color = Dim)
                    Spacer(Modifier.weight(1f))
                    if (model.shuffleOn) Icon(Icons.Filled.Shuffle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                SeekBar(model.progress, model.onSeek, Modifier.fillMaxWidth().height(24.dp)) { f ->
                    val cy = size.height / 2f
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), 3.dp.toPx())
                    drawLine(Color.White, Offset(0f, cy), Offset(size.width * f, cy), 3.dp.toPx())
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width * f, cy), radius = 12.dp.toPx()), 12.dp.toPx(), Offset(size.width * f, cy))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 46.dp, 26.dp, 1f, true, ButtonKind.Previous)
                IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 64.dp, 40.dp, 1f, true, ButtonKind.Play)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 46.dp, 26.dp, 1f, true, ButtonKind.Next)
            }
            Spacer(Modifier.height(18.dp))
            // Options, as a short list in the XMB's own manner.
            Option(Icons.Filled.Shuffle, "Shuffle", if (model.shuffleOn) "On" else "Off", model.onToggleShuffle)
            model.isFavourite?.let { Option(if (it) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite", if (it) "On" else "Off", model.onToggleFavourite) }
            Option(Icons.Filled.Timer, "Sleep Timer", if (model.sleepTimerActive) "On" else "Off", model.onOpenSleepTimer)
            if (model.outputSwitchAvailable) {
                Option(if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker, "Output", if (model.outputDevice == OutputDevice.BLUETOOTH) "Bluetooth" else "Speaker", model.onToggleOutput)
            }
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, model.message)
            if (notes.isNotEmpty()) Text(notes.joinToString("   "), style = rodin(12.5f), color = Dim, modifier = Modifier.padding(top = 6.dp))
            if (p.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Up Next", style = rodin(15f, glow = true), color = Color.White)
                p.upcoming.take(5).forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Dim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.displayName, style = rodin(15f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = rodin(11.5f), color = Dim, maxLines = 1)
                        }
                        IconButton(Icons.AutoMirrored.Filled.ArrowForward, "Swap for next available", { model.onSwapUpcoming(i) }, Modifier, 32.dp, 18.dp, 1f, true, ButtonKind.Plain)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun Option(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
        val haptics = LocalHaptics.current
        Row(
            Modifier
                .fillMaxWidth()
                .skinClick(remember { MutableInteractionSource() }) {
                    haptics?.click()
                    onClick()
                }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = rodin(15f), color = Color.White, modifier = Modifier.weight(1f))
            Text(value, style = rodin(15f, glow = value == "On"), color = if (value == "Off") Dim else Color.White)
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        Column(
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "No music" else "",
                        rodin(15f, glow = true),
                        Modifier.fillMaxWidth().then(if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier),
                        color = Color.White,
                    )
                    Text(
                        if (song != null) "${formatDurationMs(model.positionMs)} / ${formatDurationMs(model.durationMs)}  ·  ${song.artist}" else "",
                        style = rodin(12f),
                        color = Dim,
                        maxLines = 1,
                    )
                }
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 36.dp, 20.dp, 1f, true, ButtonKind.Plain)
                IconButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 40.dp, 22.dp, 1f, true, ButtonKind.Plain)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 36.dp, 20.dp, 1f, true, ButtonKind.Plain)
                if (model.onOpenSettings != null) {
                    Box(Modifier.testTag("nav:settings")) {
                        IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 36.dp, 20.dp, 1f, true, ButtonKind.Plain)
                    }
                }
            }
            if (song != null && model.durationMs > 0) {
                SeekBar(model.positionMs.toFloat() / model.durationMs, { model.onSeek((it * model.durationMs).toLong()) }, Modifier.fillMaxWidth().height(14.dp)) { f ->
                    val cy = size.height / 2f
                    drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx())
                    drawLine(Color.White, Offset(0f, cy), Offset(size.width * f, cy), 2.dp.toPx())
                }
            }
        }
    }
}
