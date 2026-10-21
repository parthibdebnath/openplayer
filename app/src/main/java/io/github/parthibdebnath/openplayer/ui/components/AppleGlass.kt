package io.github.parthibdebnath.openplayer.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import android.graphics.Paint as PlatformPaint
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.Shader as PlatformShader

/*
 * Liquid Glass, after Apple's.
 *
 * What Apple describes for the material (WWDC25, "Meet Liquid Glass"), and how each piece is built
 * here - the construction follows Kyant's AndroidLiquidGlass, an open-source recreation:
 *
 *  - Lensing. Glass bends what's behind it rather than scattering it. Behind the flat face the
 *    backdrop shows through undisturbed (lightly blurred, and more saturated - "vibrancy"); across
 *    the curved bezel it's pulled in from further inside, following a circular profile, with a
 *    slight split of colour where the bend is strongest.
 *  - Specular highlights. A hairline of light along the rim, added rather than painted on, lit
 *    where the edge faces a light from the top left - and on the opposite edge, where that light
 *    leaves the glass - and dark along the edges at right angles to it.
 *  - Shadow. A soft, faint shadow grounds the element; larger elements cast deeper ones.
 *  - Interaction. Touch lights the glass from beneath the finger outwards, and it swells a little.
 *    (Apple's also leans towards a drag like a gel; here it doesn't, so controls feel solid.)
 *
 * Where glass may go follows Apple's rule too: only the floating layer of controls - buttons, the
 * tab bar, dialogs. Panels, fields and badges in the content get plain translucent fills instead,
 * since glass on content (and glass on glass) is what makes it look busy.
 *
 * Glass that floats outside the content (the tab bar) refracts a recording of the screen. Glass
 * inside the content can't record the content it's part of; here that content is the flat page
 * colour, so its backdrop is that colour, put through the same vibrancy.
 *
 * Refraction needs Android 13 (RuntimeShader), blur and vibrancy Android 12. Below that the glass
 * keeps its surface, highlight and shadow, as Apple's does under Reduce Transparency.
 */

/** Whether the Apple-style glass is in use. False brings back the previous glass (LiquidGlass.kt). */
const val USE_APPLE_GLASS = true

/** What a glass surface is, which decides how much of the material it gets. */
enum class GlassRole {
    /** A control in the floating layer - a button. Full glass. */
    Control,

    /** A larger floating element - a dialog or notification. Glass with a deeper shadow. */
    Floating,

    /** Part of the content - a panel, field or badge. A plain translucent fill, no glass. */
    Container,
}

// --- Shaders --------------------------------------------------------------------------------

private const val RoundedRectSdf = """
float sdRoundedRect(float2 p, float2 halfSize, float radius) {
    float2 c = abs(p) - (halfSize - float2(radius));
    return length(max(c, 0.0)) - radius + min(max(c.x, c.y), 0.0);
}

// The outward direction at p, smoothed across the corners by evaluating with a larger radius.
float2 gradRoundedRect(float2 p, float2 halfSize, float radius) {
    float2 c = abs(p) - (halfSize - float2(radius));
    if (c.x >= 0.0 || c.y >= 0.0) {
        float2 m = max(c, 0.0);
        if (length(m) < 0.0001) return float2(0.0, sign(p.y));
        return sign(p) * normalize(m);
    }
    float gx = step(c.y, c.x);
    return sign(p) * float2(gx, 1.0 - gx);
}
"""

