package com.quranreminder.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.DayActivity
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.RecitationResultEntity
import com.quranreminder.app.database.ReviewAyah
import com.quranreminder.app.recitation.RecitationGoals
import com.quranreminder.app.ui.AyahHandoff
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.quranTextStyle
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Recitation history kept on the device: the streak and the daily goal, the last seven days,
 * the ayahs whose last reading still had mistakes (one tap to read them again) and recent
 * results.
 */
@Composable
fun RecitationHistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = remember { QuranDatabase.getDatabase(context) }
    val dao = remember { database.recitationHistoryDao() }
    val goal by RecitationGoals.dailyGoal(context)
    val today = RecitationGoals.today()
    val activity by remember { dao.observeAllActivity() }.collectAsState(initial = emptyList())
    val review by remember { dao.observeReview() }.collectAsState(initial = emptyList())
    val recent by remember { dao.observeRecent() }.collectAsState(initial = emptyList())
    val totalAyahs by remember { dao.observeDistinctAyahs() }.collectAsState(initial = 0)
    val streak = RecitationGoals.streak(activity, goal, today)
    val todayAyahs = activity.firstOrNull { it.day == today }?.ayahs ?: 0

    // Surah names and ayah text for the rows, read once per list change.
    val keys = (review.map { it.surahNumber to it.ayahNumber } + recent.map { it.surahNumber to it.ayahNumber }).distinct()
    val verses by produceState(emptyMap<Pair<Int, Int>, QuranVerse>(), keys) {
        value = withContext(Dispatchers.IO) {
            keys.mapNotNull { (s, a) -> database.quranVerseDao().getVerseOnce(s, a)?.let { (s to a) to it } }.toMap()
        }
    }

    fun reciteAgain(surah: Int, ayah: Int) {
        AyahHandoff.recite = surah to ayah
        navController.popBackStack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(
            title = t("recitation.history"),
            eyebrow = t("recitation.history.eyebrow"),
            leading = { NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() }) }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "streak") {
                StreakCard(
                    streak = streak,
                    todayAyahs = todayAyahs,
                    goal = goal,
                    totalAyahs = totalAyahs,
                    onGoalChange = { RecitationGoals.setDailyGoal(context, it) }
                )
            }
            item(key = "week") { WeekCard(activity = activity, goal = goal, today = today) }

            item(key = "reviewTitle") { NurSectionTitle(t("recitation.review.title")) }
            if (review.isEmpty()) {
                item(key = "reviewEmpty") {
                    Text(
                        if (recent.isEmpty()) t("recitation.history.empty") else t("recitation.review.empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            } else {
                item(key = "reviewList") {
                    NurSurfaceCard(Modifier.fillMaxWidth()) {
                        Column {
                            review.forEachIndexed { index, item ->
                                ReviewRow(item, verses[item.surahNumber to item.ayahNumber]) { reciteAgain(item.surahNumber, item.ayahNumber) }
                                if (index < review.lastIndex) NurDivider()
                            }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "recentTitle") { NurSectionTitle(t("recitation.recent.title")) }
                item(key = "recentList") {
                    NurSurfaceCard(Modifier.fillMaxWidth()) {
                        Column {
                            recent.forEachIndexed { index, item ->
                                RecentRow(item, verses[item.surahNumber to item.ayahNumber]) { reciteAgain(item.surahNumber, item.ayahNumber) }
                                if (index < recent.lastIndex) NurDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCard(streak: Int, todayAyahs: Int, goal: Int, totalAyahs: Int, onGoalChange: (Int) -> Unit) {
    val c = nurColors
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streak > 0) c.ornament else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        t("recitation.streak.days").replace("{n}", streak.toString()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        t("recitation.streak.note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            NurDivider(inset = 0.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("recitation.goal.title"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        t("recitation.goal.today").replace("{done}", todayAyahs.toString()).replace("{goal}", goal.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NurIconButton(Icons.Default.Remove, t("recitation.goal.less"), onClick = { onGoalChange(goal - 1) })
                Text(
                    "$goal",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.goldText,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                NurIconButton(Icons.Default.Add, t("recitation.goal.more"), onClick = { onGoalChange(goal + 1) })
            }
            Text(
                t("recitation.history.total").replace("{n}", totalAyahs.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = c.goldText
            )
        }
    }
}

/** Ayahs recited on each of the last seven days, with the goal as a dashed line. */
@Composable
private fun WeekCard(activity: List<DayActivity>, goal: Int, today: Long) {
    val c = nurColors
    val byDay = activity.associate { it.day to it.ayahs }
    val days = (6 downTo 0).map { today - it }
    val top = maxOf(goal, days.maxOf { byDay[it] ?: 0 }, 1)
    val locale = com.quranreminder.app.ui.theme.appLocale()
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("recitation.week.title"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val bar = c.ornament
            val empty = MaterialTheme.colorScheme.outlineVariant
            val line = MaterialTheme.colorScheme.primary
            val heights = days.map { day ->
                val target = (byDay[day] ?: 0).toFloat() / top
                animateFloatAsState(target, tween(600), label = "bar").value
            }
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val slot = size.width / 7f
                val barWidth = slot * 0.46f
                heights.forEachIndexed { index, h ->
                    val x = slot * index + (slot - barWidth) / 2f
                    val barHeight = (size.height * h).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        if (h > 0f) bar else empty,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
                val goalY = size.height * (1f - goal.toFloat() / top)
                drawLine(
                    line.copy(alpha = 0.6f),
                    Offset(0f, goalY),
                    Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                )
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val date = LocalDate.ofEpochDay(day)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${byDay[day] ?: 0}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day == today) c.goldText else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(item: ReviewAyah, verse: QuranVerse?, onClick: () -> Unit) {
    val accuracy = if (item.wordCount == 0) 0 else item.correctCount * 100 / item.wordCount
    HistoryRow(
        title = "${verse?.surahName ?: item.surahNumber} ${item.surahNumber}:${item.ayahNumber}",
        subtitle = t("recitation.review.row").replace("{p}", accuracy.toString()).replace("{n}", item.attempts.toString()),
        arabic = verse?.ayahTextArabic,
        accuracy = accuracy,
        onClick = onClick
    )
}

@Composable
private fun RecentRow(item: RecitationResultEntity, verse: QuranVerse?, onClick: () -> Unit) {
    val accuracy = if (item.wordCount == 0) 0 else item.correctCount * 100 / item.wordCount
    val modeLabel = if (item.mode == "memorize") t("recitation.mode.memorize") else t("recitation.mode.check")
    HistoryRow(
        title = "${verse?.surahName ?: item.surahNumber} ${item.surahNumber}:${item.ayahNumber}",
        subtitle = "$modeLabel · ${relativeTime(item.createdAt)}",
        arabic = null,
        accuracy = accuracy,
        onClick = onClick
    )
}

@Composable
private fun HistoryRow(title: String, subtitle: String, arabic: String?, accuracy: Int, onClick: () -> Unit) {
    val c = nurColors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (accuracy == 100) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$accuracy%",
                style = MaterialTheme.typography.labelMedium,
                color = if (accuracy == 100) c.goldText else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!arabic.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    arabic,
                    style = quranTextStyle(18),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** "just now", "5 min ago", "yesterday" or a date, in the app's language. */
@Composable
private fun relativeTime(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0)
    val day = RecitationGoals.dayOf(millis)
    val today = RecitationGoals.today()
    return when {
        minutes < 1 -> t("time.justNow")
        minutes < 60 -> t("time.minutesAgo").replace("{n}", minutes.toString())
        day == today -> t("time.hoursAgo").replace("{n}", (minutes / 60).toString())
        day == today - 1 -> t("time.yesterday")
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMMM", com.quranreminder.app.ui.theme.appLocale())
            .format(LocalDate.ofEpochDay(day))
    }
}
