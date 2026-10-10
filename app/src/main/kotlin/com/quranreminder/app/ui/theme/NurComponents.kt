package com.quranreminder.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quranreminder.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- geometry

/** Eight-point star (Rub el Hizb outline) centred at [center]. */
fun starPath(center: Offset, outer: Float, inner: Float = outer * 0.72f, rotation: Double = -PI / 2): Path {
    val path = Path()
    repeat(16) { i ->
        val r = if (i % 2 == 0) outer else inner
        val a = rotation + i * PI / 8
        val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

/** Two overlapping squares (one turned 45°), the logo geometry, as a stroked outline. */
private fun DrawScope.rubElHizb(center: Offset, half: Float, color: Color, stroke: Float) {
    val a = Path().apply {
        moveTo(center.x - half, center.y - half); lineTo(center.x + half, center.y - half)
        lineTo(center.x + half, center.y + half); lineTo(center.x - half, center.y + half); close()
    }
    val d = half * 1.4142f
    val b = Path().apply {
        moveTo(center.x, center.y - d); lineTo(center.x + d, center.y)
        lineTo(center.x, center.y + d); lineTo(center.x - d, center.y); close()
    }
    drawPath(a, color, style = Stroke(stroke))
    drawPath(b, color, style = Stroke(stroke))
}

/**
 * A quiet lattice of Rub el Hizb outlines, the only ornament in the system. It fades toward
 * the start edge so headings and text always sit on a calm field.
 */
fun Modifier.starLattice(color: Color, cell: Dp = 64.dp, alpha: Float = 0.12f): Modifier = drawBehind {
    val step = cell.toPx()
    val half = step * 0.26f
    val stroke = 1.dp.toPx()
    var y = step * 0.5f
    var row = 0
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step * 0.5f else step
        while (x < size.width + step) {
            val fade = (x / size.width).coerceIn(0f, 1f)
            rubElHizb(Offset(x, y), half, color.copy(alpha = alpha * (0.2f + 0.8f * fade)), stroke)
            x += step
        }
        y += step * 0.86f
        row++
    }
}

// ---------------------------------------------------------------- brand

/** The Noorain mark (two interlaced squares with a light at the centre). */
@Composable
fun NurEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    @Suppress("UNUSED_PARAMETER") color: Color = NurColors.Gold,
    @Suppress("UNUSED_PARAMETER") ringColor: Color = color,
    onDark: Boolean = nurColors.isDark || LocalOnBand.current
) {
    Image(
        painter = painterResource(if (onDark) R.drawable.noorain_mark_on_dark else R.drawable.noorain_mark),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** Logo plus wordmark, for headers and the auth/splash screens. */
@Composable
fun NurLockup(modifier: Modifier = Modifier, markSize: Dp = 40.dp, color: Color = nurColors.onBand, onDark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NurEmblem(size = markSize, onDark = onDark)
        Text("Noorain", style = MaterialTheme.typography.headlineMedium, color = color)
    }
}

// ---------------------------------------------------------------- band & headers

/** True while composing inside a [NurBand]; icon buttons and the emblem switch to on-band styles. */
val LocalOnBand = compositionLocalOf { false }

/**
 * The deep-green band that opens every primary screen. It draws behind the status bar
 * (the app is edge-to-edge) and carries the star lattice.
 */
@Composable
fun NurBand(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = nurColors
    CompositionLocalProvider(LocalOnBand provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.band, c.bandDeep)))
                .starLattice(c.ornament)
                .statusBarsPadding()
                .padding(bottom = bottomPadding),
            content = content
        )
    }
}

/**
 * Screen header on the green band. [eyebrow] renders as a subtitle under the title.
 * [leading] / [trailing] take [NurIconButton]s, which adapt to the band automatically.
 */
@Composable
fun NurScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleColor: Color = nurColors.onBand,
    bottomPadding: Dp = 18.dp
) {
    NurBand(modifier = modifier, bottomPadding = bottomPadding) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leading != null) leading() else Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!eyebrow.isNullOrBlank()) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.bodySmall,
                        color = nurColors.onBandMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = trailing)
        }
    }
}

