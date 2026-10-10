package com.quranreminder.app.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
    /** Lens ellipse: centre and half-size in root coordinates. */
    var lensCenter by mutableStateOf(Offset.Zero)
    var lensHalf by mutableStateOf(Offset.Zero)
    var visible by mutableStateOf(false)
    /** Root offset of the content layer the backdrop shader runs on. */
    var contentOrigin by mutableStateOf(Offset.Zero)
}

val LocalGlassState = staticCompositionLocalOf<NurGlassState?> { null }

/** True where the real (shader) glass can run: RuntimeShader needs Android 13. */
val nurGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// A clear, dome-shaped lens: the centre magnifies, the rim bends hard (content is pulled in
// and compressed toward the edge) and splits slightly into colour, like the reference video.
private const val LENS_FN = """
uniform float4 lens;      // cx, cy, rx, ry  (layer px)
uniform float lensOn;

bool inLens(float2 p) {
    float2 d = (p - lens.xy) / lens.zw;
    return lensOn > 0.5 && dot(d, d) < 1.0;
}

half4 lensSample(float2 p) {   // samples the global `content` shader
    float2 d = (p - lens.xy) / lens.zw;
    float r2 = dot(d, d);
    float h = sqrt(max(0.0, 1.0 - r2));
    float bend = 1.0 - h;                       // 0 at the centre, 1 at the rim
    float scale = 0.78 - 0.42 * bend * bend;    // magnify the middle, squeeze the edge
    float2 base = lens.xy + d * lens.zw * scale;
    float2 dir = d / max(sqrt(r2), 0.0001);
    float spread = 6.0 * bend * bend * bend;    // chromatic split, strongest at the rim
    half4 cg = content.eval(base);
    half4 cr = content.eval(base + dir * spread);
    half4 cb = content.eval(base - dir * spread);
    return half4(cr.r, cg.g, cb.b, max(cg.a, max(cr.a, cb.a)));
}
"""

private const val BACKDROP_SHADER = """
uniform shader content;
uniform float4 bar;       // l, t, r, b  (layer px)
uniform float barR;
uniform float barOn;
uniform float blurStep;
$LENS_FN

float sdRoundBox(float2 p, float2 c, float2 h, float r) {
    float2 q = abs(p - c) - h + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    if (inLens(p)) return lensSample(p);
    if (barOn > 0.5) {
        float2 c = (bar.xy + bar.zw) * 0.5;
        float2 hs = (bar.zw - bar.xy) * 0.5;
        if (sdRoundBox(p, c, hs, barR) < 0.0) {
            // Frosted: a gaussian over what scrolls under the bar, sampled on two interleaved
            // grids (offset by half a step) so the blur is smooth instead of blocky.
            half4 acc = half4(0.0);
            float w = 0.0;
            for (int i = -4; i <= 4; i++) {
                for (int j = -3; j <= 3; j++) {
                    float2 o = float2(float(i), float(j));
                    float k = exp(-dot(o, o) / 10.0);
                    acc += content.eval(p + o * blurStep) * k;
                    acc += content.eval(p + (o + 0.5) * blurStep) * k;
                    w += 2.0 * k;
                }
            }
            return acc / w;
        }
    }
    return content.eval(p);
}
"""

private const val BAR_SHADER = """
uniform shader content;
$LENS_FN

half4 main(float2 p) {
    if (inLens(p)) return lensSample(p);
    return content.eval(p);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setLens(center: Offset, half: Offset, on: Boolean) {
    setFloatUniform("lens", center.x, center.y, half.x.coerceAtLeast(1f), half.y.coerceAtLeast(1f))
    setFloatUniform("lensOn", if (on) 1f else 0f)
}

/**
 * Applied to the screen content under the glass bar: frosts the area behind the bar and
 * refracts the area behind the lens. No-op below Android 13.
 */
fun Modifier.nurGlassBackdrop(state: NurGlassState): Modifier =
    if (!nurGlassSupported) this else composed {
        val shader = remember { RuntimeShader(BACKDROP_SHADER) }
        this
            .onGloballyPositioned { state.contentOrigin = it.positionInRoot() }
            .graphicsLayer {
                if (!state.visible || state.bar.isEmpty) {
                    renderEffect = null
                    return@graphicsLayer
                }
                val o = state.contentOrigin
                val b = state.bar
                shader.setFloatUniform("bar", b.left - o.x, b.top - o.y, b.right - o.x, b.bottom - o.y)
                shader.setFloatUniform("barR", state.barRadius)
                // Frosting off: a full-screen blur pass cost ~40ms per scroll frame on a mid-range phone.
                shader.setFloatUniform("barOn", 0f)
                shader.setFloatUniform("blurStep", 1.6f * density)
                shader.setLens(state.lensCenter - o, state.lensHalf, true)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
    }

/**
 * Applied to the bar itself (track + icons + labels): whatever sits under the lens is
 * magnified and bent at the rim. [center] and [half] are in this layer's own pixels.
 */
fun Modifier.nurLensRefraction(center: () -> Offset, half: () -> Offset, enabled: () -> Boolean): Modifier =
    if (!nurGlassSupported) this else composed {
        val shader = remember { RuntimeShader(BAR_SHADER) }
        this.graphicsLayer {
            if (!enabled()) {
                renderEffect = null
                return@graphicsLayer
            }
            shader.setLens(center(), half(), true)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }

/** [nurGlassBackdrop] only while the glass bar is on screen (top-level routes). */
fun nurGlassBackdropIf(enabled: Boolean, state: NurGlassState): Modifier =
    if (enabled) Modifier.nurGlassBackdrop(state) else Modifier
