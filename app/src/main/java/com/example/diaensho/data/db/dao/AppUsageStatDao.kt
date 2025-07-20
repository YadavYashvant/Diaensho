package com.example.diaensho.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AppUsageStatDao {
    @Query("SELECT * FROM app_usage_stats WHERE date = :date")
    fun getUsageStatsForDate(date: LocalDate): Flow<List<AppUsageStatEntity>>

    @Query("SELECT * FROM app_usage_stats WHERE isSynced = 0")
    suspend fun getUnsyncedStats(): List<AppUsageStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stats: List<AppUsageStatEntity>)

    @Query("UPDATE app_usage_stats SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
