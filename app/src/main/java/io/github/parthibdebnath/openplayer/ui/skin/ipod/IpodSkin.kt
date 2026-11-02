package io.github.parthibdebnath.openplayer.ui.skin.ipod

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassRole
import io.github.parthibdebnath.openplayer.ui.components.MarqueeText
import io.github.parthibdebnath.openplayer.ui.components.onTapOrHold
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
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.formatDurationMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** The screen's two inks: the unlit greyscale panel, and its pixels. */
private object Lcd {
    val panel = Color(0xFFC5CBC1)
    val panelDeep = Color(0xFFB5BCB1)
    val ink = Color(0xFF1C1E1B)
    val inkSoft = Color(0xFF4A4E48)
}

/**
 * What the click wheel says to whatever is on the screen. The Now Playing page takes the wheel
 * for volume and scrubbing; a list registers itself so the wheel scrolls it.
 */
@Stable
internal class WheelBus {
    var onStep: ((Int) -> Boolean)? = null
    var onCenter: (() -> Boolean)? = null
    var onCenterHold: (() -> Boolean)? = null
    /** Scroll areas on screen, newest last; the wheel scrolls the newest. */
    val lists = mutableListOf<ScrollableState>()
    val list: ScrollableState? get() = lists.lastOrNull()
}

internal val LocalWheelBus = staticCompositionLocalOf<WheelBus?> { null }

/**
 * The early iPod: the whole phone is its white face, with a greyscale LCD above and the click
 * wheel below. MENU goes back, the centre button selects, and turning the wheel moves through
 * menus, scrolls lists and - on Now Playing - sets the volume. Everything is in Chicago.
 */
object IpodSkin : Skin() {
    override val theme = UiTheme.IPOD
    override val colors = AppColors(background = Lcd.panel, onBackground = Lcd.ink)
    override val showsHeader = false
    override val showsMiniPlayer = false
    override val swipesBetweenTabs = false
    override val gutter: Dp = 7.dp

    private fun chicago(size: Float) = TextStyle(fontFamily = SkinFonts.chicago, fontSize = size.sp, lineHeight = (size * 1.28f).sp)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle = chicago(
        when {
            sizeSp >= 20 -> 17f
            sizeSp >= 18 -> 16f
            sizeSp >= 16 -> 15f
            sizeSp >= 14 -> 13f
            else -> 12f
        },
    )

    private enum class Page { Menu, OnTheGo, Tab }

    private val mainMenu = listOf("Playlists", "On-The-Go", "Effects", "Settings", "Now Playing")

    // --- the device ------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        val bus = remember { WheelBus() }
        var page by remember { mutableStateOf(Page.Tab) }
        var cursor by remember { mutableIntStateOf(0) }
        val haptics = LocalHaptics.current
        val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val currentModel by rememberUpdatedState(model)

        fun openMenu() {
            cursor = when (currentModel.selectedTab) {
                0 -> 0
                2 -> 2
                else -> 4
            }
            page = Page.Menu
        }

        fun select(index: Int) {
            when (index) {
                0 -> { page = Page.Tab; currentModel.onSelectTab(0) }
                1 -> { page = Page.OnTheGo; cursor = 0 }
                2 -> { page = Page.Tab; currentModel.onSelectTab(2) }
                3 -> { page = Page.Tab; currentModel.onOpenSettings() }
                else -> { page = Page.Tab; currentModel.onSelectTab(1) }
            }
        }

