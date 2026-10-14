package com.quranreminder.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.profile.Personalization
import com.quranreminder.app.profile.ProfileRepository
import com.quranreminder.app.recitation.RecitationGoals
import com.quranreminder.app.recitation.ReviewSchedule
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "personal_home"
private const val KEY_LEVEL_ASK_HIDDEN = "level_ask_hidden"
private const val KEY_GENDER_ASKED = "gender_asked"

/** Short surahs a learner can listen to and repeat, one a day in turn. */
private val SHORT_SURAHS = listOf(112, 113, 114, 1, 103, 108, 110, 111, 105, 106, 107, 109, 102, 104, 100, 101, 99, 97, 95, 94, 93)

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

/**
 * "Today" on Home, shaped by the reader's level and goal: listen-and-repeat for learners,
 * due reviews for those memorising, the hour's ayah explained for those seeking meaning.
 * Until the level is known, it asks for it (once; it can be put off).
 */
@Composable
fun TodayCard(navController: NavHostController, surahName: (Int) -> String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by ProfileRepository.profile.collectAsState()
    LaunchedEffect(Unit) { ProfileRepository.load(context) }
    var askHidden by remember { mutableStateOf(prefs(context).getBoolean(KEY_LEVEL_ASK_HIDDEN, false)) }
    val focus = Personalization.focus(profile)
    val failed = t("profile.saveFailed")

    if (focus == null) {
        if (askHidden || !SessionManager(context).isSignedIn) return
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NurSectionTitle(t("today.ask.title"))
            NurSurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        t("today.ask.desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    listOf("beginner", "fluent", "hafiz").forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) {
                                    scope.launch {
                                        ProfileRepository.update(context, mapOf("quran_level" to level)).onFailure {
                                            Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t("profile.level.$level"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(t("profile.level.$level.desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = nurColors.goldText, modifier = Modifier.size(20.dp))
                        }
                    }
                    TextButton(
                        onClick = {
                            prefs(context).edit().putBoolean(KEY_LEVEL_ASK_HIDDEN, true).apply()
                            askHidden = true
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text(t("today.ask.later"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        return
    }

    val today = remember { RecitationGoals.today() }
    when (focus) {
        "memorize" -> {
            val due by produceState(-1, today) {
                value = withContext(Dispatchers.IO) {
                    val all = QuranDatabase.getDatabase(context).recitationHistoryDao().observeAll().first()
                    ReviewSchedule.due(all, today).size
                }
            }
            if (due < 0) return
            if (due > 0) {
                TodayRow(modifier, Icons.Outlined.Replay, t("today.memorize.due.title"), t("today.memorize.due.desc").replace("{n}", due.toString())) {
                    navController.navigate("recitation_history")
                }
            } else {
                TodayRow(modifier, Icons.Outlined.Psychology, t("today.memorize.new.title"), t("today.memorize.new.desc")) {
                    navController.openTab("recitation")
                }
            }
        }
        "understand" -> {
            val verse by produceState<com.quranreminder.app.database.QuranVerse?>(null) {
                value = withContext(Dispatchers.IO) {
                    runCatching { LockScreenVerseSelector.select(context, QuranDatabase.getDatabase(context)) }.getOrNull()
                }
            }
            val v = verse ?: return
            val ref = "${v.surahName} ${v.surahNumber}:${v.ayahNumber}"
            val prompt = t("surahDetail.askPrompt").replace("{ref}", ref)
            TodayRow(modifier, Icons.Outlined.AutoAwesome, t("today.understand.title"), t("today.understand.desc").replace("{ref}", ref)) {
                com.quranreminder.app.ui.AyahHandoff.askText = prompt
                navController.openTab("tadabbur_chat")
            }
        }
        else -> { // learn
            val surah = SHORT_SURAHS[(today % SHORT_SURAHS.size).toInt()]
            val name = surahName(surah) ?: surah.toString()
            TodayRow(modifier, Icons.Outlined.Headphones, t("today.learn.title"), t("today.learn.desc").replace("{surah}", name)) {
                navController.navigate("surah_detail/$surah?ayah=1")
            }
        }
    }
}

@Composable
private fun TodayRow(modifier: Modifier, icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NurSectionTitle(t("today.title"))
        NurSurfaceCard(Modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NurStarMedallion(icon, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = nurColors.goldText)
            }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Asked once after signing in, when the profile has no gender: lets the assistant use the
 * right forms of address. Optional; it can be changed on the profile page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderAskSheet() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by ProfileRepository.profile.collectAsState()
    var asked by remember { mutableStateOf(prefs(context).getBoolean(KEY_GENDER_ASKED, false)) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && SessionManager(context).isSignedIn) {
            // Ask only once the server confirms the gender is not set.
            ProfileRepository.refresh(context)
            checked = true
        }
    }
    val p = profile
    if (asked || !checked || p == null || p.gender != null) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val failed = t("profile.saveFailed")
    fun done() {
        prefs(context).edit().putBoolean(KEY_GENDER_ASKED, true).apply()
        asked = true
    }
    ModalBottomSheet(onDismissRequest = { done() }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t("gender.ask.title"), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(t("gender.ask.desc"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            listOf("male" to t("gender.ask.male"), "female" to t("gender.ask.female")).forEach { (value, label) ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(role = Role.Button) {
                            scope.launch {
                                ProfileRepository.update(context, mapOf("gender" to value))
                                    .onSuccess { done() }
                                    .onFailure { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            TextButton(onClick = { done() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(t("gender.ask.skip"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
