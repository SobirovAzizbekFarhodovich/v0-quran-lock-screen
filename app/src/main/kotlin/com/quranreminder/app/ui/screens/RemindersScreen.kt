package com.quranreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.Reminder
import com.quranreminder.app.notification.rememberNotificationAccess
import com.quranreminder.app.ui.theme.NurEmptyState
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurStarMedallion
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.ReminderViewModel

@Composable
fun RemindersScreen(
    viewModel: ReminderViewModel,
    navController: NavHostController
) {
    val context = LocalContext.current
    val reminders = viewModel.reminders.collectAsState().value
    val showDialog = remember { mutableStateOf(false) }
    val appSettingsFlow = remember {
        QuranDatabase.getDatabase(context.applicationContext).appSettingsDao().getSettings()
    }
    val appSettings = appSettingsFlow.collectAsState(initial = null).value
    val access = rememberNotificationAccess(onExactAccessGranted = { viewModel.rescheduleAll() })
    val hasEnabledReminder = reminders.any { it.isEnabled }

    // Existing enabled reminders but we've never asked (e.g. upgraded from an older build):
    // ask once, in context, when the user opens the reminders screen.
    LaunchedEffect(hasEnabledReminder) {
        if (hasEnabledReminder && !access.canPost && !access.askedBefore) {
            access.requestPermission()
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NurScreenHeader(
                title = t("reminders.title"),
                eyebrow = t("reminders.eyebrow"),
                leading = {
                    NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
                }
            )

            val hint: (@Composable () -> Unit)? = when {
                hasEnabledReminder && access.shouldShowDeniedHint -> {
                    {
                        InfoBlock(
                            icon = Icons.Outlined.NotificationsOff,
                            text = t("reminders.permission.denied"),
                            action = t("reminders.permission.openSettings"),
                            onAction = access.openNotificationSettings
                        )
                    }
                }
                hasEnabledReminder && appSettings != null && !appSettings.notificationsEnabled -> {
                    {
                        InfoBlock(
                            icon = Icons.Outlined.NotificationsOff,
                            text = t("reminders.appNotificationsOff"),
                            action = t("settings.title"),
                            onAction = { navController.navigate("settings") }
                        )
                    }
                }
                hasEnabledReminder && access.canPost && !access.canScheduleExact -> {
                    {
                        InfoBlock(
                            icon = Icons.Outlined.Alarm,
                            text = t("reminders.exactAlarm.hint"),
                            action = t("reminders.exactAlarm.allow"),
                            onAction = access.openExactAlarmSettings
                        )
                    }
                }
                else -> null
            }

            if (reminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.Center
                ) {
                    NurEmptyState(
                        icon = Icons.Outlined.Schedule,
                        title = t("reminders.empty.title"),
                        body = t("reminders.empty.body")
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hint != null) item(key = "hint") { hint() }
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onToggle = { updated ->
                                viewModel.updateReminder(updated)
                                if (updated.isEnabled) access.ensurePermission()
                            },
                            onDelete = {
                                viewModel.deleteReminder(reminder)
                            }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showDialog.value = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(t("reminders.add"), style = MaterialTheme.typography.labelLarge) }
        )

        if (showDialog.value) {
            AddReminderDialog(
                onDismiss = { showDialog.value = false },
                onAdd = { reminder ->
                    viewModel.addReminder(reminder)
                    showDialog.value = false
                    access.ensurePermission()
                }
            )
        }
    }
}

/** Gold-keyline info block for permission / settings hints, with its action. */
@Composable
private fun InfoBlock(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    val c = nurColors
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (c.isDark) 0.35f else 0.45f), shape)
            .border(BorderStroke(1.dp, c.frame), shape)
            .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        NurStarMedallion(icon, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onToggle: (Reminder) -> Unit,
    onDelete: () -> Unit
) {
    val suraLabel = t("reminders.suraLabel")
    val ayahLabel = t("reminders.ayahLabel")
    val deleteLabel = t("reminders.delete")
    val c = nurColors
    NurSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(java.util.Locale.US, "%02d:%02d", reminder.hourOfDay, reminder.minuteOfHour),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$suraLabel${reminder.surahNumber}$ayahLabel${reminder.ayahNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.goldText
                )
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { isEnabled ->
                    onToggle(reminder.copy(isEnabled = isEnabled))
                }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (Reminder) -> Unit
) {
    val selectedSurah = remember { mutableStateOf("1") }
    val selectedAyah = remember { mutableStateOf("1") }
    val selectedHour = remember { mutableStateOf("8") }
    val selectedMinute = remember { mutableStateOf("0") }
    val keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    val surah = selectedSurah.value.toIntOrNull()?.coerceIn(1, 114)
    val ayah = selectedAyah.value.toIntOrNull()?.coerceAtLeast(1)
    val hour = selectedHour.value.toIntOrNull()?.coerceIn(0, 23)
    val minute = selectedMinute.value.toIntOrNull()?.coerceIn(0, 59)
    val canAdd = surah != null && ayah != null && hour != null && minute != null
    val addTitle = t("reminders.add")
    val surahFieldLabel = t("reminders.field.surah")
    val ayahFieldLabel = t("reminders.field.ayah")
    val hourFieldLabel = t("reminders.field.hour")
    val minuteFieldLabel = t("reminders.field.minute")
    val confirmLabel = t("reminders.confirm")
    val cancelLabel = t("reminders.cancel")
    val c = nurColors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.ornament,
        unfocusedBorderColor = c.frame,
        focusedLabelColor = c.goldText,
        cursorColor = MaterialTheme.colorScheme.primary
    )
    val timeStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(addTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("reminders.dialog.timeSection"), style = MaterialTheme.typography.titleSmall, color = c.goldText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = selectedHour.value,
                        onValueChange = { selectedHour.value = it.filter(Char::isDigit).take(2) },
                        label = { Text(hourFieldLabel) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        textStyle = timeStyle,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = selectedMinute.value,
                        onValueChange = { selectedMinute.value = it.filter(Char::isDigit).take(2) },
                        label = { Text(minuteFieldLabel) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        textStyle = timeStyle,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text(t("reminders.dialog.verseSection"), style = MaterialTheme.typography.titleSmall, color = c.goldText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = selectedSurah.value,
                        onValueChange = { selectedSurah.value = it.filter(Char::isDigit).take(3) },
                        label = { Text(surahFieldLabel, maxLines = 1) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = selectedAyah.value,
                        onValueChange = { selectedAyah.value = it.filter(Char::isDigit).take(3) },
                        label = { Text(ayahFieldLabel, maxLines = 1) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canAdd,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    if (surah != null && ayah != null && hour != null && minute != null) {
                        onAdd(
                            Reminder(
                                surahNumber = surah,
                                ayahNumber = ayah,
                                hourOfDay = hour,
                                minuteOfHour = minute
                            )
                        )
                    }
                }
            ) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(cancelLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