        val wheel = object : WheelInput {
            override fun step(direction: Int) {
                when (page) {
                    Page.Menu -> {
                        val next = (cursor + direction).coerceIn(0, mainMenu.lastIndex)
                        if (next != cursor) haptics?.tick()
                        cursor = next
                    }
                    Page.OnTheGo -> {
                        val next = (cursor + direction).coerceIn(0, (currentModel.playback.upcoming.size - 1).coerceAtLeast(0))
                        if (next != cursor) haptics?.tick()
                        cursor = next
                    }
                    Page.Tab -> {
                        if (bus.onStep?.invoke(direction) == true) {
                            haptics?.tick()
                            return
                        }
                        bus.list?.let { list ->
                            haptics?.tick()
                            scope.launch { list.scrollBy(direction * with(density) { 30.dp.toPx() }) }
                        }
                    }
                }
            }

            override fun menu() {
                haptics?.click()
                when (page) {
                    Page.Menu -> Unit
                    Page.OnTheGo -> openMenu()
                    Page.Tab -> if (back?.hasEnabledCallbacks() == true) back.onBackPressed() else openMenu()
                }
            }

            override fun center() {
                haptics?.click()
                when (page) {
                    Page.Menu -> select(cursor)
                    Page.OnTheGo -> currentModel.onSwapUpcoming(cursor)
                    Page.Tab -> bus.onCenter?.invoke()
                }
            }

            override fun centerHold() {
                if (page == Page.Tab && bus.onCenterHold?.invoke() == true) haptics?.click()
            }

            override fun previous() { haptics?.click(); currentModel.onPrevious() }
            override fun next() { haptics?.click(); currentModel.onNext() }
            override fun playPause() { haptics?.click(); currentModel.onPlayPause() }
        }

        val title = when (page) {
            Page.Menu -> "openplayer"
            Page.OnTheGo -> "On-The-Go"
            Page.Tab -> when (model.selectedTab) {
                0 -> "Music"
                1 -> "Now Playing"
                else -> "Effects"
            }
        }

