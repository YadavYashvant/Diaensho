package com.example.diaensho.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiaryEntryDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "text") val text: String,
    @Json(name = "timestamp") val timestamp: String
)

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
