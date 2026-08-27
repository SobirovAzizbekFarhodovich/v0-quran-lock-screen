package com.quranreminder.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.recitation.RecitationClient
import com.quranreminder.app.recitation.RecitationResult
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel

@Composable
fun RecitationScreen(navController: NavHostController, quranViewModel: QuranViewModel) {
    val context = LocalContext.current
    val token = remember { SessionManager(context).accessToken.orEmpty() }
    val client = remember(token) { RecitationClient(token) }
    var surah by remember { mutableStateOf("1") }
    var ayah by remember { mutableStateOf("1") }
    var fullSurah by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RecitationResult?>(null) }
    val idleStatus = t("recitation.status.idle")
    var message by remember { mutableStateOf(idleStatus) }
    var matchedWordCount by remember { mutableIntStateOf(0) }
    val surahVerses by quranViewModel.currentSurahVerses.collectAsState()
    val listeningStatus = t("recitation.status.listening")
    val analyzingStatus = t("recitation.status.analyzing")
    val readyStatus = t("recitation.status.ready")
    val needPermissionStatus = t("recitation.status.needPermission")

    val targetWords = remember(surahVerses, fullSurah, ayah) {
        val ayahNumber = ayah.toIntOrNull()
        val verses = if (fullSurah) surahVerses else surahVerses.filter { it.ayahNumber == ayahNumber }
        verses.sortedBy { it.ayahNumber }.flatMap { verse -> verse.ayahTextArabic.trim().split(Regex("\\s+")) }.filter { it.isNotBlank() }
    }

    fun begin() {
        val surahNumber = surah.toIntOrNull()?.coerceIn(1, 114) ?: 1
        val ayahNumber = if (fullSurah) 0 else ayah.toIntOrNull()?.coerceAtLeast(1) ?: 1
        result = null
        matchedWordCount = 0
        quranViewModel.loadSurahVerses(surahNumber)
        client.start(
            surahNumber,
            ayahNumber,
            onPartial = { partial -> matchedWordCount = matchArabicWords(partial.transcript, targetWords) },
            onResult = { value ->
                result = value
                matchedWordCount = targetWords.size
                recording = false
                message = readyStatus
            },
            onError = { error -> recording = false; message = error }
        )
        recording = true
        message = listeningStatus
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else message = needPermissionStatus
    }
    DisposableEffect(Unit) { onDispose { client.close() } }
    LaunchedEffect(surah) { surah.toIntOrNull()?.let { quranViewModel.loadSurahVerses(it) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(
            title = t("recitation.title"), eyebrow = t("recitation.eyebrow"),
            leading = { NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() }) }
        )
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                t("recitation.intro"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(surah, { surah = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(t("recitation.surahNumber")) }, singleLine = true)
            if (!fullSurah) {
                OutlinedTextField(ayah, { ayah = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(t("recitation.ayahNumber")) }, singleLine = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(t("recitation.fullSurah"), style = MaterialTheme.typography.titleSmall)
                    Text(t("recitation.fullSurah.note"), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = fullSurah, onCheckedChange = { fullSurah = it })
            }

            if (targetWords.isNotEmpty() && (recording || result != null)) {
                AyahHighlightCard(words = targetWords, matchedCount = matchedWordCount)
            }

            Spacer(Modifier.height(4.dp))

            if (result == null) {
                RecordButton(
                    recording = recording,
                    onClick = {
                        if (recording) { client.stop(); message = analyzingStatus }
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            result?.let { value ->
                ScoreGauge(score = value.score)
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("recitation.transcriptLabel") + value.transcript.ifBlank { t("recitation.transcriptEmpty") })
                        if (value.issues.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                value.issues.forEach { NurPill(text = it, tone = NurColors.TerracottaSoft) }
                            }
                        }
                    }
                }
                Text(
                    text = t("recitation.retry"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            result = null
                            matchedWordCount = 0
                            message = idleStatus
                        }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Strips Arabic diacritics/tatweel and normalizes alef/ya/hamza variants for robust matching. */
private fun normalizeArabic(text: String): String {
    val diacritics = Regex("[\\u064B-\\u0652\\u0640\\u0670]")
    return diacritics.replace(text, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .trim()
}

/**
 * Returns how many of [targetWords], read in order from the start, are matched by the
 * words in [transcript] — a simple karaoke-style "how far has the reader gotten" signal.
 * Whisper may re-transcribe the whole buffer on every partial, so this always re-aligns
 * from the beginning rather than trying to diff against the previous partial.
 */
private fun matchArabicWords(transcript: String, targetWords: List<String>): Int {
    if (targetWords.isEmpty() || transcript.isBlank()) return 0
    val heard = transcript.trim().split(Regex("\\s+")).map { normalizeArabic(it) }.filter { it.isNotBlank() }
    val target = targetWords.map { normalizeArabic(it) }
    var matched = 0
    for (word in heard) {
        if (matched < target.size && target[matched] == word) matched++
    }
    return matched
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahHighlightCard(words: List<String>, matchedCount: Int) {
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            words.forEachIndexed { index, word ->
                val isMatched = index < matchedCount
                val isCurrent = index == matchedCount
                Text(
                    text = word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isMatched -> NurColors.Gold
                        isCurrent -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "record")
    val ringScale by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
        label = "ringScale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
        label = "ringAlpha"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .drawBehind {
                        drawCircle(color = NurColors.Gold, style = Stroke(width = 3.dp.toPx()))
                    }
            )
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = if (recording) pulse else 1f; scaleY = if (recording) pulse else 1f }
                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording) "To'xtatish" else "Yozib olishni boshlash",
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun ScoreGauge(score: Int) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onBackground
    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = 10.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
        }
        Text(
            text = "$score%",
            style = MaterialTheme.typography.headlineSmall,
            color = textColor
        )
    }
}
