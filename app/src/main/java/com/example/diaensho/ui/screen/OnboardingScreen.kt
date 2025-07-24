package com.example.diaensho.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.util.PowerManagerHelper
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Cogniscribe!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Please grant the following permissions to enable voice diary entries:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Microphone Permission
        PermissionButton(
            text = if (uiState.microphonePermissionGranted) "✓ Microphone Granted" else "Grant Microphone Permission",
            onClick = onRequestMicPermission,
            enabled = !uiState.microphonePermissionGranted,
            description = "Required for voice recognition"
        )

        // Usage Stats Permission
        PermissionButton(
            text = if (uiState.usageStatsPermissionGranted) "✓ Usage Stats Granted" else "Grant Usage Stats Permission",
            onClick = onRequestUsageStatsPermission,
            enabled = !uiState.usageStatsPermissionGranted,
            description = "Required for app usage tracking"
        )

        // Battery Optimization Permission
        PermissionButton(
            text = if (uiState.batteryOptimizationDisabled) "✓ Battery Optimization Disabled" else "Disable Battery Optimization",
            onClick = {
                context.startActivity(onboardingViewModel.getBatteryOptimizationIntent())
            },
            enabled = !uiState.batteryOptimizationDisabled,
            description = "Required for reliable voice detection"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPermissionsGranted,
            enabled = uiState.allPermissionsGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }

        if (!uiState.allPermissionsGranted) {
            Text(
                text = "Please grant all permissions to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PermissionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text)
        }
        if (enabled) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
