package com.example.diaensho.data.network

import com.example.diaensho.data.network.model.DiaryEntryDto
import retrofit2.http.*

interface DiaryApiService {
    @POST("entries")
    suspend fun createEntry(@Body entry: DiaryEntryDto): DiaryEntryDto

    @POST("usage-stats")
    suspend fun uploadUsageStats(@Body stats: List<AppUsageStatDto>): List<AppUsageStatDto>

    @GET("summaries")
    suspend fun getDailySummary(@Query("date") date: String): DailySummaryDto

    @GET("search")
    suspend fun searchEntries(@Query("query") query: String): List<DiaryEntryDto>
}

data class AppUsageStatDto(
    val packageName: String,
    val totalTimeInForeground: Long,
    val date: String
)

data class DailySummaryDto(
    val date: String,
    val narrative: String,
    val highlights: List<String>
)
