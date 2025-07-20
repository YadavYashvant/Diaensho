package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel : ViewModel() {
    data class UiState(
        val microphonePermissionGranted: Boolean = false,
        val usageStatsPermissionGranted: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun setMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(microphonePermissionGranted = granted) }
    }

    fun setUsageStatsPermission(granted: Boolean) {
        _uiState.update { it.copy(usageStatsPermissionGranted = granted) }
    }
} 