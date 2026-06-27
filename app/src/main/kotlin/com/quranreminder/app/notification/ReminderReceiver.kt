package com.quranreminder.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.quranreminder.app.lockscreen.DailyAyahForegroundService
import com.quranreminder.app.lockscreen.DailyAyahScheduler

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                DailyAyahScheduler.schedule(context)
                startDailyAyahService(context)
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

    private fun startDailyAyahService(context: Context) {
        val serviceIntent = Intent(context, DailyAyahForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
