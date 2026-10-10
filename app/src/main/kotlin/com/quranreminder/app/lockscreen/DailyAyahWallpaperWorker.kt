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
            if (applyHourlyWallpaper(applicationContext)) Result.success() else Result.retry()
        } catch (security: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

}

/**
 * Renders the current hour's ayah onto the lock screen. Offline: verses come from Room.
 * Returns false when no verse is available yet (content not loaded). Shared by the WorkManager
 * job and the top-of-the-hour alarm ([HourlyWallpaperReceiver]).
 */
suspend fun applyHourlyWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
    val database = QuranDatabase.getDatabase(context)
    DatabaseInitializer(context).initializeDatabase(database)
    val verse = LockScreenVerseSelector.select(context, database) ?: return@withContext false
    // Skip only if exactly this render (hour + verse + text + language + font + background)
    // is already on the lock screen.
    val prefs = context.getSharedPreferences(LockScreenWallpaperApplier.PREFS, Context.MODE_PRIVATE)
    val signature = LockScreenWallpaperApplier.renderSignature(context, verse)
    if (prefs.getString(LockScreenWallpaperApplier.KEY_LAST_APPLIED_HOUR, null) != signature) {
        LockScreenWallpaperApplier.apply(context, verse, rememberForHour = true)
    }
    true
}
