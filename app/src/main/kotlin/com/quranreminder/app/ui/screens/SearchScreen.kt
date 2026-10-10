package com.quranreminder.app.ui.screens

import com.quranreminder.app.ui.theme.LocalNavBarPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.SurahHeader
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurEmptyState
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import java.util.Locale

/** How far the content sheet rises over the band. */
private val SheetOverlap = 20.dp

@Composable
fun SearchScreen(
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val surahs = viewModel.surahs.collectAsState().value
    val filteredSurahs by remember(searchQuery, surahs) {
        derivedStateOf { filterSurahs(searchQuery, surahs) }
    }
    val ayahReference = remember(searchQuery) {
        parseAyahReference(searchQuery)?.takeIf { it.surahNumber in 1..114 && it.ayahNumber > 0 }
    }
    val openReference: (AyahReference) -> Unit = { reference ->
        navController.navigate("surah_detail/${reference.surahNumber}?ayah=${reference.ayahNumber}")
    }
    val navBar = LocalNavBarPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurBand(bottomPadding = SheetOverlap + 18.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("search.title"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = nurColors.onBand
                    )
                    Text(
                        text = t("search.eyebrow"),
                        style = MaterialTheme.typography.bodySmall,
                        color = nurColors.onBandMuted
                    )
                }
                NurIconButton(
                    icon = Icons.Default.Bookmarks,
                    contentDescription = t("bookmarks.title"),
                    onClick = { navController.navigate("bookmarks") }
                )
            }
            Spacer(Modifier.height(16.dp))
            BandSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                // Navigating on every keystroke opened "2:2" before the user could finish
                // typing "2:255"; the reference is opened explicitly instead.
                onSearch = { ayahReference?.let(openReference) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        NurSheet(
            modifier = Modifier
                .weight(1f)
                .quranOverlapAbove(SheetOverlap)
        ) {
            when {
                ayahReference != null -> {
                    val surahName = surahs.firstOrNull { it.surahNumber == ayahReference.surahNumber }?.surahName.orEmpty()
                    NurSurfaceCard(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                            .fillMaxWidth(),
                        onClick = { openReference(ayahReference) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NurStarMedallion(Icons.Default.MenuBook, size = 48.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = t("search.openAyah"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$surahName ${ayahReference.surahNumber}:${ayahReference.ayahNumber}".trim(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = nurColors.goldText
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                surahs.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = navBar)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = t("home.dataPreparing"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                filteredSurahs.isEmpty() -> {
                    NurEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = t("search.noResults"),
                        body = t("search.hint")
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = navBar + 16.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            item(key = "hint") {
                                Text(
                                    text = t("search.hint"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(filteredSurahs, key = { it.surahNumber }) { surah ->
                            Column {
                                NurSurahRow(
                                    surah = surah,
                                    onClick = { navController.navigate("surah_detail/${surah.surahNumber}") }
                                )
                                NurDivider(inset = 20.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rounded paper-coloured search field that sits on the green band. */
@Composable
private fun BandSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(scheme.surface)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                cursorBrush = SolidColor(scheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = t("search.placeholder"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = t("search.clear"),
                    tint = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class AyahReference(
    val surahNumber: Int,
    val ayahNumber: Int
)

private fun filterSurahs(query: String, surahs: List<SurahHeader>): List<SurahHeader> {
    val trimmed = query.trim()
    // An empty query lists every surah, so the Quran screen is never blank.
    if (trimmed.isBlank()) return surahs
    if (parseAyahReference(trimmed) != null) return emptyList()

    val number = trimmed.toIntOrNull()
    if (number != null) {
        return surahs.filter { it.surahNumber == number }
    }

    val normalized = normalizeSearchText(trimmed)
    return surahs.filter { surah ->
        normalizeSearchText(surah.surahName).contains(normalized) ||
            surah.surahNameArabic.contains(trimmed)
    }
}

private fun parseAyahReference(query: String): AyahReference? {
    val match = Regex("^\\s*(\\d{1,3})\\s*[:./,-]\\s*(\\d{1,3})\\s*$").matchEntire(query)
        ?: return null
    return AyahReference(
        surahNumber = match.groupValues[1].toIntOrNull() ?: return null,
        ayahNumber = match.groupValues[2].toIntOrNull() ?: return null
    )
}

private fun normalizeSearchText(value: String): String {
    val prepared = value
        .lowercase(Locale.ROOT)
        .replace('ʼ', '\'')
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('`', '\'')
        .replace("g'", "g")
        .replace("o'", "o")
        .replace("ʻ", "")

    return transliterateCyrillic(prepared)
        .replace(Regex("[^a-z0-9а-яёғқўҳ ]"), "")
        .trim()
}

private fun transliterateCyrillic(value: String): String {
    val builder = StringBuilder(value.length)
    value.forEach { char ->
        builder.append(cyrillicToLatin[char] ?: char)
    }
    return builder.toString()
}

private val cyrillicToLatin = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
    'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i",
    'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
    'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
    'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "s", 'ч' to "ch",
    'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ь' to "", 'э' to "e",
    'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q", 'ғ' to "g",
    'ҳ' to "h"
)
