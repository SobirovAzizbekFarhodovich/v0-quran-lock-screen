package com.quranreminder.app.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exported only because the system delivers these broadcasts; every action it listens for is a
 * protected system broadcast that third-party apps cannot send, and anything else is ignored.
 * Alarms don't survive reboot, and need re-computing after clock / timezone changes.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // WorkManager persists its own work, but make sure the hourly wallpaper job exists.
            runCatching { DailyAyahScheduler.schedule(appContext) }
        }
        // Alarms are cleared on reboot and must follow clock/timezone changes and permission grants.
        runCatching { com.quranreminder.app.lockscreen.HourlyWallpaperAlarm.schedule(appContext) }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000L) { ReminderScheduler.rescheduleAll(appContext) }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
