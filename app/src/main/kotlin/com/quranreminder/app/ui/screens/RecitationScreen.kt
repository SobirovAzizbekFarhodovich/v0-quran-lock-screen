package com.quranreminder.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.quranreminder.app.recitation.RecitationClient
import com.quranreminder.app.ui.theme.LocalAppStrings
import com.quranreminder.app.ui.theme.LocalNavBarPadding
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSecondaryButton
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.quranTextStyle
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import com.quranreminder.app.recitation.RecitationResult

@Suppress("UNUSED_PARAMETER")
@Composable
fun RecitationScreen(navController: NavHostController, quranViewModel: QuranViewModel) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val client = remember(strings) { RecitationClient(context, strings) }
    var surah by remember { mutableStateOf("1") }
    var ayah by remember { mutableStateOf("1") }
    var fullSurah by remember { mutableStateOf(false) }
    // Opened from an ayah's menu in the reader: start on that ayah.
    LaunchedEffect(com.quranreminder.app.ui.AyahHandoff.recite) {
        com.quranreminder.app.ui.AyahHandoff.recite?.let { (s, a) ->
            surah = s.toString()
            ayah = a.toString()
            fullSurah = false
            com.quranreminder.app.ui.AyahHandoff.recite = null
        }
    }
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
    DisposableEffect(client) { onDispose { client.close() } }
    LaunchedEffect(surah) { surah.toIntOrNull()?.let { quranViewModel.loadSurahVerses(it) } }

    val surahName = surahVerses.firstOrNull()?.surahName.orEmpty()
    val showAyah = targetWords.isNotEmpty() && (!fullSurah || recording || result != null)

    val navBar = LocalNavBarPadding.current
    val onRecordClick = {
        if (recording) { client.stop(); message = analyzingStatus }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(title = t("recitation.title"), eyebrow = t("recitation.eyebrow"))

        // Scrolling content: intro, picker, the ayah being recited and the result.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Soft fade where the scroll meets the pinned record area, instead of a hard cut.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 28.dp.toPx()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Black,
                            ((size.height - fade) / size.height).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Black,
                            1f to androidx.compose.ui.graphics.Color.Transparent
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                t("recitation.intro"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            PickerCard(
                surah = surah,
                onSurahChange = { surah = it.filter(Char::isDigit).take(3) },
                ayah = ayah,
                onAyahChange = { ayah = it.filter(Char::isDigit).take(3) },
                // The ayah card's reference already names the surah whenever it is shown.
                surahName = if (showAyah) "" else surahName,
                fullSurah = fullSurah,
                onFullSurahChange = { fullSurah = it },
                enabled = !recording
            )

            if (showAyah) {
                val reference = buildString {
                    if (surahName.isNotBlank()) append(surahName).append(" · ")
                    append(surah.toIntOrNull() ?: 1)
                    if (!fullSurah) append(':').append(ayah.toIntOrNull() ?: 1)
                }
                AyahHighlightCard(words = targetWords, matchedCount = matchedWordCount, reference = reference)
            }

            result?.let { value ->
                StarScoreGauge(score = value.score)
                ResultCard(transcript = value.transcript, issues = value.issues)
            }
        }

        // Pinned action area, always on screen and above the floating glass bar.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBar + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (result == null) {
                RecordButton(recording = recording, onClick = onRecordClick)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recording) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                NurSecondaryButton(
                    text = t("recitation.retry"),
                    icon = Icons.Default.Refresh,
                    onClick = {
                        result = null
                        matchedWordCount = 0
                        message = idleStatus
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PickerCard(
    surah: String,
    onSurahChange: (String) -> Unit,
    ayah: String,
    onAyahChange: (String) -> Unit,
    surahName: String,
    fullSurah: Boolean,
    onFullSurahChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    val c = nurColors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.ornament,
        unfocusedBorderColor = c.frame,
        focusedLabelColor = c.goldText,
        cursorColor = MaterialTheme.colorScheme.primary
    )
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = surah,
                        onValueChange = onSurahChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(t("recitation.surahNumber"), maxLines = 1) },
                        textStyle = MaterialTheme.typography.titleMedium,
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = numberKeyboard,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = if (fullSurah) "" else ayah,
                        onValueChange = onAyahChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(t("recitation.ayahNumber"), maxLines = 1) },
                        textStyle = MaterialTheme.typography.titleMedium,
                        singleLine = true,
                        enabled = enabled && !fullSurah,
                        keyboardOptions = numberKeyboard,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors
                    )
                }
                if (surahName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(surahName, style = MaterialTheme.typography.labelLarge, color = c.goldText, modifier = Modifier.padding(start = 4.dp))
                }
            }
            NurDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Switch) { onFullSurahChange(!fullSurah) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t("recitation.fullSurah"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(t("recitation.fullSurah.note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = fullSurah,
                    onCheckedChange = onFullSurahChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * Strips diacritics, tatweel and the Quranic annotation marks, and folds letter variants, so
 * the Uthmani ayah text and the recognizer's plain Arabic compare word for word. Without the
 * wasla alef «ٱ» and the pause marks (ۚ ۖ ۛ …) folded, «ٱللَّهِ» never matched «الله».
 */
private fun normalizeArabic(text: String): String {
    val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0640\\u0670\\u06D6-\\u06ED]")
    return marks.replace(text, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace("ء", "")
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

/**
 * The ayah being recited, in the same gold double frame as the ayah card. Words already
 * recited turn gold, the next word is in the primary color, the rest wait muted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahHighlightCard(words: List<String>, matchedCount: Int, reference: String) {
    val c = nurColors
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    NurSurfaceCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Arabic reads right to left: lay the words out RTL so the first word sits at the right.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    words.forEachIndexed { index, word ->
                        Text(
                            text = word,
                            style = quranTextStyle(28),
                            color = when {
                                index < matchedCount -> c.goldText
                                index == matchedCount -> MaterialTheme.colorScheme.primary
                                else -> muted
                            }
                        )
                    }
                }
            }
            Text(reference, style = MaterialTheme.typography.labelLarge, color = c.goldText)
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val c = nurColors
    val infinite = rememberInfiniteTransition(label = "record")
    val ringScale by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
        label = "ringScale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
        label = "ringAlpha"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val container = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val content = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    val description = if (recording) t("recitation.stop") else t("recitation.start")

    Box(modifier = Modifier.size(144.dp), contentAlignment = Alignment.Center) {
        // Resting gold star keyline around the button; it pulses outward while recording.
        Canvas(
            Modifier
                .size(144.dp)
                .graphicsLayer {
                    val s = if (recording) ringScale else 0.82f
                    scaleX = s; scaleY = s
                    alpha = if (recording) ringAlpha else 0.9f
                }
        ) {
            drawPath(
                starPath(center, size.minDimension / 2f - 2.dp.toPx(), size.minDimension / 2f * 0.84f),
                c.ornament,
                style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = if (recording) pulse else 1f; scaleY = if (recording) pulse else 1f }
                .clip(CircleShape)
                .background(container)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/** Score drawn as an eight-point star whose gold outline fills in proportion to the score. */
@Composable
private fun StarScoreGauge(score: Int) {
    val c = nurColors
    val clamped = score.coerceIn(0, 100)
    val progress by animateFloatAsState(targetValue = clamped / 100f, animationSpec = tween(900), label = "score")
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primaryContainer
    val label = t("recitation.scoreLabel")
    Box(
        modifier = Modifier
            .size(188.dp)
            .semantics { contentDescription = "$label: $clamped%" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(188.dp)) {
            val stroke = 7.dp.toPx()
            val outer = size.minDimension / 2f - stroke
            val star = starPath(center, outer, outer * 0.8f)
            drawPath(star, fill)
            drawPath(star, track, style = Stroke(stroke, join = StrokeJoin.Round))
            val measure = PathMeasure().apply { setPath(star, false) }
            val segment = Path()
            measure.getSegment(0f, measure.length * progress, segment, true)
            drawPath(segment, c.ornament, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$clamped%", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.goldText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(transcript: String, issues: List<String>) {
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("recitation.transcriptTitle"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (transcript.isBlank()) {
                    Text(t("recitation.transcriptEmpty"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        transcript,
                        style = quranTextStyle(22),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (issues.isNotEmpty()) {
                NurDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("recitation.issuesTitle"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        issues.forEach {
                            NurPill(
                                text = it,
                                tone = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
