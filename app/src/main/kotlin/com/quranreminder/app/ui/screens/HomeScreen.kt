package com.quranreminder.app.ui.screens

import com.quranreminder.app.ui.theme.LocalNavBarPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.SurahHeader
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.ui.theme.NurAyahCard
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurListRow
import com.quranreminder.app.ui.theme.NurLockup
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.quranTextStyle
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** How far the hourly-ayah card rises over the band's lower edge. */
private val HeroOverlap = 72.dp

@Composable
fun HomeScreen(
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    val surahs = viewModel.surahs.collectAsState().value
    val bandHeight = (LocalConfiguration.current.screenHeightDp * 0.36f).dp
    val navBar = LocalNavBarPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = navBar + 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "hero") {
            Column {
                HomeBand(
                    minHeight = bandHeight,
                    onReminders = { navController.navigate("reminders") },
                    onSearch = { navController.openTopLevel("search") }
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .quranOverlapAbove(HeroOverlap)
                ) {
                    HourlyAyahCard(onClick = { verse ->
                        navController.navigate("surah_detail/${verse.surahNumber}?ayah=${verse.ayahNumber}")
                    })
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = nurColors.goldText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = t("home.hourlyAyah.note"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "features") {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureTileRow(
                    first = FeatureTileSpec(Icons.Default.AutoAwesome, t("home.islamicAi"), t("home.islamicAi.desc")) {
                        navController.openTopLevel("tadabbur_chat")
                    },
                    second = FeatureTileSpec(Icons.Default.Wallpaper, t("home.lockScreen"), t("home.lockScreen.desc")) {
                        navController.navigate("lock_preview")
                    }
                )
                FeatureTileRow(
                    first = FeatureTileSpec(Icons.Default.Mic, t("home.recitation"), t("home.recitation.desc")) {
                        navController.openTopLevel("recitation")
                    },
                    second = FeatureTileSpec(Icons.Default.MenuBook, t("home.quran"), t("home.quran.desc")) {
                        navController.openTopLevel("search")
                    }
                )
            }
        }

        item(key = "more") {
            val firstSurahName = surahs.firstOrNull { it.surahNumber == 1 }?.surahName
            NurSurfaceCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Column {
                    NurListRow(
                        title = t("home.reminders"),
                        subtitle = t("home.reminders.desc"),
                        leadingIcon = Icons.Default.NotificationsActive,
                        trailing = { RowChevron() },
                        onClick = { navController.navigate("reminders") }
                    )
                    NurDivider()
                    NurListRow(
                        title = t("bookmarks.title"),
                        subtitle = t("home.bookmarks.desc"),
                        leadingIcon = Icons.Default.Bookmarks,
                        trailing = { RowChevron() },
                        onClick = { navController.navigate("bookmarks") }
                    )
                    NurDivider()
                    NurListRow(
                        title = t("home.continueReading"),
                        subtitle = listOfNotNull(firstSurahName, t("surahDetail.ayahPrefix").trim() + " 1")
                            .joinToString(" · "),
                        leadingIcon = Icons.Default.AutoStories,
                        trailing = { RowChevron() },
                        onClick = { navController.navigate("surah_detail/1?ayah=1") }
                    )
                }
            }
        }
    }
}

