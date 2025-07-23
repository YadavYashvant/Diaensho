package com.example.diaensho.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerManagerHelper @Inject constructor(
    private val context: Context
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Diaensho::HotwordDetectionWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes timeout
        }
    }

    fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            // Handle any errors that might occur during release
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getRequestIgnoreBatteryOptimizationsIntent(): Intent {
        return Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
