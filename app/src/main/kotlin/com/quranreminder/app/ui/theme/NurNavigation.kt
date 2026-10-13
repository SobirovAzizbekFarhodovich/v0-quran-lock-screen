package com.quranreminder.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.selection.selectable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Top-level destinations shown in the bottom navigation bar. */
data class NurDestination(val route: String, val labelKey: String, val icon: ImageVector, val selectedIcon: ImageVector)

val NurTopLevelDestinations = listOf(
    NurDestination("home", "nav.home", Icons.Outlined.Home, Icons.Filled.Home),
    NurDestination("search", "nav.quran", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
    NurDestination("tadabbur_chat", "nav.assistant", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    NurDestination("recitation", "nav.recitation", Icons.Outlined.Mic, Icons.Filled.Mic),
    NurDestination("settings", "nav.settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)

val NurTopLevelRoutes = NurTopLevelDestinations.map { it.route }.toSet()

/**
 * Space the floating glass bar takes at the bottom of top-level screens (bar + system nav inset).
 * Top-level screens draw UNDER the bar and use this for their bottom content padding, so lists
 * scroll beneath the glass; fixed bottom elements (chat input, record button) sit above it.
 */
val LocalNavBarPadding = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** One damped spring: position [x], velocity [v]. */
private class Spring(var x: Float, var v: Float = 0f) {
    fun step(target: Float, k: Float, c: Float, dt: Float) {
        val a = -k * (x - target) - c * v
        v += a * dt
        x += v * dt
    }
}

private class LensSim {
    val x = Spring(0f)
    val w = Spring(0f)
    val stretch = Spring(0f)
    val lift = Spring(1f)
    /** 0 = lens hidden, 1 = shown. The lens only exists while the bar is being touched. */
    val appear = Spring(0f)
    var appearTarget = 0f
    var tx = 0f
    var tw = 0f
    var ready = false
    var dragging = false
    var dragVel = 0f
    val resting: Boolean
        get() = !dragging &&
            abs(x.x - tx) < 0.2f && abs(x.v) < 2f &&
            abs(w.x - tw) < 0.2f && abs(w.v) < 2f &&
            abs(stretch.x) < 0.002f && abs(stretch.v) < 0.02f &&
            abs(lift.x - 1f) < 0.002f && abs(lift.v) < 0.02f &&
            abs(appear.x - appearTarget) < 0.002f && abs(appear.v) < 0.02f
}

private data class GlassPalette(
    val track: Color,
    val trackShadeTop: Color,
    val trackLightBottom: Color,
    val lensTint: Color,
    val lensRim: Color,
    val lensShadow: Color,
    val innerLight: Color,
    val innerDark: Color,
    val shine: Color,
    val selected: Color,
    val unselected: Color
)

/**
 * «Liquid glass» bottom navigation, after the GlassTabs reference: a solid, slightly recessed
 * track and, over the chosen tab, a CLEAR glass lens that is taller than the track, read
 * through (labels under it are magnified), with a bright rim, a specular crescent, light caught
 * on the lower edge and a soft cast shadow.
 *
 * Everything moves on springs: position and width are underdamped (they overshoot and settle),
 * speed stretches the lens along the motion through its own loose spring (the wobble), and the
 * lens can be grabbed — press lifts it, dragging follows the finger with a rubber band past the
 * ends, a flick carries to where it would coast. Tapping a tab still just selects it.
 * With system animations off it jumps.
 */
@Composable
fun NurNavigationBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val c = nurColors
    val palette = if (c.isDark) {
        GlassPalette(
            track = Color(0xFF0A1C16),
            trackShadeTop = Color.Black.copy(alpha = 0.55f),
            trackLightBottom = Color.White.copy(alpha = 0.04f),
            lensTint = Color.White.copy(alpha = 0.10f),
            lensRim = Color.White.copy(alpha = 0.28f),
            lensShadow = Color.Black,
            innerLight = Color.White.copy(alpha = 0.22f),
            innerDark = Color.Black.copy(alpha = 0.45f),
            shine = Color.White.copy(alpha = 0.5f),
            selected = NurColors.GoldLight,
            unselected = Color(0xFF9DB5AA)
        )
    } else {
        GlassPalette(
            track = Color(0xFFE4DFD1),
            trackShadeTop = NurColors.Green.copy(alpha = 0.14f),
            trackLightBottom = Color.White.copy(alpha = 0.7f),
            lensTint = Color.White.copy(alpha = 0.55f),
            lensRim = Color.White.copy(alpha = 0.95f),
            lensShadow = NurColors.Green,
            innerLight = Color.White.copy(alpha = 0.95f),
            innerDark = NurColors.Green.copy(alpha = 0.22f),
            shine = Color.White.copy(alpha = 0.9f),
            selected = NurColors.Green,
            unselected = NurColors.InkMuted
        )
    }

    // Real liquid glass (Android 13+): the track is translucent and the screen content under it
    // is frosted by a shader; the lens refracts both the content and the bar.
    val glass = LocalGlassState.current
    val shaderGlass = glass != null && nurGlassSupported
    val trackFill = when {
        !shaderGlass -> palette.track
        // Nearly opaque: content scrolling behind never fights the labels, and no screen-wide
        // blur is needed (that cost a frame's budget on every scroll and switch).
        c.isDark -> Color(0xFF0C2019).copy(alpha = 0.92f)
        else -> Color(0xFFFBF8F1).copy(alpha = 0.93f)
    }
    var trackOrigin by remember { mutableStateOf(Offset.Zero) }

    val selectedIndex = NurTopLevelDestinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    // The drag gesture is installed once (pointerInput(Unit)); it must read the CURRENT tab, not
    // the one captured when it was created, or dragging back to the first tab would do nothing.
    val currentIndex by rememberUpdatedState(selectedIndex)
    val onSelectState by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }

    val tabLeft = remember { mutableStateListOf(*Array(NurTopLevelDestinations.size) { 0f }) }
    val tabWidth = remember { mutableStateListOf(*Array(NurTopLevelDestinations.size) { 0f }) }
    val sim = remember { LensSim() }
    // Frame-driven values the lens and labels read.
    var lensX by remember { mutableFloatStateOf(0f) }
    var lensW by remember { mutableFloatStateOf(0f) }
    var lensStretch by remember { mutableFloatStateOf(0f) }
    var lensLift by remember { mutableFloatStateOf(1f) }
    var kick by remember { mutableIntStateOf(0) }
    var suppressClick by remember { mutableStateOf(false) }

    var lensReady by remember { mutableStateOf(false) }
    var lensAppear by remember { mutableFloatStateOf(0f) }

    val trackHeight = 64.dp
    val proud = 6.dp
    // Lens geometry: a near-round drop, taller than the track, centred on the spring position.
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val inset = with(density) { 4.dp.toPx() }
    val lensBaseH = with(density) { (trackHeight + proud * 2).toPx() }
    // A capsule, wider than tall, like iOS: a little wider than one tab.
    val avgTab = if (tabWidth.isNotEmpty() && tabWidth.last() > 0f) tabWidth.sum() / tabWidth.size else lensBaseH
    val lensBaseW = max(avgTab * 1.22f, lensBaseH * 1.32f)

    // Per-frame lens values are read ONLY inside layout/draw/layer lambdas (never during
    // composition), so moving the lens redraws the glass without recomposing the bar.
    fun lensCxNow(): Float {
        val first = tabLeft.first()
        val last = tabLeft.last() + tabWidth.last()
        val cx = lensX + lensW / 2f
        return if (last > first) cx.coerceIn(first + lensBaseW * 0.40f, last - lensBaseW * 0.40f) else cx
    }
    fun lensSxNow() = lensLift * (1f + lensStretch.coerceIn(-0.25f, 0.45f))
    fun lensSyNow() = lensLift / (1f + lensStretch.coerceIn(-0.25f, 0.45f) * 0.9f)
    // 0 = the resting pill, 1 = the full lens. The lens grows out of the pill and, when it
    // lets go, settles back into exactly the pill's shape (no uniform shrink-and-vanish).
    fun lensMorphNow(): Float {
        val a = lensAppear.coerceIn(0f, 1f)
        return a * a * (3f - 2f * a)
    }
    // Lens centre in the track layer's own pixels (the track starts `inset` before the tabs).
    fun lensLocalNow(): Offset {
        val k = lensMorphNow()
        val pillCx = lensX + lensW / 2f
        return Offset(pillCx + (lensCxNow() - pillCx) * k + inset, trackHeightPx / 2f)
    }
    val pillHalfH = with(density) { (trackHeight - 8.dp).toPx() } / 2f
    fun lensHalfNow(): Offset {
        val k = lensMorphNow()
        val full = Offset(lensBaseW / 2f * lensSxNow(), lensBaseH / 2f * lensSyNow())
        val pill = Offset(kotlin.math.max(lensW, pillHalfH * 2f) / 2f, pillHalfH)
        return Offset(pill.x + (full.x - pill.x) * k, pill.y + (full.y - pill.y) * k)
    }

    fun publish() {
        lensX = sim.x.x; lensW = sim.w.x; lensStretch = sim.stretch.x; lensLift = sim.lift.x
        lensAppear = sim.appear.x
        if (!lensReady && sim.w.x > 0f) lensReady = true
        glass?.let {
            it.lensCenter = trackOrigin + lensLocalNow()
            it.lensHalf = lensHalfNow()
            it.visible = sim.w.x > 0f
            it.lensOn = sim.w.x > 0f && sim.appear.x > 0.01f
            it.lensStrength = lensMorphNow()
        }
    }

    fun aimAt(index: Int) {
        val w = tabWidth.getOrNull(index) ?: return
        if (w <= 0f) return
        sim.tx = tabLeft[index]
        sim.tw = w
        if (!sim.ready || reducedMotion) {
            sim.ready = true
            sim.x.x = sim.tx; sim.w.x = sim.tw; sim.x.v = 0f; sim.w.v = 0f
            publish()
        } else {
            kick++
        }
    }

    LaunchedEffect(selectedIndex, tabWidth.toList()) { aimAt(selectedIndex) }

    // The one animation loop: runs while the lens is moving, sleeps when it rests.
    LaunchedEffect(kick) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            val done = withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else min(1f / 30f, (now - last) / 1_000_000_000f)
                last = now
                if (sim.dragging) sim.x.step(sim.tx, 900f, 45f, dt) else sim.x.step(sim.tx, 230f, 24f, dt)
                sim.w.step(sim.tw, 260f, 20f, dt)
                // The lens stretches with the finger's speed only while it is being dragged. Once
                // let go it relaxes straight back (critically damped): no swelling after release.
                if (sim.dragging) {
                    val speedDp = sim.dragVel / density.density
                    sim.stretch.step(min(0.45f, abs(speedDp) / 1600f), 320f, 9f, dt)
                } else {
                    sim.stretch.step(0f, 320f, 2f * kotlin.math.sqrt(320f), dt)
                }
                // "Arrived" a little before the spring is fully still, so the lens starts settling
                // into the pill as it lands instead of lingering on the tab.
                val travelling = abs(sim.x.x - sim.tx) > 4f * density.density || abs(sim.x.v) > 160f * density.density
                sim.appearTarget = if (sim.dragging || travelling) 1f else 0f
                // Appears fast and a little springy; leaves softer.
                if (sim.appearTarget > 0f) sim.appear.step(1f, 520f, 26f, dt) else sim.appear.step(0f, 300f, 2f * kotlin.math.sqrt(300f), dt)
                if (sim.appear.x < 0f) { sim.appear.x = 0f; sim.appear.v = 0f }
                // The lift is part of the same motion as the appearance: the lens rises out of
                // the pill in one move and settles back into it in one move (no second shrink).
                sim.lift.x = 1f + 0.12f * sim.appear.x.coerceIn(0f, 1f)
                sim.lift.v = 0f
                if (sim.resting) {
                    sim.x.x = sim.tx; sim.w.x = sim.tw; sim.stretch.x = 0f; sim.lift.x = 1f
                    sim.appear.x = sim.appearTarget
                    publish(); true
                } else {
                    publish(); false
                }
            }
            if (done) break
        }
    }

    DisposableEffect(glass) { onDispose { glass?.visible = false } }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 6.dp)
    ) {
        // Track: solid and slightly recessed (inner shade on top, light on the bottom lip).
        Row(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .shadow(10.dp, CircleShape, ambientColor = palette.lensShadow.copy(alpha = 0.18f), spotColor = palette.lensShadow.copy(alpha = 0.22f))
                .onGloballyPositioned { coords ->
                    trackOrigin = coords.positionInRoot()
                    glass?.lensCenter = trackOrigin + lensLocalNow()
                    glass?.bar = coords.boundsInRoot()
                    glass?.barRadius = coords.size.height / 2f
                }
                .nurLensRefraction(
                    center = { lensLocalNow() },
                    half = { lensHalfNow() },
                    strength = { lensMorphNow() },
                    enabled = { shaderGlass && lensReady && lensAppear > 0.01f }
                )
                .clip(CircleShape)
                .background(trackFill)
                .border(
                    1.dp,
                    if (shaderGlass) {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (c.isDark) 0.26f else 0.95f),
                            1f to Color.White.copy(alpha = if (c.isDark) 0.06f else 0.35f)
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                    CircleShape
                )
                .drawWithContent {
                    if (shaderGlass && lensW > 0f) {
                        // The selection at rest: a soft pill under the tab. It fades as the lens
                        // rises out of it and returns where the lens settles.
                        val fade = 1f - lensMorphNow()
                        if (fade > 0f) {
                            val ph = size.height - 8.dp.toPx()
                            drawRoundRect(
                                (if (c.isDark) Color.White else NurColors.Green).copy(
                                    alpha = (if (c.isDark) 0.12f else 0.08f) * fade
                                ),
                                topLeft = Offset(lensX + 4.dp.toPx(), 4.dp.toPx()),
                                size = Size(lensW, ph),
                                cornerRadius = CornerRadius(ph / 2, ph / 2)
                            )
                        }
                    }
                    drawContent()
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(
                        Brush.verticalGradient(0f to palette.trackShadeTop, 0.18f to Color.Transparent),
                        cornerRadius = r
                    )
                    drawRoundRect(
                        Brush.verticalGradient(0.85f to Color.Transparent, 1f to palette.trackLightBottom),
                        cornerRadius = r
                    )
                }
                .pointerInput(Unit) {
                    // Liquid drag, iOS-style: a press ANYWHERE on the bar pulls the lens under the
                    // finger, it follows while dragging (rubber band past the ends) and on release
                    // settles on the nearest tab — a flick carries to where it would coast.
                    // Taps select through the same path, so the down is consumed here and the
                    // tabs' own click is only used by accessibility services.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        if (reducedMotion) {
                            val px0 = down.position.x - 4.dp.toPx()
                            val hit = tabLeft.indices.firstOrNull { px0 >= tabLeft[it] && px0 <= tabLeft[it] + tabWidth[it] }
                            waitForUpOrCancellation(PointerEventPass.Initial)
                            if (hit != null && hit != currentIndex) onSelectState(NurTopLevelDestinations[hit].route)
                            return@awaitEachGesture
                        }
                        val px = down.position.x - 4.dp.toPx()
                        val onLens = px >= sim.x.x && px <= sim.x.x + sim.w.x
                        // On the lens: keep the grab point. Elsewhere: centre the lens on the finger.
                        val grab = if (onLens) px - sim.x.x else sim.w.x / 2f
                        val lo = tabLeft.first()
                        val hi = tabLeft.last() + tabWidth.last()
                        fun aim(x: Float) {
                            var nx = x - grab
                            if (nx < lo) nx = lo - (lo - nx) * 0.25f
                            if (nx + sim.w.x > hi) nx = hi - sim.w.x + (nx + sim.w.x - hi) * 0.25f
                            sim.tx = nx
                            val centre = nx + sim.w.x / 2
                            tabLeft.indices.firstOrNull { centre >= tabLeft[it] && centre <= tabLeft[it] + tabWidth[it] }
                                ?.let { sim.tw = tabWidth[it] }
                        }
                        var lastX = px
                        var lastT = down.uptimeMillis
                        sim.dragging = true
                        sim.dragVel = 0f
                        aim(px)
                        kick++
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { change.consume(); break }
                            val x = change.position.x - 4.dp.toPx()
                            val dtS = max(1L, change.uptimeMillis - lastT) / 1000f
                            sim.dragVel = sim.dragVel * 0.6f + ((x - lastX) / dtS) * 0.4f
                            lastX = x; lastT = change.uptimeMillis
                            change.consume()
                            aim(x)
                        }
                        val centre = sim.tx + sim.w.x / 2 + sim.dragVel * 0.05f
                        val best = tabLeft.indices.minByOrNull { abs(tabLeft[it] + tabWidth[it] / 2 - centre) } ?: currentIndex
                        sim.dragging = false
                        // A little of the flick carries into the settle; the full flick velocity
                        // made the lens fly and bulge past the tab.
                        sim.x.v = sim.dragVel * 0.25f
                        sim.tx = tabLeft[best]
                        sim.tw = tabWidth[best]
                        kick++
                        if (best != currentIndex) onSelectState(NurTopLevelDestinations[best].route)
                    }
                }
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            NurTopLevelDestinations.forEachIndexed { index, dest ->
                val selected = index == selectedIndex
                // Without the shader, the lens is faked: a tab grows by how much of it sits under
                // the glass. With the shader the lens itself magnifies, and nothing here reads the
                // per-frame lens position (so dragging doesn't recompose the bar).
                val cover = if (shaderGlass) {
                    if (selected) 1f else 0f
                } else {
                    val l0 = lensX
                    val l1 = lensX + lensW
                    val a = tabLeft[index]
                    val z = a + tabWidth[index]
                    if (tabWidth[index] > 0f) (max(0f, min(z, l1) - max(a, l0)) / tabWidth[index]) else 0f
                }
                val scale = if (!shaderGlass && cover > 0.01f) 1f + 0.07f * cover * lensLift else 1f
                val tint = lerpColor(palette.unselected, palette.selected, cover.coerceIn(0f, 1f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            tabLeft[index] = it.positionInParent().x - 4.dp.value * density.density
                            tabWidth[index] = it.size.width.toFloat()
                        }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!selected) onSelectState(dest.route)
                            }
                        )
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    val label = t(dest.labelKey)
                    Text(
                        label,
                        // Long labels (Yordamchi, Настройки) step down so they fit the slot even
                        // when the lens magnifies them.
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (label.length > 8) 10.5.sp else 12.sp
                        ),
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                        // The lens magnifies the label; let it grow past the tab instead of clipping.
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }

        // The lens, drawn above the labels; pointer-transparent (the track handles input).
        if (shaderGlass && lensReady) {
            // Clear drop: the shader does the magnifying and bending; this draws only the glass
            // itself — a dark band under the top rim, light pooled at the bottom, a bright rim
            // with a faint colour split, and a soft cast shadow.
            val wDp = with(density) { lensBaseW.toDp() }
            val hDp = with(density) { lensBaseH.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((lensLocalNow().x - lensBaseW / 2f).roundToInt(), (-proud).roundToPx()) }
                    .size(wDp, hDp)
                    .graphicsLayer {
                        val half = lensHalfNow()
                        scaleX = half.x * 2f / lensBaseW
                        scaleY = half.y * 2f / lensBaseH
                        alpha = lensMorphNow()
                    }
                    .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.24f))
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        val r = CornerRadius(h / 2f, h / 2f)
                        val sw = 1.dp.toPx()
                        // Barely-there body: clear glass, a touch lighter toward the rim.
                        drawRoundRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (c.isDark) 0.06f else 0.08f),
                                0.45f to Color.Transparent,
                                1f to Color.White.copy(alpha = if (c.isDark) 0.04f else 0.07f)
                            ),
                            cornerRadius = r
                        )
                        // Light enters at the top edge: a bright, thin specular line.
                        drawRoundRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (c.isDark) 0.55f else 0.9f),
                                0.35f to Color.Transparent
                            ),
                            topLeft = Offset(sw * 1.5f, sw * 1.5f),
                            size = Size(w - sw * 3f, h - sw * 3f),
                            cornerRadius = CornerRadius((h - sw * 3f) / 2f, (h - sw * 3f) / 2f),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                        // ...and pools softly along the bottom inside edge.
                        drawRoundRect(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.White.copy(alpha = if (c.isDark) 0.28f else 0.5f)
                            ),
                            topLeft = Offset(sw * 2.5f, sw * 2.5f),
                            size = Size(w - sw * 5f, h - sw * 5f),
                            cornerRadius = CornerRadius((h - sw * 5f) / 2f, (h - sw * 5f) / 2f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Rim: brighter where it faces the light.
                        drawRoundRect(
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = if (c.isDark) 0.55f else 0.95f),
                                0.5f to Color.White.copy(alpha = if (c.isDark) 0.18f else 0.45f),
                                1f to Color.White.copy(alpha = if (c.isDark) 0.35f else 0.75f),
                                start = Offset.Zero, end = Offset(w, h)
                            ),
                            cornerRadius = r,
                            style = Stroke(sw)
                        )
                    }
            )
        } else if (!shaderGlass && lensW > 0f) {
            val stretch = lensStretch.coerceIn(-0.25f, 0.45f)
            val lo = tabLeft.first()
            val hi = tabLeft.last() + tabWidth.last()
            // The track's ends are walls: an overshoot flattens against them.
            val left0 = max(lo, lensX)
            val right0 = min(hi, lensX + lensW)
            val width = max(12f, right0 - left0)
            val squash = if (lensW > 0f) width / lensW else 1f
            val sx = lensLift * (1f + stretch)
            val sy = (lensLift / (1f + stretch * 0.9f)) * (1f + (1f - squash) * 0.6f)
            val widthDp = with(density) { width.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((left0 + 4.dp.toPx()).roundToInt(), (-proud).roundToPx()) }
                    .width(widthDp)
                    .height(trackHeight + proud * 2)
                    .graphicsLayer { scaleX = sx; scaleY = sy }
                    .shadow(12.dp, CircleShape, ambientColor = palette.lensShadow.copy(alpha = 0.25f), spotColor = palette.lensShadow.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .drawWithContent {
                        val h = size.height
                        val r = CornerRadius(h / 2, h / 2)
                        // Clear body: a faint radial sheen, not frost — the label reads through.
                        drawRoundRect(
                            Brush.radialGradient(
                                0f to palette.lensTint.copy(alpha = palette.lensTint.alpha * 1.0f),
                                0.55f to palette.lensTint.copy(alpha = palette.lensTint.alpha * 0.15f),
                                1f to palette.lensTint.copy(alpha = palette.lensTint.alpha * 0.5f),
                                center = Offset(size.width * 0.3f, h * 0.2f),
                                radius = size.width * 1.2f
                            ),
                            cornerRadius = r
                        )
                        // Refraction darkens the lower-right edge; light enters top-left.
                        drawRoundRect(
                            Brush.linearGradient(
                                0f to palette.innerLight, 0.22f to Color.Transparent,
                                0.75f to Color.Transparent, 1f to palette.innerDark,
                                start = Offset.Zero, end = Offset(size.width, h)
                            ),
                            cornerRadius = r,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        // Thin bright rim.
                        drawRoundRect(palette.lensRim, cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
                        // Specular crescent along the upper-left.
                        drawRoundRect(
                            Brush.horizontalGradient(
                                0f to palette.shine.copy(alpha = palette.shine.alpha * 0.75f), 1f to Color.Transparent,
                                startX = 10.dp.toPx(), endX = size.width * 0.6f
                            ),
                            topLeft = Offset(10.dp.toPx(), 3.dp.toPx()),
                            size = Size(size.width * 0.6f - 10.dp.toPx(), 6.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                        // Light caught at the lower-right edge.
                        drawRoundRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, 1f to palette.shine.copy(alpha = palette.shine.alpha * 0.45f),
                                startX = size.width * 0.65f - 12.dp.toPx(), endX = size.width - 12.dp.toPx()
                            ),
                            topLeft = Offset(size.width * 0.65f - 12.dp.toPx(), h - 7.dp.toPx()),
                            size = Size(size.width * 0.35f, 4.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)
