package com.example.diaensho.data.db.dao

import androidx.room.*
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AppUsageStatDao {
    @Query("SELECT * FROM app_usage_stats WHERE date = :date ORDER BY totalTimeInForeground DESC")
    fun getUsageStatsForDate(date: LocalDate): Flow<List<AppUsageStatEntity>>

    @Query("SELECT * FROM app_usage_stats WHERE isSynced = 0")
    suspend fun getUnsyncedStats(): List<AppUsageStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stat: AppUsageStatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stats: List<AppUsageStatEntity>)

    @Update
    suspend fun update(stat: AppUsageStatEntity)

    @Delete
    suspend fun delete(stat: AppUsageStatEntity)

    @Query("UPDATE app_usage_stats SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM app_usage_stats WHERE date < :cutoffDate")
    suspend fun deleteOldStats(cutoffDate: LocalDate)
}
