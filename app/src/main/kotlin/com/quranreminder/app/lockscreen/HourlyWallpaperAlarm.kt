package com.quranreminder.app.lockscreen

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/**
 * Changes the lock-screen ayah at the top of every hour without the app being opened and
 * without internet (verses come from the on-device Room database).
 *
 * WorkManager's hourly job drifts and is often deferred by OEM battery managers, so an
 * AlarmManager alarm is armed for the next full hour: exact when "Alarms & reminders" is
 * granted, otherwise within a 10-minute window. Each firing
 * re-arms the next hour. The WorkManager job stays as a backup.
 */
object HourlyWallpaperAlarm {
    private const val REQUEST_CODE = 7_001
    private const val WINDOW_MS = 10 * 60 * 1000L
    const val ACTION = "com.quranreminder.app.action.HOURLY_WALLPAPER"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(appContext)
        val triggerAt = nextHourMillis()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                // Without "Alarms & reminders" an inexact alarm may slip up to ~45 minutes; a
                // 10-minute window is the tightest the system grants without that permission.
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MS, pendingIntent)
            }
        }.onFailure {
            // SecurityException if exact permission was revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, HourlyWallpaperReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** The next full hour, plus a few seconds so the hour key has definitely rolled over. */
    private fun nextHourMillis(): Long = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 5)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Fired by [HourlyWallpaperAlarm]; renders the new hour's ayah onto the lock screen. */
class HourlyWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HourlyWallpaperAlarm.ACTION) return
        val appContext = context.applicationContext
        HourlyWallpaperAlarm.schedule(appContext)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val applied = withTimeoutOrNull(8_000L) { applyHourlyWallpaper(appContext) } ?: false
                // Couldn't finish inside the broadcast window: let WorkManager finish the job.
                if (!applied) DailyAyahScheduler.updateToday(appContext)
            } catch (_: Exception) {
                DailyAyahScheduler.updateToday(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
