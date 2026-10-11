package com.quranreminder.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.quranTextStyle
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import kotlinx.coroutines.launch

private const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

/** Arabic size in the reader; the screen does not read the user's font-size setting. */
private const val READER_ARABIC_SP = 28

/** Items in the list before the first ayah (the opening card). Used for the target-ayah scroll. */
private const val ITEMS_BEFORE_AYAHS = 1

@Composable
fun SurahDetailScreen(
    surahNumber: Int,
    targetAyahNumber: Int? = null,
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    val loadedVerses = viewModel.currentSurahVerses.collectAsState().value
    // currentSurahVerses still holds the previous surah until this one loads; don't show it.
    val verses = if (loadedVerses.firstOrNull()?.surahNumber == surahNumber) loadedVerses else emptyList()
    val bookmarkedVerseKeys = viewModel.bookmarkedVerseKeys.collectAsState().value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val loadingText = t("surahDetail.loading")
    val eyebrowFallbackText = t("surahDetail.eyebrowFallback")
    val backText = t("common.back")
    val shareFailedText = t("surahDetail.shareFailed")
    val ayahPrefixText = t("surahDetail.ayahPrefix")
    val shareImageDesc = t("surahDetail.shareImage")
    val selectLockScreenDesc = t("surahDetail.selectLockScreen")
    val footnoteLabel = t("surahDetail.footnote")
    val footnoteHideLabel = t("surahDetail.footnoteHide")
    val saveDesc = t("surahDetail.save")
    val removeBookmarkDesc = t("surahDetail.removeBookmark")
    val metaText = t("home.sura") + surahNumber + " · " +
        t("surah.ayahCount").replace("{n}", verses.size.toString())

    LaunchedEffect(surahNumber) { viewModel.loadSurahVerses(surahNumber) }

    LaunchedEffect(verses, targetAyahNumber) {
        val target = targetAyahNumber ?: return@LaunchedEffect
        val index = verses.indexOfFirst { it.ayahNumber == target }
        // The opening card precedes the ayahs in the list.
        if (index >= 0) listState.scrollToItem(index + ITEMS_BEFORE_AYAHS)
    }

    val firstVerse = verses.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ReaderHeader(
            title = firstVerse?.surahName ?: eyebrowFallbackText,
            arabicName = firstVerse?.surahNameArabic.orEmpty(),
            fallbackSubtitle = eyebrowFallbackText,
            backText = backText,
            onBack = { navController.popBackStack() }
        )

        if (verses.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NurEmblem(size = 46.dp, onDark = nurColors.isDark)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loadingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 24.dp)
        ) {
            item(key = "opening") {
                OpeningCard(
                    showBasmala = surahNumber != 1 && surahNumber != 9,
                    meta = metaText,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
            items(verses, key = { it.id }) { verse ->
                val isBookmarked = QuranViewModel.verseKey(verse.surahNumber, verse.ayahNumber) in bookmarkedVerseKeys
                AyahBlock(
                    verse = verse,
                    highlighted = verse.ayahNumber == targetAyahNumber,
                    ayahLabel = "$ayahPrefixText${verse.ayahNumber}",
                    shareImageDesc = shareImageDesc,
                    selectLockScreenDesc = selectLockScreenDesc,
                    footnoteLabel = footnoteLabel,
                    footnoteHideLabel = footnoteHideLabel,
                    bookmarkDesc = if (isBookmarked) removeBookmarkDesc else saveDesc,
                    isBookmarked = isBookmarked,
                    onBookmarkClick = { viewModel.toggleBookmark(verse.surahNumber, verse.ayahNumber) },
                    onLockScreenClick = {
                        // Open the lock-screen page on this ayah to choose background and font;
                        // it is saved only when the user presses apply there.
                        navController.navigate("lock_preview?surah=${verse.surahNumber}&ayah=${verse.ayahNumber}")
                    },
                    onShareClick = {
                        scope.launch {
                            runCatching { LockScreenWallpaperApplier.shareImage(context, verse) }
                                .onFailure {
                                    Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                )
                if (verse != verses.last()) NurDivider(inset = 16.dp)
            }
        }
        }
    }
}

/** Band header: back, surah name, and the Arabic surah name beneath it in gold. */
@Composable
private fun ReaderHeader(
    title: String,
    arabicName: String,
    fallbackSubtitle: String,
    backText: String,
    onBack: () -> Unit
) {
    NurBand(bottomPadding = 16.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NurIconButton(icon = Icons.Default.ArrowBack, contentDescription = backText, onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = nurColors.onBand,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (arabicName.isNotBlank()) {
                    Text(
                        text = arabicName,
                        style = quranTextStyle(20).copy(lineHeight = 32.sp),
                        color = nurColors.ornament,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = fallbackSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = nurColors.onBandMuted
                    )
                }
            }
        }
    }
}

/** Opening card: the Basmala (except where it is the first ayah, or absent) and the surah's size. */
@Composable
private fun OpeningCard(showBasmala: Boolean, meta: String, modifier: Modifier = Modifier) {
    NurSurfaceCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showBasmala) {
                Text(
                    text = BASMALA,
                    style = quranTextStyle(26),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            StarRule()
            Text(
                text = meta,
                style = MaterialTheme.typography.labelLarge,
                color = nurColors.goldText,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A hairline broken by a small gold star. */
@Composable
private fun StarRule() {
    val c = nurColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp).height(1.dp).background(c.frame))
        Canvas(Modifier.padding(horizontal = 10.dp).size(10.dp)) {
            drawPath(starPath(center, size.minDimension / 2f), c.ornament)
        }
        Box(Modifier.width(44.dp).height(1.dp).background(c.frame))
    }
}

@Composable
private fun AyahBlock(
    verse: QuranVerse,
    highlighted: Boolean,
    ayahLabel: String,
    shareImageDesc: String,
    selectLockScreenDesc: String,
    bookmarkDesc: String,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onLockScreenClick: () -> Unit,
    onShareClick: () -> Unit,
    footnoteLabel: String = "",
    footnoteHideLabel: String = ""
) {
    val shape = MaterialTheme.shapes.large
    val scheme = MaterialTheme.colorScheme
    val container = if (highlighted) {
        Modifier
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(lerp(scheme.surface, scheme.secondaryContainer, 0.55f))
            .border(BorderStroke(1.dp, nurColors.frame), shape)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(container)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (verse.ayahTextArabic.isNotBlank()) {
            Text(
                text = verse.ayahTextArabic,
                modifier = Modifier.fillMaxWidth(),
                style = quranTextStyle(READER_ARABIC_SP),
                color = scheme.onSurface,
                textAlign = TextAlign.Right
            )
        }
        if (verse.transliteration.isNotBlank()) {
            Text(
                text = verse.transliteration,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = verse.translation,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface
        )
        if (verse.footnotes.isNotBlank()) {
            FootnoteToggle(verse.footnotes, footnoteLabel, footnoteHideLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuranStarNumber(
                number = verse.ayahNumber,
                size = 40.dp,
                gold = true,
                modifier = Modifier.semantics { contentDescription = ayahLabel }
            )
            Spacer(Modifier.weight(1f))
            NurIconButton(Icons.Default.Share, shareImageDesc, onShareClick)
            NurIconButton(Icons.Default.Lock, selectLockScreenDesc, onLockScreenClick)
            NurIconButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = bookmarkDesc,
                onClick = onBookmarkClick,
                emphasized = isBookmarked
            )
        }
    }
}

/**
 * The translator's commentary, folded away under the translation so the ayah reads cleanly;
 * a tap opens it beside a thin gold rule.
 */
@Composable
private fun FootnoteToggle(footnotes: String, label: String, hideLabel: String) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val gold = nurColors.goldText
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { open = !open }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (open) hideLabel else label,
                style = MaterialTheme.typography.labelLarge,
                color = gold
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = open) {
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(gold.copy(alpha = 0.6f))
                )
                Text(
                    text = footnotes,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
