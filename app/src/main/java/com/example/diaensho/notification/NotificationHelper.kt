package com.example.diaensho.notification

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
        const val HOTWORD_CHANNEL_ID = "HotwordDetectionChannel"
        const val SYNC_CHANNEL_ID = "SyncChannel"
        const val HOTWORD_NOTIFICATION_ID = 1
        const val SYNC_NOTIFICATION_ID = 2
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hotwordChannel = NotificationChannel(
                HOTWORD_CHANNEL_ID,
                "Hotword Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the status of voice recognition"
                setShowBadge(false)
            }

            val syncChannel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Data Synchronization",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows sync status of diary entries and app usage"
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(listOf(hotwordChannel, syncChannel))
        }
    }

    fun createHotwordNotification(message: String = "Listening for hotword...") =
        NotificationCompat.Builder(context, HOTWORD_CHANNEL_ID)
            .setContentTitle("Cogniscribe Active")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun createSyncNotification(message: String) =
        NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setContentTitle("Syncing Data")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun updateHotwordNotification(message: String) {
        val notification = createHotwordNotification(message)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(HOTWORD_NOTIFICATION_ID, notification)
    }

    fun updateSyncNotification(message: String) {
        val notification = createSyncNotification(message)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(SYNC_NOTIFICATION_ID, notification)
    }

    fun cancelSyncNotification() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(SYNC_NOTIFICATION_ID)
    }
}
