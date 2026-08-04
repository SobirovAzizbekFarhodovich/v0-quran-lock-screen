package com.quranreminder.app.lockscreen

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quranreminder.app.database.DatabaseInitializer
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyAyahWallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = QuranDatabase.getDatabase(applicationContext)
            DatabaseInitializer(applicationContext).initializeDatabase(database)

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentHour = LockScreenWallpaperApplier.currentHour()
            if (prefs.getString(KEY_LAST_APPLIED_HOUR, null) == currentHour) {
                return@withContext Result.success()
            }

            val verse = LockScreenVerseSelector.select(applicationContext, database)
                ?: return@withContext Result.retry()
            LockScreenWallpaperApplier.apply(applicationContext, verse, rememberForHour = true)

            Result.success()
        } catch (security: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PREFS = LockScreenWallpaperApplier.PREFS
        private const val KEY_LAST_APPLIED_HOUR = LockScreenWallpaperApplier.KEY_LAST_APPLIED_HOUR
    }
}