/** Refraction across the bezel: content is sampled from further inside along a circular profile. */
private const val LensShader = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float height;
uniform float amount;
uniform float dispersion;
$RoundedRectSdf
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - origin - halfSize;
    float sd = sdRoundedRect(p, halfSize, radius);
    if (-sd >= height) return content.eval(coord);
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / height) * amount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradRoundedRect(p, halfSize, gradRadius));
    float2 refracted = coord - d * grad;
    if (dispersion <= 0.0) return content.eval(refracted);

    // A prism's spread: seven bands of the spectrum, each displaced a little differently, the
    // spread growing towards the corners where the bend is diagonal.
    float spread = dispersion * (p.x * p.y) / (halfSize.x * halfSize.y);
    float2 dc = -d * grad * spread;
    half4 color = half4(0.0);
    half4 s = content.eval(refracted + dc);
    color.r += s.r / 3.5; color.a += s.a / 7.0;
    s = content.eval(refracted + dc * (2.0 / 3.0));
    color.r += s.r / 3.5; color.g += s.g / 7.0; color.a += s.a / 7.0;
    s = content.eval(refracted + dc * (1.0 / 3.0));
    color.r += s.r / 3.5; color.g += s.g / 3.5; color.a += s.a / 7.0;
    s = content.eval(refracted);
    color.g += s.g / 3.5; color.a += s.a / 7.0;
    s = content.eval(refracted - dc * (1.0 / 3.0));
    color.g += s.g / 3.5; color.b += s.b / 3.0; color.a += s.a / 7.0;
    s = content.eval(refracted - dc * (2.0 / 3.0));
    color.b += s.b / 3.0; color.a += s.a / 7.0;
    s = content.eval(refracted - dc);
    color.r += s.r / 7.0; color.b += s.b / 3.0; color.a += s.a / 7.0;
    return color;
}
"""

/** The rim's light: strongest where the edge faces along the light's axis, either way. */
private const val HighlightShader = """
uniform float2 size;
uniform float radius;
uniform float2 light;
$RoundedRectSdf

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradRoundedRect(p, halfSize, gradRadius);
    float intensity = abs(dot(grad, light));
    return half4(intensity, intensity, intensity, intensity);
}
"""

/** Light spreading from the fingertip. */
private const val GlowShader = """
uniform float2 position;
uniform float radius;
uniform half alpha;

