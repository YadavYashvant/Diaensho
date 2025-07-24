package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import com.example.diaensho.util.PowerManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val powerManagerHelper: PowerManagerHelper
) : ViewModel() {
    data class UiState(
        val microphonePermissionGranted: Boolean = false,
        val usageStatsPermissionGranted: Boolean = false,
        val batteryOptimizationDisabled: Boolean = false,
        val allPermissionsGranted: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        checkBatteryOptimization()
    }

    fun setMicrophonePermission(granted: Boolean) {
        _uiState.update {
            it.copy(
                microphonePermissionGranted = granted,
                allPermissionsGranted = granted && it.usageStatsPermissionGranted && it.batteryOptimizationDisabled
            )
        }
    }

    fun setUsageStatsPermission(granted: Boolean) {
        _uiState.update {
            it.copy(
                usageStatsPermissionGranted = granted,
                allPermissionsGranted = granted && it.microphonePermissionGranted && it.batteryOptimizationDisabled
            )
        }
    }

    fun checkBatteryOptimization() {
        val isIgnoringBatteryOptimizations = powerManagerHelper.isIgnoringBatteryOptimizations()
        _uiState.update {
            it.copy(
                batteryOptimizationDisabled = isIgnoringBatteryOptimizations,
                allPermissionsGranted = isIgnoringBatteryOptimizations &&
                    it.microphonePermissionGranted &&
                    it.usageStatsPermissionGranted
            )
        }
    }

    fun getBatteryOptimizationIntent() = powerManagerHelper.getRequestIgnoreBatteryOptimizationsIntent()

}
