package com.quranreminder.app.ui.theme

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Liquid-glass geometry shared between the floating tab bar and the screen content under it.
 * The bar publishes where it is and where its lens is (root coordinates); the content layer
 * reads it to frost what lies under the bar and to refract what lies under the lens.
 */
@Stable
class NurGlassState {
    /** Bar track bounds in root coordinates. */
    var bar by mutableStateOf(Rect.Zero)
    var barRadius by mutableFloatStateOf(0f)
    /** Lens capsule: centre and half-size in root coordinates. */
    var lensCenter by mutableStateOf(Offset.Zero)
    var lensHalf by mutableStateOf(Offset.Zero)
    /** True while the lens is up (the bar is being touched or the selection is travelling). */
    var lensOn by mutableStateOf(false)
    /** 0..1: how far the lens has grown out of the resting pill (refraction fades with it). */
    var lensStrength by mutableFloatStateOf(0f)
    /** The bar is on screen (top-level routes). */
    var visible by mutableStateOf(false)
    /** Root offset of the content layer the backdrop shader runs on. */
    var contentOrigin by mutableStateOf(Offset.Zero)
}

val LocalGlassState = staticCompositionLocalOf<NurGlassState?> { null }

/** True where the real (shader) glass can run: RuntimeShader needs Android 13. */
val nurGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/*
 * The lens is a capsule of clear glass, like iOS's: the middle is a window with a slight
 * magnification, and only a band along the rim bends light — content from just outside the
 * capsule is pulled into the rim and compressed along it, with a faint colour split there.
 * No fisheye: text in the middle stays straight and readable.
 */
private const val LENS_FN = """
uniform float4 lens;      // cx, cy, half width, half height (layer px)
uniform float lensOn;
uniform float rim;        // width of the bending band (px)
uniform float strength;   // 0..1: the lens fades into the resting pill by this

float sdLens(float2 p) {
    float r = lens.w;                         // capsule: corner radius = half height
    float2 q = abs(p - lens.xy) - lens.zw + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

bool inLens(float2 p) {
    return lensOn > 0.5 && sdLens(p) < 0.0;
}

half4 lensSample(float2 p) {   // samples the global `content` shader
    float inside = -sdLens(p);                          // px from the rim, > 0
    float t = clamp(1.0 - inside / rim, 0.0, 1.0);      // 0 in the window, 1 at the rim
    float2 e = float2(0.75, 0.0);
    float2 n = float2(sdLens(p + e.xy) - sdLens(p - e.xy), sdLens(p + e.yx) - sdLens(p - e.yx));
    n = n / max(length(n), 0.0001);                     // outward normal
    float2 c = lens.xy;
    float2 base = c + (p - c) * mix(1.0, 0.92, strength);  // ~1.09x in the window
    float bend = t * t * rim * 0.85 * strength;             // rim: reach outside and squeeze in
    float2 s = base + n * bend;
    float split = 1.6 * t * t * t * strength;
    half4 cg = content.eval(s);
    if (split < 0.05) return cg;
    half4 cr = content.eval(s + n * split);
    half4 cb = content.eval(s - n * split);
    return half4(cr.r, cg.g, cb.b, max(cg.a, max(cr.a, cb.a)));
}
"""

/* Frost under the bar: the blurred content, a little more saturated and lifted, kept only
   inside the bar's capsule; the rest of the layer stays clear. Input `content` is blurred. */
private const val BAR_FROST_SHADER = """
uniform shader content;
uniform float4 bar;       // l, t, r, b  (layer px)
uniform float barR;
uniform half4 ground;     // the screen background colour (opaque)

float sdRoundBox(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - h + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float2 c = (bar.xy + bar.zw) * 0.5;
    float2 hs = (bar.zw - bar.xy) * 0.5;
    float d = sdRoundBox(p, c, hs, barR);
    if (d > 0.5) return half4(0.0);
    half4 b = content.eval(p);
    // Over the opaque ground, so the frost always covers fully — even while two screens
    // cross-fade and the layer itself is partly transparent.
    half3 rgb = b.rgb + ground.rgb * (1.0 - b.a);
    half luma = dot(rgb, half3(0.299, 0.587, 0.114));
    half3 sat = clamp(mix(half3(luma), rgb, 1.35), 0.0, 1.0);   // vibrancy
    half a = clamp(0.5 - d, 0.0, 1.0);                   // anti-aliased edge
    return half4(sat * a, a);
}
"""

