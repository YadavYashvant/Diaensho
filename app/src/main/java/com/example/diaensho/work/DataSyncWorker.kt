package com.example.diaensho.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.diaensho.data.repository.MainRepository
import com.example.diaensho.data.repository.AuthRepository
import com.example.diaensho.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MainRepository,
    private val authRepository: AuthRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DataSyncWorker"
        private const val SYNC_TIMEOUT_MINUTES = 10L
    }

    override suspend fun doWork(): Result = coroutineScope {
        try {
            // Check if user is authenticated before attempting sync
            if (!authRepository.isLoggedIn()) {
                Log.w(TAG, "User not authenticated - skipping sync")
                notificationHelper.updateSyncNotification("Sync skipped - please sign in")
                return@coroutineScope Result.success()
            }

            notificationHelper.updateSyncNotification("Starting data synchronization...")

            withTimeout(TimeUnit.MINUTES.toMillis(SYNC_TIMEOUT_MINUTES)) {
                // Run both sync operations concurrently with proper error handling
                val entriesSyncDeferred = async {
                    try {
                        notificationHelper.updateSyncNotification("Syncing diary entries...")
                        repository.syncUnsyncedEntries()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync diary entries", e)

                        // Handle authentication errors specifically
                        if (e.message?.contains("Authentication required") == true) {
                            Log.w(TAG, "Authentication expired during sync")
                            authRepository.signOut() // Clear invalid auth data
                            notificationHelper.updateSyncNotification("Authentication expired - please sign in again")
                            return@async false
                        }

                        notificationHelper.updateSyncNotification("Failed to sync diary entries")
                        false
                    }
                }

                val statsSyncDeferred = async {
                    try {
                        notificationHelper.updateSyncNotification("Syncing app usage stats...")
                        repository.syncUnsyncedStats()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync app usage stats", e)

                        // Handle authentication errors specifically
                        if (e.message?.contains("Authentication required") == true) {
                            Log.w(TAG, "Authentication expired during sync")
                            authRepository.signOut() // Clear invalid auth data
                            notificationHelper.updateSyncNotification("Authentication expired - please sign in again")
                            return@async false
                        }

                        notificationHelper.updateSyncNotification("Failed to sync app usage stats")
                        false
                    }
                }

                // Wait for both operations to complete
                val entriesSynced = entriesSyncDeferred.await()
                val statsSynced = statsSyncDeferred.await()

                // Return success only if both operations succeeded
                if (entriesSynced && statsSynced) {
                    notificationHelper.updateSyncNotification("Sync completed successfully")
                    Result.success()
                } else if (entriesSynced || statsSynced) {
                    // Partial success - some data synced
                    notificationHelper.updateSyncNotification("Sync partially completed")
                    Result.success()
                } else {
                    notificationHelper.updateSyncNotification("Sync failed, will retry later")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync operation failed or timed out", e)
            notificationHelper.updateSyncNotification("Sync operation failed or timed out")
            Result.retry()
        } finally {
            // Clear the sync notification after a delay
            kotlinx.coroutines.delay(3000)
            notificationHelper.cancelSyncNotification()
        }
    }
}
