package com.example.diaensho.work

import android.content.Context
import androidx.work.*
import com.example.diaensho.data.repository.MainRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerConfig @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MainRepository
) {
    private var isFullyInitialized = false

    fun setupPeriodicWork() {
        setupAppUsageTracking()
        setupDataSync()
        scheduleImmediateSync()
        isFullyInitialized = true
    }

    fun isInitialized() = isFullyInitialized

    fun reinitializeIfNeeded() {
        if (!isFullyInitialized) {
            setupPeriodicWork()
        }
    }

    private fun setupAppUsageTracking() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val usageTrackingRequest = PeriodicWorkRequestBuilder<AppUsageTrackingWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Flex period
        ).setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_USAGE_TRACKING,
            ExistingPeriodicWorkPolicy.UPDATE,
            usageTrackingRequest
        )
    }

    private fun setupDataSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            6, TimeUnit.HOURS,
            1, TimeUnit.HOURS // Flex period
        ).setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_DATA_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    private fun scheduleImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateSync = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_IMMEDIATE_SYNC,
            ExistingWorkPolicy.KEEP,
            immediateSync
        )
    }

    fun requestImmediateSync() {
        scheduleImmediateSync()
    }

    fun cancelAllWork() {
        WorkManager.getInstance(context).cancelAllWork()
        isFullyInitialized = false
    }

    companion object {
        private const val WORK_NAME_USAGE_TRACKING = "usage_tracking"
        private const val WORK_NAME_DATA_SYNC = "data_sync"
        private const val WORK_NAME_IMMEDIATE_SYNC = "immediate_sync"
    }
}
