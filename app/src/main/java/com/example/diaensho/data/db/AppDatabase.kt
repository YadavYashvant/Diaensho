package com.example.diaensho.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.diaensho.data.db.dao.AppUsageStatDao
import com.example.diaensho.data.db.dao.DiaryEntryDao
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.db.entity.DiaryEntryEntity

@Database(
    entities = [DiaryEntryEntity::class, AppUsageStatEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun appUsageStatDao(): AppUsageStatDao
}
