package com.example.diaensho.di

import android.content.Context
import androidx.room.Room
import com.example.diaensho.data.db.AppDatabase
import com.example.diaensho.data.db.dao.AppUsageStatDao
import com.example.diaensho.data.db.dao.DiaryEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "diary_database"
        ).build()
    }

    @Provides
    fun provideDiaryEntryDao(database: AppDatabase): DiaryEntryDao {
        return database.diaryEntryDao()
    }

    @Provides
    fun provideAppUsageStatDao(database: AppDatabase): AppUsageStatDao {
        return database.appUsageStatDao()
    }
}
