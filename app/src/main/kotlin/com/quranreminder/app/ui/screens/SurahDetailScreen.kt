package com.quranreminder.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(surahNumber) {
        viewModel.loadSurahVerses(surahNumber)
    }

    if (verses.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Loading...",
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        return
    }

    val firstVerse = verses.first()

    LaunchedEffect(verses, targetAyahNumber) {
        val target = targetAyahNumber ?: return@LaunchedEffect
        val index = verses.indexOfFirst { it.ayahNumber == target }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = firstVerse.surahName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = firstVerse.surahNameArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Verses list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(verses) { verse ->
                VerseDetailCard(
                    verse = verse,
                    isBookmarked = QuranViewModel.verseKey(
                        verse.surahNumber,
                        verse.ayahNumber
                    ) in bookmarkedVerseKeys,
                    onBookmarkClick = {
                        viewModel.toggleBookmark(verse.surahNumber, verse.ayahNumber)
                    },
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
                            runCatching {
                                LockScreenWallpaperApplier.shareImage(context, verse)
                            }.onFailure {
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
fun VerseDetailCard(
    verse: QuranVerse,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onLockScreenClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Oyat ${verse.ayahNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Rasm qilib ulashish",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onLockScreenClick) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock-screen uchun tanlash",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onBookmarkClick) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Saqlash",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Arabic text
            Text(
                text = verse.ayahTextArabic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Translation
            Text(
                text = verse.translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}
