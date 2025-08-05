package com.example.diaensho.di

import com.example.diaensho.data.network.AuthApiService
import com.example.diaensho.data.network.DiaryApiService
import com.example.diaensho.data.preferences.AuthPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(authPreferences: AuthPreferences): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val token = authPreferences.getFormattedAuthToken()

            val newRequest = if (token != null && !originalRequest.url.encodedPath.contains("/auth/")) {
                originalRequest.newBuilder()
                    .header("Authorization", token)
                    .build()
            } else {
                originalRequest
            }

            chain.proceed(newRequest)
        }
    }

    @Provides
    @Singleton
    @Named("authenticated")
    fun provideAuthenticatedOkHttpClient(
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("unauthenticated")
    fun provideUnauthenticatedOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    @Named("authenticated")
    fun provideAuthenticatedRetrofit(
        @Named("authenticated") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://diaensho-backend.onrender.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @Named("unauthenticated")
    fun provideUnauthenticatedRetrofit(
        @Named("unauthenticated") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://diaensho-backend.onrender.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(@Named("unauthenticated") retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDiaryApiService(@Named("authenticated") retrofit: Retrofit): DiaryApiService {
        return retrofit.create(DiaryApiService::class.java)
    }
}
