package com.example.diaensho.work

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.repository.MainRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class AppUsageTrackingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MainRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AppUsageTrackingWorker"
        private const val MIN_USAGE_TIME_MS = 1000L // 1 second minimum
    }

    override suspend fun doWork(): Result {
        return try {
            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val packageManager = applicationContext.packageManager
            val today = LocalDate.now()
            val startTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = System.currentTimeMillis()

            val stats = withContext(Dispatchers.IO) {
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
            }

            // Filter and transform usage stats
            val appUsageStats = stats
                .filter { stat ->
                    // Filter out system apps and apps with minimal usage
                    try {
                        val packageInfo = packageManager.getPackageInfo(stat.packageName, 0)
                        val isSystemApp = (packageInfo.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM)) != 0
                        !isSystemApp && stat.totalTimeInForeground >= MIN_USAGE_TIME_MS
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }
                }
                .map { stat ->
                    AppUsageStatEntity(
                        packageName = stat.packageName,
                        totalTimeInForeground = stat.totalTimeInForeground,
                        date = today
                    )
                }
                .sortedByDescending { it.totalTimeInForeground }

            if (appUsageStats.isNotEmpty()) {
                repository.saveAppUsageStats(appUsageStats)
                Log.d(TAG, "Saved ${appUsageStats.size} app usage stats")
                Result.success()
            } else {
                Log.d(TAG, "No app usage stats to save")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track app usage", e)
            Result.retry()
        }
    }
}
