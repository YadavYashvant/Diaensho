package com.example.diaensho.data.repository

import android.util.Log
import com.example.diaensho.data.db.dao.AppUsageStatDao
import com.example.diaensho.data.db.dao.DiaryEntryDao
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import com.example.diaensho.data.network.DiaryApiService
import com.example.diaensho.data.network.model.AppUsageStatDto
import com.example.diaensho.data.network.model.DailySummaryDto
import com.example.diaensho.data.network.model.DiaryEntryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Result

@Singleton
class MainRepository @Inject constructor(
    private val diaryEntryDao: DiaryEntryDao,
    private val appUsageStatDao: AppUsageStatDao,
    private val apiService: DiaryApiService
) {
    // Diary Entry Operations
    suspend fun addDiaryEntry(text: String) {
        Log.d("MainRepository", "Attempting to save diary entry: '$text'")
        val entry = DiaryEntryEntity(
            text = text,
            timestamp = LocalDateTime.now()
        )
        try {
            val entryId = diaryEntryDao.insert(entry)
            Log.i("MainRepository", "Diary entry saved successfully with ID: $entryId, content: '$text'")
        } catch (e: Exception) {
            Log.e("MainRepository", "Failed to save diary entry: '$text'", e)
            throw e
        }
    }

    fun getDiaryEntries(): Flow<List<DiaryEntryEntity>> {
        return diaryEntryDao.getAllEntries()
    }

    fun getDiaryEntriesForDate(date: LocalDate): Flow<List<DiaryEntryEntity>> {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay()
        return diaryEntryDao.getEntriesBetween(startOfDay, endOfDay)
    }

    fun getDiaryEntriesForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DiaryEntryEntity>> {
        val startDateTime = startDate.atStartOfDay()
        val endDateTime = endDate.plusDays(1).atStartOfDay()
        return diaryEntryDao.getEntriesBetween(startDateTime, endDateTime)
    }

    // App Usage Stats Operations
    suspend fun saveAppUsageStats(stats: List<AppUsageStatEntity>) {
        appUsageStatDao.insertAll(stats)
    }

    fun getAppUsageStatsForDate(date: LocalDate): Flow<List<AppUsageStatEntity>> {
        return appUsageStatDao.getUsageStatsForDate(date)
    }

    // Enhanced Synchronization Operations
    suspend fun syncUnsyncedEntries() {
        val unsyncedEntries = diaryEntryDao.getUnsyncedEntries()
        if (unsyncedEntries.isNotEmpty()) {
            try {
                val dtos = unsyncedEntries.map { entity ->
                    DiaryEntryDto(
                        text = entity.text,
                        timestamp = entity.timestamp.toString()
                    )
                }
                val syncedEntries = dtos.map { dto ->
                    apiService.createEntry(dto)
                }
                diaryEntryDao.markAsSynced(unsyncedEntries.map { it.id })
            } catch (e: Exception) {
                throw RuntimeException("Failed to sync diary entries", e)
            }
        }
    }

    suspend fun syncUnsyncedStats() {
        val unsyncedStats = appUsageStatDao.getUnsyncedStats()
        if (unsyncedStats.isNotEmpty()) {
            try {
                val dtos = unsyncedStats.map { stat ->
                    AppUsageStatDto(
                        packageName = stat.packageName,
                        totalTimeInForeground = stat.totalTimeInForeground,
                        date = stat.date.toString()
                    )
                }
                apiService.uploadUsageStats(dtos)
                appUsageStatDao.markAsSynced(unsyncedStats.map { it.id })
            } catch (e: Exception) {
                throw RuntimeException("Failed to sync usage stats", e)
            }
        }
    }

    // New Daily Summary Operations
    suspend fun getDailySummary(date: LocalDate): Result<DailySummaryDto> {
        return try {
            val summary = apiService.getDailySummary(date.toString())
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun searchEntries(query: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DiaryEntryEntity>> {
        return diaryEntryDao.getEntriesBetween(
            startTime = startDate.atStartOfDay(),
            endTime = endDate.plusDays(1).atStartOfDay()
        ).map { entries ->
            entries.filter { it.text.contains(query, ignoreCase = true) }
        }
    }
}
