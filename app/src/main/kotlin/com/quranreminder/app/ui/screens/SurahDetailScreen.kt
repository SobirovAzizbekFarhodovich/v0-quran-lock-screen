package com.quranreminder.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import kotlinx.coroutines.launch

@Composable
fun SurahDetailScreen(
    surahNumber: Int,
    targetAyahNumber: Int? = null,
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    val verses = viewModel.currentSurahVerses.collectAsState().value
    val bookmarkedVerseKeys = viewModel.bookmarkedVerseKeys.collectAsState().value
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(surahNumber) { viewModel.loadSurahVerses(surahNumber) }

    if (verses.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NurEmblem(size = 46.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Oyatlar yuklanmoqda...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        return
    }

    val firstVerse = verses.first()
    LaunchedEffect(verses, targetAyahNumber) {
        val target = targetAyahNumber ?: return@LaunchedEffect
        val index = verses.indexOfFirst { it.ayahNumber == target }
        if (index >= 0) listState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = firstVerse.surahName,
            eyebrow = firstVerse.surahNameArabic.ifBlank { "Qur'oni Karim" },
            leading = {
                NurIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Orqaga",
                    onClick = { navController.popBackStack() }
                )
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NurPill(text = "O'ZBEK TARJIMASI")
                    if (verses.any { it.ayahTextArabic.isNotBlank() }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TajwidLegend("Madd", NurColors.Terracotta)
                            TajwidLegend("Ixfo", NurColors.Sage)
                        }
                    }
                }
            }
            items(verses, key = { it.id }) { verse ->
                VerseDetailCard(
                    verse = verse,
                    isBookmarked = QuranViewModel.verseKey(verse.surahNumber, verse.ayahNumber) in bookmarkedVerseKeys,
                    onBookmarkClick = { viewModel.toggleBookmark(verse.surahNumber, verse.ayahNumber) },
                    onLockScreenClick = {
                        scope.launch {
                            runCatching {
                                LockScreenWallpaperApplier.saveSelectedVerse(context, verse)
                                LockScreenWallpaperApplier.apply(context, verse, rememberForHour = true)
                            }.onSuccess {
                                Toast.makeText(context, "Lock-screen uchun shu oyat tanlandi", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Lock-screen yangilanmadi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareClick = {
                        scope.launch {
                            runCatching { LockScreenWallpaperApplier.shareImage(context, verse) }
                                .onFailure {
                                    Toast.makeText(context, "Rasm ulashilmadi", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TajwidLegend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VerseDetailCard(
    verse: QuranVerse,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onLockScreenClick: () -> Unit,
    onShareClick: () -> Unit
) {
    NurSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NurEmblem(size = 30.dp)
                    Text(
                        text = verse.ayahNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NurColors.Ink
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                NurSectionTitle("Oyat ${verse.ayahNumber}", modifier = Modifier.weight(1f))
                NurIconButton(Icons.Default.Share, "Rasm qilib ulashish", onShareClick)
                Spacer(modifier = Modifier.width(6.dp))
                NurIconButton(Icons.Default.Lock, "Lock-screen uchun tanlash", onLockScreenClick)
                Spacer(modifier = Modifier.width(6.dp))
                NurIconButton(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    "Saqlash",
                    onBookmarkClick,
                    emphasized = isBookmarked
                )
            }

            if (verse.ayahTextArabic.isNotBlank()) {
                Text(
                    text = verse.ayahTextArabic,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
            if (verse.transliteration.isNotBlank()) {
                Text(
                    text = verse.transliteration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = verse.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
