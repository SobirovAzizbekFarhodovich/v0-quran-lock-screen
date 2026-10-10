package com.quranreminder.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurLockup
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardingSlide(
    val icon: ImageVector,
    val titleKey: String,
    val descriptionKey: String
)

private val onboardingSlides = listOf(
    OnboardingSlide(Icons.Outlined.ScreenLockPortrait, "onboarding.slide1.title", "onboarding.slide1.desc"),
    OnboardingSlide(Icons.Outlined.AutoAwesome, "onboarding.slide2.title", "onboarding.slide2.desc"),
    OnboardingSlide(Icons.Outlined.Mic, "onboarding.slide3.title", "onboarding.slide3.desc"),
    OnboardingSlide(Icons.Outlined.OfflinePin, "onboarding.slide4.title", "onboarding.slide4.desc")
)

private val SheetOverlap = 24.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingSlides.size })
    val scope = rememberCoroutineScope()
    val index = pagerState.currentPage
    val isLast = index == onboardingSlides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurBand(bottomPadding = SheetOverlap + 12.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NurLockup(markSize = 32.dp)
                Spacer(Modifier.weight(1f))
                if (!isLast) {
                    TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(t("onboarding.skip"), style = MaterialTheme.typography.labelLarge, color = nurColors.onBand)
                    }
                }
            }
        }

        NurSheet(
            modifier = Modifier
                .weight(1f)
                .offset(y = -SheetOverlap)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                SlideContent(onboardingSlides[page])
            }

            StarPageIndicator(
                count = onboardingSlides.size,
                current = index,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )

            Box(
                Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp + SheetOverlap)
                    .navigationBarsPadding()
            ) {
                NurPrimaryButton(
                    text = if (isLast) t("onboarding.start") else t("onboarding.next"),
                    onClick = {
                        if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(index + 1) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SlideContent(slide: OnboardingSlide) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emblemSize = min(212.dp, maxHeight * 0.44f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SlideEmblem(icon = slide.icon, size = emblemSize)
            Spacer(Modifier.height(28.dp))
            Text(
                text = t(slide.titleKey),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = t(slide.descriptionKey),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Illustration for a slide, built only from the eight-point star: a soft halo star, a
 * turned gold keyline star with small stars at its tips, and a framed medallion holding
 * the feature icon.
 */
@Composable
private fun SlideEmblem(icon: ImageVector, size: Dp) {
    val c = nurColors
    val container = MaterialTheme.colorScheme.primaryContainer
    val tint = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val turned = -PI / 2 + PI / 8

            // Halo
            drawPath(starPath(center, r, r * 0.80f), container.copy(alpha = if (c.isDark) 0.55f else 0.6f))
            // Turned keyline with small stars at its tips
            val keyR = r * 0.90f
            drawPath(starPath(center, keyR, keyR * 0.80f, rotation = turned), c.frame, style = Stroke(1.dp.toPx()))
            repeat(8) { i ->
                val a = turned + i * PI / 4
                val tip = Offset(center.x + keyR * cos(a).toFloat(), center.y + keyR * sin(a).toFloat())
                drawPath(starPath(tip, 4.dp.toPx()), c.ornament)
            }
            // Medallion
            val medR = r * 0.60f
            val medallion = starPath(center, medR, medR * 0.84f)
            drawPath(medallion, container)
            drawPath(medallion, c.ornament, style = Stroke(1.6.dp.toPx()))
            val innerR = r * 0.50f
            drawPath(starPath(center, innerR, innerR * 0.84f), c.frame, style = Stroke(1.dp.toPx()))
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.26f))
    }
}

/** Page indicator: small gold stars; the current page is a larger filled star. */
@Composable
private fun StarPageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = nurColors
    val muted = MaterialTheme.colorScheme.outline
    val description = t("onboarding.page")
        .replace("%1", (current + 1).toString())
        .replace("%2", count.toString())
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val active = i == current
            val scale by animateFloatAsState(if (active) 1f else 0.62f, label = "pageStar")
            Canvas(Modifier.size(16.dp)) {
                val star = starPath(center, size.minDimension / 2f * scale)
                if (active) drawPath(star, c.ornament)
                else drawPath(star, muted, style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}
