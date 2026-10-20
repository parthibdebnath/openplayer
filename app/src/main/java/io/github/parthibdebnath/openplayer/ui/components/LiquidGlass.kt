package io.github.parthibdebnath.openplayer.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.Shader as PlatformShader

/*
 * Liquid glass: a curved slab of glass rather than a frosted panel.
 *
 * Three things make glass read as glass, and the old implementation - a blur with a grey tint -
 * had none of them:
 *  - Refraction. Light bends through the curved rim, so whatever is behind the edge is pulled in
 *    and distorted, with the colours splitting slightly the way a prism splits them. The flat
 *    middle shows what's behind it almost undisturbed.
 *  - A lit bevel. The rim catches light from above-left: a thin bright specular line along the
 *    edges that face it, a fainter one on the far side, and a soft glow across the curve.
 *  - Depth. The body is faintly brighter at the top than the bottom.
 *
 * Everything is drawn by one AGSL shader working from the shape's signed distance field, so the
 * bevel follows any corner radius exactly. Glass that floats over the screen (the tab bar, the
 * gesture overlay) refracts a recording of the content behind it; glass that sits inside the
 * content - buttons, panels, text fields - is drawn as a translucent material over whatever is
 * really underneath it, since it can't record the very content it's part of.
 *
 * RuntimeShader needs Android 13. Below that the same look is approximated with gradients.
 */

private const val GLASS_SHADER = """
uniform shader content;
uniform float2 glassOrigin;
uniform float2 glassSize;
uniform float cornerRadius;
uniform float bezel;
uniform float refraction;
uniform float dispersion;
uniform float magnify;
uniform float hasContent;
layout(color) uniform half4 body;
layout(color) uniform half4 tint;
uniform float highlight;
uniform float shade;
uniform float2 light;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 halfSize = glassSize * 0.5;
    float2 center = glassOrigin + halfSize;
    float2 p = coord - center;
    float r = min(cornerRadius, min(halfSize.x, halfSize.y));
    float d = sdRoundRect(p, halfSize, r);
    if (d > 1.0) return half4(0.0);
    float coverage = clamp(0.5 - d, 0.0, 1.0);

    // Outward surface normal, from the distance field's gradient.
    float e = 0.75;
    float2 grad = float2(
        sdRoundRect(p + float2(e, 0.0), halfSize, r) - sdRoundRect(p - float2(e, 0.0), halfSize, r),
        sdRoundRect(p + float2(0.0, e), halfSize, r) - sdRoundRect(p - float2(0.0, e), halfSize, r));
    float2 n = length(grad) > 0.0001 ? normalize(grad) : float2(0.0, -1.0);

    // 1 right at the rim, falling to 0 where the curved bevel meets the flat face.
    float inset = -d;
    float t = clamp(1.0 - inset / max(bezel, 1.0), 0.0, 1.0);
    // A convex lens bends hardest at its very edge.
    float bend = refraction * t * t * t;

    half3 col;
    half alpha;
    if (hasContent > 0.5) {
        float2 base = center + p / max(magnify, 0.01);
        float2 offs = n * bend;
        half4 cr = content.eval(base + offs * (1.0 + dispersion));
        half4 cg = content.eval(base + offs);
        half4 cb = content.eval(base + offs * (1.0 - dispersion));
        half4 c = half4(cr.r, cg.g, cb.b, cg.a);
        // The recording is transparent wherever nothing was drawn; the body shows through there.
        col = c.rgb + body.rgb * body.a * (1.0 - c.a);
        alpha = 1.0;
        col = col / max(alpha, 0.001);
    } else {
        col = body.rgb;
        alpha = body.a;
    }
    col = mix(col, tint.rgb, tint.a);

    // A slight top-to-bottom falloff gives the slab depth.
    col += half3((0.5 - (p.y + halfSize.y) / glassSize.y) * 0.05);

    float facing = dot(n, normalize(light));
    float lit = max(facing, 0.0);
    float far = max(-facing, 0.0);
    // A crisp line right on the rim, all the way round: brightest where it faces the light, and
    // bright again on the far side, where light that entered the glass leaves it.
    float rim = smoothstep(1.8, 0.0, inset);
    float rimLight = rim * (0.28 + 0.72 * pow(lit, 1.5) + 0.55 * pow(far, 2.0)) * highlight;
    // A soft reflection band along the lit side of the bevel.
    float band = smoothstep(bezel * 0.75, 0.0, inset) * pow(lit, 3.0) * highlight * 0.32;
    // Fresnel: glass reflects more the more obliquely it's seen, so the curve brightens towards
    // the edge all round.
    float fresnel = t * t * t * highlight * 0.16;
    col += half3(rimLight + band + fresnel);
    // On light backgrounds a white rim disappears, so the side away from the light darkens.
    col -= half3(rim * far * shade * 0.35 + t * t * far * shade * 0.08);
    alpha = clamp(alpha + rimLight * 0.9 + band * 0.7 + fresnel * 0.6 + rim * far * shade * 0.3, 0.0, 1.0);

    return half4(clamp(col, 0.0, 1.0) * alpha * coverage, alpha * coverage);
}
"""

