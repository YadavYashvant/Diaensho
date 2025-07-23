package com.example.diaensho.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUsageStatDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "packageName") val packageName: String,
    @Json(name = "totalTimeInForeground") val totalTimeInForeground: Long,
    @Json(name = "date") val date: String,
    @Json(name = "isSynced") val isSynced: Boolean = false
)
