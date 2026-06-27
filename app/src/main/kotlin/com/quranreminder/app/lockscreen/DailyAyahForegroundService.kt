package com.quranreminder.app.lockscreen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.quranreminder.app.R

class DailyAyahForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(
            SERVICE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quran_notification)
                .setContentTitle("Qur'on lock-screen yangilanishi")
                .setContentText("Kunlik oyat lock-screen foniga tayyorlanmoqda")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(false)
                .build()
        )

        DailyAyahScheduler.schedule(this)
        DailyAyahScheduler.updateToday(this)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lock-screen wallpaper updater",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Runs briefly while preparing the daily lock-screen ayah"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "daily_ayah_wallpaper_service"
        private const val SERVICE_NOTIFICATION_ID = 3101
    }
}
