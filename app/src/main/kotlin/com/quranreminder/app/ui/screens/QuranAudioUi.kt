package com.quranreminder.app.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.quranreminder.app.audio.AudioStore
import com.quranreminder.app.audio.QuranPlayer
import com.quranreminder.app.audio.Reciter
import com.quranreminder.app.audio.ReciterPrefs
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurListRow
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Header button: recites the surah from the start, or pauses / resumes it. */
@Composable
fun SurahListenButton(surah: Int, surahName: String, ayahCount: Int) {
    val context = LocalContext.current
    val audio by QuranPlayer.state.collectAsState()
    val reciter by ReciterPrefs.flow(context).collectAsState()
    val reciterName = t((reciter ?: Reciter.HUSARY).nameKey)
    val here = audio.active && audio.surah == surah
    NurIconButton(
        icon = if (here && audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (here && audio.playing) t("audio.pause") else t("audio.play"),
        emphasized = true,
        onClick = {
            if (ayahCount <= 0) return@NurIconButton
            if (here) {
                QuranPlayer.toggle(context)
            } else {
                // "Listen to the surah" means the whole surah: a single-ayah loop left over
                // from an earlier session would keep it on ayah 1.
                if (audio.repeat == QuranPlayer.Repeat.AYAH_LOOP) QuranPlayer.setRepeat(context, QuranPlayer.Repeat.OFF)
                QuranPlayer.play(context, surah, surahName, ayahCount, reciterName)
            }
        }
    )
}

/** Header button: saves the surah's recitation for listening offline, with its progress. */
@Composable
fun SurahDownloadButton(surah: Int, ayahCount: Int) {
    val context = LocalContext.current
    val reciter = ReciterPrefs.flow(context).collectAsState().value ?: Reciter.HUSARY
    val name = AudioStore.downloadWorkName(reciter, surah)
    val infos by remember(name) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name) }
        .collectAsState(initial = emptyList())
    val info = infos.lastOrNull()
    val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
    val doneCount = info?.progress?.getInt("done", 0) ?: 0
    val total = info?.progress?.getInt("total", 0)?.takeIf { it > 0 } ?: (ayahCount + 1)
    val downloaded by produceState(false, reciter, surah, ayahCount, info?.state) {
        value = withContext(Dispatchers.IO) { AudioStore.isSurahDownloaded(context, reciter, surah, ayahCount) }
    }
    val failedText = t("audio.downloadFailed")
    LaunchedEffect(info?.state) {
        if (info?.state == WorkInfo.State.FAILED) Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
    }
    val downloadedText = t("audio.downloaded")
    val percent = (doneCount * 100 / total).coerceIn(0, 100)
    val progressText = t("audio.downloading").replace("{p}", percent.toString())
    when {
        running -> Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f))
                .clickable(onClickLabel = progressText, role = Role.Button) {
                    Toast.makeText(context, progressText, Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = percent / 100f,
                modifier = Modifier.size(30.dp),
                color = nurColors.ornament,
                trackColor = nurColors.onBand.copy(alpha = 0.2f),
                strokeWidth = 3.dp
            )
        }
        downloaded -> NurIconButton(Icons.Default.DownloadDone, downloadedText, onClick = {
            Toast.makeText(context, downloadedText, Toast.LENGTH_SHORT).show()
        })
        else -> NurIconButton(Icons.Outlined.Download, t("audio.download"), onClick = {
            if (ayahCount > 0) AudioStore.download(context, reciter, surah, ayahCount)
        })
    }
}

