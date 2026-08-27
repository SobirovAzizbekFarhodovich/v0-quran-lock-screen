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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavHostController
import com.quranreminder.app.database.Reminder
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.ReminderViewModel

@Composable
fun RemindersScreen(
    viewModel: ReminderViewModel,
    navController: NavHostController
) {
    val reminders = viewModel.reminders.collectAsState().value
    val showDialog = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = t("reminders.title"),
            eyebrow = t("reminders.eyebrow"),
            leading = {
                NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
            }
        )

        if (reminders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = t("reminders.empty.title"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = t("reminders.empty.body"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { updated ->
                            viewModel.updateReminder(updated)
                        },
                        onDelete = {
                            viewModel.deleteReminder(reminder)
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialog.value = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = t("reminders.add"))
        }

        if (showDialog.value) {
            AddReminderDialog(
                onDismiss = { showDialog.value = false },
                onAdd = { reminder ->
                    viewModel.addReminder(reminder)
                    showDialog.value = false
                }
            )
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$suraLabel${reminder.surahNumber}$ayahLabel${reminder.ayahNumber}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = String.format("%02d:%02d", reminder.hourOfDay, reminder.minuteOfHour),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { isEnabled ->
                        onToggle(reminder.copy(isEnabled = isEnabled))
                    }
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = deleteLabel,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(addTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = selectedSurah.value,
                    onValueChange = { selectedSurah.value = it.filter(Char::isDigit).take(3) },
                    label = { Text(surahFieldLabel) },
                    keyboardOptions = keyboardOptions,
                    singleLine = true
                )
                OutlinedTextField(
                    value = selectedAyah.value,
                    onValueChange = { selectedAyah.value = it.filter(Char::isDigit).take(3) },
                    label = { Text(ayahFieldLabel) },
                    keyboardOptions = keyboardOptions,
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selectedHour.value,
                        onValueChange = { selectedHour.value = it.filter(Char::isDigit).take(2) },
                        label = { Text(hourFieldLabel) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = selectedMinute.value,
                        onValueChange = { selectedMinute.value = it.filter(Char::isDigit).take(2) },
                        label = { Text(minuteFieldLabel) },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canAdd,
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
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}
