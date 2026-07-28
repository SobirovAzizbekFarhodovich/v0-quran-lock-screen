package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.AppSettings
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import com.quranreminder.app.lockscreen.LockScreenAyahCategory
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel
) {
    val settings = viewModel.settings.collectAsState().value ?: AppSettings()
    val context = LocalContext.current
    val selectedCategory = remember {
        mutableStateOf(LockScreenWallpaperApplier.getSelectedCategory(context))
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
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.padding(40.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingSection(title = "Notifications")
                SettingToggle(
                    label = "Enable Notifications",
                    isChecked = settings.notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications() }
                )
                SettingToggle(
                    label = "Vibrate on Reminder",
                    isChecked = settings.vibrateOnReminder,
                    onCheckedChange = { viewModel.toggleVibration() }
                )
                SettingToggle(
                    label = "Sound Enabled",
                    isChecked = settings.soundEnabled,
                    onCheckedChange = { viewModel.toggleSound() }
                )
                SettingToggle(
                    label = "Lock Screen Notifications",
                    isChecked = settings.lockScreenNotifications,
                    onCheckedChange = { viewModel.toggleLockScreenNotifications() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingSection(title = "Display")
                SettingSlider(
                    label = "Font Size",
                    value = settings.fontSize.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 12f..24f
                )
                SettingToggle(
                    label = "Dark Mode",
                    isChecked = settings.darkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingSection(title = "Lock-screen")
                LockScreenControls(
                    selectedCategory = selectedCategory.value,
                    onPreview = { navController.navigate("lock_preview") },
                    onRandom = {
                        LockScreenWallpaperApplier.clearSelectedVerse(context)
                        DailyAyahScheduler.updateToday(context)
                    },
                    onCategorySelected = { category ->
                        LockScreenWallpaperApplier.clearSelectedVerse(context)
                        LockScreenWallpaperApplier.setSelectedCategory(context, category)
                        selectedCategory.value = category
                        DailyAyahScheduler.updateToday(context)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingSection(title = "About")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quran Reminder",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version 1.0.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ruxsatli JSON data importi orqali offline Qur'on o'qish va eslatmalar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreenControls(
    selectedCategory: LockScreenAyahCategory?,
    onPreview: () -> Unit,
    onRandom: () -> Unit,
    onCategorySelected: (LockScreenAyahCategory?) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onPreview
                ) {
                    Text("Preview")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCategorySelected(null)
                        onRandom()
                    }
                ) {
                    Text("Randomga qaytish")
                }
            }
            Text(
                text = "Lock-screen Tili (4 ta til)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val currentLang = LockScreenWallpaperApplier.getLockScreenLanguage(context)
            val languages = listOf(
                "uz_cyrillic" to "Ўзбек (Кирил)",
                "uz_latin" to "O'zbek (Lotin)",
                "en" to "English",
                "ru" to "Русский"
            )
            languages.chunked(2).forEach { rowLangs ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowLangs.forEach { (code, label) ->
                        val isSelected = currentLang == code
                        if (isSelected) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LockScreenWallpaperApplier.setLockScreenLanguage(context, code)
                                    DailyAyahScheduler.updateToday(context)
                                }
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LockScreenWallpaperApplier.setLockScreenLanguage(context, code)
                                    DailyAyahScheduler.updateToday(context)
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            Text(
                text = "Kategoriya",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LockScreenAyahCategory.entries.chunked(2).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { category ->
                        val isSelected = selectedCategory == category
                        if (isSelected) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onCategorySelected(category) }
                            ) {
                                Text(category.label)
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onCategorySelected(category) }
                            ) {
                                Text(category.label)
                            }
                        }
                    }
                    if (rowCategories.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingToggle(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = value.toInt().toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
