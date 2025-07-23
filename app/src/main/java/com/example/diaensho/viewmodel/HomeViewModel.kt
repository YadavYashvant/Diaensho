package com.example.diaensho.viewmodel

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
        loadDataForDate(currentDate)
    }

    fun loadDataForDate(date: LocalDate) {
        currentDate = date
        viewModelScope.launch {
            // Load diary entries
            repository.getDiaryEntriesForDate(date).collect {
                _entries.value = it
            }
        }

        viewModelScope.launch {
            // Load app usage stats
            repository.getAppUsageStatsForDate(date).collect {
                _appUsageStats.value = it.sortedByDescending { stat -> stat.totalTimeInForeground }
            }
        }
    }
}
