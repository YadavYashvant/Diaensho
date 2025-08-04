package com.example.diaensho.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.repository.MainRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {
    private val _entries = MutableStateFlow<List<DiaryEntryEntity>>(emptyList())
    val entries: StateFlow<List<DiaryEntryEntity>> = _entries.asStateFlow()

    private val _appUsageStats = MutableStateFlow<List<AppUsageStatEntity>>(emptyList())
    val appUsageStats: StateFlow<List<AppUsageStatEntity>> = _appUsageStats.asStateFlow()

    private var currentDate: LocalDate = LocalDate.now()

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel for date: $currentDate")
        loadDataForDate(currentDate)
    }

    fun loadDataForDate(date: LocalDate) {
        Log.d("HomeViewModel", "Loading data for date: $date")
        currentDate = date

        viewModelScope.launch {
            // Load diary entries
            Log.d("HomeViewModel", "Starting to load diary entries for: $date")
            repository.getDiaryEntriesForDate(date).collect { entriesList ->
                Log.d("HomeViewModel", "Received ${entriesList.size} diary entries for $date")
                entriesList.forEach { entry ->
                    Log.v("HomeViewModel", "Entry ID: ${entry.id}, Text: '${entry.text.take(50)}...', Time: ${entry.timestamp}")
                }
                _entries.value = entriesList
            }
        }

        viewModelScope.launch {
            // Load app usage stats
            Log.d("HomeViewModel", "Starting to load app usage stats for: $date")
            repository.getAppUsageStatsForDate(date).collect { statsList ->
                Log.d("HomeViewModel", "Received ${statsList.size} app usage stats for $date")
                _appUsageStats.value = statsList.sortedByDescending { stat -> stat.totalTimeInForeground }
            }
        }
    }
}
