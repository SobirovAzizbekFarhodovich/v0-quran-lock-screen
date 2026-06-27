package com.quranreminder.app.lockscreen

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyAyahScheduler {
    fun schedule(context: Context) {
        val dailyWork = PeriodicWorkRequestBuilder<DailyAyahWallpaperWorker>(
            24,
            TimeUnit.HOURS
        )
            .setInitialDelay(delayUntilNextMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWork
        )
    }

    fun updateToday(context: Context) {
        val immediateWork = OneTimeWorkRequestBuilder<DailyAyahWallpaperWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }

    private fun delayUntilNextMorning(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next.timeInMillis - now.timeInMillis
    }

    private const val DAILY_WORK_NAME = "daily_ayah_lock_wallpaper"
    private const val IMMEDIATE_WORK_NAME = "daily_ayah_lock_wallpaper_now"
}
