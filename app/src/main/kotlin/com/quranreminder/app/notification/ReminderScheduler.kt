package com.quranreminder.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Schedules one AlarmManager alarm per enabled reminder, for its next matching day/time.
 * When it fires, [ReminderReceiver] posts the notification and schedules the following
 * occurrence (a self-rearming chain), so nothing polls in the background.
 *
 * Exactness: if the user has granted "Alarms & reminders" (SCHEDULE_EXACT_ALARM — pre-granted
 * on Android 12/13, user-granted on 14+) we use setExactAndAllowWhileIdle; otherwise
 * setAndAllowWhileIdle, which still fires in Doze but may be deferred by a few minutes.
 * USE_EXACT_ALARM is deliberately not used: Play restricts it to alarm-clock/calendar apps.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "com.quranreminder.app.action.REMINDER_FIRE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** Schedules [reminder]'s next occurrence, or cancels it if disabled / has no valid day. */
    fun schedule(
        context: Context,
        reminder: Reminder,
        fromMillis: Long = System.currentTimeMillis()
    ) {
        if (reminder.id <= 0) return
        if (!reminder.isEnabled) {
            cancel(context, reminder.id)
            return
        }
        val triggerAt = nextTriggerAt(reminder, fromMillis)
        if (triggerAt == null) {
            cancel(context, reminder.id)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(context, reminder.id, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: SecurityException) {
            // Exact-alarm access revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(context: Context, reminderId: Int) {
        val operation = pendingIntent(context, reminderId, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
        operation.cancel()
    }

    /** Re-arms every reminder from the database (boot, app update, time/zone change, launch). */
    suspend fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val reminders = QuranDatabase.getDatabase(appContext).reminderDao().getAllReminders().first()
        reminders.forEach { schedule(appContext, it) }
    }

    /** Fire-and-forget variant for callers without a coroutine scope (e.g. Activity.onCreate). */
    fun rescheduleAllAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { rescheduleAll(appContext) }
        }
    }

    /** Next time strictly after [fromMillis] that matches the reminder's time and days. */
    fun nextTriggerAt(reminder: Reminder, fromMillis: Long): Long? {
        val days = parseDays(reminder.daysOfWeek)
        if (days.isEmpty()) return null
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, reminder.hourOfDay.coerceIn(0, 23))
                set(Calendar.MINUTE, reminder.minuteOfHour.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayIndex = candidate.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday, as in Reminder
            if (candidate.timeInMillis > fromMillis && dayIndex in days) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    /** "0,1,…,6" with 0 = Sunday. A blank value means every day. */
    fun parseDays(raw: String): Set<Int> {
        if (raw.isBlank()) return (0..6).toSet()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.toSet()
    }

    private fun pendingIntent(context: Context, reminderId: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            // Unique data so each reminder gets its own PendingIntent regardless of extras.
            data = Uri.parse("noorain-reminder://reminder/$reminderId")
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