@Composable
fun NurIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val onBand = LocalOnBand.current
    val c = nurColors
    val (bg, tint) = when {
        onBand && emphasized -> c.ornament to c.bandDeep
        onBand -> Color.White.copy(alpha = 0.10f) to c.onBand
        emphasized -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- surfaces

/** Section heading: serif, with a small gold star. It names the section; it is not a kicker. */
@Composable
fun NurSectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val gold = nurColors.ornament
        Canvas(Modifier.size(12.dp)) { drawPath(starPath(center, size.minDimension / 2f), gold) }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Paper card with a gold hairline and a soft green-tinted shadow (tonal only in dark). */
@Composable
fun NurSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    val base = modifier
        .shadow(if (nurColors.isDark) 0.dp else 6.dp, shape, ambientColor = NurColors.Green.copy(alpha = 0.10f), spotColor = NurColors.Green.copy(alpha = 0.14f))
        .clip(shape)
        .background(containerColor)
        .border(BorderStroke(1.dp, nurColors.frame.copy(alpha = 0.6f)), shape)
    Box(if (onClick == null) base else base.clickable(role = Role.Button, onClick = onClick)) { content() }
}

@Composable
fun NurPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Text(
        text = text,
        modifier = modifier
            .clip(CircleShape)
            .background(tone)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor
    )
}

/** An icon inside an eight-point star medallion with a gold keyline. */
@Composable
fun NurStarMedallion(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val gold = nurColors.ornament
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val star = starPath(center, size.toPx() / 2f, size.toPx() / 2f * 0.84f)
            drawPath(star, container)
            drawPath(star, gold, style = Stroke(1.2.dp.toPx()))
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

/**
 * The ayah card: gold double frame with star corners. Arabic in Amiri Quran, then the
 * translation and the reference. Used on Home, in the reader and for sources in AI answers.
 */
@Composable
fun NurAyahCard(
    arabic: String,
    translation: String,
    reference: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    arabicSizeSp: Int = 26,
    maxTranslationLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null
) {
    val c = nurColors
    NurSurfaceCard(modifier = modifier, onClick = onClick, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .border(BorderStroke(1.dp, c.frame), RoundedCornerShape(18.dp))
                .drawBehind {
                    val r = 5.dp.toPx()
                    val k = 14.dp.toPx()
                    listOf(
                        Offset(k, k), Offset(size.width - k, k),
                        Offset(k, size.height - k), Offset(size.width - k, size.height - k)
                    ).forEach { drawPath(starPath(it, r), c.ornament) }
                }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!label.isNullOrBlank()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.goldText, modifier = Modifier.padding(start = 8.dp))
            }
            if (arabic.isNotBlank()) {
                Text(
                    text = arabic,
                    style = quranTextStyle(arabicSizeSp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = translation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxTranslationLines,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(reference, style = MaterialTheme.typography.labelLarge, color = c.goldText, modifier = Modifier.weight(1f))
                footer?.invoke(this)
            }
        }
    }
}

/** A tappable row for lists and settings: medallion, title, subtitle and a trailing slot. */
@Composable
fun NurListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val base = modifier.fillMaxWidth().heightIn(min = 64.dp)
    Row(
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when {
            leading != null -> leading()
            leadingIcon != null -> NurStarMedallion(leadingIcon, size = 44.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                // Long Uzbek/Russian words break with a hyphen instead of mid-word.
                style = MaterialTheme.typography.titleMedium.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Thin divider used between list rows inside a card. */
@Composable
fun NurDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ---------------------------------------------------------------- actions

/** Primary action: a full-width filled pill, 56dp tall. */
@Composable
fun NurPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Secondary action: outlined pill with a gold keyline. */
@Composable
fun NurSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, nurColors.frame),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Empty / error state: star medallion, serif title, body and an optional action. */
@Composable
fun NurEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NurStarMedallion(icon, size = 72.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            NurSecondaryButton(actionText, onAction, modifier = Modifier.width(220.dp))
        }
    }
}

/** Rounded sheet that rises over the band's lower edge and holds a screen's main content. */
@Composable
fun NurSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(content = content)
    }
}
