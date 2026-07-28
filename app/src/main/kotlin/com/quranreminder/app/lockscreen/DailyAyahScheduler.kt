package com.quranreminder.app.lockscreen

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DailyAyahScheduler {
    fun schedule(context: Context) {
        val hourlyWork = PeriodicWorkRequestBuilder<DailyAyahWallpaperWorker>(
            1,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HOURLY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            hourlyWork
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

    private const val HOURLY_WORK_NAME = "hourly_ayah_lock_wallpaper"
    private const val IMMEDIATE_WORK_NAME = "hourly_ayah_lock_wallpaper_now"
}
