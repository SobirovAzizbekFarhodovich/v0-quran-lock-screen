package com.quranreminder.app.ui.screens

import android.Manifest
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import com.quranreminder.app.recitation.AyahResult
import com.quranreminder.app.recitation.RecitationReporter
import com.quranreminder.app.recitation.RecitationSync
import com.quranreminder.app.recitation.RecitationTracker
import com.quranreminder.app.recitation.QuranText
import com.quranreminder.app.recitation.QuranLocator
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

/** One word on the card: its text, how it was read, and whether it belongs to this check. */
private data class CardWord(
    val text: String,
    val status: WordStatus?,
    val hinted: Boolean,
    val current: Boolean,
    /** Last word of an ayah: the ayah number is drawn after it. */
    val ayahEnd: Int?
)

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
    // Automatic: just start reading, the ayah is recognised. Chosen: a surah and ayah picked.
    var automatic by rememberSaveable { mutableStateOf(true) }
    var pageView by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(MODE_CHECK) }
    // Opened from an ayah's menu or the review list: that ayah, chosen.
    LaunchedEffect(com.quranreminder.app.ui.AyahHandoff.recite) {
        com.quranreminder.app.ui.AyahHandoff.recite?.let { (s, a) ->
            surah = s.toString()
            ayah = a.toString()
            fullSurah = false
            automatic = false
            com.quranreminder.app.ui.AyahHandoff.recite = null
        }
    }

    var locator by remember { mutableStateOf<QuranLocator?>(null) }
    var pages by remember { mutableStateOf<IntArray?>(null) }
    LaunchedEffect(Unit) {
        locator = QuranText.locator(context)
        pages = runCatching { QuranText.pages(context) }.getOrNull()
        RecitationSync.syncNow(context)
    }
    var tracker by remember { mutableStateOf<RecitationTracker?>(null) }
    // Bumped whenever the tracker's state changes (it is not itself observable).
    var revision by remember { mutableIntStateOf(0) }
    var hinted by remember { mutableStateOf(setOf<Int>()) }
    var recording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RecitationResult?>(null) }
    val idleStatus = t("recitation.status.idle")
    val autoIdleStatus = t("recitation.status.autoIdle")
    var message by remember { mutableStateOf("") }
    val listeningStatus = t("recitation.status.listening")
    val analyzingStatus = t("recitation.status.analyzing")
    val readyStatus = t("recitation.status.ready")
    val needPermissionStatus = t("recitation.status.needPermission")
    val reportSentText = t("recitation.report.sent")
    val reportFailedText = t("recitation.report.failed")

    val surahNumberNow = surah.toIntOrNull()?.coerceIn(1, 114) ?: 1
    val ayahNumberNow = ayah.toIntOrNull()?.coerceAtLeast(1) ?: 1

    // Before recording, a chosen passage is shown so the reader can look at it first.
    LaunchedEffect(locator, automatic, surahNumberNow, ayahNumberNow, fullSurah) {
        val loc = locator ?: return@LaunchedEffect
        if (recording) return@LaunchedEffect
        result = null
        hinted = emptySet()
        tracker = if (automatic) null else RecitationTracker.chosen(loc, surahNumberNow, ayahNumberNow, fullSurah)
        revision++
    }
    LaunchedEffect(automatic) { if (!recording && result == null) message = if (automatic) autoIdleStatus else idleStatus }

    val goal by RecitationGoals.dailyGoal(context)
    val activity by remember { history.observeAllActivity() }.collectAsState(initial = emptyList())
    val today = RecitationGoals.today()
    val todayAyahs = activity.firstOrNull { it.day == today }?.ayahs ?: 0
    val streak = RecitationGoals.streak(activity, goal, today)

    fun save(results: List<AyahResult>) {
        if (results.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = results.map {
            RecitationResultEntity(
                surahNumber = it.surah, ayahNumber = it.ayah, wordCount = it.words,
                correctCount = it.correct, wrongCount = it.wrong, missedCount = it.missed,
                mode = mode, createdAt = now, day = RecitationGoals.dayOf(now)
            )
        }
        scope.launch(Dispatchers.IO) {
            runCatching { history.insertAll(rows) }
            RecitationSync.syncNow(context)
        }
    }

    fun begin() {
        val loc = locator ?: return
        val session = if (automatic) RecitationTracker.automatic(loc)
            else RecitationTracker.chosen(loc, surahNumberNow, ayahNumberNow, fullSurah) ?: return
        tracker = session
        result = null
        hinted = emptySet()
        revision++
        client.start(
            if (automatic) 0 else surahNumberNow,
            if (automatic || fullSurah) 0 else ayahNumberNow,
            onPartial = { partial ->
                session.update(partial.transcript, final = false)
                revision++
            },
            onResult = { value ->
                session.update(value.transcript, final = true)
                result = value
                recording = false
                revision++
                message = readyStatus
                if (value.transcript.isNotBlank() && session.located) save(session.ayahResults())
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

    // What the card shows: the ayah (or the mushaf page) the reader is on.
    val current = remember(revision, tracker) { tracker?.let { tr -> tr.passage.getOrNull(tr.currentIndex()) } }
    val memorize = mode == MODE_MEMORIZE
    val cardWords: List<CardWord> = remember(revision, tracker, pageView, pages, hinted, recording, result, memorize) {
        val tr = tracker ?: return@remember emptyList()
        val loc = locator ?: return@remember emptyList()
        val start = tr.start ?: return@remember emptyList()
        val passage = tr.passage
        if (passage.isEmpty()) return@remember emptyList()
        val at = passage[tr.currentIndex()]
        val marks = tr.alignment?.marks
        val reached = tr.alignment?.reached ?: 0
        val ayahs = if (pageView && pages != null) QuranText.pageAyahs(loc, pages!!, at.surah, at.ayah).second
            else listOf(at.surah to at.ayah)
        ayahs.flatMap { (s, a) ->
            val from = loc.firstWordOf(s, a) ?: return@flatMap emptyList()
            val to = loc.endOfAyah(s, a)
            (from until to).map { g ->
                val p = g - start
                val inPassage = p in passage.indices
                CardWord(
                    text = loc.words[g].text,
                    status = if (inPassage) marks?.getOrNull(p)?.status ?: WordStatus.PENDING else null,
                    hinted = inPassage && p in hinted,
                    current = inPassage && p == reached && result == null,
                    ayahEnd = if (g == to - 1) a else null
                )
            }
        }
    }
    val loadedVerses by quranViewModel.currentSurahVerses.collectAsState()
    val reference = remember(current, pageView, pages, loadedVerses) {
        val c = current ?: return@remember ""
        val name = loadedVerses.firstOrNull { it.surahNumber == c.surah }?.surahName
        val page = if (pageView && pages != null && locator != null) QuranText.pageAyahs(locator!!, pages!!, c.surah, c.ayah).first else 0
        buildString {
            if (!name.isNullOrBlank()) append(name).append(" · ")
            append(c.surah).append(':').append(c.ayah)
            if (page > 0) append(" · ").append(page).append("-sahifa")
        }
    }
    LaunchedEffect(current?.surah) { current?.surah?.let { quranViewModel.loadSurahVerses(it) } }

    // The goal and mode sit above the card, so bring it into view as recitation starts
    // and when the result arrives: the reader must see the words being marked.
    val cardInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(recording, result) {
        if (recording || result != null) {
            kotlinx.coroutines.delay(120)
            cardInView.bringIntoView()
        }
    }

    val navBar = LocalNavBarPadding.current
    val onRecordClick = {
        if (recording) { client.stop(); message = analyzingStatus }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val openHistory = { navController.navigate("recitation_history") }
    var reportOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(
            title = t("recitation.title"),
            eyebrow = t("recitation.eyebrow"),
            trailing = { NurIconButton(Icons.Default.History, t("recitation.history"), onClick = openHistory) }
        )

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
            ModeSwitch(
                memorize = memorize,
                enabled = !recording,
                onChange = { mode = if (it) MODE_MEMORIZE else MODE_CHECK }
            )

            SourceCard(
                automatic = automatic,
                onAutomaticChange = { automatic = it },
                surah = surahNumberNow,
                onSurahChange = { surah = it.toString(); ayah = "1" },
                ayah = ayah,
                onAyahChange = { ayah = it.filter(Char::isDigit).take(3) },
                fullSurah = fullSurah,
                onFullSurahChange = { fullSurah = it },
                enabled = !recording
            )

            Box(Modifier.bringIntoViewRequester(cardInView)) {
                RecitationCard(
                    words = cardWords,
                    reference = reference,
                    hideUnread = memorize && result == null,
                    waitingForAyah = automatic && tracker?.located != true,
                    recording = recording,
                    pageView = pageView,
                    onPageViewChange = { pageView = it },
                    listenTo = current?.let { it.surah to it.ayah }
                )
            }

            if (memorize && recording) {
                val tr = tracker
                NurSecondaryButton(
                    text = t("recitation.hint"),
                    icon = Icons.Default.Lightbulb,
                    enabled = tr?.located == true,
                    onClick = {
                        val next = tr?.alignment?.reached ?: 0
                        if (tr != null && next < tr.passage.size) hinted = hinted + next
                    }
                )
            }

            val final = tracker?.alignment
            val value = result
            if (value != null && final != null && tracker?.located == true) {
                StarScoreGauge(score = final.accuracy)
                MistakesCard(
                    words = tracker!!.passage.map { it.text },
                    alignment = final,
                    transcript = value.transcript,
                    hints = hinted.size
                )
                androidx.compose.material3.TextButton(onClick = { reportOpen = true }) {
                    Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("recitation.report.button"))
                }
            } else if (value != null) {
                // Nothing recognisable was heard (or the ayah could not be found).
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (value.transcript.isBlank()) t("recitation.transcriptEmpty") else t("recitation.notFound"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Today's goal and streak sit below the work, not between the reader and the ayah.
            GoalStrip(todayAyahs = todayAyahs, goal = goal, streak = streak, onClick = openHistory)
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
                    message.ifBlank { if (automatic) autoIdleStatus else idleStatus },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recording) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                val last = tracker?.passage?.lastOrNull()
                val loc = locator
                val nextStart = if (last != null && loc != null) loc.firstWordOf(last.surah, last.ayah + 1) else null
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NurSecondaryButton(
                        text = t("recitation.retry"),
                        icon = Icons.Default.Refresh,
                        onClick = {
                            result = null
                            hinted = emptySet()
                            tracker = if (automatic || locator == null) null
                                else RecitationTracker.chosen(locator!!, surahNumberNow, ayahNumberNow, fullSurah)
                            revision++
                            message = if (automatic) autoIdleStatus else idleStatus
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (nextStart != null && last != null && !fullSurah) {
                        NurPrimaryButton(
                            text = t("recitation.nextAyah"),
                            icon = Icons.Default.ArrowForward,
                            onClick = {
                                // Carry on from the ayah after the last one read, as a chosen ayah.
                                automatic = false
                                surah = last.surah.toString()
                                ayah = (last.ayah + 1).toString()
                                result = null
                                message = idleStatus
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (reportOpen) {
        ReportDialog(
            onDismiss = { reportOpen = false },
            onSend = { note ->
                reportOpen = false
                val tr = tracker
                val first = tr?.passage?.firstOrNull()
                val pcm = client.lastRecording()
                scope.launch {
                    val ok = tr != null && first != null && RecitationReporter.send(
                        context,
                        pcm = pcm,
                        surah = first.surah,
                        ayah = if (tr.passage.all { it.ayah == first.ayah }) first.ayah else 0,
                        expected = tr.passage.joinToString(" ") { it.text },
                        transcript = result?.transcript.orEmpty(),
                        alignment = tr.alignment?.marks?.joinToString("") { m -> m.status.name.take(1) }.orEmpty(),
                        note = note
                    )
                    android.widget.Toast.makeText(context, if (ok) reportSentText else reportFailedText, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/** Automatic (just read) or a chosen surah and ayah. */
@Composable
private fun SourceCard(
    automatic: Boolean,
    onAutomaticChange: (Boolean) -> Unit,
    surah: Int,
    onSurahChange: (Int) -> Unit,
    ayah: String,
    onAyahChange: (String) -> Unit,
    fullSurah: Boolean,
    onFullSurahChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SegmentedPair(
                left = t("recitation.source.auto"),
                right = t("recitation.source.chosen"),
                rightSelected = !automatic,
                enabled = enabled,
                onChange = { onAutomaticChange(!it) }
            )
            androidx.compose.animation.AnimatedContent(targetState = automatic, label = "source") { auto ->
                if (auto) {
                    Text(
                        t("recitation.source.auto.note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    PickerFields(
                        surah = surah,
                        onSurahChange = onSurahChange,
                        ayah = ayah,
                        onAyahChange = onAyahChange,
                        fullSurah = fullSurah,
                        onFullSurahChange = onFullSurahChange,
                        enabled = enabled
                    )
                }
            }
        }
    }
}

/** Two options with a pill that slides to the chosen one. */
@Composable
private fun SegmentedPair(left: String, right: String, rightSelected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (rightSelected) half else 0.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "pair")
        Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Row(Modifier.fillMaxSize()) {
            listOf(false to left, true to right).forEach { (value, label) ->
                val selected = value == rightSelected
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pairText"
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

/** Surah (picked by name) and ayah number, or the whole surah. */
@Composable
private fun PickerFields(
    surah: Int,
    onSurahChange: (Int) -> Unit,
    ayah: String,
    onAyahChange: (String) -> Unit,
    fullSurah: Boolean,
    onFullSurahChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    val c = nurColors
    val context = LocalContext.current
    val surahs by remember { QuranDatabase.getDatabase(context).quranVerseDao().getAllSurahs() }.collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.ornament,
        unfocusedBorderColor = c.frame,
        focusedLabelColor = c.goldText,
        cursorColor = MaterialTheme.colorScheme.primary
    )
    val surahName = surahs.firstOrNull { it.surahNumber == surah }?.surahName.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The surah is chosen from a list of names, not typed as a number.
            Box(
                Modifier
                    .weight(1.4f)
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(BorderStroke(1.dp, c.frame), MaterialTheme.shapes.small)
                    .clickable(enabled = enabled, role = Role.Button) { picking = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(t("recitation.surahNumber"), style = MaterialTheme.typography.labelSmall, color = c.goldText)
                    Text(
                        "$surah. $surahName",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            OutlinedTextField(
                value = if (fullSurah) "" else ayah,
                onValueChange = onAyahChange,
                modifier = Modifier.weight(1f),
                label = { Text(t("recitation.ayahNumber"), maxLines = 1) },
                textStyle = MaterialTheme.typography.titleMedium,
                singleLine = true,
                enabled = enabled && !fullSurah,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled, role = Role.Switch) { onFullSurahChange(!fullSurah) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("recitation.fullSurah"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(
                checked = fullSurah,
                onCheckedChange = onFullSurahChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
    if (picking) {
        SurahPickerDialog(
            surahs = surahs,
            onPick = { onSurahChange(it); picking = false },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun SurahPickerDialog(
    surahs: List<com.quranreminder.app.database.SurahHeader>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, surahs) {
        val q = query.trim().lowercase()
        // Typed in Latin or Cyrillic, whatever script the names are shown in.
        val latinQuery = com.quranreminder.app.lockscreen.CyrillicToLatinConverter.convert(q).lowercase().filter { it.isLetterOrDigit() }
        if (q.isEmpty()) surahs else surahs.filter {
            val latinName = com.quranreminder.app.lockscreen.CyrillicToLatinConverter.convert(it.surahName).lowercase().filter { c -> c.isLetterOrDigit() }
            it.surahName.lowercase().contains(q) || latinName.contains(latinQuery) ||
                it.surahNumber.toString() == q || it.surahNameArabic.contains(q)
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        NurSurfaceCard(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(t("recitation.surahSearch")) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn {
                    items(shown.size) { i ->
                        val s = shown[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onPick(s.surahNumber) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("${s.surahNumber}", style = MaterialTheme.typography.labelLarge, color = nurColors.goldText, modifier = Modifier.width(32.dp))
                            Text(s.surahName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Text(s.surahNameArabic, style = quranTextStyle(18), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The ayah (or the whole mushaf page) being recited, in the gold double frame of the ayah
 * card. While reciting, words reached turn gold and the next word takes the primary color;
 * the final result marks mistakes in red (a skipped word underlined) and harakat slips in
 * amber. In memorize mode the words not reached yet are covered and open as they are read
 * (or when a hint is asked for). Words of the page outside this check stay plain.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecitationCard(
    words: List<CardWord>,
    reference: String,
    hideUnread: Boolean,
    waitingForAyah: Boolean,
    recording: Boolean,
    pageView: Boolean,
    onPageViewChange: (Boolean) -> Unit,
    listenTo: Pair<Int, Int>?
) {
    val c = nurColors
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val plain = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val error = MaterialTheme.colorScheme.error
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
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reference, style = MaterialTheme.typography.labelLarge, color = c.goldText, modifier = Modifier.weight(1f))
                if (listenTo != null) AyahListenButton(listenTo.first, listenTo.second, enabled = !recording)
                androidx.compose.material3.IconButton(onClick = { onPageViewChange(!pageView) }) {
                    Icon(
                        if (pageView) Icons.Default.ViewAgenda else Icons.Default.MenuBook,
                        contentDescription = if (pageView) t("recitation.view.ayah") else t("recitation.view.page"),
                        tint = c.goldText
                    )
                }
            }
            if (waitingForAyah) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Mic, null, tint = c.ornament, modifier = Modifier.size(28.dp))
                    Text(
                        if (recording) t("recitation.auto.searching") else t("recitation.auto.hint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Arabic reads right to left: lay the words out RTL so the first word sits at the right.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        words.forEach { word ->
                            val status = word.status
                            val covered = hideUnread && status == WordStatus.PENDING && !word.hinted
                            val target = when {
                                covered -> androidx.compose.ui.graphics.Color.Transparent
                                status == null -> plain
                                word.hinted && status == WordStatus.PENDING -> muted
                                status == WordStatus.CORRECT -> c.goldText
                                status == WordStatus.WRONG || status == WordStatus.MISSED -> error
                                status == WordStatus.HARAKAT -> HarakatAmber
                                word.current -> MaterialTheme.colorScheme.primary
                                else -> muted
                            }
                            val color by animateColorAsState(target, tween(260), label = "word")
                            val cover by animateFloatAsState(if (covered) 1f else 0f, tween(260), label = "cover")
                            val coverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = word.text,
                                    style = quranTextStyle(if (pageView) 24 else 28).copy(
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
                                word.ayahEnd?.let { number ->
                                    Spacer(Modifier.width(6.dp))
                                    QuranStarNumber(number = number, size = 28.dp, gold = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plays the ayah recited by Mishary Alafasy (everyayah.com) to listen and repeat. */
@Composable
private fun AyahListenButton(surah: Int, ayah: Int, enabled: Boolean) {
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playing = false
    }
    DisposableEffect(surah, ayah) { onDispose { stop() } }
    LaunchedEffect(enabled) { if (!enabled) stop() }
    androidx.compose.material3.IconButton(
        enabled = enabled,
        onClick = {
            if (playing) {
                stop()
            } else {
                val url = "https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3".format(surah, ayah)
                val mp = android.media.MediaPlayer()
                runCatching {
                    mp.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    mp.setDataSource(url)
                    mp.setOnPreparedListener { it.start() }
                    mp.setOnCompletionListener { stop() }
                    mp.setOnErrorListener { _, _, _ -> stop(); true }
                    mp.prepareAsync()
                    player = mp
                    playing = true
                }.onFailure { mp.release() }
            }
        }
    ) {
        Icon(
            if (playing) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = t("recitation.listen"),
            tint = nurColors.goldText
        )
    }
}

/** "This result is wrong": sends the recording, only after the user agrees. */
@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("recitation.report.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("recitation.report.body"), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(t("recitation.report.note")) },
                    minLines = 2
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSend(note) }) { Text(t("recitation.report.send")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("common.cancel")) } }
    )
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

    Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        // Resting gold star keyline around the button; it pulses outward while recording.
        Canvas(
            Modifier
                .size(116.dp)
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
                .size(76.dp)
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
                modifier = Modifier.size(34.dp)
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
private fun MistakesCard(words: List<String>, alignment: WordAlignment, transcript: String, hints: Int = 0) {
    val c = nurColors
    var showTranscript by remember { mutableStateOf(false) }
    val mistakes = alignment.marks.withIndex().filter {
        it.value.status == WordStatus.WRONG || it.value.status == WordStatus.MISSED || it.value.status == WordStatus.HARAKAT
    }
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hints > 0) {
                    Text(
                        t("recitation.hintsUsed").replace("{n}", hints.toString()),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.goldText
                    )
                }
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

/** Amber for a harakat slip: the word was right, a vowel was not. */
private val HarakatAmber = androidx.compose.ui.graphics.Color(0xFFC77A1F)

/** «written» skipped, «written» read as «heard», or a vowel read differently inside «written». */
@Composable
private fun MistakeRow(written: String, mark: WordMark) {
    val isHarakat = mark.status == WordStatus.HARAKAT
    val accent = if (isHarakat) HarakatAmber else MaterialTheme.colorScheme.error
    val slip = mark.slip
    val label = when {
        mark.status == WordStatus.MISSED -> t("recitation.mistake.missed")
        isHarakat && slip != null -> t("recitation.mistake.harakat")
            .replace("{letter}", slip.letter.toString())
            .replace("{pos}", slip.position.toString())
            .replace("{expected}", vowelName(slip.expected))
            .replace("{heard}", vowelName(slip.heard))
        else -> t("recitation.mistake.wrong")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (!isHarakat && mark.status == WordStatus.WRONG && !mark.heard.isNullOrBlank()) {
            Text(mark.heard, style = quranTextStyle(20), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Text(written, style = quranTextStyle(22), color = accent)
    }
}

@Composable
private fun vowelName(vowel: com.quranreminder.app.recitation.Vowel): String = when (vowel) {
    com.quranreminder.app.recitation.Vowel.FATHA -> t("recitation.vowel.fatha")
    com.quranreminder.app.recitation.Vowel.KASRA -> t("recitation.vowel.kasra")
    com.quranreminder.app.recitation.Vowel.DAMMA -> t("recitation.vowel.damma")
    com.quranreminder.app.recitation.Vowel.SHADDA -> t("recitation.vowel.shadda")
    com.quranreminder.app.recitation.Vowel.NO_SHADDA -> t("recitation.vowel.noShadda")
}
