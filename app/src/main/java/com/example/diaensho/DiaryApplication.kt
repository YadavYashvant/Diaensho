package com.example.diaensho

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.diaensho.util.PowerManagerHelper
import com.example.diaensho.work.DataSyncWorker
import com.example.diaensho.work.WorkManagerConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DiaryApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManagerConfig: WorkManagerConfig

    @Inject
    lateinit var powerManagerHelper: PowerManagerHelper

    private val powerSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_SAVE_MODE_CHANGED -> checkAndInitializeWorkManager()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(powerSettingsReceiver, IntentFilter(Intent.ACTION_POWER_SAVE_MODE_CHANGED))
        initializeWorkManager()
    }

    private fun initializeWorkManager() {
        if (powerManagerHelper.isIgnoringBatteryOptimizations()) {
            // If battery optimization is disabled, set up periodic work
            workManagerConfig.setupPeriodicWork()
        } else {
            // Otherwise, only schedule one-time sync and wait for battery optimization to be disabled
            scheduleInitialSync()
        }
    }

    private fun scheduleInitialSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_sync",
            ExistingWorkPolicy.KEEP,
            syncWork
        )
    }

    private fun checkAndInitializeWorkManager() {
        if (powerManagerHelper.isIgnoringBatteryOptimizations() && !workManagerConfig.isInitialized()) {
            workManagerConfig.setupPeriodicWork()
        }
    }

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(powerSettingsReceiver)
    }
}
