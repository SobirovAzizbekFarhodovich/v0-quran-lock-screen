package com.quranreminder.app.lockscreen

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
        // Top-of-the-hour alarm: changes the ayah even when the app is never opened.
        HourlyWallpaperAlarm.schedule(context)
    }

    fun updateToday(context: Context) {
        val immediateWork = OneTimeWorkRequestBuilder<DailyAyahWallpaperWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }

    /**
     * While [owner] is alive, re-render the lock screen shortly after the Quran content in Room
     * changes (e.g. a language switch finished downloading), so the wallpaper doesn't keep the
     * previous language until the next hourly run. Debounced; skips the initial emission.
     */
    fun refreshOnContentChange(context: Context, owner: LifecycleOwner) {
        val appContext = context.applicationContext
        owner.lifecycleScope.launch {
            QuranDatabase.getDatabase(appContext).quranVerseDao()
                .observeVerseCount()
                .drop(1)
                .collectLatest { count ->
                    delay(CONTENT_CHANGE_DEBOUNCE_MS)
                    if (count > 0) updateToday(appContext)
                }
        }
    }

    private const val HOURLY_WORK_NAME = "hourly_ayah_lock_wallpaper"
    private const val IMMEDIATE_WORK_NAME = "hourly_ayah_lock_wallpaper_now"
    private const val CONTENT_CHANGE_DEBOUNCE_MS = 1_500L
}
