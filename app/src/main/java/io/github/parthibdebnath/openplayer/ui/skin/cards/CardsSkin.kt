package io.github.parthibdebnath.openplayer.ui.skin.cards

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import io.github.parthibdebnath.openplayer.data.model.Song
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
import kotlinx.coroutines.launch
import kotlin.math.abs

private object W {
    val ink = Color(0xFF1F1F1F)
    val inkSoft = Color(0xFF6E6E6E)
    val blue = Color(0xFF3F8CD8)
    val green = Color(0xFF4FA83D)
    val red = Color(0xFFCC3B30)
    val groupLine = Color(0xFFC4C4C4)
    val menu = Color(0xF2262626)
}

private val Wallpaper = Brush.verticalGradient(listOf(Color(0xFF0B2338), Color(0xFF15476A), Color(0xFF1F6C8A), Color(0xFF0C2A40)))

/**
 * Palm webOS: an app is a card. Swipe up from the gesture area (or tap its light bar) and the
 * cards step back so you can move between them, open one, or flick it up and away. Pages are soft
 * grey gradients with rounded group boxes, pill buttons and ON/OFF toggles; menus and dialogs
 * are dark. On Now Playing, the songs coming up are cards too - flick one away to skip it.
 */
object CardsSkin : Skin() {
    override val theme = UiTheme.CARDS
    override val colors = AppColors(background = Color(0xFFE4E4E4), onBackground = W.ink)
    override val showsHeader = false
    override val gutter: Dp = 14.dp

