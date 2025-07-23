package com.example.diaensho.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DailySummaryDto(
    @Json(name = "date") val date: String,
    @Json(name = "diaryEntries") val diaryEntries: List<DiaryEntryDto>,
    @Json(name = "appUsageStats") val appUsageStats: List<AppUsageStatDto>,
    @Json(name = "totalUsageTime") val totalUsageTime: Long,
    @Json(name = "mostUsedApps") val mostUsedApps: List<String>
)
