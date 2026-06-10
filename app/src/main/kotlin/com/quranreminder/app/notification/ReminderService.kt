package com.quranreminder.app.notification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleReminderChecks()
        return START_STICKY
    }

    private fun scheduleReminderChecks() {
        val reminderWorkRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderWorkRequest
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
