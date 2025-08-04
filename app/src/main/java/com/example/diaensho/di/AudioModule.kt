package com.example.diaensho.di

import android.content.Context
import com.example.diaensho.audio.AudioManager
import com.example.diaensho.audio.SpeechToTextManager
import com.example.diaensho.audio.WakeWordDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    
    @Provides
    @Singleton
    fun provideAudioManager(
        @ApplicationContext context: Context
    ): AudioManager {
        return AudioManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSpeechToTextManager(
        @ApplicationContext context: Context
    ): SpeechToTextManager {
        return SpeechToTextManager(context)
    }
    
    @Provides
    @Singleton
    fun provideWakeWordDetector(
        @ApplicationContext context: Context,
        audioManager: AudioManager
    ): WakeWordDetector {
        return WakeWordDetector(context, audioManager)
    }
}