/** How strongly a piece of glass bends and lights. */
@Stable
data class GlassStyle(
    /** Width of the curved rim. */
    val bezel: Dp = 10.dp,
    /** How far the rim pulls in what's behind it. Only seen where there's content behind. */
    val refraction: Dp = 14.dp,
    /** Colour split at the rim, as a fraction of [refraction]. */
    val dispersion: Float = 0.12f,
    /** Zoom of the flat middle; above 1 makes the glass a magnifier. */
    val magnify: Float = 1f,
    val highlight: Float = 0.55f,
    /** Blur of what's behind, for glass that refracts a recording. */
    val blur: Dp = 0.dp,
)

/** A recording of a screen's content, for glass floating over that screen to refract. */
@Stable
class GlassBackdrop {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var originInRoot: Offset by mutableStateOf(Offset.Zero)
}

/** The backdrop glass drawn over the current screen samples; set by [Modifier.glassBackdropSource]. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Records this content each frame so floating glass can refract it. The recording is the same
 * display list that draws the content, so it costs no extra rendering.
 */
@Composable
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier {
    val layer = rememberGraphicsLayer()
    SideEffect { backdrop.layer = layer }
    return this
        .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

internal fun supportsShaderGlass(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** The colours glass is built from, for a page of the given lightness. */
internal data class GlassColors(val body: Color, val tint: Color, val shade: Float)

internal fun glassColors(lightBackground: Boolean, opacity: Float): GlassColors =
    if (lightBackground) {
        GlassColors(body = Color.White.copy(alpha = 0.42f * opacity), tint = Color.Transparent, shade = 1f)
    } else {
        GlassColors(body = Color.White.copy(alpha = 0.085f * opacity), tint = Color.Transparent, shade = 0f)
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassShader {
    val shader = RuntimeShader(GLASS_SHADER)

    fun configure(
        drawScope: DrawScope,
        origin: Offset,
        size: Size,
        cornerRadius: Float,
        style: GlassStyle,
        colors: GlassColors,
        hasContent: Boolean,
    ) = with(drawScope) {
        shader.setFloatUniform("glassOrigin", origin.x, origin.y)
        shader.setFloatUniform("glassSize", size.width, size.height)
        shader.setFloatUniform("cornerRadius", cornerRadius)
        shader.setFloatUniform("bezel", style.bezel.toPx())
        shader.setFloatUniform("refraction", if (hasContent) style.refraction.toPx() else 0f)
        shader.setFloatUniform("dispersion", style.dispersion)
        shader.setFloatUniform("magnify", style.magnify)
        shader.setFloatUniform("hasContent", if (hasContent) 1f else 0f)
        shader.setColorUniform("body", colors.body.toArgb())
        shader.setColorUniform("tint", colors.tint.toArgb())
        shader.setFloatUniform("highlight", style.highlight)
        shader.setFloatUniform("shade", colors.shade)
        // Light from the top left; y grows downward.
        shader.setFloatUniform("light", -0.45f, -1f)
    }
}

/**
 * Glass drawn as a material over whatever is underneath it - for glass that sits inside the
 * content rather than floating over it. Translucent, so anything really behind it still shows.
 */
@Composable
internal fun Modifier.liquidGlassMaterial(
    shape: Shape,
    lightBackground: Boolean,
    opacity: Float = 1f,
    style: GlassStyle = GlassStyle(),
): Modifier {
    val colors = glassColors(lightBackground, opacity)
    if (!supportsShaderGlass()) return this.drawBehind { drawGradientGlass(cornerRadiusOf(shape), colors, style) }
    val glass = remember { GlassShader() }
    val brush = remember(glass) { ShaderBrush(glass.shader) }
    return this.drawBehind {
        glass.configure(this, Offset.Zero, size, cornerRadiusOf(shape), style, colors, hasContent = false)
        drawRect(brush)
    }
}

/** The corner radius of a rounded shape at this size, in pixels; glass is always a rounded rectangle. */
internal fun DrawScope.cornerRadiusOf(shape: Shape): Float =
    (shape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 0f

/**
 * Glass that refracts a recorded [layer] - the screen behind it, or the labels a selector lens
 * slides over. [layerOffset] is where this glass's top-left sits in the layer's own coordinates.
 *
 * The recording is drawn with a margin around the glass, so the rim has something beyond the
 * edge to pull in.
 */
@Composable
internal fun Modifier.liquidGlassLens(
    layer: () -> GraphicsLayer?,
    layerOffset: () -> Offset,
    shape: Shape,
    lightBackground: Boolean,
    style: GlassStyle,
    opacity: Float = 1f,
    bodyOverride: Color? = null,
): Modifier {
    val colors = glassColors(lightBackground, opacity).let { if (bodyOverride != null) it.copy(body = bodyOverride) else it }
    if (!supportsShaderGlass()) return this.drawBehind { drawGradientGlass(cornerRadiusOf(shape), colors, style) }
    val glass = remember { GlassShader() }
    val lens = rememberGraphicsLayer()
    return this.drawBehind {
        val source = layer()
        if (source == null) {
            drawGradientGlass(cornerRadiusOf(shape), colors, style)
            return@drawBehind
        }
        val margin = (style.refraction.toPx() + style.blur.toPx() * 2f).coerceAtLeast(1f)
        val recordSize = IntSize((size.width + margin * 2).toInt(), (size.height + margin * 2).toInt())
        if (recordSize.width <= 0 || recordSize.height <= 0) return@drawBehind
        val offset = layerOffset()
        lens.record(recordSize) {
            translate(margin - offset.x, margin - offset.y) { drawLayer(source) }
        }
        glass.configure(this, Offset(margin, margin), size, cornerRadiusOf(shape), style, colors, hasContent = true)
        val shaderEffect = PlatformRenderEffect.createRuntimeShaderEffect(glass.shader, "content")
        val blurPx = style.blur.toPx()
        lens.renderEffect = if (blurPx > 0.5f) {
            PlatformRenderEffect.createChainEffect(
                shaderEffect,
                PlatformRenderEffect.createBlurEffect(blurPx, blurPx, PlatformShader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        } else {
            shaderEffect.asComposeRenderEffect()
        }
        lens.topLeft = IntOffset(-margin.toInt(), -margin.toInt())
        drawLayer(lens)
    }
}

/** Android 12 and earlier: the same body, sheen and lit rim, drawn with plain gradients. */
private fun DrawScope.drawGradientGlass(cornerRadius: Float, colors: GlassColors, style: GlassStyle) {
    val radius = CornerRadius(cornerRadius.coerceAtMost(size.minDimension / 2f))
    drawRoundRect(color = colors.body, cornerRadius = radius)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.06f),
            1f to Color.Transparent,
        ),
        cornerRadius = radius,
    )
    val rimWidth = 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = style.highlight * 0.9f),
            0.45f to Color.White.copy(alpha = style.highlight * 0.15f),
            1f to (if (colors.shade > 0f) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = style.highlight * 0.35f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(rimWidth / 2, rimWidth / 2),
        size = Size(size.width - rimWidth, size.height - rimWidth),
        cornerRadius = radius,
        style = Stroke(width = rimWidth),
    )
}

/**
 * Draws the screen behind this element, blurred - for full-screen overlays that frost the whole
 * page. Android 12+ only; below that the overlay's own scrim carries it.
 */
@Composable
fun Modifier.glassBackdropBlur(blur: Dp): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return this
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val frosted = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val source = backdrop.layer ?: return@drawBehind
            val offset = origin - backdrop.originInRoot
            frosted.record { translate(-offset.x, -offset.y) { drawLayer(source) } }
            frosted.renderEffect = BlurEffect(blur.toPx(), blur.toPx(), TileMode.Clamp)
            drawLayer(frosted)
        }
}
