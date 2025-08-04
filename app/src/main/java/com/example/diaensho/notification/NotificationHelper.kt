package com.example.diaensho.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.diaensho.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val HOTWORD_NOTIFICATION_ID = 1001
        const val SYNC_NOTIFICATION_ID = 1002
        private const val HOTWORD_CHANNEL_ID = "hotword_detection"
        private const val SYNC_CHANNEL_ID = "data_sync"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Hotword detection channel
            val hotwordChannel = NotificationChannel(
                HOTWORD_CHANNEL_ID,
                "Voice Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the app is listening for voice commands"
                setShowBadge(false)
            }

            // Data sync channel
            val syncChannel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of data synchronization"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(hotwordChannel, syncChannel))
        }
    }

    fun createHotwordNotification(status: String): Notification {
        return NotificationCompat.Builder(context, HOTWORD_CHANNEL_ID)
            .setContentTitle("Diaensho Voice Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateHotwordNotification(status: String) {
        val notification = createHotwordNotification(status)
        notificationManager.notify(HOTWORD_NOTIFICATION_ID, notification)
    }

    fun createSyncNotification(status: String): Notification {
        return NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setContentTitle("Syncing Data")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun updateSyncNotification(status: String) {
        val notification = createSyncNotification(status)
        notificationManager.notify(SYNC_NOTIFICATION_ID, notification)
    }

    fun cancelSyncNotification() {
        notificationManager.cancel(SYNC_NOTIFICATION_ID)
    }
}
