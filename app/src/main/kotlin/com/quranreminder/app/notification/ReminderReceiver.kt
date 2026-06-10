package com.quranreminder.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule reminders after device reboot
                scheduleAllReminders(context)
            }
            "com.quranreminder.REMINDER_NOTIFICATION" -> {
                val surahNumber = intent.getIntExtra("surah_number", 1)
                val ayahNumber = intent.getIntExtra("ayah_number", 1)
                val surahName = intent.getStringExtra("surah_name") ?: "Quran"
                val verses = intent.getStringExtra("verses") ?: ""
                val translation = intent.getStringExtra("translation") ?: ""

                val notificationManager = NotificationManager(context)
                notificationManager.showReminderNotification(
                    surahNumber,
                    surahName,
                    ayahNumber,
                    verses,
                    translation
                )
            }
        }
    }

    private fun scheduleAllReminders(context: Context) {
        // This will be called after device boot to reschedule all reminders
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
