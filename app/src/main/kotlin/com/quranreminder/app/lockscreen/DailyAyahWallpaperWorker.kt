package com.quranreminder.app.lockscreen

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quranreminder.app.database.DatabaseInitializer
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyAyahWallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = QuranDatabase.getDatabase(applicationContext)
            DatabaseInitializer(applicationContext).initializeDatabase(database)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_DATE, null) == today) {
                return@withContext Result.success()
            }

            val verse = LockScreenVerseSelector.select(applicationContext, database)
                ?: return@withContext Result.retry()
            LockScreenWallpaperApplier.apply(applicationContext, verse, rememberForToday = true)

            Result.success()
        } catch (security: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PREFS = LockScreenWallpaperApplier.PREFS
        private const val KEY_LAST_DATE = LockScreenWallpaperApplier.KEY_LAST_DATE
    }
}