/** The player at the foot of the reader while a recitation is loaded. */
@Composable
fun QuranMiniPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio by QuranPlayer.state.collectAsState()
    if (!audio.active) return
    val title = when (audio.ayah) {
        null -> audio.surahName
        0 -> "${audio.surahName} · ${t("audio.basmala")}"
        else -> "${audio.surahName} · ${audio.ayah}"
    }
    val repeatLabel = when (audio.repeat) {
        QuranPlayer.Repeat.OFF -> t("audio.repeat.off")
        QuranPlayer.Repeat.AYAH_3 -> t("audio.repeat.ayah3")
        QuranPlayer.Repeat.AYAH_LOOP -> t("audio.repeat.loop")
    }
    val subtitle = when {
        audio.error -> t("audio.error")
        audio.buffering -> t("audio.loading")
        audio.repeat != QuranPlayer.Repeat.OFF -> "${t(audio.reciter.nameKey)} · $repeatLabel"
        else -> t(audio.reciter.nameKey)
    }
    NurSurfaceCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (audio.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlainIcon(Icons.Default.Close, t("audio.close")) { QuranPlayer.stop(context) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerChip(
                    icon = if (audio.repeat == QuranPlayer.Repeat.AYAH_LOOP) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    label = if (audio.repeat == QuranPlayer.Repeat.AYAH_3) "×3" else null,
                    selected = audio.repeat != QuranPlayer.Repeat.OFF,
                    description = "${t("audio.repeat")}: $repeatLabel"
                ) {
                    val next = QuranPlayer.Repeat.entries[(audio.repeat.ordinal + 1) % QuranPlayer.Repeat.entries.size]
                    QuranPlayer.setRepeat(context, next)
                }
                PlayerChip(
                    icon = Icons.Default.Speed,
                    label = "${if (audio.speed % 1f == 0f) audio.speed.toInt() else audio.speed}×",
                    selected = audio.speed != 1f,
                    description = t("audio.speed")
                ) { QuranPlayer.cycleSpeed(context) }
                Spacer(Modifier.weight(1f))
                PlainIcon(Icons.Default.SkipPrevious, t("audio.prev")) { QuranPlayer.previous(context) }
                NurIconButton(
                    icon = if (audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (audio.playing) t("audio.pause") else t("audio.resume"),
                    emphasized = true,
                    onClick = { QuranPlayer.toggle(context) }
                )
                PlainIcon(Icons.Default.SkipNext, t("audio.next")) { QuranPlayer.next(context) }
            }
        }
    }
}

@Composable
private fun PlainIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PlayerChip(icon: ImageVector, label: String?, selected: Boolean, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceVariant)
            .then(if (selected) Modifier.border(1.dp, nurColors.frame, CircleShape) else Modifier)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (label == null) 9.dp else 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) nurColors.goldText else scheme.primary, modifier = Modifier.size(18.dp))
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
    }
}

/** Settings: the reciter, and the space the downloaded recitations take. */
@Composable
fun AudioSettingsCard() {
    val context = LocalContext.current
    val reciter = ReciterPrefs.flow(context).collectAsState().value ?: Reciter.HUSARY
    var refresh by remember { mutableIntStateOf(0) }
    val used by produceState(0L, refresh) { value = withContext(Dispatchers.IO) { AudioStore.usedBytes(context) } }
    var confirm by remember { mutableStateOf(false) }
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Reciter.entries.forEachIndexed { i, r ->
                if (i > 0) NurDivider()
                NurListRow(
                    title = t(r.nameKey),
                    subtitle = t(r.descKey),
                    leadingIcon = Icons.Outlined.RecordVoiceOver,
                    trailing = { RadioButton(selected = r == reciter, onClick = { ReciterPrefs.set(context, r) }) },
                    onClick = { ReciterPrefs.set(context, r) }
                )
            }
            NurDivider()
            NurListRow(
                title = t("settings.audio.storage"),
                subtitle = if (used > 0) t("settings.audio.storage.desc").replace("{size}", Formatter.formatShortFileSize(context, used))
                else t("settings.audio.storage.empty"),
                leadingIcon = Icons.Outlined.LibraryMusic,
                trailing = if (used > 0) ({
                    TextButton(onClick = { confirm = true }) {
                        Text(t("settings.audio.delete"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }) else null,
                onClick = if (used > 0) ({ confirm = true }) else null
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(t("settings.audio.deleteConfirm"), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    QuranPlayer.stop(context)
                    AudioStore.deleteAll(context)
                    refresh++
                }) { Text(t("settings.audio.delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("common.cancel")) } }
        )
    }
}
