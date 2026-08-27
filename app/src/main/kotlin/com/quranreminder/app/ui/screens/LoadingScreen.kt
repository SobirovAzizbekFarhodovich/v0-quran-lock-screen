package com.quranreminder.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.LanguageDownloadState
import com.quranreminder.app.ui.viewmodel.QuranViewModel

/** Blocking gate shown only while the chosen language's content is still downloading. */
@Composable
fun LoadingScreen(
    language: String,
    viewModel: QuranViewModel,
    onDownloadComplete: () -> Unit
) {
    BackHandler(enabled = true) {}

    val state by viewModel.languageDownloadState.collectAsState()

    LaunchedEffect(language) { viewModel.downloadLanguageContent(language) }
    LaunchedEffect(state) {
        if (state is LanguageDownloadState.Success) onDownloadComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            is LanguageDownloadState.Failure -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                ) {
                    NurEmblem(size = 64.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        t("loading.failed"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { viewModel.downloadLanguageContent(language) }) {
                        Text(t("loading.retry"))
                    }
                }
            }
            else -> PulsingEmblem()
        }
    }
}

@Composable
private fun PulsingEmblem() {
    val infinite = rememberInfiniteTransition(label = "loading")
    val emblemScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblemScale"
    )
    val ringScale by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
        label = "ringScale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
        label = "ringAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }
                    .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
            )
            NurEmblem(
                modifier = Modifier.graphicsLayer {
                    scaleX = emblemScale
                    scaleY = emblemScale
                },
                size = 72.dp,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = t("loading.inProgress"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
