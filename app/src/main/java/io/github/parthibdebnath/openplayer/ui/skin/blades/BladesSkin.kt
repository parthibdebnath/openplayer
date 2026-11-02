package io.github.parthibdebnath.openplayer.ui.skin.blades

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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

/** One blade's colour: the 2005 dashboard's media blue, Live green and system silver. */
private class BladeColour(val base: Color, val light: Color, val dark: Color, val name: String)

private val Blades = listOf(
    BladeColour(Color(0xFF2B7BC9), Color(0xFF6FB3EE), Color(0xFF14497E), "Media"),
    BladeColour(Color(0xFF5BA62A), Color(0xFF9AD65C), Color(0xFF2F6611), "Now Playing"),
    BladeColour(Color(0xFF8A939C), Color(0xFFC8CED4), Color(0xFF555D66), "System"),
)

private val LocalBlade = staticCompositionLocalOf { Blades[1] }

private val Strip = 30.dp
private val Shade = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 4f)

/** The controller's face buttons, for the legend at the foot of each blade. */
private enum class Face(val letter: String, val colour: Color) {
    A("A", Color(0xFF5CB531)), B("B", Color(0xFFE2302B)), X("X", Color(0xFF2B6FD9)), Y("Y", Color(0xFFF2C21B)),
}

/**
 * The Xbox 360's "blades": one coloured slab per tab, the current one in front, the others
 * stacked at the edges showing only their spines and names. They slide across as you move
 * between them, and each carries a list of options with a bright highlight bar.
 */
object BladesSkin : Skin() {
    override val theme = UiTheme.BLADES
    override val colors = AppColors(background = Color(0xFF3E8A1F), onBackground = Color.White)
    override val showsHeader = false
    override val gutter: Dp = 16.dp

