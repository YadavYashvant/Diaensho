package com.example.diaensho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaensho.data.network.model.UserDto
import com.example.diaensho.data.repository.AuthRepository
import com.example.diaensho.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isSignedIn: Boolean = false,
        val currentUser: UserDto? = null,
        val errorMessage: String? = null,
        val isOnboardingCompleted: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        checkAuthenticationStatus()
    }

    private fun checkAuthenticationStatus() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            val isOnboardingCompleted = authRepository.isOnboardingCompleted()
            val currentUser = authRepository.getCurrentUser()

            if (isLoggedIn && currentUser != null) {
                // Verify token with server
                authRepository.verifyToken().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    isSignedIn = true,
                                    currentUser = result.data,
                                    isOnboardingCompleted = isOnboardingCompleted,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        }
                        is Result.Error -> {
                            // Token invalid, clear local data
                            _uiState.update {
                                it.copy(
                                    isSignedIn = false,
                                    currentUser = null,
                                    isOnboardingCompleted = isOnboardingCompleted,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        }
                        is Result.Loading -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = true,
                                    isOnboardingCompleted = isOnboardingCompleted
                                )
                            }
                        }
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSignedIn = false,
                        currentUser = null,
                        isOnboardingCompleted = isOnboardingCompleted,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            authRepository.signIn(email, password).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSignedIn = true,
                                currentUser = result.data.user,
                                errorMessage = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message
                            )
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                errorMessage = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun signUp(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            authRepository.signUp(username, email, password).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSignedIn = true,
                                currentUser = result.data.user,
                                errorMessage = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message
                            )
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                errorMessage = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.update {
            UiState(
                isOnboardingCompleted = it.isOnboardingCompleted
            )
        }
    }

    fun completeOnboarding() {
        authRepository.setOnboardingCompleted()
        _uiState.update {
            it.copy(isOnboardingCompleted = true)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
