package com.example.diaensho.data.repository

import android.app.usage.UsageStatsManager
import com.example.diaensho.data.db.dao.AppUsageStatDao
import com.example.diaensho.data.db.dao.DiaryEntryDao
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import com.example.diaensho.data.network.DiaryApiService
import com.example.diaensho.data.network.model.DiaryEntryDto
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainRepository @Inject constructor(
    private val diaryEntryDao: DiaryEntryDao,
    private val appUsageStatDao: AppUsageStatDao,
    private val apiService: DiaryApiService
) {
    // Diary Entry Operations
    suspend fun addDiaryEntry(text: String) {
        val entry = DiaryEntryEntity(
            text = text,
            timestamp = LocalDateTime.now()
        )
        diaryEntryDao.insert(entry)
    }

    fun getDiaryEntries(): Flow<List<DiaryEntryEntity>> {
        return diaryEntryDao.getAllEntries()
    }

    fun getDiaryEntriesForDate(date: LocalDate): Flow<List<DiaryEntryEntity>> {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay()
        return diaryEntryDao.getEntriesBetween(startOfDay, endOfDay)
    }

    // App Usage Stats Operations
    suspend fun saveAppUsageStats(stats: List<AppUsageStatEntity>) {
        appUsageStatDao.insertAll(stats)
    }

    fun getAppUsageStatsForDate(date: LocalDate): Flow<List<AppUsageStatEntity>> {
        return appUsageStatDao.getUsageStatsForDate(date)
    }

    // Synchronization Operations
    suspend fun syncUnsyncedEntries() {
        val unsyncedEntries = diaryEntryDao.getUnsyncedEntries()
        if (unsyncedEntries.isNotEmpty()) {
            val dtos = unsyncedEntries.map { entity ->
                DiaryEntryDto(
                    text = entity.text,
                    timestamp = entity.timestamp.toString()
                )
            }

            try {
                dtos.forEach { dto ->
                    apiService.createEntry(dto)
                }
                diaryEntryDao.markAsSynced(unsyncedEntries.map { it.id })
            } catch (e: Exception) {
                // Handle network errors
            }
        }
    }

    suspend fun syncUnsyncedStats() {
        val unsyncedStats = appUsageStatDao.getUnsyncedStats()
        if (unsyncedStats.isNotEmpty()) {
            try {
                apiService.uploadUsageStats(
                    unsyncedStats.map {
                        DiaryApiService.AppUsageStatDto(
                            packageName = it.packageName,
                            totalTimeInForeground = it.totalTimeInForeground,
                            date = it.date.toString()
                        )
                    }
                )
                appUsageStatDao.markAsSynced(unsyncedStats.map { it.id })
            } catch (e: Exception) {
                // Handle network errors
            }
        }
    }
}
