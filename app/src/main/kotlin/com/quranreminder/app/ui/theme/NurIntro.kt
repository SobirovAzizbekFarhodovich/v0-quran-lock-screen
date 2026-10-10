package com.quranreminder.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * «Two lights» — the cold-start intro. Noorain means two lights, so everything is born from two:
 *
 *  1. Night: deep green, two gold lights at rest left and right of centre (the Android 12+
 *     system splash shows exactly these two lights, so the hand-off is seamless).
 *  2. The dance: they circle each other and spiral inward, trailing comet tails.
 *  3. The meeting: a soft bloom at the centre and a gold ring wave rolling outward; where the
 *     ring passes, the Rub el Hizb lattice of the background is revealed.
 *  4. The writing: the lights part again and each becomes a pen — one draws the turned square,
 *     the other the upright square — then the knots interlace and the centre star blooms.
 *  5. The name: «Noorain» opens from its centre and a gold glint runs across it; نورين follows.
 *
 * ~3.1 s; a tap skips to the end. With system animations off the finished mark shows briefly.
 */

private const val TOTAL_MS = 3150f

/** Where the two lights rest at t = 0: this far left and right of the screen centre (dp). */
const val INTRO_LIGHT_OFFSET_DP = 64f

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

@Composable
fun NurLogoIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val time = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val finished = remember { booleanArrayOf(false) }
    fun finish() {
        if (finished[0]) return
        finished[0] = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            time.snapTo(TOTAL_MS)
            delay(600)
        } else {
            // Start the clock only once frames arrive smoothly: the first frames after a cold
            // start are slow, and a running clock would skip the whole dance during them.
            var last = 0L
            var smooth = 0
            for (i in 0 until 45) {
                val now = withFrameNanos { it }
                smooth = if (last != 0L && now - last < 25_000_000L) smooth + 1 else 0
                last = now
                if (smooth >= 3) break
            }
            time.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
            delay(120)
        }
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // A tap skips straight to the finished mark.
                detectTapGestures {
                    scope.launch {
                        time.snapTo(TOTAL_MS)
                        delay(150)
                        finish()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) { drawTwoLights(time.value) }

        // The name, under the mark: opens from its centre, then a gold glint runs across it.
        Column(
            modifier = Modifier.offset(y = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Noorain",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFF6F2E8),
                modifier = Modifier
                    .graphicsLayer {
                        val reveal = easeOut(seg(time.value, 2250f, 2850f))
                        alpha = reveal
                        translationY = (1f - reveal) * 10.dp.toPx()
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        val t = time.value
                        val reveal = easeOut(seg(t, 2250f, 2850f))
                        val half = size.width / 2f * reveal
                        clipRect(left = size.width / 2f - half, right = size.width / 2f + half) {
                            this@drawWithContent.drawContent()
                        }
                        val glint = seg(t, 2450f, 3150f)
                        if (glint in 0.001f..0.999f) {
                            val x = -0.3f * size.width + glint * 1.6f * size.width
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color(0xFFFFE9A8),
                                    1f to Color.Transparent,
                                    startX = x - size.width * 0.18f,
                                    endX = x + size.width * 0.18f
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
            )
            Text(
                "نورين",
                style = TextStyle(fontFamily = NurFonts.Quran, fontSize = 24.sp, color = NurColors.GoldLight),
                modifier = Modifier.graphicsLayer {
                    val a = easeOut(seg(time.value, 2650f, 3050f))
                    alpha = a
                    translationY = (1f - a) * 8.dp.toPx()
                }
            )
        }
    }
}

// ------------------------------------------------------------------------- drawing

private fun squarePath(cx: Float, cy: Float, half: Float, diamond: Boolean): Path = Path().apply {
    if (diamond) {
        val d = half * sqrt(2f)
        moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
    } else {
        moveTo(cx - half, cy - half); lineTo(cx + half, cy - half); lineTo(cx + half, cy + half); lineTo(cx - half, cy + half); close()
    }
}

/** A gold point of light: wide soft halo, warm glow, bright core. */
private fun DrawScope.light(at: Offset, scale: Float, alpha: Float) {
    if (alpha <= 0f || scale <= 0f) return
    val halo = 46.dp.toPx() * scale
    drawCircle(
        Brush.radialGradient(
            0f to NurColors.GoldLight.copy(alpha = 0.30f * alpha),
            1f to Color.Transparent,
            center = at, radius = halo
        ),
        radius = halo, center = at
    )
    val glow = 22.dp.toPx() * scale
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFF6DA).copy(alpha = alpha),
            0.35f to NurColors.GoldLight.copy(alpha = 0.75f * alpha),
            1f to Color.Transparent,
            center = at, radius = glow
        ),
        radius = glow, center = at
    )
    drawCircle(Color(0xFFFFFBEF).copy(alpha = alpha), radius = 4.5.dp.toPx() * scale, center = at)
}

