package com.example.diaensho.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.viewmodel.OnboardingViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onboardingViewModel: OnboardingViewModel = viewModel(),
    onPermissionsGranted: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestUsageStatsPermission: () -> Unit
) {
    val uiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Cogniscribe! Please grant the following permissions:")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestMicPermission) {
            Text(if (uiState.microphonePermissionGranted) "Microphone Granted" else "Grant Microphone Permission")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestUsageStatsPermission) {
            Text(if (uiState.usageStatsPermissionGranted) "Usage Stats Granted" else "Grant Usage Stats Permission")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPermissionsGranted,
            enabled = uiState.microphonePermissionGranted && uiState.usageStatsPermissionGranted
        ) {
            Text("Continue")
        }
    }
} 