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

            for (reminder in enabledReminders) {
                // Check if this reminder should trigger now
                if (reminder.hourOfDay == currentHour && reminder.minuteOfHour == currentMinute) {
                    val days = reminder.daysOfWeek.split(",").map { it.toInt() }
                    if (currentDayOfWeek in days) {
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
}
