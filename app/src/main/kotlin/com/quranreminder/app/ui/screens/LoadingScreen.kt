package com.quranreminder.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranreminder.app.ui.theme.LocalOnBand
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurEmptyState
import com.quranreminder.app.ui.theme.NurLockup
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.starLattice
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.LanguageDownloadState
import com.quranreminder.app.ui.viewmodel.QuranViewModel

/**
 * Gate shown while [language]'s content is loading. Bundled languages finish almost
 * instantly; downloads that fail show a localized reason, Retry, and — when
 * [fallbackLanguage] is given — a way back to a language that works offline.
 */
@Composable
fun LoadingScreen(
    language: String,
    viewModel: QuranViewModel,
    onDownloadComplete: () -> Unit,
    fallbackLanguage: String? = null,
    onUseFallback: (String) -> Unit = {}
) {
    val state by viewModel.languageDownloadState.collectAsState()
    // Only react to state that belongs to *this* language; a stale Success/Failure
    // from a previous request must not complete or fail this gate.
    val failure = (state as? LanguageDownloadState.Failure)?.takeIf { it.language == language }
    val succeeded = (state as? LanguageDownloadState.Success)?.language == language

    BackHandler(enabled = true) {
        if (failure != null && fallbackLanguage != null) onUseFallback(fallbackLanguage)
    }

    LaunchedEffect(language) { viewModel.downloadLanguageContent(language) }
    LaunchedEffect(succeeded) {
        if (succeeded) onDownloadComplete()
    }

    if (failure != null) {
        FailureState(
            messageKey = failure.messageKey,
            onRetry = { viewModel.downloadLanguageContent(language) },
            fallbackLanguage = fallbackLanguage?.takeIf { it != language },
            onUseFallback = onUseFallback
        )
    } else {
        ProgressState(languageName = appLanguageLabel(language))
    }
}

/** Calm in-progress state on the green field, continuing the splash. */
@Composable
private fun ProgressState(languageName: String) {
    val c = nurColors
    val infinite = rememberInfiniteTransition(label = "loading")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    CompositionLocalProvider(LocalOnBand provides true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(c.band, c.bandDeep)))
                .starLattice(c.ornament, alpha = 0.10f)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
            ) {
                NurEmblem(
                    size = 96.dp,
                    onDark = true,
                    modifier = Modifier.graphicsLayer {
                        val s = 0.96f + 0.06f * pulse
                        scaleX = s
                        scaleY = s
                        alpha = 0.82f + 0.18f * pulse
                    }
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = languageName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.onBand,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t("loading.inProgress"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onBandMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                // The view model reports no fraction, so progress is indeterminate.
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = c.ornament,
                    trackColor = c.onBand.copy(alpha = 0.16f)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = t("loading.hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onBandMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FailureState(
    messageKey: String,
    onRetry: () -> Unit,
    fallbackLanguage: String?,
    onUseFallback: (String) -> Unit
) {
    val overlap = 24.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurBand(bottomPadding = overlap + 16.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NurLockup(markSize = 32.dp)
            }
        }
        NurSheet(
            modifier = Modifier
                .weight(1f)
                .offset(y = -overlap)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = overlap)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                NurEmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = t("loading.failed"),
                    body = t(messageKey),
                    actionText = t("loading.retry"),
                    onAction = onRetry
                )
                if (fallbackLanguage != null) {
                    TextButton(
                        onClick = { onUseFallback(fallbackLanguage) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = t("loading.backTo").replace("%s", appLanguageLabel(fallbackLanguage)),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
