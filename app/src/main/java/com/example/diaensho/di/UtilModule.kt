package com.example.diaensho.di

import android.content.Context
import com.example.diaensho.util.PowerManagerHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {
    @Provides
    @Singleton
    fun providePowerManagerHelper(
        @ApplicationContext context: Context
    ): PowerManagerHelper {
        return PowerManagerHelper(context)
    }
}
