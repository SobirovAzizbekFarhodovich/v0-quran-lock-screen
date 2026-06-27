package com.quranreminder.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quranreminder.app.MainActivity
import com.quranreminder.app.R

class NotificationManager(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Reminder notification channel
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Quran Reminders",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Quran reading reminders"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(reminderChannel)

            // Lock screen notification channel (high priority)
            val lockScreenChannel = NotificationChannel(
                LOCK_SCREEN_CHANNEL_ID,
                "Lock Screen Reminders",
                AndroidNotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Lock screen reminders for Quran reading"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                )
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(lockScreenChannel)
        }
    }

    fun showReminderNotification(
        surahNumber: Int,
        surahName: String,
        ayahNumber: Int,
        verses: String,
        translation: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("surah_number", surahNumber)
            putExtra("ayah_number", ayahNumber)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            surahNumber * 1000 + ayahNumber,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, LOCK_SCREEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quran_notification)
            .setContentTitle("$surahName $ayahNumber")
            .setContentText(verses.take(100))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$translation\n\n$verses")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pendingIntent, true)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(
            surahNumber * 1000 + ayahNumber,
            notification
        )
    }

    fun showSimpleNotification(
        title: String,
        message: String,
        notificationId: Int = SIMPLE_NOTIFICATION_ID
    ) {
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quran_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "quran_reminders"
        const val LOCK_SCREEN_CHANNEL_ID = "quran_lock_screen"
        const val SIMPLE_NOTIFICATION_ID = 1001
    }
}
