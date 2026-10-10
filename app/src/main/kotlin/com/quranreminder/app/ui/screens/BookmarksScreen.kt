package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.Bookmark
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.QuranVerseDao
import com.quranreminder.app.ui.theme.NurEmptyState
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel

@Composable
fun BookmarksScreen(
    navController: NavHostController,
    quranViewModel: QuranViewModel
) {
    val bookmarks = quranViewModel.bookmarks.collectAsState().value
    val surahs = quranViewModel.surahs.collectAsState().value
    val context = LocalContext.current
    // Read-only verse lookup for the translation preview (QuranViewModel has no single-verse API).
    val verseDao = remember { QuranDatabase.getDatabase(context).quranVerseDao() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = t("bookmarks.title"),
            eyebrow = t("bookmarks.eyebrow"),
            leading = {
                NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
            }
        )

        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.Center
            ) {
                NurEmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = t("bookmarks.empty.title"),
                    body = t("bookmarks.empty.body"),
                    actionText = t("bookmarks.empty.action"),
                    onAction = {
                        navController.navigate("search") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        verseDao = verseDao,
                        fallbackSurahName = surahs.firstOrNull { it.surahNumber == bookmark.surahNumber }?.surahName,
                        onClick = {
                            navController.navigate("surah_detail/${bookmark.surahNumber}?ayah=${bookmark.ayahNumber}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    verseDao: QuranVerseDao,
    fallbackSurahName: String?,
    onClick: () -> Unit
) {
    val verseFlow = remember(bookmark.surahNumber, bookmark.ayahNumber) {
        verseDao.getVerse(bookmark.surahNumber, bookmark.ayahNumber)
    }
    val verse: QuranVerse? by verseFlow.collectAsState(initial = null)
    val surahName = verse?.surahName ?: fallbackSurahName ?: (t("home.sura") + bookmark.surahNumber)

    NurSurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuranStarNumber(number = bookmark.surahNumber, size = 42.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = surahName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${t("surahDetail.ayahPrefix")}${bookmark.ayahNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = nurColors.goldText
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).width(24.dp)
                )
            }

            val translation = verse?.translation.orEmpty()
            if (translation.isNotBlank()) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (bookmark.notes.isNotEmpty()) {
                Text(
                    text = bookmark.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
