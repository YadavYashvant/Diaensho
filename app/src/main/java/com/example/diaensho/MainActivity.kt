package com.example.diaensho

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.diaensho.service.HotwordDetectionService
import com.example.diaensho.ui.screen.*
import com.example.diaensho.ui.theme.DiaenshoTheme
import com.example.diaensho.viewmodel.AuthViewModel
import com.example.diaensho.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestMicrophonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onboardingViewModel?.setMicrophonePermission(isGranted)
    }

    private val requestUsageStatsPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onboardingViewModel?.setUsageStatsPermission(hasUsageStatsPermission())
    }

    private var onboardingViewModel: OnboardingViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DiaenshoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }

    @Composable
    private fun MainApp() {
        val navController = rememberNavController()
        val authViewModel: AuthViewModel = hiltViewModel()
        val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

        var selectedDate by remember { mutableStateOf(LocalDate.now()) }

        // Determine start destination based on auth state
        val startDestination = when {
            !authUiState.isSignedIn -> "signin"
            !authUiState.isOnboardingCompleted -> "onboarding"
            else -> "home"
        }

        LaunchedEffect(authUiState.isSignedIn, authUiState.isOnboardingCompleted) {
            when {
                !authUiState.isSignedIn -> {
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
                !authUiState.isOnboardingCompleted -> {
                    navController.navigate("onboarding") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
                else -> {
                    navController.navigate("home") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                    // Start the hotword detection service when fully authenticated and onboarded
                    startHotwordService()
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("signin") {
                SignInScreen(
                    authViewModel = authViewModel,
                    onSignInSuccess = {
                        if (authUiState.isOnboardingCompleted) {
                            navController.navigate("home") {
                                popUpTo("signin") { inclusive = true }
                            }
                        } else {
                            navController.navigate("onboarding") {
                                popUpTo("signin") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToSignUp = {
                        navController.navigate("signup")
                    }
                )
            }

            composable("signup") {
                SignUpScreen(
                    authViewModel = authViewModel,
                    onSignUpSuccess = {
                        navController.navigate("onboarding") {
                            popUpTo("signup") { inclusive = true }
                        }
                    },
                    onNavigateToSignIn = {
                        navController.popBackStack()
                    }
                )
            }

            composable("onboarding") {
                val onboardingVM: OnboardingViewModel = hiltViewModel()

                // Store reference for permission callbacks
                onboardingViewModel = onboardingVM

                OnboardingScreen(
                    onboardingViewModel = onboardingVM,
                    onPermissionsGranted = {
                        authViewModel.completeOnboarding()
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onRequestMicPermission = {
                        requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestUsageStatsPermission = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        requestUsageStatsPermission.launch(intent)
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    homeViewModel = hiltViewModel(),
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    onSearchClick = {
                        navController.navigate("search")
                    }
                )
            }

            composable("search") {
                SearchScreen(
                    searchViewModel = hiltViewModel(),
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun startHotwordService() {
        if (hasMicrophonePermission()) {
            val serviceIntent = Intent(this, HotwordDetectionService::class.java)
            startForegroundService(serviceIntent)
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        // Update onboarding permissions when returning to the app
        onboardingViewModel?.let { vm ->
            vm.setMicrophonePermission(hasMicrophonePermission())
            vm.setUsageStatsPermission(hasUsageStatsPermission())
            vm.checkBatteryOptimization()
        }
    }
}
