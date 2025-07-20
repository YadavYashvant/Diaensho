package com.example.diaensho

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.service.HotwordDetectionService
import com.example.diaensho.ui.screen.OnboardingScreen
import com.example.diaensho.ui.screen.HomeScreen
import com.example.diaensho.ui.screen.SearchScreen
import com.example.diaensho.viewmodel.OnboardingViewModel
import java.time.LocalDate

sealed class Screen {
    object Onboarding : Screen()
    object Home : Screen()
    object Search : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Onboarding) }
            val onboardingViewModel: OnboardingViewModel = viewModel()
            var selectedDate by remember { mutableStateOf(LocalDate.now()) }

            // Permission launchers
            val requestMicPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { granted -> onboardingViewModel.setMicrophonePermission(granted) }
            )

            fun checkMicPermission(context: Context): Boolean {
                return ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }

            fun checkUsageStatsPermission(context: Context): Boolean {
                val appOps = ContextCompat.getSystemService(context, android.app.AppOpsManager::class.java)
                val mode = appOps?.checkOpNoThrow(
                    "android:get_usage_stats",
                    android.os.Process.myUid(),
                    context.packageName
                )
                return mode == android.app.AppOpsManager.MODE_ALLOWED
            }

            // Observe permission state
            LaunchedEffect(Unit) {
                onboardingViewModel.setMicrophonePermission(checkMicPermission(this@MainActivity))
                onboardingViewModel.setUsageStatsPermission(checkUsageStatsPermission(this@MainActivity))
            }

            // Start service if permissions granted
            val uiState by onboardingViewModel.uiState.collectAsState()
            LaunchedEffect(uiState) {
                if (uiState.microphonePermissionGranted && uiState.usageStatsPermissionGranted) {
                    val intent = Intent(this@MainActivity, HotwordDetectionService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this@MainActivity.startForegroundService(intent)
                    } else {
                        this@MainActivity.startService(intent)
                    }
                }
            }

            Scaffold(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    is Screen.Onboarding -> OnboardingScreen(
                        onboardingViewModel = onboardingViewModel,
                        onPermissionsGranted = { currentScreen = Screen.Home },
                        onRequestMicPermission = {
                            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRequestUsageStatsPermission = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            startActivity(intent)
                        }
                    )
                    is Screen.Home -> HomeScreen(
                        selectedDate = selectedDate,
                        onDateChange = { selectedDate = it },
                        onSearchClick = { currentScreen = Screen.Search }
                    )
                    is Screen.Search -> SearchScreen(
                        onBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}