private fun DrawScope.drawTwoLights(t: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2f, h / 2f)
    val r0 = INTRO_LIGHT_OFFSET_DP.dp.toPx()

    // Night field: the same flat green as the system splash (windowSplashScreenBackground), so
    // the hand-off has no tone jump; the depth gradient only rises with the ring wave.
    drawRect(NurColors.Green)
    val depth = easeOut(seg(t, 1100f, 2400f))
    if (depth > 0f) drawRect(Brush.verticalGradient(listOf(Color.Transparent, NurColors.GreenDeep.copy(alpha = depth))))

    // 1–2 · The dance: two lights orbit and spiral in (0–1100 ms), with comet tails.
    fun dancePos(p: Float, phase: Float): Offset {
        val theta = phase + PI.toFloat() * 1.65f * easeInOut(p)
        val r = r0 * (1f - p.pow(1.7f))
        return Offset(c.x + r * cos(theta), c.y + r * sin(theta) * 0.92f)
    }
    val p1 = seg(t, 0f, 1100f)
    val danceAlpha = 1f - seg(t, 1080f, 1200f)
    if (danceAlpha > 0f) {
        for (phase in floatArrayOf(PI.toFloat(), 0f)) {
            for (k in 18 downTo 1) {
                val pk = p1 - k * 0.011f
                if (pk <= 0f) continue
                val fade = (1f - k / 19f).pow(2)
                drawCircle(
                    NurColors.GoldLight.copy(alpha = 0.55f * fade * danceAlpha),
                    radius = 3.6.dp.toPx() * (0.35f + 0.65f * fade),
                    center = dancePos(pk, phase)
                )
            }
            light(dancePos(p1, phase), 1f, danceAlpha)
        }
    }

    // 3 · The meeting: a ring wave rolls outward and reveals the lattice behind it.
    val ringP = seg(t, 1080f, 2100f)
    val ringR = easeOut(ringP) * max(w, h) * 0.8f
    if (ringP > 0f) {
        drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
        drawLattice(NurColors.Gold.copy(alpha = 0.10f))
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent,
                center = c, radius = ringR.coerceAtLeast(1f)
            ),
            radius = max(w, h), center = c, blendMode = BlendMode.DstIn
        )
        drawContext.canvas.restore()
        drawCircle(
            NurColors.GoldLight.copy(alpha = 0.55f * (1f - ringP)),
            radius = ringR, center = c, style = Stroke(1.5.dp.toPx())
        )
    }
    val flashP = seg(t, 1060f, 1650f)
    if (flashP > 0f && flashP < 1f) {
        val fr = 28.dp.toPx() + 150.dp.toPx() * easeOut(flashP)
        val fa = (if (flashP < 0.12f) flashP / 0.12f else (1f - flashP).pow(2)) * 0.9f
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF6DA).copy(alpha = fa),
                0.4f to NurColors.GoldLight.copy(alpha = 0.45f * fa),
                1f to Color.Transparent,
                center = c, radius = fr
            ),
            radius = fr, center = c
        )
    }

    // 4 · The writing: each light becomes the pen of one square (geometry of brand/generate.py).
    val markSize = 144.dp.toPx()
    val u = markSize / 512f
    val half = 150f * u
    val band = 26f * u
    val key = 40f * u
    val sqA = squarePath(c.x, c.y, half, diamond = false)
    val sqB = squarePath(c.x, c.y, half, diamond = true)
    val emerge = easeOut(seg(t, 1250f, 1420f))
    val trace = easeInOut(seg(t, 1400f, 2200f))
    val turn = -18f * (1f - easeOut(seg(t, 1400f, 2400f)))
    val penAlpha = (if (t >= 1250f) 1f else 0f) * (1f - seg(t, 2150f, 2320f))

    fun bandStroke(path: Path, color: Color) {
        drawPath(path, NurColors.GreenDeep, style = Stroke(key, join = StrokeJoin.Miter))
        drawPath(path, color, style = Stroke(band, join = StrokeJoin.Miter))
    }

    rotate(turn, c) {
        val heads = arrayOfNulls<Offset>(2)
        listOf(sqB to NurColors.GoldLight, sqA to NurColors.Gold).forEachIndexed { i, (path, color) ->
            val measure = PathMeasure().apply { setPath(path, false) }
            val len = measure.length
            if (trace > 0f) {
                val drawn = Path()
                measure.getSegment(0f, len * trace, drawn, true)
                bandStroke(drawn, color)
            }
            val start = measure.getPosition(0f)
            heads[i] = if (trace > 0f) {
                measure.getPosition(len * trace)
            } else {
                Offset(c.x + (start.x - c.x) * emerge, c.y + (start.y - c.y) * emerge)
            }
        }
        // Interlace: the turned square passes over the upright one at alternating crossings.
        val knot = seg(t, 2120f, 2330f)
        if (knot > 0f) {
            val over = Path()
            listOf(
                Offset(c.x + 62f * u, c.y - half), Offset(c.x + half, c.y + 62f * u),
                Offset(c.x - 62f * u, c.y + half), Offset(c.x - half, c.y - 62f * u)
            ).forEach { p -> over.addOval(Rect(p, 26f * u * knot)) }
            clipPath(over) { bandStroke(sqB, NurColors.GoldLight) }
        }
        // The pens.
        heads.forEach { head -> head?.let { light(it, 0.62f, penAlpha) } }

        // 5 · The centre star blooms — the light both squares hold.
        val star = easeOutBack(seg(t, 2150f, 2600f))
        if (star > 0f) {
            val gr = (84f * u * star).coerceAtLeast(1f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFF6DA), 0.45f to NurColors.GoldLight.copy(alpha = 0.55f), 1f to Color.Transparent,
                    center = c, radius = gr
                ),
                radius = gr, center = c
            )
            drawPath(starPath(c, 40f * u * star, 22f * u * star), Color(0xFFFFF6DA))
        }
    }
}

/** The Rub el Hizb lattice, same rhythm as [starLattice] but drawn directly (no fade). */
private fun DrawScope.drawLattice(color: Color) {
    val step = 64.dp.toPx()
    val half = step * 0.26f
    val d = half * 1.4142f
    val stroke = Stroke(1.dp.toPx())
    var y = step * 0.5f
    var row = 0
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step * 0.5f else step
        while (x < size.width + step) {
            val a = Path().apply {
                moveTo(x - half, y - half); lineTo(x + half, y - half); lineTo(x + half, y + half); lineTo(x - half, y + half); close()
            }
            val b = Path().apply { moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close() }
            drawPath(a, color, style = stroke)
            drawPath(b, color, style = stroke)
            x += step
        }
        y += step * 0.86f
        row++
    }
}
