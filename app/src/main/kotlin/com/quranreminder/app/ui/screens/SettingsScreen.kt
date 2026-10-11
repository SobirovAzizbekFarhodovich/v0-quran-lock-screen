package com.quranreminder.app.ui.screens

import com.quranreminder.app.ui.theme.LocalNavBarPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.database.AppSettings
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import com.quranreminder.app.lockscreen.LockScreenAyahCategory
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.notification.rememberNotificationAccess
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurListRow
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
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
    val displayName = remember { session.displayName.trim() }
    val notificationAccess = rememberNotificationAccess()
    val navBar = LocalNavBarPadding.current

    if (showLogoutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(t("settings.logout.confirmTitle"), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(t("settings.logout.confirmBody"), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        session.signOut()
                        showLogoutConfirm = false
                        navController.navigate("auth") { popUpTo(0) }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(t("settings.logout"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(t("settings.logout.cancel"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
            eyebrow = displayName.ifBlank { "Noorain" },
            leading = { NurEmblem(size = 44.dp, modifier = Modifier.padding(start = 4.dp, end = 4.dp)) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = navBar + 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------------------------------------------------- notifications
            item { SettingSection(t("settings.notifications")) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        SwitchRow(
                            title = t("settings.notifications.enable"),
                            icon = Icons.Outlined.Notifications,
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.toggleNotifications()
                                if (enabled) notificationAccess.ensurePermission()
                            }
                        )
                        if (settings.notificationsEnabled && notificationAccess.shouldShowDeniedHint) {
                            Column(Modifier.fillMaxWidth().padding(start = 74.dp, end = 12.dp, bottom = 4.dp)) {
                                Text(
                                    t("reminders.permission.denied"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(
                                    onClick = notificationAccess.openNotificationSettings,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp)
                                ) {
                                    Text(
                                        t("reminders.permission.openSettings"),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        NurDivider()
                        SwitchRow(
                            title = t("settings.notifications.vibrate"),
                            icon = Icons.Outlined.Vibration,
                            checked = settings.vibrateOnReminder,
                            onCheckedChange = { viewModel.toggleVibration() }
                        )
                        NurDivider()
                        SwitchRow(
                            title = t("settings.notifications.sound"),
                            icon = Icons.Outlined.VolumeUp,
                            checked = settings.soundEnabled,
                            onCheckedChange = { viewModel.toggleSound() }
                        )
                        NurDivider()
                        SwitchRow(
                            title = t("settings.notifications.lockScreen"),
                            icon = Icons.Outlined.Lock,
                            checked = settings.lockScreenNotifications,
                            onCheckedChange = { viewModel.toggleLockScreenNotifications() }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- appearance
            item { SettingSection(t("settings.appearance"), Modifier.padding(top = 12.dp)) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        NurListRow(
                            title = t("settings.language.title"),
                            subtitle = languageDisplayName(settings.language),
                            leadingIcon = Icons.Outlined.Language,
                            trailing = { Chevron() },
                            onClick = { navController.navigate("language_selection?isFirstRun=false") }
                        )
                        NurDivider()
                        SwitchRow(
                            title = t("settings.darkMode"),
                            icon = Icons.Outlined.DarkMode,
                            checked = settings.darkMode,
                            onCheckedChange = { dark -> if (dark != settings.darkMode) viewModel.toggleDarkMode() }
                        )
                        NurDivider()
                        FontSizeControl(
                            value = settings.fontSize,
                            onValueChange = { viewModel.setFontSize(it) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- lock screen
            item { SettingSection(t("settings.lockScreen"), Modifier.padding(top = 12.dp)) }
            item {
                LockScreenGroup(
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

            // ---------------------------------------------------------- about
            item { SettingSection(t("settings.about"), Modifier.padding(top = 12.dp)) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        NurListRow(
                            title = "Noorain",
                            subtitle = t("settings.about.tagline"),
                            leading = { NurEmblem(size = 44.dp) }
                        )
                        Text(
                            text = t("settings.about.description"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }

            // ---------------------------------------------------------- account
            item { SettingSection(t("settings.account"), Modifier.padding(top = 12.dp)) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        NurListRow(
                            title = displayName.ifBlank { t("settings.account") },
                            subtitle = t("settings.account.signedIn"),
                            leadingIcon = Icons.Outlined.Person
                        )
                        NurDivider()
                        SignOutRow(onClick = { showLogoutConfirm = true })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LockScreenGroup(
    selectedCategory: LockScreenAyahCategory?,
    onPreview: () -> Unit,
    onRandom: () -> Unit,
    onCategorySelected: (LockScreenAyahCategory?) -> Unit
) {
    val c = nurColors
    NurSurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            NurListRow(
                title = t("settings.lockScreen.preview"),
                subtitle = t("settings.lockScreen.preview.desc"),
                leadingIcon = Icons.Outlined.Wallpaper,
                trailing = { Chevron() },
                onClick = onPreview
            )
            NurDivider()
            NurListRow(
                title = t("settings.lockScreen.random"),
                subtitle = t("settings.lockScreen.random.desc"),
                leadingIcon = Icons.Outlined.Shuffle,
                trailing = if (selectedCategory == null) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = {
                    onCategorySelected(null)
                    onRandom()
                }
            )
            NurDivider()
            // Hourly ayah changes in the background; OEM battery managers (Honor, Xiaomi…) can
            // hold the alarm back unless the app is allowed to run in the background.
            val context = androidx.compose.ui.platform.LocalContext.current
            NurListRow(
                title = t("settings.lockScreen.background"),
                subtitle = t("settings.lockScreen.background.desc"),
                leadingIcon = Icons.Outlined.BatteryChargingFull,
                trailing = { Chevron() },
                onClick = {
                    // Honor/Huawei gate background wake-ups in their own "App launch" manager
                    // (the alarm fires but the app may not be allowed to start); open it
                    // directly, else the standard battery-optimization list.
                    val vendorScreens = listOf(
                        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                    val openedVendor = vendorScreens.any { (pkg, cls) ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent().setClassName(pkg, cls)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                    }
                    if (!openedVendor) runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
            NurDivider()
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = t("settings.lockScreen.category"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LockScreenAyahCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(category) },
                            label = {
                                Text(
                                    t("settings.lockScreen.category.${category.id}"),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = c.frame,
                                selectedBorderColor = c.ornament
                            ),
                            modifier = Modifier.heightIn(min = 40.dp)
                        )
                    }
                }
                Text(
                    text = t("settings.lockScreen.languageNote"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FontSizeControl(value: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        NurListRow(
            title = t("settings.fontSize"),
            leadingIcon = Icons.Outlined.FormatSize,
            trailing = {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = nurColors.goldText
                )
            }
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 12f..24f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NurListRow(
        title = title,
        leadingIcon = icon,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun SignOutRow(onClick: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NurStarMedallion(
            Icons.Outlined.Logout,
            size = 44.dp,
            container = MaterialTheme.colorScheme.errorContainer,
            tint = danger
        )
        Text(t("settings.logout"), style = MaterialTheme.typography.titleMedium, color = danger, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingSection(title: String, modifier: Modifier = Modifier) {
    NurSectionTitle(text = title, modifier = modifier.padding(horizontal = 4.dp))
}

/** Native names of the app languages (shown in their own script, so not translated). */
private fun languageDisplayName(code: String): String = when (code) {
    "uz_cyrillic" -> "O‘zbek (kirill)"
    "ru" -> "Русский"
    "en" -> "English"
    else -> "O‘zbek (lotin)"
}
