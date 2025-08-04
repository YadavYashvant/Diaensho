package com.example.diaensho.data.network

import com.example.diaensho.data.network.model.AppUsageStatDto
import com.example.diaensho.data.network.model.DailySummaryDto
import com.example.diaensho.data.network.model.DiaryEntryDto
import retrofit2.http.*

interface DiaryApiService {
    @POST("api/entries")
    suspend fun createEntry(@Body entry: DiaryEntryDto): DiaryEntryDto

    @POST("api/usage-stats")
    suspend fun uploadUsageStats(@Body stats: List<AppUsageStatDto>): List<AppUsageStatDto>

    @POST("api/usage-stats/batch")
    suspend fun uploadBatchUsageStats(@Body stats: List<AppUsageStatDto>): List<AppUsageStatDto>

    @GET("api/summaries")
    suspend fun getDailySummary(@Query("date") date: String): DailySummaryDto

    @GET("api/search")
    suspend fun searchEntries(@Query("q") query: String): List<DiaryEntryDto>
}
