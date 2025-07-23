package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaensho.data.repository.MainRepository
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {

    data class SearchState(
        val query: String = "",
        val isLoading: Boolean = false,
        val results: List<DiaryEntryEntity> = emptyList(),
        val error: String? = null
    )

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var searchJob: Job? = null
    private val searchDebounceTime = 300L // milliseconds

    fun search(query: String) {
        searchJob?.cancel()
        _searchState.update { it.copy(query = query, isLoading = true, error = null) }

        if (query.isBlank()) {
            _searchState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        searchJob = viewModelScope.launch {
            try {
                delay(searchDebounceTime) // Debounce search input

                val endDate = LocalDate.now()
                val startDate = endDate.minus(30, ChronoUnit.DAYS)

                repository.searchEntries(query, startDate, endDate)
                    .catch { e ->
                        _searchState.update {
                            it.copy(
                                error = "Search failed: ${e.localizedMessage}",
                                isLoading = false
                            )
                        }
                    }
                    .collect { results ->
                        _searchState.update {
                            it.copy(
                                results = results,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                _searchState.update {
                    it.copy(
                        error = "An unexpected error occurred",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchState.update { SearchState() }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
