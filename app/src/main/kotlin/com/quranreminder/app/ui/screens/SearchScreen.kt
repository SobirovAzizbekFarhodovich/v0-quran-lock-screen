package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.SurahHeader
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    var searchQuery by remember { mutableStateOf("") }
    val surahs = viewModel.surahs.collectAsState().value
    val filteredSurahs by remember(searchQuery, surahs) {
        derivedStateOf { filterSurahs(searchQuery, surahs) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = t("search.title"),
            eyebrow = t("search.eyebrow"),
            leading = {
                NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
            }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    searchQuery = newValue
                    parseAyahReference(newValue)?.let { reference ->
                        if (reference.surahNumber in 1..114 && reference.ayahNumber > 0) {
                            navController.navigate(
                                "surah_detail/${reference.surahNumber}?ayah=${reference.ayahNumber}"
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = t("search.placeholder"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
        }

        when {
            searchQuery.isBlank() -> {
                Text(
                    text = t("search.hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            filteredSurahs.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = t("search.noResults"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSurahs) { surah ->
                        SearchSurahCard(
                            surah = surah,
                            onClick = {
                                navController.navigate("surah_detail/${surah.surahNumber}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSurahCard(
    surah: SurahHeader,
    onClick: () -> Unit
) {
    NurSurahRow(surah = surah, onClick = onClick)
}

private data class AyahReference(
    val surahNumber: Int,
    val ayahNumber: Int
)

private fun filterSurahs(query: String, surahs: List<SurahHeader>): List<SurahHeader> {
    val trimmed = query.trim()
    if (trimmed.isBlank() || parseAyahReference(trimmed) != null) {
        return emptyList()
    }

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