half4 main(float2 coord) {
    float intensity = smoothstep(radius, radius * 0.5, distance(coord, position)) * alpha;
    return half4(intensity, intensity, intensity, intensity);
}
"""

// --- Material constants ---------------------------------------------------------------------

/** The light comes from the top left: 45 degrees, pointing down-right. */
private const val LIGHT_X = 0.70710677f
private const val LIGHT_Y = 0.70710677f

private val HighlightWidth = 0.5.dp
private val HighlightBlur = 0.25.dp
private const val HIGHLIGHT_ALPHA = 0.5f

/** Saturation boost applied to whatever is seen through the glass. */
private const val VIBRANCY = 1.5f

/** Apple's system fills, used where glass doesn't belong. */
private val FillDark = Color(0xFF787880).copy(alpha = 0.24f)
private val FillLight = Color(0xFF767680).copy(alpha = 0.12f)

/** A saturation colour matrix, as Core Image's colour controls build it. */
private fun saturationMatrix(saturation: Float): FloatArray {
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    return floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private val VibrancyMatrix = saturationMatrix(VIBRANCY)

/** The page colour as it looks through glass: the same vibrancy, worked out directly. */
internal fun vibrant(color: Color): Color {
    val m = VibrancyMatrix
    val r = (m[0] * color.red + m[1] * color.green + m[2] * color.blue).coerceIn(0f, 1f)
    val g = (m[5] * color.red + m[6] * color.green + m[7] * color.blue).coerceIn(0f, 1f)
    val b = (m[10] * color.red + m[11] * color.green + m[12] * color.blue).coerceIn(0f, 1f)
    return Color(r, g, b, color.alpha)
}

/** The plain fill that content-layer surfaces get instead of glass. */
internal fun contentFill(lightBackground: Boolean): Color = if (lightBackground) FillLight else FillDark

// --- The material ---------------------------------------------------------------------------

/** How one piece of glass is made up. Sizes are in dp; zero switches a part off. */
@Stable
internal data class GlassSpec(
    val blur: Dp = 2.dp,
    val lensHeight: Dp = 12.dp,
    val lensAmount: Dp = 24.dp,
    val dispersion: Boolean = false,
    val vibrancy: Boolean = true,
    val surface: Color = Color.Transparent,
    val highlightAlpha: Float = 1f,
    val shadowRadius: Dp = 24.dp,
    val shadowAlpha: Float = 0.1f,
    val innerShadowRadius: Dp = 0.dp,
    val innerShadowAlpha: Float = 0f,
)

/** Where a piece of glass finds what's behind it. */
internal class GlassSource(
    /** The recording to refract, or null for a flat page colour. */
    val layer: () -> GraphicsLayer?,
    /** This glass's top-left in the recording's coordinates. */
    val offset: () -> Offset,
    /** The page colour, used when there's no recording. */
    val flatColor: Color,
)

/** The resources one piece of glass draws with; held across frames. */
@Stable
internal class GlassResources(
    val backdrop: GraphicsLayer,
    val shadow: GraphicsLayer,
) {
    val lens: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LensShader) else null
    val highlight: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(HighlightShader) else null
    val glow: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(GlowShader) else null
    val path = Path()
    val paint = PlatformPaint(PlatformPaint.ANTI_ALIAS_FLAG)
}

@Composable
internal fun rememberGlassResources(): GlassResources {
    val backdrop = rememberGraphicsLayer()
    val shadow = rememberGraphicsLayer()
    return remember(backdrop, shadow) {
        shadow.compositingStrategy = CompositingStrategy.Offscreen
        GlassResources(backdrop, shadow)
    }
}

private fun DrawScope.radiusOf(shape: Shape): Float =
    ((shape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 0f).coerceAtMost(size.minDimension / 2f)

private fun DrawScope.outlinePath(shape: Shape, into: Path): Path {
    into.reset()
    val r = radiusOf(shape)
    into.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r, r))
    return into
}

/** The soft shadow, cut away beneath the glass itself so it never darkens what shows through. */
private fun DrawScope.drawGlassShadow(res: GlassResources, path: Path, radius: Dp, alpha: Float) {
    if (alpha <= 0f || radius.value <= 0f) return
    val r = radius.toPx()
    val dy = r / 6f
    val pad = ceil(r * 2f)
    res.shadow.record(IntSize((size.width + pad * 2).toInt(), (size.height + pad * 2 + dy).toInt())) {
        translate(pad, pad) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val paint = res.paint.apply {
                    reset()
                    isAntiAlias = true
                    color = Color.Black.copy(alpha = alpha).toArgb()
                    maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
                }
                native.save()
                native.translate(0f, dy)
                native.drawPath(path.asAndroidPath(), paint)
                native.restore()
                paint.maskFilter = null
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                native.drawPath(path.asAndroidPath(), paint)
                paint.xfermode = null
            }
        }
    }
    res.shadow.topLeft = IntOffset(-pad.toInt(), -pad.toInt())
    drawLayer(res.shadow)
}

/** What's behind the glass, as the glass shows it: refracted, blurred, more vivid. */
private fun DrawScope.drawGlassBackdrop(res: GlassResources, shape: Shape, path: Path, spec: GlassSpec, source: GlassSource) {
    val layer = source.layer()
    if (layer == null) {
        // A flat colour refracts to itself; all that changes through glass is its vibrancy.
        val flat = source.flatColor
        if (flat.alpha > 0f) drawPath(path, if (spec.vibrancy) vibrant(flat) else flat)
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // No render effects before Android 12: what's behind shows through as it is.
        val offset = source.offset()
        clipPath(path) {
            if (source.flatColor.alpha > 0f) drawRect(source.flatColor)
            translate(-offset.x, -offset.y) { drawLayer(layer) }
        }
        return
    }
    val blur = spec.blur.toPx()
    val pad = ceil(blur * 2f)
    val offset = source.offset()
    res.backdrop.record(IntSize((size.width + pad * 2).toInt().coerceAtLeast(1), (size.height + pad * 2).toInt().coerceAtLeast(1))) {
        // The page colour first: the recording is transparent wherever nothing was drawn, and
        // glass refracts what's really there - the page - not a hole.
        if (source.flatColor.alpha > 0f) drawRect(source.flatColor)
        translate(pad - offset.x, pad - offset.y) { drawLayer(layer) }
    }
    var effect: PlatformRenderEffect? = null
    if (spec.vibrancy) effect = PlatformRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(VibrancyMatrix))
    if (blur > 0.5f) {
        effect = if (effect == null) {
            PlatformRenderEffect.createBlurEffect(blur, blur, PlatformShader.TileMode.CLAMP)
        } else {
            PlatformRenderEffect.createBlurEffect(blur, blur, effect, PlatformShader.TileMode.CLAMP)
        }
    }
    val lensHeight = spec.lensHeight.toPx()
    val lensAmount = spec.lensAmount.toPx()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensHeight > 0.5f && lensAmount > 0.5f) {
        val lens = configureLens(res, Offset(pad, pad), size, radiusOf(shape), lensHeight, lensAmount, spec.dispersion)
        effect = if (effect == null) lens else PlatformRenderEffect.createChainEffect(lens, effect)
    }
    res.backdrop.renderEffect = effect?.asComposeRenderEffect()
    res.backdrop.topLeft = IntOffset(-pad.toInt(), -pad.toInt())
    clipPath(path) { drawLayer(res.backdrop) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun configureLens(
    res: GlassResources,
    origin: Offset,
    size: Size,
    radius: Float,
    height: Float,
    amount: Float,
    dispersion: Boolean,
): PlatformRenderEffect {
    val shader = res.lens as RuntimeShader
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("origin", origin.x, origin.y)
    shader.setFloatUniform("radius", radius)
    shader.setFloatUniform("height", height)
    shader.setFloatUniform("amount", amount)
    shader.setFloatUniform("dispersion", if (dispersion) 1f else 0f)
    return PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** Touch light: a soft glow centred on the finger, over a faint lift of the whole surface. */
private fun DrawScope.drawGlassGlow(res: GlassResources, path: Path, progress: Float, position: Offset) {
    if (progress <= 0.001f) return
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = res.paint.apply {
            reset()
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            color = Color.White.copy(alpha = 0.08f * progress).toArgb()
        }
        native.drawPath(path.asAndroidPath(), paint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = res.glow as RuntimeShader
            shader.setFloatUniform("position", position.x.coerceIn(0f, size.width), position.y.coerceIn(0f, size.height))
            shader.setFloatUniform("radius", size.minDimension * 1.5f)
            shader.setFloatUniform("alpha", 0.15f * progress)
            paint.color = android.graphics.Color.WHITE
            paint.shader = shader
            native.drawPath(path.asAndroidPath(), paint)
            paint.shader = null
        }
        paint.xfermode = null
    }
}

/** The lit rim: a hairline inside the edge, added to what's beneath. */
private fun DrawScope.drawGlassHighlight(res: GlassResources, shape: Shape, path: Path, alpha: Float) {
    if (alpha <= 0.001f) return
    val width = ceil(HighlightWidth.toPx().coerceAtMost(size.minDimension / 2f)) * 2f
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = res.paint.apply {
            reset()
            isAntiAlias = true
            style = PlatformPaint.Style.STROKE
            strokeWidth = width
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            maskFilter = BlurMaskFilter(HighlightBlur.toPx().coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = res.highlight as RuntimeShader
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", radiusOf(shape))
            shader.setFloatUniform("light", LIGHT_X, LIGHT_Y)
            paint.shader = shader
            paint.color = Color.White.copy(alpha = HIGHLIGHT_ALPHA * alpha).toArgb()
        } else {
            paint.color = Color.White.copy(alpha = 0.38f * alpha).toArgb()
        }
        native.save()
        native.clipPath(path.asAndroidPath())
        native.drawPath(path.asAndroidPath(), paint)
        native.restore()
        paint.shader = null
        paint.maskFilter = null
        paint.xfermode = null
        paint.style = PlatformPaint.Style.FILL
    }
}

/** A shadow inside the glass along its top, for glass that has lifted off the surface. */
private fun DrawScope.drawGlassInnerShadow(res: GlassResources, path: Path, radius: Dp, alpha: Float) {
    if (alpha <= 0.001f || radius.value <= 0.1f) return
    val r = radius.toPx()
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.clipPath(path.asAndroidPath())
        val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
        native.drawColor(Color.Black.copy(alpha = 0.15f * alpha).toArgb())
        val paint = res.paint.apply {
            reset()
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
        }
        native.translate(0f, r)
        native.drawPath(path.asAndroidPath(), paint)
        native.restoreToCount(layer)
        native.restore()
        paint.xfermode = null
        paint.maskFilter = null
    }
}

/**
 * Draws a piece of glass behind this element's content: shadow, backdrop, surface, touch light,
 * then the content, then the rim light and any inner shadow on top - the order Apple layers them.
 */
internal fun Modifier.drawGlass(
    res: GlassResources,
    shape: Shape,
    spec: () -> GlassSpec,
    source: GlassSource,
    press: () -> Float = { 0f },
    pressPosition: () -> Offset = { Offset.Zero },
): Modifier = drawWithContent {
    drawGlassLayers(res, shape, spec(), source, press(), pressPosition()) { this@drawWithContent.drawContent() }
}

/** [drawGlass]'s layers, for glass that decides frame by frame whether to draw as glass at all. */
internal fun DrawScope.drawGlassLayers(
    res: GlassResources,
    shape: Shape,
    s: GlassSpec,
    source: GlassSource,
    press: Float,
    pressPosition: Offset,
    content: DrawScope.() -> Unit,
) {
    val path = outlinePath(shape, res.path)
    drawGlassShadow(res, path, s.shadowRadius, s.shadowAlpha)
    drawGlassBackdrop(res, shape, path, s, source)
    if (s.surface.alpha > 0f) drawPath(path, s.surface)
    drawGlassGlow(res, path, press, pressPosition)
    content()
    drawGlassHighlight(res, shape, path, s.highlightAlpha)
    drawGlassInnerShadow(res, path, s.innerShadowRadius, s.innerShadowAlpha)
}

// --- Touch ------------------------------------------------------------------------------------

/** Press state for a glass control: how far it's pressed in, and where the finger is. */
@Stable
internal class GlassPress(private val scope: CoroutineScope) {
    // No overshoot: a bouncier spring made the swell wobble, like jelly rather than glass.
    private val spec = spring<Float>(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = 0.001f)
    private val offsetSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = Offset(0.5f, 0.5f))
    val progress = Animatable(0f, 0.001f)
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    var start = Offset.Zero
        private set

    fun down(at: Offset) {
        start = at
        scope.launch { position.snapTo(at) }
        scope.launch { progress.animateTo(1f, spec) }
    }

    fun move(to: Offset) {
        scope.launch { position.snapTo(to) }
    }

    fun up() {
        scope.launch { progress.animateTo(0f, spec) }
        scope.launch { position.animateTo(start, offsetSpec) }
    }
}

@Composable
internal fun rememberGlassPress(): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPress(scope) }
}

/**
 * Watches touches without taking them, so the element's own click handling still works, and
 * feeds them to [press].
 */
internal fun Modifier.glassPressInput(press: GlassPress): Modifier = pointerInput(press) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        press.down(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            press.move(change.position)
        }
        press.up()
    }
}

/**
 * A small, even swell while pressed. Only that: an earlier version also leaned the control towards
 * a drag and stretched it along it, which made buttons feel soft and malleable rather than solid.
 */
internal fun Modifier.glassFlex(press: GlassPress, swell: Dp = 4.dp): Modifier = graphicsLayer {
    val h = size.height
    if (h <= 0f) return@graphicsLayer
    val scale = lerp(1f, 1f + swell.toPx() / h, press.progress.value)
    scaleX = scale
    scaleY = scale
}

// --- Surfaces -----------------------------------------------------------------------------------

/** Glass for a control or a dialog sitting on the flat page. */
@Composable
internal fun Modifier.appleGlassSurface(
    shape: Shape,
    role: GlassRole,
    background: Color,
    lightBackground: Boolean,
    opacity: Float,
): Modifier {
    if (role == GlassRole.Container) {
        val fill = contentFill(lightBackground)
        return this.drawWithContent {
            drawPath(outlinePath(shape, Path()), fill.copy(alpha = fill.alpha * opacity))
            drawContent()
        }
    }
    val res = rememberGlassResources()
    val press = rememberGlassPress()
    val floating = role == GlassRole.Floating
    // Regular glass keeps a faint frost of its own over a flat colour, so it still reads as a
    // surface there; lighter on light pages, where it otherwise vanishes into them.
    val surface = when {
        lightBackground -> Color.White.copy(alpha = (if (floating) 0.45f else 0.3f) * opacity)
        else -> Color.White.copy(alpha = (if (floating) 0.1f else 0.06f) * opacity)
    }
    val spec = GlassSpec(
        surface = surface,
        // Bigger elements cast deeper shadows; over a light page they're kept lighter.
        shadowRadius = if (floating) 40.dp else 24.dp,
        shadowAlpha = (if (floating) 0.16f else 0.1f) * (if (lightBackground) 0.7f else 1f),
    )
    val source = remember(background) { GlassSource(layer = { null }, offset = { Offset.Zero }, flatColor = background) }
    return this
        .glassFlex(press, swell = if (floating) 0.dp else 4.dp)
        .drawGlass(res, shape, { spec }, source, press = { press.progress.value }, pressPosition = { press.position.value })
        .glassPressInput(press)
}
