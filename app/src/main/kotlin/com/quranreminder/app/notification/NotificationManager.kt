package com.quranreminder.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quranreminder.app.MainActivity
import com.quranreminder.app.R
import com.quranreminder.app.database.AppSettings

/**
 * Posts reminder notifications. Channel sound/vibration can't be changed after a channel is
 * created, so the in-app "sound" / "vibrate" toggles map onto one of four channels (created
 * lazily, names localized and refreshed on every post). The lock-screen toggle maps onto
 * notification visibility. No full-screen intent: a reading reminder is not an alarm/call.
 */
class NotificationManager(private val context: Context) {
    private val systemManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    /** True when the app may post notifications (runtime permission on 13+ and app-level block). */
    fun canPostNotifications(): Boolean = canPost(context)

    fun showReminderNotification(
        notificationId: Int,
        surahNumber: Int,
        ayahNumber: Int,
        title: String,
        text: String,
        bigText: String,
        settings: AppSettings,
        language: String
    ) {
        if (!canPostNotifications()) return
        removeLegacyChannels()
        val channelId = ensureReminderChannel(settings.soundEnabled, settings.vibrateOnReminder, language)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("surah_number", surahNumber)
            putExtra("ayah_number", ayahNumber)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_quran_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .setVisibility(
                if (settings.lockScreenNotifications) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )
        if (!settings.soundEnabled && !settings.vibrateOnReminder) {
            builder.setSilent(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post — nothing else to do.
        }
    }

    private fun ensureReminderChannel(sound: Boolean, vibrate: Boolean, language: String): String {
        val (id, nameKey) = when {
            sound && vibrate -> REMINDER_CHANNEL_ID to "notif.channel.reminders"
            sound -> REMINDER_CHANNEL_SOUND_ID to "notif.channel.reminders.soundOnly"
            vibrate -> REMINDER_CHANNEL_VIBRATE_ID to "notif.channel.reminders.vibrateOnly"
            else -> REMINDER_CHANNEL_QUIET_ID to "notif.channel.reminders.quiet"
        }
        val importance = if (sound || vibrate) {
            AndroidNotificationManager.IMPORTANCE_HIGH
        } else {
            AndroidNotificationManager.IMPORTANCE_DEFAULT
        }
        // Re-creating an existing channel only updates its name/description (localization);
        // the user's own channel customisations are preserved by the system.
        val channel = NotificationChannel(id, AppLanguage.text(language, nameKey), importance).apply {
            description = AppLanguage.text(language, "notif.channel.reminders.desc")
            if (sound) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
            enableVibration(vibrate)
        }
        systemManager.createNotificationChannel(channel)
        return id
    }

    private fun removeLegacyChannels() {
        LEGACY_CHANNEL_IDS.forEach { id ->
            if (systemManager.getNotificationChannel(id) != null) {
                systemManager.deleteNotificationChannel(id)
            }
        }
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "quran_reminders"
        private const val REMINDER_CHANNEL_SOUND_ID = "quran_reminders_sound_only"
        private const val REMINDER_CHANNEL_VIBRATE_ID = "quran_reminders_vibrate_only"
        private const val REMINDER_CHANNEL_QUIET_ID = "quran_reminders_quiet"

        /** Channels from older builds: the IMPORTANCE_MAX alarm-sound channel and the removed FGS. */
        private val LEGACY_CHANNEL_IDS = listOf("quran_lock_screen", "daily_ayah_wallpaper_service")

        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
