package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import com.example.diaensho.data.repository.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun loadEntriesForDate(date: LocalDate) {
        viewModelScope.launch {
            repository.getDiaryEntriesForDate(date).collect {
                _entries.value = it
            }
        }
    }
} 