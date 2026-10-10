package com.quranreminder.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quranreminder.app.ui.theme.LocalOnBand
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.quranTextStyle
import com.quranreminder.app.ui.theme.starLattice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Exponential ease-out: arrives quickly, settles softly. */
private val SettleEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Brief branded screen shown while the session/settings check completes.
 * Signature motion, once per composition: the emblem turns 45° into place as it
 * fades and grows in, then the wordmark rises. Everything is done in under a second,
 * so a fast settings load never cuts the motion off mid-way in a jarring state.
 */
@Composable
fun SplashScreen() {
    val c = nurColors
    val emblem = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { emblem.animateTo(1f, tween(durationMillis = 700, easing = SettleEasing)) }
        delay(320)
        wordmark.animateTo(1f, tween(durationMillis = 480, easing = SettleEasing))
    }

    CompositionLocalProvider(LocalOnBand provides true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(c.band, c.bandDeep)))
                .starLattice(c.ornament, alpha = 0.10f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NurEmblem(
                    size = 120.dp,
                    onDark = true,
                    modifier = Modifier.graphicsLayer {
                        val p = emblem.value
                        alpha = p
                        scaleX = 0.72f + 0.28f * p
                        scaleY = 0.72f + 0.28f * p
                        rotationZ = -45f * (1f - p)
                    }
                )
                Spacer(Modifier.height(24.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        val p = wordmark.value
                        alpha = p
                        translationY = (1f - p) * 12.dp.toPx()
                    }
                ) {
                    Text(
                        text = "Noorain",
                        style = MaterialTheme.typography.headlineLarge,
                        color = c.onBand
                    )
                    Text(
                        text = "نورين",
                        style = quranTextStyle(20),
                        color = c.ornament
                    )
                }
            }
        }
    }
}
