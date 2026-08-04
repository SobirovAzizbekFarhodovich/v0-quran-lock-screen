package com.quranreminder.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.SurahHeader
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.lockscreen.LockScreenVerseSelector
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    viewModel: QuranViewModel,
    navController: NavHostController
) {
    val surahs = viewModel.surahs.collectAsState().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HomeHeader(
                    onReminders = { navController.navigate("reminders") },
                    onSearch = { navController.navigate("search") },
                    onSettings = { navController.navigate("settings") }
                )
            }

            item {
                DailyAyahCard(onClick = { verse ->
                    navController.navigate("surah_detail/${verse.surahNumber}?ayah=${verse.ayahNumber}")
                })
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeFeatureTile(
                        icon = Icons.Default.MenuBook,
                        title = "Qur'oni Karim",
                        description = "O'qing va qidiring",
                        iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("search") }
                    )
                    HomeFeatureTile(
                        icon = Icons.Default.NotificationsActive,
                        title = "Eslatmalar",
                        description = "Tilovat vaqtini belgilang",
                        iconBackground = NurColors.TerracottaSoft,
                        iconTint = NurColors.Terracotta,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("reminders") }
                    )
                }
            }

            item {
                HomeFeatureTile(
                    icon = Icons.Default.Mic,
                    title = "Tilovat tekshiruvi",
                    description = "AI bilan qiroatni tekshiring",
                    iconBackground = NurColors.TerracottaSoft,
                    iconTint = NurColors.Terracotta,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("recitation") }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeFeatureTile(
                        icon = Icons.Default.ChatBubble,
                        title = "Islomiy AI",
                        description = "Ishonchli manbalar asosida",
                        iconBackground = NurColors.SageSoft,
                        iconTint = NurColors.Sage,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("tadabbur_chat") }
                    )
                    HomeFeatureTile(
                        icon = Icons.Default.Wallpaper,
                        title = "Lock-screen",
                        description = "Oyatni ekran foniga qo'ying",
                        iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("lock_preview") }
                    )
                }
            }

            item {
                ContinueReadingCard(onClick = { navController.navigate("surah_detail/1?ayah=1") })
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        NurSectionTitle("Suralar")
                        Text(
                            text = if (surahs.isEmpty()) "Suralar yuklanmoqda" else "${surahs.size} ta sura",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                        )
                    }
                    NurIconButton(
                        icon = Icons.Default.Search,
                        contentDescription = "Sura qidirish",
                        onClick = { navController.navigate("search") }
                    )
                }
            }

            if (surahs.isEmpty()) {
                item {
                    NurSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Qur'on ma'lumotlari tayyorlanmoqda...",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(surahs, key = { it.surahNumber }) { surah ->
                    NurSurahRow(
                        surah = surah,
                        onClick = { navController.navigate("surah_detail/${surah.surahNumber}") }
                    )
                }
            }
        }

        NurBottomNavigation(navController = navController)
    }
}

@Composable
private fun HomeHeader(
    onReminders: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            NurSectionTitle("Qur'on bilan bugun")
            Text(
                text = "Assalomu alaykum!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        NurIconButton(Icons.Default.NotificationsActive, "Eslatmalar", onReminders)
        Spacer(modifier = Modifier.width(8.dp))
        NurIconButton(Icons.Default.Search, "Qidirish", onSearch)
        Spacer(modifier = Modifier.width(8.dp))
        NurIconButton(Icons.Default.Settings, "Sozlamalar", onSettings, emphasized = true)
    }
}

@Composable
private fun DailyAyahCard(onClick: (QuranVerse) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val currentVerse = verse
    NurSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = NurColors.Ink,
        onClick = { currentVerse?.let(onClick) }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = currentVerse?.let { "SOATLIK OYAT · ${it.surahName.uppercase()}, ${it.surahNumber}:${it.ayahNumber}" }
                    ?: "SOATLIK OYAT",
                style = MaterialTheme.typography.labelSmall,
                color = NurColors.GoldSoft
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentVerse?.ayahTextArabic.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                color = NurColors.Cream,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = currentVerse?.translation ?: "Lock-screen bilan bir xil oyat tayyorlanmoqda...",
                style = MaterialTheme.typography.bodySmall,
                color = NurColors.Cream.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun HomeFeatureTile(
    icon: ImageVector,
    title: String,
    description: String,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NurSurfaceCard(modifier = modifier.height(144.dp), onClick = onClick) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconBackground, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(onClick: () -> Unit) {
    NurSurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NurEmblem(size = 34.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Davom eting", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Al-Fotiha, 1-oyat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun NurSurahRow(surah: SurahHeader, onClick: () -> Unit) {
    NurSurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                NurEmblem(size = 36.dp)
                Text(
                    text = surah.surahNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NurColors.Ink
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.surahName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sura ${surah.surahNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = surah.surahNameArabic,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NurPill(text = "Qur'on")
            }
        }
    }
}

@Composable
private fun NurBottomNavigation(navController: NavHostController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(Icons.Default.Home, "Bosh", true) { navController.navigate("home") }
        BottomNavItem(Icons.Default.MenuBook, "Qur'on", false) { navController.navigate("search") }
        BottomNavItem(Icons.Default.Bookmark, "Saqlangan", false) { navController.navigate("bookmarks") }
        BottomNavItem(Icons.Default.ChatBubble, "Yordamchi", false) { navController.navigate("tadabbur_chat") }
        BottomNavItem(Icons.Default.Settings, "Sozlama", false) { navController.navigate("settings") }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
