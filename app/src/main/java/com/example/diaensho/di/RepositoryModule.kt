package com.example.diaensho.di

import com.example.diaensho.data.repository.AuthRepository
import com.example.diaensho.data.repository.MainRepository
import com.example.diaensho.data.network.AuthApiService
import com.example.diaensho.data.network.DiaryApiService
import com.example.diaensho.data.preferences.AuthPreferences
import com.example.diaensho.data.db.dao.AppUsageStatDao
import com.example.diaensho.data.db.dao.DiaryEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApiService: AuthApiService,
        authPreferences: AuthPreferences
    ): AuthRepository {
        return AuthRepository(authApiService, authPreferences)
    }

    @Provides
    @Singleton
    fun provideMainRepository(
        diaryEntryDao: DiaryEntryDao,
        appUsageStatDao: AppUsageStatDao,
        apiService: DiaryApiService
    ): MainRepository {
        return MainRepository(diaryEntryDao, appUsageStatDao, apiService)
    }
}
