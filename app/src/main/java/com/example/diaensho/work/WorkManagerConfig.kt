package com.example.diaensho.work

import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerConfig @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "WorkManagerConfig"
        private const val PERIODIC_SYNC_WORK_NAME = "periodic_data_sync"
        private const val APP_USAGE_TRACKING_WORK_NAME = "app_usage_tracking"
    }

    private var isPeriodicWorkInitialized = false

    fun setupPeriodicWork() {
        if (isPeriodicWorkInitialized) {
            Log.d(TAG, "Periodic work already initialized")
            return
        }

        try {
            // Setup data sync work (every 6 hours)
            val syncConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncWork = PeriodicWorkRequestBuilder<DataSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(syncConstraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            // Setup app usage tracking work (daily)
            val usageConstraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val usageWork = PeriodicWorkRequestBuilder<AppUsageTrackingWorker>(1, TimeUnit.DAYS)
                .setConstraints(usageConstraints)
                .setInitialDelay(1, TimeUnit.HOURS) // Start after 1 hour
                .build()

            // Enqueue both work requests
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWork
            )

            workManager.enqueueUniquePeriodicWork(
                APP_USAGE_TRACKING_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                usageWork
            )

            isPeriodicWorkInitialized = true
            Log.i(TAG, "Periodic work setup completed")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup periodic work", e)
        }
    }

    fun isInitialized(): Boolean = isPeriodicWorkInitialized

    fun cancelAllWork() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        workManager.cancelUniqueWork(APP_USAGE_TRACKING_WORK_NAME)
        isPeriodicWorkInitialized = false
        Log.i(TAG, "All periodic work cancelled")
    }
}