    private fun convection(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.metro, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.22f).sp, shadow = Shade)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val size = when {
            sizeSp >= 20 -> 20f
            sizeSp >= 18 -> 18f
            sizeSp >= 16 -> 16f
            sizeSp >= 14 -> 14f
            else -> 12.5f
        }
        return convection(size, if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }

    // --- the dashboard ----------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        val haptics = LocalHaptics.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // The ambient backdrop: near-black with a soft green-white swirl of light.
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F12), Color(0xFF1A2026), Color(0xFF0B0F12))))
                    drawCircle(Brush.radialGradient(listOf(Color(0x5560C030), Color.Transparent), center = Offset(size.width * 0.75f, size.height * 0.3f), radius = size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.75f, size.height * 0.3f))
                    drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent), center = Offset(size.width * 0.2f, size.height * 0.85f), radius = size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.2f, size.height * 0.85f))
                }
                .padding(top = 30.dp, bottom = 22.dp),
        ) {
            val count = model.tabs.size
            val bladeWidth = maxWidth - Strip * (count - 1)
            // Blades behind the current one first, then the current one on top.
            val order = model.tabs.indices.sortedBy { if (it == model.selectedTab) 1 else 0 }
            order.forEach { i ->
                val left by animateDpAsState(
                    when {
                        i <= model.selectedTab -> Strip * i
                        else -> maxWidth - Strip * (count - i)
                    },
                    tween(380, easing = FastOutSlowInEasing),
                    label = "blade$i",
                )
                val colour = Blades[i % Blades.size]
                val active = i == model.selectedTab
                Box(
                    Modifier
                        .offset(x = left)
                        .width(if (active) bladeWidth else maxOf(Strip, maxWidth - left))
                        .fillMaxHeight()
                        .shadow(if (active) 18.dp else 6.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .drawBehind { drawBlade(colour) }
                        .then(
                            if (active) {
                                Modifier
                            } else {
                                Modifier
                                    .testTag("tab:${model.tabs[i]}")
                                    .semantics { role = Role.Tab; selected = false; contentDescription = colour.name }
                                    .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                                        haptics?.tick()
                                        model.onSelectTab(i)
                                    }
                            },
                        ),
                ) {
                    if (active) {
                        CompositionLocalProvider(LocalBlade provides colour) {
                            Column(Modifier.fillMaxSize().semantics { role = Role.Tab; selected = true }.testTag("tab:${model.tabs[i]}")) {
                                BladeHeader(colour.name, model)
                                Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
                                Legend(model)
                            }
                        }
                    } else {
                        // The spine: the blade's name running down its edge.
                        Text(
                            colour.name,
                            style = convection(15f, FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 18.dp).rotatedDown(),
                        )
                    }
                }
            }
        }
    }

    /** A blade's surface: lighter at the top, a glossy band across it, deepening below. */
    private fun DrawScope.drawBlade(colour: BladeColour) {
        drawRect(Brush.verticalGradient(0f to colour.light, 0.18f to colour.base, 0.75f to lerp(colour.base, colour.dark, 0.55f), 1f to colour.dark))
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.38f), 0.09f to Color.White.copy(alpha = 0.06f), 0.1f to Color.Transparent), size = Size(size.width, size.height * 0.12f))
        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.28f), 0.03f to Color.Transparent), size = Size(size.width * 0.3f, size.height))
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, 0f), Size(1.dp.toPx(), size.height))
    }

    /** Text laid along the spine, reading downwards. */
    private fun Modifier.rotatedDown(): Modifier = layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity, minWidth = 0, maxHeight = Constraints.Infinity))
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = -(placeable.width / 2 - placeable.height / 2) + 4.dp.roundToPx(),
                y = placeable.width / 2 - placeable.height / 2,
            ) { rotationZ = 90f }
        }
    }

    @Composable
    private fun BladeHeader(name: String, model: FrameModel) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(name, style = convection(30f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = convection(14f, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.9f))
        }
    }

    @Composable
    private fun FaceButton(face: Face, size: Dp = 22.dp) {
        Box(
            Modifier.size(size).drawBehind {
                drawCircle(Color.Black.copy(alpha = 0.35f), this.size.minDimension / 2f, center + Offset(0f, 1.5f))
                drawCircle(Brush.radialGradient(listOf(lerp(face.colour, Color.White, 0.45f), face.colour, lerp(face.colour, Color.Black, 0.35f)), center = center - Offset(this.size.width * 0.15f, this.size.height * 0.2f), radius = this.size.minDimension * 0.7f), this.size.minDimension / 2f)
                drawCircle(Color.White.copy(alpha = 0.5f), this.size.minDimension / 2f, style = Stroke(1.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(face.letter, style = TextStyle(fontFamily = SkinFonts.metro, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.55f).sp), color = Color.White)
        }
    }

    @Composable
    private fun Legend(model: FrameModel) {
        val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(Face.A, "Select") {}
            LegendItem(Face.B, "Back") { back?.onBackPressed() }
            Spacer(Modifier.weight(1f))
            LegendItem(Face.Y, if (model.playback.isPlaying) "Pause" else "Play", model.onPlayPause)
        }
    }

    @Composable
    private fun LegendItem(face: Face, label: String, onClick: () -> Unit) {
        Row(Modifier.skinTap(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            FaceButton(face)
            Spacer(Modifier.width(6.dp))
            Text(label, style = convection(14f, FontWeight.SemiBold), color = Color.White)
        }
    }

    // --- controls ------------------------------------------------------------------------------------

    /** A round chrome button, as on the guide; Play takes the green of the A button. */
    @Composable
    private fun Orb(icon: ImageVector, size: Dp, pressed: Boolean, green: Boolean, alpha: Float = 1f) {
        Box(
            Modifier.size(size).alpha(alpha).drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, 2.dp.toPx()))
                val body = if (green) listOf(Color(0xFFB5F07D), Color(0xFF5CB531), Color(0xFF2C6E12)) else listOf(Color.White, Color(0xFFC9CED3), Color(0xFF6F7780))
                drawCircle(Brush.radialGradient(body, center = center - Offset(r * 0.2f, r * 0.35f), radius = r * 1.5f), r)
                if (pressed) drawCircle(Color.Black.copy(alpha = 0.2f), r)
                drawCircle(Color.White.copy(alpha = 0.7f), r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), startY = center.y - r, endY = center.y), topLeft = Offset(center.x - r * 0.65f, center.y - r * 0.9f), size = Size(r * 1.3f, r * 0.8f))
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (green) Color.White else Color(0xFF2A2F35), modifier = Modifier.size(size * 0.5f))
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
            ButtonKind.Toggle -> Box(modifier.then(click), contentAlignment = Alignment.Center) { CheckBox(icon == Icons.Filled.Check) }
            ButtonKind.Confirm -> Row(modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f).then(click).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FaceButton(Face.A)
                Spacer(Modifier.width(6.dp))
                Text(if (icon == Icons.Filled.Add) "Add" else "OK", style = convection(15f, FontWeight.SemiBold), color = Color.White)
            }
            else -> Box(modifier.then(click), contentAlignment = Alignment.Center) {
                val side = when (kind) {
                    ButtonKind.Play -> 70.dp
                    ButtonKind.Next, ButtonKind.Previous -> 50.dp
                    else -> size.coerceIn(30.dp, 46.dp)
                }
                Orb(icon, side, pressed, green = kind == ButtonKind.Play, alpha = if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f)
            }
        }
    }

    /** The dashboard's check box: a dark rounded square, a white tick when on. */
    @Composable
    private fun CheckBox(checked: Boolean) {
        Canvas(Modifier.size(26.dp)) {
            val r = CornerRadius(5.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.85f), cornerRadius = r, style = Stroke(2.dp.toPx()))
            if (checked) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.72f)
                    lineTo(size.width * 0.78f, size.height * 0.28f)
                }
                drawPath(p, Color.White, style = Stroke(3.dp.toPx()))
            }
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Color.Black.copy(alpha = 0.25f))
            drawOutline(outline, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
        }
        else -> modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier
        .background(Color.White, RoundedCornerShape(4.dp))
        .border(2.dp, if (focused) Color(0xFF5CB531) else Color.White, RoundedCornerShape(4.dp))

    override fun textFieldInk(focused: Boolean): Color = Color(0xFF1A1A1A)

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        Canvas(modifier.width(5.dp).fillMaxHeight()) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            drawRoundRect(Color.Black.copy(alpha = 0.25f), cornerRadius = CornerRadius(size.width / 2f))
            val h = (size.height * visible / total).coerceAtLeast(24.dp.toPx())
            val top = (size.height - h) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(0f, top), Size(size.width, h), CornerRadius(size.width / 2f))
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 30.dp
        override val thumbWidth: Dp = 12.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            // The volume meter's segmented bar: a row of blocks, lit up to the value.
            val blocks = 20
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (blocks - 1)) / blocks
            val h = 12.dp.toPx()
            val top = (size.height - h) / 2f
            val lit = (fraction * blocks).let { if (fraction > 0f) it.coerceAtLeast(1f) else 0f }
            for (i in 0 until blocks) {
                val on = i < lit
                drawRoundRect(
                    if (on) Color.White.copy(alpha = if (enabled) 0.95f else 0.5f) else Color.Black.copy(alpha = 0.3f),
                    Offset(i * (w + gap), top),
                    Size(w, h),
                    CornerRadius(2.dp.toPx()),
                )
            }
            if (pressed) {
                val x = fraction * size.width
                drawRoundRect(Color.White, Offset(x - 2.dp.toPx(), top - 5.dp.toPx()), Size(4.dp.toPx(), h + 10.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val haptics = LocalHaptics.current
        Row(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Checkbox; selected = checked }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Checkbox) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = Color.White, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            CheckBox(checked)
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
        val blade = LocalBlade.current
        Row(modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        }
                        .background(
                            if (selected) Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E6EA))) else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.32f))),
                            RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = convection(15f, FontWeight.SemiBold).copy(shadow = if (selected) null else Shade),
                        color = if (selected) blade.dark else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }

    // --- menus and messages ---------------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 220.dp)
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3B4249), Color(0xFF1E2328))), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                MenuItem(item.label + if (item.selected) "  ✓" else "", highlighted = pressed, modifier = Modifier.skinClick(interaction) { onPick(item) })
            }
        }
    }

    /** A menu item; the highlighted one sits on a bright bar, as the dashboard drew its focus. */
    @Composable
    private fun MenuItem(label: String, highlighted: Boolean, modifier: Modifier = Modifier, trailing: String? = null, subtitle: String? = null) {
        val blade = LocalBlade.current
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(
                    if (highlighted) {
                        Modifier
                            .shadow(4.dp, RoundedCornerShape(6.dp))
                            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6F2D8))), RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = convection(18f, FontWeight.SemiBold).copy(shadow = if (highlighted) null else Shade), color = if (highlighted) blade.dark else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = convection(13f).copy(shadow = if (highlighted) null else Shade), color = if (highlighted) blade.dark.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
            if (trailing != null) Text(trailing, style = convection(14f).copy(shadow = if (highlighted) null else Shade), color = if (highlighted) blade.dark else Color.White.copy(alpha = 0.85f))
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Column(
            modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3B4249), Color(0xFF1E2328))), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Text(title, style = convection(20f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(message, style = convection(15f), color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.skinTap(onClick = onConfirm)) { LegendLabel(Face.A, "Yes") }
                Box(Modifier.testTag("dialog:dismiss").skinTap(onClick = onDismiss)) { LegendLabel(Face.B, "Back") }
            }
        }
    }

    @Composable
    private fun LegendLabel(face: Face, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FaceButton(face, 24.dp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = convection(15f, FontWeight.SemiBold), color = Color.White)
        }
    }

    // --- lists --------------------------------------------------------------------------------------------

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = convection(15f), color = Color.White)
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(entries, key = { it.key }) { entry ->
                    Box(
                        Modifier
                            .testTag("playlist:${entry.title}")
                            .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                    ) {
                        MenuItem(entry.title, highlighted = entry.emphasized, trailing = entry.subtitle.substringBefore(" ·"))
                        entry.menu()
                    }
                }
            }
        }
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        val blade = LocalBlade.current
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .then(if (model.isCurrent) Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6F2D8))), RoundedCornerShape(6.dp)) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = model.title,
                    style = convection(17f, FontWeight.SemiBold).copy(shadow = if (model.isCurrent) null else Shade),
                    color = if (model.isCurrent) blade.dark else Color.White,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = convection(13f).copy(shadow = if (model.isCurrent) null else Shade), color = if (model.isCurrent) blade.dark else Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(modifier, content = content)
    }

    // --- now playing -----------------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel -> panel(Modifier.fillMaxWidth().then(panel(Modifier, RoundedCornerShape(8.dp), GlassRole.Container, 1f)).padding(12.dp)) }
            Spacer(Modifier.height(6.dp))
            // The card: like a gamercard, a dark rounded panel with the song on it.
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xCC1E2328), Color(0xE6101418))), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                MarqueeText(
                    song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                    convection(22f, FontWeight.SemiBold),
                    Modifier.fillMaxWidth().then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier.skinTap(onClick = model.onOpenLibrary)),
                    color = Color.White,
                )
                Text(song?.artist.orEmpty(), style = convection(15f), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                Text(p.playlistLabel, style = convection(13f), color = Color(0xFF9AD65C), maxLines = 1)
                if (song != null) {
                    Spacer(Modifier.height(10.dp))
                    SeekBar(model.progress, model.onSeek, Modifier.fillMaxWidth().height(22.dp)) { f ->
                        val h = 8.dp.toPx()
                        val top = (size.height - h) / 2f
                        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB5F07D), Color(0xFF5CB531)), startY = top, endY = top + h), Offset(0f, top), Size(size.width * f, h), CornerRadius(h / 2f))
                        drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f), style = Stroke(1.dp.toPx()))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatDurationMs(p.positionMs), style = convection(13f), color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(formatDurationMs(p.durationMs), style = convection(13f), color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 50.dp, 26.dp, 1f, true, ButtonKind.Previous)
                IconButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", model.onPlayPause, Modifier, 70.dp, 36.dp, 1f, true, ButtonKind.Play)
                IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 50.dp, 26.dp, 1f, true, ButtonKind.Next)
            }
            Spacer(Modifier.height(10.dp))
            val haptics = LocalHaptics.current
            fun tap(action: () -> Unit) = Modifier.skinClick(MutableInteractionSource()) { haptics?.click(); action() }
            MenuItem("Shuffle", highlighted = model.shuffleOn, trailing = if (model.shuffleOn) "On" else "Off", modifier = tap(model.onToggleShuffle))
            model.isFavourite?.let { MenuItem("Favourite", highlighted = false, trailing = if (it) "Yes" else "No", modifier = tap(model.onToggleFavourite)) }
            MenuItem("Sleep Timer", highlighted = false, trailing = if (model.sleepTimerActive) "On" else "Off", modifier = tap(model.onOpenSleepTimer))
            if (model.outputSwitchAvailable) {
                MenuItem("Output", highlighted = false, trailing = if (model.outputDevice == OutputDevice.BLUETOOTH) "Bluetooth" else "Speaker", modifier = tap(model.onToggleOutput))
            }
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, model.message)
            if (notes.isNotEmpty()) Text(notes.joinToString("  ·  "), style = convection(13f), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            if (p.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Up Next", style = convection(20f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(start = 12.dp))
                p.upcoming.take(5).forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().onTapOrHold(onHold = { model.onHoldSong(s) }, holdLabel = "Edit tags"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { MenuItem(s.displayName, highlighted = false, subtitle = s.artist) }
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
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(Brush.verticalGradient(listOf(Color(0xCC1E2328), Color(0xE6101418))), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                        convection(16f, FontWeight.SemiBold),
                        Modifier.fillMaxWidth().then(if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier),
                        color = Color.White,
                    )
                    Text(song?.artist.orEmpty(), style = convection(12.5f), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (model.onOpenSettings != null) {
                        Box(Modifier.testTag("nav:settings")) {
                            IconButton(Icons.Filled.Settings, "Settings", model.onOpenSettings, Modifier, 34.dp, 18.dp, 1f, true, ButtonKind.Plain)
                        }
                    }
                    IconButton(Icons.Filled.SkipPrevious, "Previous", model.onPrevious, Modifier, 34.dp, 18.dp, 1f, true, ButtonKind.Plain)
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by pressedState(interaction)
                        Box(Modifier.semantics { contentDescription = if (model.isPlaying) "Pause" else "Play" }.skinClick(interaction) { model.onPlayPause() }) {
                            Orb(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, 44.dp, pressed, green = true)
                        }
                    }
                    IconButton(Icons.Filled.SkipNext, "Next", model.onNext, Modifier, 34.dp, 18.dp, 1f, true, ButtonKind.Plain)
                }
            }
            if (song != null && model.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                SeekBar(model.positionMs.toFloat() / model.durationMs, { model.onSeek((it * model.durationMs).toLong()) }, Modifier.fillMaxWidth().height(14.dp)) { f ->
                    val h = 5.dp.toPx()
                    val top = (size.height - h) / 2f
                    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
                    drawRoundRect(Color(0xFF7CC94B), Offset(0f, top), Size(size.width * f, h), CornerRadius(h / 2f))
                }
            }
        }
    }
}