private const val LENS_SHADER = """
uniform shader content;
$LENS_FN

half4 main(float2 p) {
    if (inLens(p)) return lensSample(p);
    return content.eval(p);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setLens(center: Offset, half: Offset, on: Boolean, rimPx: Float, strength: Float) {
    // A capsule needs its width at least its height.
    val hy = half.y.coerceAtLeast(1f)
    setFloatUniform("lens", center.x, center.y, half.x.coerceAtLeast(hy), hy)
    setFloatUniform("lensOn", if (on) 1f else 0f)
    setFloatUniform("rim", rimPx)
    setFloatUniform("strength", strength.coerceIn(0f, 1f))
}

/**
 * Applied to the screen content under the glass bar: frosts (blurs, with vibrancy) what lies
 * under the bar, and refracts what lies under the lens. Blur runs on the GPU (RenderEffect),
 * so scrolling under the bar stays smooth. No-op below Android 13.
 */
fun Modifier.nurGlassBackdrop(state: NurGlassState): Modifier =
    if (!nurGlassSupported) this else composed {
        val frost = remember { RuntimeShader(BAR_FROST_SHADER) }
        val ground = androidx.compose.material3.MaterialTheme.colorScheme.background
        // The frost depends only on where the bar is: build it once per bar geometry, so the
        // lens moving (every frame of a drag) never re-blurs the screen. The lens refracts the
        // bar's own layer instead (see nurLensRefraction).
        val cache = remember { arrayOfNulls<Any>(2) } // [key, effect]
        this
            .onGloballyPositioned { state.contentOrigin = it.positionInRoot() }
            .graphicsLayer {
                if (!state.visible || state.bar.isEmpty) {
                    renderEffect = null
                    return@graphicsLayer
                }
                val o = state.contentOrigin
                val b = state.bar
                val key = listOf(b.left - o.x, b.top - o.y, b.right - o.x, b.bottom - o.y, state.barRadius, ground.value, density)
                if (cache[0] != key) {
                    frost.setFloatUniform("bar", b.left - o.x, b.top - o.y, b.right - o.x, b.bottom - o.y)
                    frost.setFloatUniform("barR", state.barRadius)
                    frost.setFloatUniform("ground", ground.red, ground.green, ground.blue, 1f)
                    val blurPx = 22f * density
                    val frosted = RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(frost, "content"),
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    )
                    // Clear content with the frosted capsule laid over it.
                    cache[1] = RenderEffect.createBlendModeEffect(
                        RenderEffect.createOffsetEffect(0f, 0f),
                        frosted,
                        BlendMode.SRC_OVER
                    ).asComposeRenderEffect()
                    cache[0] = key
                }
                renderEffect = cache[1] as androidx.compose.ui.graphics.RenderEffect
            }
    }

/**
 * Applied to the bar itself (track + icons + labels): whatever sits under the lens is seen
 * through it, slightly magnified, bent at the rim. [center] and [half] are in this layer's px.
 */
fun Modifier.nurLensRefraction(center: () -> Offset, half: () -> Offset, strength: () -> Float, enabled: () -> Boolean): Modifier =
    if (!nurGlassSupported) this else composed {
        val shader = remember { RuntimeShader(LENS_SHADER) }
        this.graphicsLayer {
            if (!enabled()) {
                renderEffect = null
                return@graphicsLayer
            }
            shader.setLens(center(), half(), true, 11f * density, strength())
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }

/** [nurGlassBackdrop] only while the glass bar is on screen (top-level routes). */
fun nurGlassBackdropIf(enabled: Boolean, state: NurGlassState): Modifier =
    if (enabled) Modifier.nurGlassBackdrop(state) else Modifier