/** Green band: logo at left, reminders and search at right, then the greeting. */
@Composable
private fun HomeBand(
    minHeight: Dp,
    onReminders: () -> Unit,
    onSearch: () -> Unit
) {
    NurBand(
        modifier = Modifier.heightIn(min = minHeight),
        bottomPadding = HeroOverlap + 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NurLockup(modifier = Modifier.weight(1f), markSize = 36.dp)
            NurIconButton(Icons.Default.NotificationsActive, t("home.reminders"), onReminders)
            NurIconButton(Icons.Default.Search, t("home.searchIcon"), onSearch)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = t("home.greeting"),
            style = MaterialTheme.typography.headlineLarge,
            color = nurColors.onBand,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = t("home.today"),
            style = MaterialTheme.typography.bodyMedium,
            color = nurColors.onBandMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HourlyAyahCard(onClick: (QuranVerse) -> Unit) {
    val context = LocalContext.current
    var verse by remember { mutableStateOf<QuranVerse?>(null) }
    LaunchedEffect(Unit) {
        while (isActive) {
            verse = withContext(Dispatchers.IO) {
                LockScreenVerseSelector.select(context, QuranDatabase.getDatabase(context))
            }
            // Also reflects a manually selected lock-screen verse shortly after returning home.
            delay(10_000)
        }
    }
    val current = verse
    NurAyahCard(
        arabic = current?.ayahTextArabic.orEmpty(),
        translation = current?.translation ?: t("home.hourlyAyah.preparing"),
        reference = current?.let { "${it.surahName}, ${it.surahNumber}:${it.ayahNumber}" }.orEmpty(),
        modifier = Modifier.fillMaxWidth(),
        arabicSizeSp = 26,
        maxTranslationLines = 6,
        onClick = current?.let { v -> { onClick(v) } }
    )
}

private class FeatureTileSpec(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
private fun FeatureTileRow(first: FeatureTileSpec, second: FeatureTileSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeatureTile(first, Modifier.weight(1f))
        FeatureTile(second, Modifier.weight(1f))
    }
}

@Composable
private fun FeatureTile(spec: FeatureTileSpec, modifier: Modifier = Modifier) {
    NurSurfaceCard(modifier = modifier.fillMaxHeight(), onClick = spec.onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NurStarMedallion(spec.icon, size = 52.dp)
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Opens a bottom-navigation destination the same way the NavigationBar does. */
private fun NavHostController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** One surah in the Quran list: number in a star, name and ayah count, Arabic name at right. */
@Composable
fun NurSurahRow(surah: SurahHeader, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        QuranStarNumber(number = surah.surahNumber, size = 42.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = surah.surahName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            quranSurahAyahCount(surah.surahNumber)?.let { count ->
                Text(
                    text = t("surah.ayahCount").replace("{n}", count.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (surah.surahNameArabic.isNotBlank()) {
            Text(
                text = surah.surahNameArabic,
                style = quranTextStyle(22).copy(lineHeight = 34.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

/** A number set inside a small eight-point star (surah and ayah numbers). */
@Composable
internal fun QuranStarNumber(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    gold: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (gold) scheme.secondaryContainer else scheme.primaryContainer
    val textColor = if (gold) scheme.onSecondaryContainer else scheme.onPrimaryContainer
    val keyline = nurColors.ornament
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val outer = this.size.minDimension / 2f - 1.dp.toPx()
            val star = starPath(center, outer, outer * 0.84f)
            drawPath(star, fill)
            drawPath(star, keyline, style = Stroke(1.2.dp.toPx()))
        }
        Text(
            text = number.toString(),
            style = if (number >= 100) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * Pulls the content up by [overlap] so it rises over whatever sits above it, without
 * leaving a gap below (unlike Modifier.offset, which does not change the layout size).
 */
internal fun Modifier.quranOverlapAbove(overlap: Dp): Modifier = layout { measurable, constraints ->
    val px = overlap.roundToPx()
    // Grow the height constraints by the overlap so that, once pulled up, the content still
    // fills exactly the space its parent gave it (e.g. under Modifier.weight).
    val grown = if (constraints.hasBoundedHeight) {
        constraints.copy(minHeight = constraints.minHeight + px, maxHeight = constraints.maxHeight + px)
    } else {
        constraints.copy(minHeight = constraints.minHeight + px)
    }
    val placeable = measurable.measure(grown)
    layout(placeable.width, (placeable.height - px).coerceAtLeast(constraints.minHeight)) {
        placeable.place(0, -px)
    }
}

/** Ayah count of a surah (Hafs numbering, 6236 in total), or null for an unknown number. */
internal fun quranSurahAyahCount(surahNumber: Int): Int? = SurahAyahCounts.getOrNull(surahNumber - 1)

private val SurahAyahCounts = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
)
