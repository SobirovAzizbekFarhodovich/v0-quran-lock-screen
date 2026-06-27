package com.quranreminder.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = QuranDatabase.getDatabase(applicationContext)
            val reminderDao = database.reminderDao()
            val verseDao = database.quranVerseDao()

            val enabledReminders = reminderDao.getEnabledReminders().first()
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
            val currentTotalMinutes = currentHour * 60 + currentMinute
            val previousDayOfWeek = if (currentDayOfWeek == 0) 6 else currentDayOfWeek - 1

            for (reminder in enabledReminders) {
                val reminderTotalMinutes = reminder.hourOfDay * 60 + reminder.minuteOfHour
                val minutesSinceReminder =
                    (currentTotalMinutes - reminderTotalMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY

                if (minutesSinceReminder in 0 until WORK_WINDOW_MINUTES) {
                    val days = reminder.daysOfWeek.split(",").map { it.toInt() }
                    val reminderDay = if (currentTotalMinutes < reminderTotalMinutes) {
                        previousDayOfWeek
                    } else {
                        currentDayOfWeek
                    }
                    if (reminderDay in days) {
                        // Get the verse for this reminder
                        val verse = verseDao.getVerse(
                            reminder.surahNumber,
                            reminder.ayahNumber
                        ).first()

                        if (verse != null) {
                            val notificationManager = NotificationManager(applicationContext)
                            notificationManager.showReminderNotification(
                                verse.surahNumber,
                                verse.surahName,
                                verse.ayahNumber,
                                verse.ayahTextArabic,
                                verse.translation
                            )
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private companion object {
        const val WORK_WINDOW_MINUTES = 15
        const val MINUTES_PER_DAY = 24 * 60
    }
}