    private fun prelude(size: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = SkinFonts.prelude, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.25f).sp)

    override fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle {
        val size = when {
            sizeSp >= 20 -> 20f
            sizeSp >= 18 -> 18f
            sizeSp >= 16 -> 16.5f
            sizeSp >= 14 -> 14.5f
            else -> 13f
        }
        return prelude(size, if (bold) FontWeight.Bold else FontWeight.Normal)
    }

    private val tabIcons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlayCircle, Icons.Filled.GraphicEq)

    private fun cardTitle(tab: String) = when (tab) {
        "File" -> "Library"
        "Play" -> "Now Playing"
        else -> "Effects"
    }

    // --- the device ------------------------------------------------------------------------------------

    @Composable
    override fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit) {
        var cardView by remember { mutableStateOf(false) }
        val snapshots = remember { mutableStateMapOf<Int, ImageBitmap>() }
        val layer = rememberGraphicsLayer()
        val scope = rememberCoroutineScope()
        val currentModel by rememberUpdatedState(model)

        fun snapshot(tab: Int) {
            scope.launch { runCatching { layer.toImageBitmap() }.getOrNull()?.let { snapshots[tab] = it } }
        }

        // Each card's look is kept fresh while it's on screen, so card view shows it as it was left
        // however it was left - by a swipe, the app menu, or card view itself.
        LaunchedEffect(cardView, model.selectedTab) {
            if (cardView) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(1500)
                snapshot(currentModel.selectedTab)
            }
        }

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            StatusBar(model, onAppMenu = { cardView = false })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (cardView) {
                    CardView(
                        model = model,
                        snapshots = snapshots,
                        onOpen = { i ->
                            cardView = false
                            currentModel.onSelectTab(i)
                        },
                        onThrowAway = { i ->
                            snapshots.remove(i)
                            // Thrown away, the card starts afresh: the library returns to its list.
                            if (i == 0) {
                                currentModel.onSelectTab(0)
                                currentModel.onSelectTab(0)
                            }
                        },
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFFEDEDED), Color(0xFFDCDCDC))))
                            .drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                    ) {
                        PageHeader(cardTitle(model.tabs[model.selectedTab]), tabIcons[model.selectedTab])
                        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
                    }
                }
            }
            GestureArea(
                cardView = cardView,
                onCardView = {
                    if (!cardView) snapshot(model.selectedTab)
                    cardView = !cardView
                },
            )
        }
    }

    @Composable
    private fun StatusBar(model: FrameModel, onAppMenu: () -> Unit) {
        val now = rememberCurrentTime()
        val time = formatClockTime(now, model.twentyFourHour)
        var menuOpen by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 10.dp)) {
            // The app menu, at the top left.
            Box(Modifier.align(Alignment.CenterStart)) {
                Text(
                    "openplayer ▾",
                    style = prelude(14f, FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.testTag("frame:menu").skinTap {
                        onAppMenu()
                        menuOpen = true
                    },
                )
                if (menuOpen) {
                    Popup(offset = IntOffset(0, with(LocalDensity.current) { 26.dp.roundToPx() }), onDismissRequest = { menuOpen = false }, properties = PopupProperties(focusable = true)) {
                        val haptics = LocalHaptics.current
                        Menu(
                            listOf(ContextMenuItem("Preferences", onClick = model.onOpenSettings)) +
                                model.tabs.mapIndexed { i, tab -> ContextMenuItem(cardTitle(tab), selected = i == model.selectedTab, onClick = { model.onSelectTab(i) }) },
                        ) { item ->
                            menuOpen = false
                            haptics?.click()
                            item.onClick()
                        }
                    }
                }
            }
            Text("${time.time}${time.amPm?.let { " $it" }.orEmpty()}", style = prelude(14f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.align(Alignment.Center))
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
                    for (i in 0 until 4) {
                        val h = size.height * (i + 1) / 4f
                        drawRect(Color.White, Offset(i * size.width / 4f, size.height - h), Size(size.width / 4f - 1.5.dp.toPx(), h))
                    }
                }
                Spacer(Modifier.width(8.dp))
                val percent = batteryPercent()
                Canvas(Modifier.size(width = 12.dp, height = 18.dp)) {
                    // A battery standing upright, filled from the bottom.
                    val nub = 2.dp.toPx()
                    drawRoundRect(Color.White, Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, nub), CornerRadius(1.dp.toPx()))
                    drawRoundRect(Color.White, Offset(0f, nub), Size(size.width, size.height - nub), CornerRadius(2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                    val fill = (size.height - nub - 3.dp.toPx()) * percent / 100f
                    drawRect(Color.White, Offset(1.5.dp.toPx(), size.height - 1.5.dp.toPx() - fill), Size(size.width - 3.dp.toPx(), fill))
                }
            }
        }
    }

    /** The page header: a soft grey pill across the top of the card, with the app's icon and title. */
    @Composable
    private fun PageHeader(title: String, icon: ImageVector) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)
                .shadow(2.dp, RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFDADADA))), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(22.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = prelude(19f, FontWeight.SemiBold), color = W.ink)
        }
    }

    /** A launcher icon: a soft, glossy rounded square. */
    @Composable
    private fun AppIcon(icon: ImageVector, size: Dp) {
        Box(
            Modifier
                .size(size)
                .shadow(2.dp, RoundedCornerShape(size * 0.25f))
                .background(Brush.verticalGradient(listOf(Color(0xFF63B0EE), Color(0xFF1E62B0))), RoundedCornerShape(size * 0.25f))
                .drawBehind {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), endY = this.size.height * 0.5f),
                        size = Size(this.size.width, this.size.height * 0.5f),
                        cornerRadius = CornerRadius(this.size.width * 0.25f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }

    /**
     * The gesture area under the screen, with its light bar. Swipe up (or tap the bar) for card
     * view; swipe right to left along it to go back.
     */
    @Composable
    private fun GestureArea(cardView: Boolean, onCardView: () -> Unit) {
        val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        var glow by remember { mutableStateOf(0f) }
        val shownGlow by animateFloatAsState(maxOf(glow, if (cardView) 0.6f else 0f), tween(250), label = "lightbar")
        val haptics = LocalHaptics.current
        val currentOnCardView by rememberUpdatedState(onCardView)
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cards:gesture")
                .semantics { contentDescription = "Card view" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        glow = 1f
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dx += change.position.x - change.previousPosition.x
                            dy += change.position.y - change.previousPosition.y
                            change.consume()
                        }
                        glow = 0f
                        when {
                            dy < -40.dp.toPx() && abs(dy) > abs(dx) -> { haptics?.click(); currentOnCardView() }
                            dx < -60.dp.toPx() && abs(dx) > abs(dy) -> { haptics?.click(); back?.onBackPressed() }
                            abs(dx) < 12.dp.toPx() && abs(dy) < 12.dp.toPx() -> { haptics?.click(); currentOnCardView() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth(0.42f).height(10.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f + 0.5f * shownGlow), Color.Transparent)), Offset(0f, c.y - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
                drawOval(
                    Brush.radialGradient(listOf(Color(0xFFB8E1FF).copy(alpha = 0.25f + 0.6f * shownGlow), Color.Transparent), center = c, radius = size.width * 0.4f),
                    topLeft = Offset(size.width * 0.1f, 0f),
                    size = Size(size.width * 0.8f, size.height),
                )
            }
        }
    }

    // --- card view -----------------------------------------------------------------------------------

    @Composable
    private fun CardView(
        model: FrameModel,
        snapshots: Map<Int, ImageBitmap>,
        onOpen: (Int) -> Unit,
        onThrowAway: (Int) -> Unit,
    ) {
        var focus by remember { mutableStateOf(model.selectedTab) }
        val position by animateFloatAsState(focus.toFloat(), spring(dampingRatio = 0.85f, stiffness = 300f), label = "cards")
        val haptics = LocalHaptics.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Wallpaper)
                .pointerInput(model.tabs.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!change.isConsumed) dx += change.position.x - change.previousPosition.x
                        }
                        if (abs(dx) > 60.dp.toPx()) {
                            val next = (focus + if (dx < 0) 1 else -1).coerceIn(0, model.tabs.lastIndex)
                            if (next != focus) haptics?.tick()
                            focus = next
                        }
                    }
                },
        ) {
            val cardW = maxWidth * 0.62f
            val cardH = maxHeight * 0.66f
            val spacing = cardW + 18.dp
            model.tabs.forEachIndexed { i, tab ->
                val offsetX = (maxWidth - cardW) / 2 + spacing * (i - position)
                ThrowableCard(
                    title = cardTitle(tab),
                    icon = tabIcons[i],
                    snapshot = snapshots[i],
                    tag = "card:$tab",
                    modifier = Modifier.offset(x = offsetX, y = (maxHeight - cardH) / 2).size(cardW, cardH),
                    onOpen = { onOpen(i) },
                    onThrowAway = { onThrowAway(i) },
                )
            }
        }
    }

    /** A card that opens on a tap and, flicked up, flies away and comes back afresh. */
    @Composable
    private fun ThrowableCard(title: String, icon: ImageVector, snapshot: ImageBitmap?, tag: String, modifier: Modifier, onOpen: () -> Unit, onThrowAway: () -> Unit) {
        val lift = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val haptics = LocalHaptics.current
        val currentOnThrowAway by rememberUpdatedState(onThrowAway)
        Column(modifier.graphicsLayer { translationY = lift.value; alpha = (1f + lift.value / 1500f).coerceIn(0f, 1f) }) {
            Text(title, style = prelude(15f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFEDEDED), Color(0xFFDCDCDC))))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (lift.value < -110.dp.toPx()) {
                                        haptics?.click()
                                        lift.animateTo(-2000f, tween(260, easing = FastOutSlowInEasing))
                                        currentOnThrowAway()
                                        lift.snapTo(600f)
                                        lift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 250f))
                                    } else {
                                        lift.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { lift.animateTo(0f) } },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                scope.launch { lift.snapTo((lift.value + delta).coerceAtMost(40f)) }
                            },
                        )
                    }
                    .testTag(tag)
                    .semantics { contentDescription = title }
                    .skinClick(remember { MutableInteractionSource() }) { onOpen() },
            ) {
                if (snapshot != null) {
                    Image(snapshot, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(icon, 64.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(title, style = prelude(16f, FontWeight.SemiBold), color = W.ink)
                    }
                }
            }
        }
    }

    // --- controls ------------------------------------------------------------------------------------

    /** webOS's pill button: soft grey, or coloured for a yes (green), a no (red) or a primary action (blue). */
    @Composable
    private fun PillButton(label: String?, icon: ImageVector?, modifier: Modifier, tint: Color? = null, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val colours = when {
            tint != null -> listOf(lerpColor(tint, Color.White, 0.25f), tint)
            else -> listOf(Color(0xFFFBFBFB), Color(0xFFD5D5D5))
        }
        Row(
            modifier
                .shadow(if (pressed) 0.dp else 2.dp, RoundedCornerShape(50))
                .background(Brush.verticalGradient(if (pressed) colours.reversed() else colours), RoundedCornerShape(50))
                .border(1.dp, if (tint != null) lerpColor(tint, Color.Black, 0.25f) else Color(0xFFADADAD), RoundedCornerShape(50))
                .skinClick(interaction, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (tint != null) Color.White else W.ink
            if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
            if (icon != null && label != null) Spacer(Modifier.width(6.dp))
            if (label != null) Text(label, style = prelude(15.5f, FontWeight.SemiBold), color = ink, maxLines = 1)
        }
    }

    private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

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
            ButtonKind.Toggle -> Box(modifier.then(click), contentAlignment = Alignment.Center) { OnOffToggle(icon == Icons.Filled.Check) }
            ButtonKind.Confirm -> Box(modifier.alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.45f)) {
                PillButton(if (icon == Icons.Filled.Add) "Add" else "Done", null, Modifier.semantics { this.contentDescription = contentDescription }, tint = W.blue, onClick = onClick)
            }
            else -> {
                val side = size.coerceIn(32.dp, 56.dp)
                Box(
                    modifier
                        .size(side)
                        .alpha(if (enabled) opacity.coerceAtLeast(0.45f) else 0.35f)
                        .then(click)
                        .shadow(if (pressed) 0.dp else 2.dp, CircleShape)
                        .background(Brush.verticalGradient(if (pressed) listOf(Color(0xFFD5D5D5), Color(0xFFF5F5F5)) else listOf(Color(0xFFFBFBFB), Color(0xFFD5D5D5))), CircleShape)
                        .border(1.dp, Color(0xFFADADAD), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = W.ink, modifier = Modifier.size(side * 0.5f))
                }
            }
        }
    }

    /** The webOS toggle: a pill reading ON or OFF, with a white knob at the other end. */
    @Composable
    private fun OnOffToggle(on: Boolean) {
        val position by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "toggle")
        Box(
            Modifier
                .width(78.dp)
                .height(32.dp)
                .background(
                    Brush.verticalGradient(if (on) listOf(Color(0xFF2F74C0), Color(0xFF5BA3EA)) else listOf(Color(0xFF9C9C9C), Color(0xFFC4C4C4))),
                    RoundedCornerShape(50),
                )
                .border(1.dp, Color(0xFF6F6F6F), RoundedCornerShape(50)),
        ) {
            Text(
                if (on) "ON" else "OFF",
                style = prelude(13.5f, FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.align(if (on) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 11.dp),
            )
            Box(
                Modifier
                    .offset(x = 48.dp * position + 2.dp, y = 2.dp)
                    .size(28.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDDDDD))), CircleShape),
            )
        }
    }

    @Composable
    override fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier = when (role) {
        GlassRole.Control -> modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.verticalGradient(listOf(Color(0xFFFBFBFB), Color(0xFFD5D5D5))))
            drawOutline(outline, Color(0xFFADADAD), style = Stroke(1.dp.toPx()))
        }
        else -> modifier
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .border(1.dp, W.groupLine, RoundedCornerShape(12.dp))
    }

    @Composable
    override fun textField(modifier: Modifier, focused: Boolean): Modifier = modifier
        .background(Color.White, RoundedCornerShape(8.dp))
        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFF5A623) else Color(0xFFB5B5B5), RoundedCornerShape(8.dp))

    @Composable
    override fun Divider(modifier: Modifier) {
        Column(modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFC6C6C6)))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White))
        }
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
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, h), CornerRadius(size.width / 2f))
        }
    }

    override val slider: SliderLook = object : SliderLook {
        override val height: Dp = 34.dp
        override val thumbWidth: Dp = 26.dp
        override fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean) {
            val tw = thumbWidth.toPx()
            val h = 8.dp.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9A9A9A), Color(0xFFCFCFCF)), startY = top, endY = top + h), Offset(tw / 2f, top), Size(size.width - tw, h), CornerRadius(h / 2f))
            val x = tw / 2f + (size.width - tw) * fraction
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2F74C0), Color(0xFF6CB0F0)), startY = top, endY = top + h), Offset(tw / 2f, top), Size(x - tw / 2f, h), CornerRadius(h / 2f))
            val c = Offset(x, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.2f), tw / 2f, c + Offset(0f, 1.5.dp.toPx()))
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8D8)), startY = c.y - tw / 2f, endY = c.y + tw / 2f), tw / 2f * (if (pressed) 1.08f else 1f), c)
            drawCircle(Color(0xFF8F8F8F), tw / 2f * (if (pressed) 1.08f else 1f), c, style = Stroke(1.dp.toPx()))
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
            Text(label, style = textStyle(fontSizeSp, bold, LocalDensity.current), color = W.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            OnOffToggle(checked)
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
        // A radio button group: joined pills, the chosen one pressed in and blue.
        Row(
            modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color(0xFFADADAD), RoundedCornerShape(50)),
        ) {
            items.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Brush.verticalGradient(if (selected) listOf(Color(0xFF2F74C0), Color(0xFF5BA3EA)) else listOf(Color(0xFFFBFBFB), Color(0xFFD5D5D5))))
                        .drawBehind { if (i > 0) drawLine(Color(0xFFADADAD), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                        .semantics { role = Role.Tab; this.selected = selected }
                        .skinClick(remember(i) { MutableInteractionSource() }, role = Role.Tab) {
                            if (!selected) haptics?.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = prelude(14.5f, FontWeight.SemiBold), color = if (selected) Color.White else W.ink, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }

    // --- menus and dialogs ---------------------------------------------------------------------------

    @Composable
    override fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit) {
        // webOS's pop-up menu: dark, rounded, its items divided by fine lines.
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 210.dp)
                .padding(6.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .background(W.menu, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp),
        ) {
            items.forEachIndexed { i, item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by pressedState(interaction)
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (pressed) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF5BA3EA), Color(0xFF2F74C0)))) else Modifier)
                        .skinClick(interaction) { onPick(item) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = prelude(16.5f), color = Color.White, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    if (item.selected) {
                        Spacer(Modifier.width(14.dp))
                        Text("✓", style = prelude(16f, FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }
    }

    @Composable
    override fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
        // A dialog: a dark sheet with the choices as full-width pills.
        Column(
            modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF2363636), Color(0xF21C1C1C))), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(title, style = prelude(19f, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(message, style = prelude(15f), color = Color(0xFFD0D0D0))
            Spacer(Modifier.height(14.dp))
            PillButton("Yes", null, Modifier.fillMaxWidth(), tint = W.green, onClick = onConfirm)
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", null, Modifier.fillMaxWidth().testTag("dialog:dismiss"), onClick = onDismiss)
        }
    }

    // --- lists ---------------------------------------------------------------------------------------

    /** A palm-group: a rounded white box with a small grey label on its top edge. */
    @Composable
    private fun Group(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Box(modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (label != null) 9.dp else 0.dp)
                    .shadow(1.dp, RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .border(1.dp, W.groupLine, RoundedCornerShape(12.dp))
                    .padding(top = if (label != null) 8.dp else 0.dp),
                content = content,
            )
            if (label != null) {
                Text(
                    label.uppercase(),
                    style = prelude(11.5f, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
                    color = Color.White,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFA9A9A9), Color(0xFF8A8A8A))), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
    }

    @Composable
    override fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier) {
        Box(modifier) {
            if (emptyMessage != null) Text(emptyMessage, style = prelude(15f), color = W.inkSoft)
            Group("Playlists", Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.key }) { entry ->
                        val index = entries.indexOf(entry)
                        Box(
                            Modifier
                                .testTag("playlist:${entry.title}")
                                .skinClick(remember { MutableInteractionSource() }, onLongClick = entry.onLongClick, onLongClickLabel = entry.longClickLabel) { entry.onClick() },
                        ) {
                            Column {
                                if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color(0xFFE2E2E2)))
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (entry.kind) {
                                            CollectionEntry.Kind.Favourites -> Icons.Filled.Favorite
                                            CollectionEntry.Kind.Tag -> Icons.AutoMirrored.Filled.Label
                                            CollectionEntry.Kind.Playlist -> Icons.Filled.MusicNote
                                        },
                                        null,
                                        tint = if (entry.emphasized) W.blue else Color(0xFF8A8A8A),
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(entry.title, style = prelude(17f, if (entry.emphasized) FontWeight.Bold else FontWeight.Normal), color = W.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(entry.subtitle.substringBefore(" ·"), style = prelude(13f), color = W.inkSoft)
                                }
                            }
                            entry.menu()
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        Box(
            modifier
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .border(1.dp, W.groupLine, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp),
            content = content,
        )
    }

    @Composable
    override fun SongRow(model: SongRowModel, modifier: Modifier) {
        Box(
            modifier
                .then(if (model.unreadable) Modifier.alpha(0.45f) else Modifier)
                .drawBehind { if (model.index > 0) drawLine(Color(0xFFE2E2E2), Offset(6.dp.toPx(), 0f), Offset(size.width - 6.dp.toPx(), 0f), 1.dp.toPx()) }
                .skinClick(remember { MutableInteractionSource() }, onLongClick = model.onLongClick, onLongClickLabel = "Song options") { model.onClick() },
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.isCurrent) {
                    Icon(Icons.Filled.PlayArrow, null, tint = W.blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                MarqueeText(
                    text = model.title,
                    style = prelude(16f, if (model.isCurrent) FontWeight.Bold else FontWeight.Normal),
                    color = if (model.isCurrent) W.blue else W.ink,
                    scrollRequest = model.titleScrollRequest,
                    idleOverflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(model.artist, style = prelude(13f), color = W.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
            model.menu()
        }
    }

    // --- the Music app's now playing ---------------------------------------------------------------------

    @Composable
    override fun NowPlaying(model: NowPlayingModel) {
        val song = model.song
        val p = model.playback
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A5057), Color(0xFF23272B), Color(0xFF111315))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                model.header(Modifier.fillMaxWidth())
                model.sleepTimerPanel?.let { panel -> Group(null, Modifier.padding(top = 8.dp)) { panel(Modifier.fillMaxWidth().padding(12.dp)) } }
                Spacer(Modifier.height(14.dp))
                // The cover, with its reflection on the glossy floor below it.
                Box(Modifier.align(Alignment.CenterHorizontally).size(180.dp)) {
                    Cover(Modifier.fillMaxSize())
                }
                // Flipped about its own middle, the cover's foot shows at the top of this strip: a reflection.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 180.dp, height = 40.dp)
                        .clipToBounds()
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color(0x8C2A2E33), Color(0xFF23272B))))
                        },
                ) {
                    Cover(Modifier.size(180.dp).graphicsLayer { scaleY = -1f; alpha = 0.5f })
                }
                Spacer(Modifier.height(4.dp))
                MarqueeText(
                    song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                    prelude(22f, FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().then(if (song != null) Modifier.onTapOrHold(onHold = model.onHoldTitle, holdLabel = "Edit tags") else Modifier.skinTap(onClick = model.onOpenLibrary)),
                    color = Color.White,
                )
                Text(song?.artist.orEmpty(), style = prelude(16f).copy(textAlign = TextAlign.Center), color = Color(0xFFC8C8C8), modifier = Modifier.fillMaxWidth(), maxLines = 1)
                if (song != null) {
                    Spacer(Modifier.height(10.dp))
                    SeekBar(model.progress, model.onSeek, Modifier.fillMaxWidth().height(30.dp)) { f -> with(slider) { drawSlider(f, true, 0, false) } }
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatDurationMs(p.positionMs), style = prelude(13f), color = Color(0xFFC8C8C8))
                        Spacer(Modifier.weight(1f))
                        Text("-" + formatDurationMs((p.durationMs - p.positionMs).coerceAtLeast(0)), style = prelude(13f), color = Color(0xFFC8C8C8))
                    }
                }
                Spacer(Modifier.height(12.dp))
                CommandMenu(model)
                val notes = listOfNotNull("Shuffle locked".takeIf { model.shuffleLocked }, "Effects on".takeIf { model.fxOn }, "Sleep timer on".takeIf { model.sleepTimerActive }, model.message)
                if (notes.isNotEmpty()) Text(notes.joinToString("  ·  "), style = prelude(13f).copy(textAlign = TextAlign.Center), color = Color(0xFFC8C8C8), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (p.upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Up next · flick a card away to skip it", style = prelude(13f), color = Color(0xFFC8C8C8))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.upcoming.take(5).forEachIndexed { i, s ->
                            SongCard(s, onFlick = { model.onSwapUpcoming(i) }, onHold = { model.onHoldSong(s) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun Cover(modifier: Modifier) {
        Box(
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7FC4F2), Color(0xFF3A6FB8), Color(0xFF6B3FA0))))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.fillMaxSize(0.45f))
        }
    }

    /** The command menu: floating translucent buttons along the foot of the scene. */
    @Composable
    private fun CommandMenu(model: NowPlayingModel) {
        val p = model.playback
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CommandButton(Icons.Filled.Shuffle, "Shuffle", lit = model.shuffleOn, onClick = model.onToggleShuffle)
            Row(
                Modifier
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommandButton(Icons.Filled.SkipPrevious, "Previous", framed = false, onClick = model.onPrevious)
                CommandButton(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.isPlaying) "Pause" else "Play", framed = false, big = true, onClick = model.onPlayPause)
                CommandButton(Icons.Filled.SkipNext, "Next", framed = false, onClick = model.onNext)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                model.isFavourite?.let { CommandButton(if (it) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite", lit = it, onClick = model.onToggleFavourite) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CommandButton(Icons.Filled.Timer, "Sleep timer", lit = model.sleepTimerActive, onClick = model.onOpenSleepTimer)
            if (model.outputSwitchAvailable) {
                Spacer(Modifier.width(14.dp))
                CommandButton(if (model.outputDevice == OutputDevice.BLUETOOTH) Icons.Filled.Bluetooth else Icons.Filled.Speaker, "Output", onClick = model.onToggleOutput)
            }
        }
    }

    @Composable
    private fun CommandButton(icon: ImageVector, description: String, lit: Boolean = false, framed: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by pressedState(interaction)
        val haptics = LocalHaptics.current
        val side = if (big) 60.dp else 48.dp
        Box(
            Modifier
                .size(side)
                .semantics { contentDescription = description; selected = lit }
                .skinClick(interaction) {
                    haptics?.click()
                    onClick()
                }
                .then(
                    if (framed || pressed) {
                        Modifier
                            .background(
                                if (lit) Brush.verticalGradient(listOf(Color(0xFF6CB0F0), Color(0xFF2F74C0))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (pressed) 0.35f else 0.2f), Color.White.copy(alpha = 0.06f))),
                                CircleShape,
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(side * 0.52f))
        }
    }

    /** A song as a little card. Flick it up and it's thrown away - skipped for the next song available. */
    @Composable
    private fun SongCard(song: Song, onFlick: () -> Unit, onHold: () -> Unit) {
        val lift = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val haptics = LocalHaptics.current
        val currentOnFlick by rememberUpdatedState(onFlick)
        Column(
            Modifier
                .width(132.dp)
                .height(150.dp)
                .graphicsLayer {
                    translationY = lift.value
                    alpha = (1f + lift.value / 400f).coerceIn(0f, 1f)
                }
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFD9D9D9))), RoundedCornerShape(12.dp))
                .pointerInput(song.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (lift.value < -70.dp.toPx()) {
                                    haptics?.click()
                                    lift.animateTo(-900f, tween(220))
                                    currentOnFlick()
                                    lift.snapTo(0f)
                                } else {
                                    lift.animateTo(0f, spring(dampingRatio = 0.7f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { lift.animateTo(0f) } },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            scope.launch { lift.snapTo((lift.value + delta).coerceAtMost(20f)) }
                        },
                    )
                }
                .onTapOrHold(onHold = onHold, holdLabel = "Edit tags")
                .padding(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFF9BD0F5), Color(0xFF5A86C8)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(song.displayName, style = prelude(13.5f, FontWeight.SemiBold), color = W.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = prelude(11.5f), color = W.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    override fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier) {
        val song = model.song
        // A notification-bar-like strip at the card's foot: dark, with the song and its controls.
        Column(
            modifier
                .fillMaxWidth()
                .padding(10.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF4A5057), Color(0xFF23272B))), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        song?.displayName ?: if (model.playbackRestored) "Nothing playing" else "",
                        prelude(16f, FontWeight.SemiBold),
                        Modifier.fillMaxWidth().then(if (song != null && model.onHoldTitle != null) Modifier.onTapOrHold(onHold = { model.onHoldTitle.invoke(song) }, holdLabel = "Edit tags") else Modifier),
                        color = Color.White,
                    )
                    Text(song?.artist.orEmpty(), style = prelude(12.5f), color = Color(0xFFC8C8C8), maxLines = 1)
                }
                if (model.onOpenSettings != null) {
                    Box(Modifier.testTag("nav:settings")) { CommandButton(Icons.Filled.Settings, "Settings", framed = false, onClick = model.onOpenSettings) }
                }
                CommandButton(Icons.Filled.SkipPrevious, "Previous", framed = false, onClick = model.onPrevious)
                CommandButton(if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (model.isPlaying) "Pause" else "Play", framed = true, onClick = model.onPlayPause)
                CommandButton(Icons.Filled.SkipNext, "Next", framed = false, onClick = model.onNext)
            }
            if (song != null && model.durationMs > 0) {
                SeekBar(model.positionMs.toFloat() / model.durationMs, { model.onSeek((it * model.durationMs).toLong()) }, Modifier.fillMaxWidth().height(14.dp)) { f ->
                    val h = 4.dp.toPx()
                    val top = (size.height - h) / 2f
                    drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
                    drawRoundRect(Color(0xFF6CB0F0), Offset(0f, top), Size(size.width * f, h), CornerRadius(h / 2f))
                }
            }
        }
    }
}
