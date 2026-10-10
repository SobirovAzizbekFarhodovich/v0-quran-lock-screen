package com.quranreminder.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quranreminder.app.database.AppSettings
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.lockscreen.CyrillicToLatinConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Non-exported: only reachable through the explicit PendingIntent that [ReminderScheduler]
 * hands to AlarmManager, so other apps cannot spoof reminder notifications.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId <= 0) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { fire(appContext, reminderId) }
            } catch (_: Exception) {
                // Never crash the process from a background alarm.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, reminderId: Int) {
        val database = QuranDatabase.getDatabase(context)
        val reminder = database.reminderDao().getReminderById(reminderId).first()
        if (reminder == null || !reminder.isEnabled) {
            ReminderScheduler.cancel(context, reminderId)
            return
        }

        // Re-arm first so a failure below never breaks the daily chain. The one-minute offset
        // keeps an alarm delivered a hair early from re-scheduling the same occurrence.
        ReminderScheduler.schedule(context, reminder, System.currentTimeMillis() + 60_000L)

        val settings = database.appSettingsDao().getSettings().first() ?: AppSettings()
        if (!settings.notificationsEnabled) return

        val notificationManager = NotificationManager(context)
        if (!notificationManager.canPostNotifications()) return

        val language = settings.language.ifBlank { AppLanguage.current(context) }
        val verse = database.quranVerseDao().getVerseOnce(reminder.surahNumber, reminder.ayahNumber)
        val latin = language == "uz_latin" || language == "uz"
        fun localize(text: String): String =
            if (latin) CyrillicToLatinConverter.convert(text) else text

        val title = AppLanguage.text(language, "notif.reminder.title")
        val reference = if (verse != null) {
            "${localize(verse.surahName)} ${verse.surahNumber}:${verse.ayahNumber}"
        } else {
            "${reminder.surahNumber}:${reminder.ayahNumber}"
        }
        val note = reminder.reminderText.trim()
        val translation = verse?.translation
            ?.replace(Regex("\\[[^\\]]+\\]"), "")
            ?.trim()
            ?.let(::localize)
            .orEmpty()
        val text = when {
            translation.isNotEmpty() -> "$reference — $translation"
            note.isNotEmpty() -> "$reference — $note"
            else -> "$reference — ${AppLanguage.text(language, "notif.reminder.fallbackBody")}"
        }
        val bigText = buildString {
            if (note.isNotEmpty()) append(note).append("\n\n")
            append(reference)
            if (verse != null && verse.ayahTextArabic.isNotBlank()) append("\n\n").append(verse.ayahTextArabic)
            if (translation.isNotEmpty()) append("\n\n").append(translation)
            if (verse == null && note.isEmpty()) {
                append("\n\n").append(AppLanguage.text(language, "notif.reminder.fallbackBody"))
            }
        }

        notificationManager.showReminderNotification(
            notificationId = NOTIFICATION_ID_BASE + reminder.id,
            surahNumber = reminder.surahNumber,
            ayahNumber = reminder.ayahNumber,
            title = title,
            text = text,
            bigText = bigText,
            settings = settings,
            language = language
        )
    }

    private companion object {
        const val NOTIFICATION_ID_BASE = 20_000
        const val RECEIVER_BUDGET_MS = 9_000L
    }
}
