package com.example.diaensho.work

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.repository.MainRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class AppUsageTrackingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MainRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val today = LocalDate.now()
            val startTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            val appUsageStats = stats.map { stat ->
                AppUsageStatEntity(
                    packageName = stat.packageName,
                    totalTimeInForeground = stat.totalTimeInForeground,
                    date = today
                )
            }

            repository.saveAppUsageStats(appUsageStats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
