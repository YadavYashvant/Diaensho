package com.example.diaensho.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "app_usage_stats")
data class AppUsageStatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val totalTimeInForeground: Long,
    val date: LocalDate,
    val isSynced: Boolean = false
)
