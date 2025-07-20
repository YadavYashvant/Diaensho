package com.example.diaensho.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.diaensho.data.repository.MainRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MainRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            // Run both sync operations concurrently
            val entriesSyncDeferred = async { repository.syncUnsyncedEntries() }
            val statsSyncDeferred = async { repository.syncUnsyncedStats() }

            // Wait for both sync operations to complete
            entriesSyncDeferred.await()
            statsSyncDeferred.await()

            Result.success()
        } catch (e: Exception) {
            // If sync fails, retry the work
            Result.retry()
        }
    }
}
