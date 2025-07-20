package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaensho.data.repository.MainRepository
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {
    private val _results = MutableStateFlow<List<DiaryEntryEntity>>(emptyList())
    val results: StateFlow<List<DiaryEntryEntity>> = _results.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            // Replace with actual repository search implementation
            val allEntries = repository.getDiaryEntries() // Should be a Flow
            allEntries.collect { entries ->
                _results.value = entries.filter { it.text.contains(query, ignoreCase = true) }
            }
        }
    }
} 