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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.database.AppSettings
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import com.quranreminder.app.lockscreen.LockScreenAyahCategory
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    session: SessionManager
) {
    val settings = viewModel.settings.collectAsState().value ?: AppSettings()
    val context = LocalContext.current
    val selectedCategory = remember {
        mutableStateOf(LockScreenWallpaperApplier.getSelectedCategory(context))
    }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(t("settings.logout.confirmTitle")) },
            text = { Text(t("settings.logout.confirmBody")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    session.signOut()
                    showLogoutConfirm = false
                    navController.navigate("auth") { popUpTo(0) }
                }) { Text(t("settings.logout"), color = NurColors.Terracotta) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(t("settings.logout.cancel"))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = t("settings.title"),
            eyebrow = "Noorain",
            leading = {
                androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = t("settings.back"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingSection(title = t("settings.notifications"))
                SettingToggle(
                    label = t("settings.notifications.enable"),
                    isChecked = settings.notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications() }
                )
                SettingToggle(
                    label = t("settings.notifications.vibrate"),
                    isChecked = settings.vibrateOnReminder,
                    onCheckedChange = { viewModel.toggleVibration() }
                )
                SettingToggle(
                    label = t("settings.notifications.sound"),
                    isChecked = settings.soundEnabled,
                    onCheckedChange = { viewModel.toggleSound() }
                )
                SettingToggle(
                    label = t("settings.notifications.lockScreen"),
                    isChecked = settings.lockScreenNotifications,
                    onCheckedChange = { viewModel.toggleLockScreenNotifications() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingSection(title = t("settings.appearance"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(t("settings.language.title"), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (settings.language) {
                                "uz_cyrillic" -> "O‘zbek (kirill)"
                                "ru" -> "Русский"
                                "en" -> "English"
                                else -> "O‘zbek (lotin)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = { navController.navigate("language_selection?isFirstRun=false") }) {
                            Text(t("settings.language.change"))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingSlider(
                    label = t("settings.fontSize"),
                    value = settings.fontSize.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 12f..24f
                )
                AppearanceControl(
                    isDark = settings.darkMode,
                    onSelect = { dark -> if (dark != settings.darkMode) viewModel.toggleDarkMode() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingSection(title = t("settings.lockScreen"))
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
                SettingSection(title = t("settings.about"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Noorain",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = t("settings.about.tagline"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = t("settings.about.description"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(t("settings.logout"), color = NurColors.Terracotta)
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
        shape = RoundedCornerShape(18.dp)
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
                    Text(t("settings.lockScreen.preview"))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCategorySelected(null)
                        onRandom()
                    }
                ) {
                    Text(t("settings.lockScreen.random"))
                }
            }
            Text(
                text = t("settings.lockScreen.languageNote"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Text(
                text = t("settings.lockScreen.category"),
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
fun AppearanceControl(
    isDark: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(t("settings.appearance"), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppearanceOption(label = t("settings.appearance.light"), selected = !isDark, modifier = Modifier.weight(1f)) { onSelect(false) }
                AppearanceOption(label = t("settings.appearance.dark"), selected = isDark, modifier = Modifier.weight(1f)) { onSelect(true) }
            }
        }
    }
}

@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingSection(title: String) {
    NurSectionTitle(text = title, modifier = Modifier.padding(vertical = 8.dp))
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
        shape = RoundedCornerShape(18.dp)
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
        shape = RoundedCornerShape(18.dp)
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
