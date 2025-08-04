package com.example.diaensho.data.db.dao

import androidx.room.*
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DiaryEntryDao {
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEntriesBetween(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE isSynced = 0")
    suspend fun getUnsyncedEntries(): List<DiaryEntryEntity>

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<DiaryEntryEntity>)

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Delete
    suspend fun delete(entry: DiaryEntryEntity)

    @Query("UPDATE diary_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
