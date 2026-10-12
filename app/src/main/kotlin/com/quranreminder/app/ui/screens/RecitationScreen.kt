package com.quranreminder.app.ui.screens

import android.Manifest
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.quranreminder.app.recitation.WordStatus
import com.quranreminder.app.recitation.WordMark
import com.quranreminder.app.recitation.TargetWord
import com.quranreminder.app.recitation.RecitationGoals
import com.quranreminder.app.recitation.RecitationAlignment
import com.quranreminder.app.recitation.WordAlignment
import com.quranreminder.app.database.RecitationResultEntity
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurIconButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.History
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
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

/** "check": the ayah is shown and marked; "memorize": it is hidden and opens word by word. */
private const val MODE_CHECK = "check"
private const val MODE_MEMORIZE = "memorize"

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecitationScreen(navController: NavHostController, quranViewModel: QuranViewModel) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val client = remember(strings) { RecitationClient(context, strings) }
    val history = remember { QuranDatabase.getDatabase(context).recitationHistoryDao() }
    var surah by rememberSaveable { mutableStateOf("1") }
    var ayah by rememberSaveable { mutableStateOf("1") }
    var fullSurah by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(MODE_CHECK) }
    // Opened from an ayah's menu or the review list: start on that ayah.
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
    var alignment by remember { mutableStateOf<WordAlignment?>(null) }
    val idleStatus = t("recitation.status.idle")
    var message by remember { mutableStateOf(idleStatus) }
    val surahVerses by quranViewModel.currentSurahVerses.collectAsState()
    val listeningStatus = t("recitation.status.listening")
    val analyzingStatus = t("recitation.status.analyzing")
    val readyStatus = t("recitation.status.ready")
    val needPermissionStatus = t("recitation.status.needPermission")

    val surahNumberNow = surah.toIntOrNull()?.coerceIn(1, 114) ?: 1
    val targetWords = remember(surahVerses, fullSurah, ayah) {
        val ayahNumber = ayah.toIntOrNull()
        val verses = surahVerses
            .filter { it.surahNumber == surahNumberNow && (fullSurah || it.ayahNumber == ayahNumber) }
            .sortedBy { it.ayahNumber }
        verses.flatMap { verse ->
            RecitationAlignment.words(verse.ayahTextArabic).map { TargetWord(verse.surahNumber, verse.ayahNumber, it) }
        }
    }
    val lastAyah = surahVerses.filter { it.surahNumber == surahNumberNow }.maxOfOrNull { it.ayahNumber } ?: 0

    // Today's goal and the streak, from the history kept on the device.
    val goal by RecitationGoals.dailyGoal(context)
    val activity by remember { history.observeAllActivity() }.collectAsState(initial = emptyList())
    val today = RecitationGoals.today()
    val todayAyahs = activity.firstOrNull { it.day == today }?.ayahs ?: 0
    val streak = RecitationGoals.streak(activity, goal, today)

    fun resetResult() {
        result = null
        alignment = null
    }

    fun save(final: WordAlignment) {
        val words = targetWords
        if (words.isEmpty() || final.marks.size != words.size) return
        val now = System.currentTimeMillis()
        val rows = words.indices.groupBy { words[it].surah to words[it].ayah }.map { (key, indexes) ->
            val marks = indexes.map { final.marks[it].status }
            RecitationResultEntity(
                surahNumber = key.first,
                ayahNumber = key.second,
                wordCount = marks.size,
                correctCount = marks.count { it == WordStatus.CORRECT },
                wrongCount = marks.count { it == WordStatus.WRONG },
                missedCount = marks.count { it == WordStatus.MISSED },
                mode = mode,
                createdAt = now,
                day = RecitationGoals.dayOf(now)
            )
        }
        scope.launch(Dispatchers.IO) { runCatching { history.insertAll(rows) } }
    }

    fun begin() {
        val ayahNumber = if (fullSurah) 0 else ayah.toIntOrNull()?.coerceAtLeast(1) ?: 1
        resetResult()
        quranViewModel.loadSurahVerses(surahNumberNow)
        val texts = targetWords.map { it.text }
        alignment = RecitationAlignment.align(texts, "", final = false)
        client.start(
            surahNumberNow,
            ayahNumber,
            onPartial = { partial -> alignment = RecitationAlignment.align(texts, partial.transcript, final = false) },
            onResult = { value ->
                val final = RecitationAlignment.align(texts, value.transcript, final = true)
                result = value
                alignment = final
                recording = false
                message = readyStatus
                if (value.transcript.isNotBlank()) save(final)
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
    // A new passage or mode starts clean.
    LaunchedEffect(surah, ayah, fullSurah, mode) { if (!recording) resetResult() }

    val surahName = surahVerses.firstOrNull { it.surahNumber == surahNumberNow }?.surahName.orEmpty()
    val showAyah = targetWords.isNotEmpty() && (!fullSurah || recording || result != null)
    val memorize = mode == MODE_MEMORIZE

    // The goal and mode sit above the ayah, so bring the ayah into view as recitation starts
    // and when the result arrives: the reader must see the words being marked.
    val ayahInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(recording, result) {
        if (recording || result != null) {
            kotlinx.coroutines.delay(120)
            ayahInView.bringIntoView()
        }
    }

    val navBar = LocalNavBarPadding.current
    val onRecordClick = {
        if (recording) { client.stop(); message = analyzingStatus }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val openHistory = { navController.navigate("recitation_history") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(
            title = t("recitation.title"),
            eyebrow = t("recitation.eyebrow"),
            trailing = { NurIconButton(Icons.Default.History, t("recitation.history"), onClick = openHistory) }
        )

        // Scrolling content: goal, mode, picker, the ayah being recited and the result.
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
            GoalStrip(todayAyahs = todayAyahs, goal = goal, streak = streak, onClick = openHistory)

            ModeSwitch(
                memorize = memorize,
                enabled = !recording,
                onChange = { mode = if (it) MODE_MEMORIZE else MODE_CHECK }
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
                    append(surahNumberNow)
                    if (!fullSurah) append(':').append(ayah.toIntOrNull() ?: 1)
                }
                Box(Modifier.bringIntoViewRequester(ayahInView)) {
                    AyahHighlightCard(
                        words = targetWords.map { it.text },
                        alignment = alignment,
                        hideUnread = memorize && result == null,
                        reference = reference
                    )
                }
            }

            val final = alignment
            if (result != null && final != null) {
                StarScoreGauge(score = final.accuracy)
                MistakesCard(words = targetWords.map { it.text }, alignment = final, transcript = result?.transcript.orEmpty())
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
                val current = ayah.toIntOrNull() ?: 1
                val hasNext = !fullSurah && current < lastAyah
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NurSecondaryButton(
                        text = t("recitation.retry"),
                        icon = Icons.Default.Refresh,
                        onClick = {
                            resetResult()
                            message = idleStatus
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (hasNext) {
                        NurPrimaryButton(
                            text = t("recitation.nextAyah"),
                            icon = Icons.Default.ArrowForward,
                            onClick = {
                                ayah = (current + 1).toString()
                                resetResult()
                                message = idleStatus
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Today's progress toward the daily goal and the current streak; opens the history. */
@Composable
private fun GoalStrip(todayAyahs: Int, goal: Int, streak: Int, onClick: () -> Unit) {
    val c = nurColors
    val progress by animateFloatAsState((todayAyahs.toFloat() / goal).coerceIn(0f, 1f), tween(700), label = "goal")
    val label = t("recitation.goal.today").replace("{done}", todayAyahs.toString()).replace("{goal}", goal.toString())
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = t("recitation.history"), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val track = MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(44.dp)) {
                    val stroke = 4.dp.toPx()
                    drawArc(track, 0f, 360f, false, style = Stroke(stroke))
                    drawArc(c.ornament, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("$todayAyahs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    when {
                        todayAyahs >= goal -> t("recitation.goal.done")
                        todayAyahs == 0 -> t("recitation.goal.notStarted")
                        else -> t("recitation.goal.left").replace("{n}", (goal - todayAyahs).toString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (streak > 0) c.ornament else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    t("recitation.streak.days").replace("{n}", streak.toString()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (streak > 0) c.goldText else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Check (ayah shown, mistakes marked) or memorize (ayah hidden, revealed as it is recited). */
@Composable
private fun ModeSwitch(memorize: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = nurColors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            val half = maxWidth / 2
            val x by animateDpAsState(if (memorize) half else 0.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "mode")
            Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Row(Modifier.fillMaxSize()) {
                listOf(false to t("recitation.mode.check"), true to t("recitation.mode.memorize")).forEach { (value, label) ->
                    val selected = value == memorize
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "modeText"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(enabled = enabled, role = Role.RadioButton) { onChange(value) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                    }
                }
            }
        }
        Text(
            if (memorize) t("recitation.mode.memorize.note") else t("recitation.mode.check.note"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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
 * The ayah being recited, in the same gold double frame as the ayah card. While reciting,
 * words reached turn gold and the next word takes the primary color; the final result marks
 * mistakes in red (a skipped word is underlined). In memorize mode the words not reached yet
 * are covered and open one by one as they are read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahHighlightCard(words: List<String>, alignment: WordAlignment?, hideUnread: Boolean, reference: String) {
    val c = nurColors
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val error = MaterialTheme.colorScheme.error
    val reached = alignment?.reached ?: 0
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    words.forEachIndexed { index, word ->
                        val status = alignment?.marks?.getOrNull(index)?.status ?: WordStatus.PENDING
                        val covered = hideUnread && status == WordStatus.PENDING
                        val target = when {
                            covered -> androidx.compose.ui.graphics.Color.Transparent
                            status == WordStatus.CORRECT -> c.goldText
                            status == WordStatus.WRONG || status == WordStatus.MISSED -> error
                            index == reached -> MaterialTheme.colorScheme.primary
                            else -> muted
                        }
                        val color by animateColorAsState(target, tween(260), label = "word")
                        val cover by animateFloatAsState(if (covered) 1f else 0f, tween(260), label = "cover")
                        val coverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        Text(
                            text = word,
                            style = quranTextStyle(28).copy(
                                textDecoration = if (status == WordStatus.MISSED) androidx.compose.ui.text.style.TextDecoration.Underline else null
                            ),
                            color = color,
                            modifier = Modifier.drawBehind {
                                if (cover > 0f) {
                                    drawRoundRect(
                                        coverColor.copy(alpha = coverColor.alpha * cover),
                                        topLeft = Offset(0f, size.height * 0.18f),
                                        size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.64f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                    )
                                }
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

/** What went wrong, word by word, and what was heard. */
@Composable
private fun MistakesCard(words: List<String>, alignment: WordAlignment, transcript: String) {
    val c = nurColors
    var showTranscript by remember { mutableStateOf(false) }
    val mistakes = alignment.marks.withIndex().filter { it.value.status == WordStatus.WRONG || it.value.status == WordStatus.MISSED }
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (transcript.isBlank()) {
                    Text(t("recitation.transcriptEmpty"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (mistakes.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = c.goldText, modifier = Modifier.size(24.dp))
                        Text(t("recitation.allCorrect"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Text(
                        t("recitation.mistakes.title").replace("{n}", mistakes.size.toString()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    mistakes.forEach { (index, mark) ->
                        MistakeRow(written = words[index], mark = mark)
                    }
                }
            }
            if (transcript.isNotBlank()) {
                NurDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTranscript = !showTranscript }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("recitation.transcriptTitle"), style = MaterialTheme.typography.labelLarge, color = c.goldText, modifier = Modifier.weight(1f))
                    Icon(
                        if (showTranscript) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.goldText
                    )
                }
                androidx.compose.animation.AnimatedVisibility(showTranscript) {
                    Text(
                        transcript,
                        style = quranTextStyle(22),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/** «written» skipped, or «written» read as «heard». */
@Composable
private fun MistakeRow(written: String, mark: WordMark) {
    val error = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (mark.status == WordStatus.MISSED) t("recitation.mistake.missed") else t("recitation.mistake.wrong"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        if (mark.status == WordStatus.WRONG && !mark.heard.isNullOrBlank()) {
            Text(mark.heard, style = quranTextStyle(20), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Text(written, style = quranTextStyle(22), color = error)
    }
}
