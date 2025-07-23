package com.example.diaensho

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.service.HotwordDetectionService
import com.example.diaensho.ui.screen.*
import com.example.diaensho.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

sealed class Screen {
    object Onboarding : Screen()
    object Home : Screen()
    object Search : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var hotwordServiceIntent: Intent? = null

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hotwordServiceIntent = Intent(this, HotwordDetectionService::class.java)

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Onboarding) }
            val onboardingViewModel: OnboardingViewModel = viewModel()
            var selectedDate by remember { mutableStateOf(LocalDate.now()) }

            // Permission launchers
            val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { }

            val requestMicPermissionLauncher = rememberActivityResultLauncher(
                onboardingViewModel = onboardingViewModel
            )

            // Check notification permission on launch for Android 13+
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasNotificationPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasNotificationPermission) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Check permissions after activity resume
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        checkInitialPermissions(onboardingViewModel)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Monitor permission state changes
            val uiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()

            // Start/stop service based on permissions
            LaunchedEffect(
                uiState.microphonePermissionGranted,
                uiState.usageStatsPermissionGranted,
                uiState.batteryOptimizationDisabled
            ) {
                handleServiceLifecycle(
                    micPermissionGranted = uiState.microphonePermissionGranted,
                    usageStatsPermissionGranted = uiState.usageStatsPermissionGranted,
                    batteryOptimizationDisabled = uiState.batteryOptimizationDisabled
                )
            }

            Scaffold(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    Screen.Onboarding -> OnboardingScreen(
                        onboardingViewModel = onboardingViewModel,
                        onPermissionsGranted = { currentScreen = Screen.Home },
                        onRequestMicPermission = {
                            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRequestUsageStatsPermission = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )
                    Screen.Home -> HomeScreen(
                        selectedDate = selectedDate,
                        onDateChange = { selectedDate = it },
                        onSearchClick = { currentScreen = Screen.Search }
                    )
                    Screen.Search -> SearchScreen(
                        onBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }

    private fun checkInitialPermissions(viewModel: OnboardingViewModel) {
        viewModel.setMicrophonePermission(checkMicrophonePermission())
        viewModel.setUsageStatsPermission(checkUsageStatsPermission())
        viewModel.checkBatteryOptimization()
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun handleServiceLifecycle(
        micPermissionGranted: Boolean,
        usageStatsPermissionGranted: Boolean,
        batteryOptimizationDisabled: Boolean
    ) {
        if (micPermissionGranted && usageStatsPermissionGranted && batteryOptimizationDisabled) {
            startHotwordService()
        } else {
            stopHotwordService()
        }
    }

    private fun startHotwordService() {
        hotwordServiceIntent?.let { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun stopHotwordService() {
        hotwordServiceIntent?.let { stopService(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHotwordService()
    }
}

@Composable
private fun rememberActivityResultLauncher(
    onboardingViewModel: OnboardingViewModel
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted ->
        onboardingViewModel.setMicrophonePermission(granted)
    }
)