        // The face: white polycarbonate, faintly lit from above.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF6F6F6), Color(0xFFEDEDED)))),
        ) {
            val lcdHeight = maxHeight * 0.47f
            val wheelSize = min(maxWidth.value * 0.80f, (maxHeight - lcdHeight).value * 0.78f).dp
            val topGap = maxHeight * 0.045f
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(topGap))
                // The screen, behind its dark edge.
                Box(
                    Modifier
                        .padding(horizontal = 26.dp)
                        .fillMaxWidth()
                        .height(lcdHeight)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A2C2A))
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Lcd.panel, Lcd.panelDeep)))
                        .drawBehind {
                            // A faint inner shadow along the top of the glass.
                            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.16f), 0.05f to Color.Transparent))
                        },
                ) {
                    CompositionLocalProvider(LocalWheelBus provides bus) {
                        Column(Modifier.fillMaxSize()) {
                            TitleBar(title, model.playback.isPlaying, model.playback.shuffleOn)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (page) {
                                    Page.Menu -> MenuList(mainMenu, cursor, onPick = { select(it) })
                                    Page.OnTheGo -> OnTheGo(model, cursor) { cursor = it }
                                    Page.Tab -> content(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                ClickWheel(wheel, Modifier.size(wheelSize))
                Spacer(Modifier.weight(1.2f))
            }
        }
    }

    @Composable
    private fun TitleBar(title: String, playing: Boolean, shuffle: Boolean) {
        Column {
            Box(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 7.dp)) {
                Canvas(Modifier.align(Alignment.CenterStart).size(12.dp)) {
                    if (playing) {
                        drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width * 0.95f, size.height / 2f); lineTo(size.width * 0.15f, size.height); close() }, Lcd.ink)
                    } else {
                        drawRect(Lcd.ink, Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height))
                        drawRect(Lcd.ink, Offset(size.width * 0.6f, 0f), Size(size.width * 0.28f, size.height))
                    }
                }
                Text(title, style = chicago(16f), color = Lcd.ink, modifier = Modifier.align(Alignment.Center), maxLines = 1)
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    if (shuffle) {
                        ShuffleGlyph(Modifier.size(width = 14.dp, height = 11.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Battery(batteryPercent(), Modifier.size(width = 24.dp, height = 11.dp))
                }
            }
            // The rule under the title.
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(Lcd.ink))
        }
    }

    @Composable
    private fun ShuffleGlyph(modifier: Modifier) {
        Canvas(modifier) {
            val s = Stroke(1.6.dp.toPx())
            val w = size.width
            val h = size.height
            drawPath(Path().apply { moveTo(0f, h * 0.2f); cubicTo(w * 0.4f, h * 0.2f, w * 0.5f, h * 0.8f, w * 0.85f, h * 0.8f) }, Lcd.ink, style = s)
            drawPath(Path().apply { moveTo(0f, h * 0.8f); cubicTo(w * 0.4f, h * 0.8f, w * 0.5f, h * 0.2f, w * 0.85f, h * 0.2f) }, Lcd.ink, style = s)
            drawPath(Path().apply { moveTo(w * 0.72f, 0f); lineTo(w, h * 0.2f); lineTo(w * 0.72f, h * 0.4f); close() }, Lcd.ink)
            drawPath(Path().apply { moveTo(w * 0.72f, h * 0.6f); lineTo(w, h * 0.8f); lineTo(w * 0.72f, h); close() }, Lcd.ink)
        }
    }

    @Composable
    private fun Battery(percent: Int, modifier: Modifier) {
        Canvas(modifier) {
            val nub = size.width * 0.1f
            val body = Size(size.width - nub, size.height)
            drawRect(Lcd.ink, Offset.Zero, body, style = Stroke(1.5.dp.toPx()))
            drawRect(Lcd.ink, Offset(body.width, size.height * 0.3f), Size(nub, size.height * 0.4f))
            val inset = 2.5.dp.toPx()
            // Four bars, as the iPod showed its charge.
            val bars = ((percent + 12) / 25).coerceIn(0, 4)
            val barW = (body.width - inset * 2) / 4f
            for (i in 0 until bars) {
                drawRect(Lcd.ink, Offset(inset + i * barW, inset), Size(barW - 1.dp.toPx(), size.height - inset * 2))
            }
        }
    }

    // --- the click wheel ---------------------------------------------------------------------------

    private interface WheelInput {
        fun step(direction: Int)
        fun menu()
        fun center()
        fun centerHold()
        fun previous()
        fun next()
        fun playPause()
    }

    /**
     * The click wheel: a touch ring that clicks once every fifteen degrees, with four buttons
     * under its compass points, and the centre button.
     */
    @Composable
    private fun ClickWheel(input: WheelInput, modifier: Modifier) {
        var pressedZone by remember { mutableStateOf<Int?>(null) }
        val currentInput by rememberUpdatedState(input)
        Box(
            modifier
                .testTag("ipod:wheel")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val outer = size.width / 2f
                        val inner = outer * CENTER_FRACTION
                        val startDist = hypot(down.position.x - c.x, down.position.y - c.y)
                        if (startDist > outer) return@awaitEachGesture
                        down.consume()
                        val inCenter = startDist < inner
                        val zone = if (inCenter) 4 else zoneOf(down.position, c)
                        pressedZone = zone
                        var lastAngle = angleOf(down.position, c)
                        var accumulated = 0f
                        var turned = 0f
                        val downTime = down.uptimeMillis
                        var held = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            if (inCenter) {
                                if (!held && change.uptimeMillis - downTime > 600) {
                                    held = true
                                    currentInput.centerHold()
                                }
                                continue
                            }
                            val angle = angleOf(change.position, c)
                            var delta = angle - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            lastAngle = angle
                            accumulated += delta
                            turned += abs(delta)
                            if (turned > 8f) pressedZone = null
                            while (accumulated >= STEP_DEGREES) {
                                accumulated -= STEP_DEGREES
                                currentInput.step(1)
                            }
                            while (accumulated <= -STEP_DEGREES) {
                                accumulated += STEP_DEGREES
                                currentInput.step(-1)
                            }
                        }
                        pressedZone = null
                        if (inCenter) {
                            if (!held) currentInput.center()
                        } else if (turned < 8f) {
                            when (zone) {
                                0 -> currentInput.menu()
                                1 -> currentInput.next()
                                2 -> currentInput.playPause()
                                3 -> currentInput.previous()
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = "Click wheel"
                    onClick(label = "Select") { input.center(); true }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = center
                // The ring: matte, a shade darker than the face, with a soft edge.
                drawCircle(Color.Black.copy(alpha = 0.05f), r, c + Offset(0f, 2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFEDEDED), Color(0xFFE4E4E4), Color(0xFFDCDCDC)), center = c, radius = r), r, c)
                drawCircle(Color(0xFFCBCBCB), r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                pressedZone?.let { z ->
                    if (z in 0..3) {
                        drawArc(Color.Black.copy(alpha = 0.05f), -135f + 90f * ((z + 3) % 4), 90f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                    }
                }
                val ink = Color(0xFFADADAD)
                drawMenuLabel(c, r, ink)
                drawSkip(Offset(c.x + r * 0.73f, c.y), r * 0.075f, ink, forward = true)
                drawSkip(Offset(c.x - r * 0.73f, c.y), r * 0.075f, ink, forward = false)
                drawPlayPause(Offset(c.x, c.y + r * 0.74f), r * 0.07f, ink)
                // The centre button.
                val inner = r * CENTER_FRACTION
                drawCircle(Color.Black.copy(alpha = 0.06f), inner + 2.dp.toPx(), c)
                drawCircle(
                    Brush.verticalGradient(
                        if (pressedZone == 4) listOf(Color(0xFFE8E8E8), Color(0xFFF2F2F2)) else listOf(Color.White, Color(0xFFF1F1F1)),
                        startY = c.y - inner,
                        endY = c.y + inner,
                    ),
                    inner,
                    c,
                )
                drawCircle(Color(0xFFCFCFCF), inner, c, style = Stroke(1.dp.toPx()))
            }
        }
    }

    private const val CENTER_FRACTION = 0.37f
    private const val STEP_DEGREES = 15f

    private fun angleOf(p: Offset, c: Offset) = (atan2(p.y - c.y, p.x - c.x) * 180.0 / PI).toFloat()

    /** 0 top (MENU), 1 right (next), 2 bottom (play), 3 left (previous). */
    private fun zoneOf(p: Offset, c: Offset): Int {
        val a = (angleOf(p, c) + 360f + 45f) % 360f
        return when {
            a < 90f -> 1
            a < 180f -> 2
            a < 270f -> 3
            else -> 0
        }
    }

    private fun DrawScope.drawMenuLabel(c: Offset, r: Float, ink: Color) {
        // "MENU" in spaced capitals, drawn as strokes so it needs no font of its own.
        val h = r * 0.085f
        val w = h * 0.78f
        val gap = h * 0.42f
        val total = w * 4 + gap * 3
        var x = c.x - total / 2f
        val top = c.y - r * 0.78f - h / 2f
        val s = Stroke(h * 0.2f)
        // M
        drawPath(Path().apply { moveTo(x, top + h); lineTo(x, top); lineTo(x + w / 2f, top + h * 0.55f); lineTo(x + w, top); lineTo(x + w, top + h) }, ink, style = s)
        x += w + gap
        // E
        drawPath(Path().apply { moveTo(x + w, top); lineTo(x, top); lineTo(x, top + h); lineTo(x + w, top + h); moveTo(x, top + h / 2f); lineTo(x + w * 0.8f, top + h / 2f) }, ink, style = s)
        x += w + gap
        // N
        drawPath(Path().apply { moveTo(x, top + h); lineTo(x, top); lineTo(x + w, top + h); lineTo(x + w, top) }, ink, style = s)
        x += w + gap
        // U
        drawPath(Path().apply { moveTo(x, top); lineTo(x, top + h * 0.7f); quadraticTo(x, top + h, x + w / 2f, top + h); quadraticTo(x + w, top + h, x + w, top + h * 0.7f); lineTo(x + w, top) }, ink, style = s)
    }

    private fun DrawScope.drawSkip(c: Offset, s: Float, ink: Color, forward: Boolean) {
        val d = if (forward) 1f else -1f
        for (k in 0..1) {
            val x0 = c.x + d * (-s * 1.6f + k * s * 1.3f)
            drawPath(Path().apply { moveTo(x0, c.y - s); lineTo(x0 + d * s * 1.3f, c.y); lineTo(x0, c.y + s); close() }, ink)
        }
        drawRect(ink, Offset(if (forward) c.x + s * 1.0f else c.x - s * 1.25f, c.y - s), Size(s * 0.25f, s * 2))
    }

    private fun DrawScope.drawPlayPause(c: Offset, s: Float, ink: Color) {
        drawPath(Path().apply { moveTo(c.x - s * 2f, c.y - s); lineTo(c.x - s * 0.6f, c.y); lineTo(c.x - s * 2f, c.y + s); close() }, ink)
        drawRect(ink, Offset(c.x + s * 0.2f, c.y - s), Size(s * 0.5f, s * 2))
        drawRect(ink, Offset(c.x + s * 1.1f, c.y - s), Size(s * 0.5f, s * 2))
    }

    // --- lists ------------------------------------------------------------------------------------

    /** A row of the iPod's menus: the chosen one inverted, with a chevron for what leads further. */
    @Composable
    private fun MenuRow(label: String, inverted: Boolean, chevron: Boolean, modifier: Modifier = Modifier, trailing: String? = null) {
        Row(
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(if (inverted) Lcd.ink else Color.Transparent)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = chicago(15.5f), color = if (inverted) Lcd.panel else Lcd.ink, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = chicago(15.5f), color = if (inverted) Lcd.panel else Lcd.ink)
            if (chevron) Chevron(if (inverted) Lcd.panel else Lcd.ink)
        }
    }

    @Composable
    private fun Chevron(color: Color) {
        Canvas(Modifier.size(width = 9.dp, height = 12.dp)) {
            drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width * 0.9f, size.height / 2f); lineTo(size.width * 0.15f, size.height) }, color, style = Stroke(2.dp.toPx()))
        }
    }

    @Composable
    private fun MenuList(items: List<String>, cursor: Int, onPick: (Int) -> Unit) {
        Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
            items.forEachIndexed { i, label ->
                MenuRow(
                    label,
                    inverted = i == cursor,
                    chevron = true,
                    modifier = Modifier
                        .testTag("ipod:menu:$label")
                        .skinClick(remember { MutableInteractionSource() }) { onPick(i) },
                )
            }
        }
    }

    @Composable
    private fun OnTheGo(model: FrameModel, cursor: Int, onCursor: (Int) -> Unit) {
        val upcoming = model.playback.upcoming
        if (upcoming.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs", style = chicago(15.5f), color = Lcd.ink)
            }
            return
        }
        Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
            upcoming.take(8).forEachIndexed { i, song ->
                MenuRow(
                    song.displayName,
                    inverted = i == cursor,
                    chevron = false,
                    trailing = formatDurationMs(song.durationMs),
                    modifier = Modifier.skinClick(remember { MutableInteractionSource() }) { onCursor(i) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text("Press the centre button to skip a song.", style = chicago(12f), color = Lcd.inkSoft, modifier = Modifier.padding(7.dp))
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        val state = rememberLazyListState()
        RegisterList(state)
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = chicago(13f), color = Lcd.ink, modifier = Modifier.padding(4.dp))
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                itemsIndexed(entries, key = { _, e -> e.key }) { _, entry ->
                    Box(Modifier.testTag("playlist:${entry.title}")) {
                        MenuRow(
                            entry.title,
                            inverted = entry.emphasized,
                            chevron = true,
                            modifier = Modifier.skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                        )
                        entry.menu()
                    }
                }
            }
            Scrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }

    /** Hands a list to the wheel while it's on screen. */
    @Composable
    private fun RegisterList(state: ScrollableState) {
        val bus = LocalWheelBus.current ?: return
        DisposableEffect(bus, state) {
            bus.lists.remove(state)
            bus.lists.add(state)
            onDispose { bus.lists.remove(state) }
        }
    }

    @Composable
    override fun ScrollArea(state: ScrollableState) = RegisterList(state)

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .background(if (model.isCurrent) Lcd.ink else Color.Transparent)
                .then(if (model.unreadable) Modifier.alpha(0.5f) else Modifier)
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 7.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = model.title,
                    style = chicago(15.5f),
                    color = if (model.isCurrent) Lcd.panel else Lcd.ink,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
            model.menu()
        }
    }

    @Composable
    override fun Scrollbar(state: LazyListState, modifier: Modifier) {
        RegisterList(state)
        Canvas(modifier.width(8.dp).fillMaxHeight().padding(vertical = 2.dp)) {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total == 0 || visible == 0 || visible >= total) return@Canvas
            drawRect(Lcd.ink, style = Stroke(1.2.dp.toPx()))
            val thumbH = (size.height * visible / total).coerceAtLeast(12.dp.toPx())
            val top = (size.height - thumbH) * (state.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
            drawRect(Lcd.ink, Offset(0f, top), Size(size.width, thumbH))
        }
    }

    // --- controls -----------------------------------------------------------------------------------

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
            ButtonKind.Toggle -> Box(modifier.heightIn(min = 26.dp).then(click), contentAlignment = Alignment.Center) {
                Text(if (icon == Icons.Filled.Check) "On" else "Off", style = chicago(15.5f), color = Lcd.ink)
            }
            ButtonKind.Confirm -> Box(
                modifier
                    .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)
                    .then(click)
                    .background(if (pressed) Lcd.ink else Color.Transparent)
                    .border(1.5.dp, Lcd.ink)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(if (icon == Icons.Filled.Add) "Add" else "OK", style = chicago(15.5f), color = if (pressed) Lcd.panel else Lcd.ink)
            }
            else -> Box(
                modifier
                    .size(size.coerceIn(22.dp, 34.dp))
                    .then(click)
                    .background(if (pressed) Lcd.ink else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = (if (pressed) Lcd.panel else Lcd.ink).copy(alpha = if (enabled) opacity.coerceAtLeast(0.4f) else 0.35f), modifier = Modifier.size(iconSize.coerceIn(14.dp, 20.dp)))
            }
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            drawOutline(shape.createOutline(size, layoutDirection, this), Lcd.ink, style = Stroke(1.5.dp.toPx()))
        }
        else -> modifier.background(Lcd.panel).border(1.5.dp, Lcd.ink)
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier =
        modifier.background(Color(0xFFD8DDD5)).border(if (focused) 2.dp else 1.dp, Lcd.ink)

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(Lcd.ink))
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 22.dp
        override val thumbWidth: Dp = 10.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            // The iPod's progress bar, with the scrubber's diamond at the value.
            val h = 9.dp.toPx()
            val top = (size.height - h) / 2f
            val tw = thumbWidth.toPx()
            val left = tw / 2f
            val right = size.width - tw / 2f
            drawRect(Lcd.ink, Offset(left, top), Size(right - left, h), style = Stroke(1.5.dp.toPx()))
            val x = left + (right - left) * fraction
            drawRect(Lcd.ink.copy(alpha = if (enabled) 1f else 0.4f), Offset(left, top), Size(x - left, h))
            if (pressed) {
                val d = tw * 0.7f
                drawPath(Path().apply { moveTo(x, top - d * 0.4f); lineTo(x + d, top + h / 2f); lineTo(x, top + h + d * 0.4f); lineTo(x - d, top + h / 2f); close() }, Lcd.ink)
            }
        }
    }

    @Composable
    override fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier, bold: Boolean, fontSizeSp: Int) {
        val haptics = LocalHaptics.current
        // As the iPod's own settings read: the name, and its value at the right.
        Row(
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Switch; selected = checked }
                .skinClick(remember { MutableInteractionSource() }, role = Role.Switch) {
                    haptics?.click()
                    onToggle(!checked)
                }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = chicago(if (fontSizeSp >= 20 && bold) 13.5f else 12.5f), color = Lcd.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(if (checked) "On" else "Off", style = chicago(14.5f), color = Lcd.ink)
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
        Row(modifier.fillMaxWidth().height(26.dp).border(1.5.dp, Lcd.ink)) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selected) Lcd.ink else Color.Transparent)
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = chicago(13.5f), color = if (selected) Lcd.panel else Lcd.ink, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 170.dp)
                .background(Lcd.panel)
                .border(1.5.dp, Lcd.ink)
                .padding(vertical = 2.dp),
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                Row(
                    Modifier.fillMaxWidth().height(28.dp).background(if (pressed) Lcd.ink else Color.Transparent).skinClick(interaction) { onPick(item) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = chicago(15.5f), color = if (pressed) Lcd.panel else Lcd.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (item.selected) {
                        Spacer(Modifier.width(10.dp))
                        Text("✓", style = chicago(15.5f), color = if (pressed) Lcd.panel else Lcd.ink)
                    }
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        Column(modifier.fillMaxWidth().background(Lcd.panel).border(1.5.dp, Lcd.ink)) {
            Box(Modifier.fillMaxWidth().background(Lcd.ink).padding(horizontal = 7.dp, vertical = 4.dp)) {
                Text(title, style = chicago(14.5f), color = Lcd.panel)
            }
            Text(message, style = chicago(13f), color = Lcd.ink, modifier = Modifier.padding(7.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Cancel" to onDismiss, "OK" to onConfirm).forEach { (label, action) ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by pressedState(interaction)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(28.dp)
                            .then(if (label == "Cancel") Modifier.testTag("dialog:dismiss") else Modifier)
                            .background(if (pressed) Lcd.ink else Color.Transparent)
                            .border(0.75.dp, Lcd.ink)
                            .skinClick(interaction) { action() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = chicago(15.5f), color = if (pressed) Lcd.panel else Lcd.ink)
                    }
                }
            }
        }
    }

    // --- Now Playing -----------------------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val bus = LocalWheelBus.current
        var scrubbing by remember { mutableStateOf(false) }
        var scrubFraction by remember { mutableStateOf<Float?>(null) }
        var volume by remember { mutableIntStateOf(-1) }
        var volumeShownAt by remember { mutableLongStateOf(0L) }
        val currentModel by rememberUpdatedState(model)

        if (bus != null) {
            DisposableEffect(bus) {
                bus.onStep = { direction ->
                    val m = currentModel
                    if (scrubbing && m.hasSong) {
                        val base = scrubFraction ?: m.progress
                        val stepFraction = if (m.playback.durationMs > 0) 3000f / m.playback.durationMs else 0.02f
                        val next = (base + direction * stepFraction).coerceIn(0f, 1f)
                        scrubFraction = next
                        m.onSeek(next)
                    } else {
                        volume = m.onVolumeDelta(direction * 3)
                        volumeShownAt = System.currentTimeMillis()
                    }
                    true
                }
                bus.onCenter = {
                    if (currentModel.hasSong) {
                        scrubbing = !scrubbing
                        scrubFraction = null
                    }
                    true
                }
                bus.onCenterHold = {
                    currentModel.onOpenSleepTimer()
                    true
                }
                onDispose {
                    bus.onStep = null
                    bus.onCenter = null
                    bus.onCenterHold = null
                }
            }
        }
        // The volume bar shows for a moment after the wheel stops; the scrubber gives up after a while.
        LaunchedEffect(volumeShownAt) {
            if (volumeShownAt != 0L) {
                delay(1800)
                volumeShownAt = 0L
            }
        }
        LaunchedEffect(scrubbing, scrubFraction) {
            if (scrubbing) {
                delay(5000)
                scrubbing = false
                scrubFraction = null
            }
        }

        val song = model.song
        val p = model.playback
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 6.dp)) {
            model.header(Modifier.fillMaxWidth())
            model.sleepTimerPanel?.let { panel ->
                panel(Modifier.fillMaxWidth().background(Lcd.panel).border(1.5.dp, Lcd.ink).padding(7.dp))
                Spacer(Modifier.height(6.dp))
            }
            if (song == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (model.playbackRestored) "Press MENU, then\nchoose Playlists" else "",
                        style = chicago(15.5f).copy(textAlign = TextAlign.Center),
                        color = Lcd.ink,
                    )
                }
                return@Column
            }
            Text("Track ${song.sortIndex + 1}", style = chicago(13f), color = Lcd.ink, maxLines = 1)
            Spacer(Modifier.weight(1f))
            val centred = chicago(17f).copy(textAlign = TextAlign.Center)
            MarqueeText(song.displayName, centred, Modifier.fillMaxWidth().onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags"), color = Lcd.ink)
            Spacer(Modifier.height(4.dp))
            MarqueeText(song.artist, centred.copy(fontSize = 15.sp), Modifier.fillMaxWidth(), color = Lcd.ink)
            Spacer(Modifier.height(4.dp))
            MarqueeText(p.playlistLabel, centred.copy(fontSize = 15.sp), Modifier.fillMaxWidth(), color = Lcd.ink)
            val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "FX on".takeIf { model.fxOn }, "Sleep timer on".takeIf { model.sleepTimerActive }, model.message)
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(notes.joinToString("  ·  "), style = chicago(12f).copy(textAlign = TextAlign.Center), color = Lcd.inkSoft, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.weight(1f))
            if (volumeShownAt != 0L && volume >= 0) {
                VolumeBar(volume)
            } else {
                ProgressBar(scrubFraction ?: model.progress, scrubbing)
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    val shownPos = ((scrubFraction ?: model.progress) * p.durationMs).toLong()
                    Text(formatDurationMs(shownPos), style = chicago(13f), color = Lcd.ink)
                    Spacer(Modifier.weight(1f))
                    Text("-" + formatDurationMs((p.durationMs - shownPos).coerceAtLeast(0)), style = chicago(13f), color = Lcd.ink)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    @Composable
    private fun ProgressBar(fraction: Float, scrubbing: Boolean) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = 9.dp.toPx()
            val top = (size.height - h) / 2f
            drawRect(Lcd.ink, Offset(0f, top), Size(size.width, h), style = Stroke(1.5.dp.toPx()))
            val x = size.width * fraction.coerceIn(0f, 1f)
            if (scrubbing) {
                // The scrubber: a hollow bar with a diamond riding it.
                val d = 6.dp.toPx()
                drawPath(Path().apply { moveTo(x, size.height / 2f - d); lineTo(x + d, size.height / 2f); lineTo(x, size.height / 2f + d); lineTo(x - d, size.height / 2f); close() }, Lcd.ink)
            } else {
                drawRect(Lcd.ink, Offset(0f, top), Size(x, h))
            }
        }
    }

    @Composable
    private fun VolumeBar(percent: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Speaker(false)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(14.dp)) {
                val h = 9.dp.toPx()
                val top = (size.height - h) / 2f
                drawRect(Lcd.ink, Offset(0f, top), Size(size.width, h), style = Stroke(1.5.dp.toPx()))
                drawRect(Lcd.ink, Offset(0f, top), Size(size.width * percent / 100f, h))
            }
            Spacer(Modifier.width(6.dp))
            Speaker(true)
        }
    }

    @Composable
    private fun Speaker(loud: Boolean) {
        Canvas(Modifier.size(width = 16.dp, height = 14.dp)) {
            val h = size.height
            drawRect(Lcd.ink, Offset(0f, h * 0.35f), Size(size.width * 0.2f, h * 0.3f))
            drawPath(Path().apply { moveTo(size.width * 0.18f, h * 0.35f); lineTo(size.width * 0.45f, h * 0.1f); lineTo(size.width * 0.45f, h * 0.9f); lineTo(size.width * 0.18f, h * 0.65f); close() }, Lcd.ink)
            val waves = if (loud) 3 else 1
            for (i in 1..waves) {
                val r = h * 0.15f * i + h * 0.05f
                drawArc(Lcd.ink, -45f, 90f, false, Offset(size.width * 0.5f - r, h / 2f - r), Size(r * 2, r * 2), style = Stroke(1.3.dp.toPx()))
            }
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) = Unit
}
