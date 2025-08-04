package com.example.diaensho.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DailySummaryDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "date") val date: String,
    @Json(name = "summary") val summary: String,
    @Json(name = "highlights") val highlights: List<String> = emptyList(),
    @Json(name = "mood") val mood: String? = null,
    @Json(name = "wordCount") val wordCount: Int = 0,
    @Json(name = "entryCount") val entryCount: Int = 0
)
