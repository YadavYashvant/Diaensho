package com.example.diaensho.data.repository

import android.util.Log
import com.example.diaensho.data.network.AuthApiService
import com.example.diaensho.data.network.model.*
import com.example.diaensho.data.preferences.AuthPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val authPreferences: AuthPreferences
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    suspend fun signUp(username: String, email: String, password: String): Flow<Result<AuthResponse>> = flow {
        emit(Result.Loading())
        try {
            val request = SignUpRequest(username, email, password)

            // The backend only returns a token, so we create a simple response
            val tokenResponse = authApiService.signUp(request)

            // Extract the token from the response JSON manually since backend format differs
            val token = when (tokenResponse) {
                is Map<*, *> -> tokenResponse["token"] as? String
                else -> null
            } ?: throw Exception("No token received from server")

            // Save the token
            authPreferences.saveAuthToken(token)

            // Create a dummy user since backend doesn't return user info
            // We'll extract username from the request for now
            val user = UserDto(
                id = 1L, // Dummy ID
                username = username,
                email = email
            )

            // Save user info locally
            authPreferences.saveUserInfo(user.id, user.username, user.email)

            val authResponse = AuthResponse(token = token, user = user)
            emit(Result.Success(authResponse))

        } catch (e: HttpException) {
            Log.e(TAG, "Sign up HTTP error: ${e.code()} - ${e.message()}")
            val errorMessage = when (e.code()) {
                400 -> "Invalid signup data. Please check your input."
                409 -> "An account with this email already exists."
                422 -> "Please check your email format and password requirements."
                500 -> "Server error. Please try again later."
                else -> "Signup failed: ${e.message()}"
            }
            emit(Result.Error(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            emit(Result.Error("Sign up failed: ${e.message}"))
        }
    }

    suspend fun signIn(email: String, password: String): Flow<Result<AuthResponse>> = flow {
        emit(Result.Loading())
        try {
            Log.d(TAG, "Attempting sign in for email: $email")

            // Since backend expects username but we collect email, we'll use email as username
            // This is a common pattern where email serves as the username
            val request = SignInRequest(username = email, password = password)

            // The backend only returns a token, so we create a simple response
            val tokenResponse = authApiService.signIn(request)
            Log.d(TAG, "Received token response: $tokenResponse")

            // Extract the token from the response JSON manually since backend format differs
            val token = when (tokenResponse) {
                is Map<*, *> -> tokenResponse["token"] as? String
                else -> null
            } ?: throw Exception("No token received from server")

            Log.i(TAG, "Successfully received token, saving locally")

            // Save the token
            authPreferences.saveAuthToken(token)

            // Create basic user info from email since backend doesn't provide it during signin
            val username = email.substringBefore("@")
            val user = UserDto(
                id = 1L, // Dummy ID - we'll get real ID from verification if available
                username = email, // Use full email as username to match backend
                email = email
            )

            // Save user info locally
            authPreferences.saveUserInfo(user.id, user.username, user.email)

            val authResponse = AuthResponse(token = token, user = user)
            emit(Result.Success(authResponse))

        } catch (e: HttpException) {
            Log.e(TAG, "Sign in HTTP error: ${e.code()} - ${e.message()}")

            // Clear any existing token since signin failed
            authPreferences.clearAuthData()

            val errorMessage = when (e.code()) {
                400 -> "Please check your email and password."
                401 -> "Invalid email or password. Please try again."
                403 -> "Account access denied. This could mean:\n• Wrong password\n• Account not found\n• Please check your credentials or try signing up again."
                404 -> "Account not found. Please sign up first."
                429 -> "Too many login attempts. Please try again later."
                500 -> "Server error. Please try again later."
                else -> "Sign in failed: HTTP ${e.code()}"
            }
            emit(Result.Error(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            authPreferences.clearAuthData()
            emit(Result.Error("Sign in failed: ${e.message}"))
        }
    }

    suspend fun verifyToken(): Flow<Result<UserDto>> = flow {
        emit(Result.Loading())
        try {
            val token = authPreferences.getFormattedAuthToken()
            if (token == null) {
                emit(Result.Error("No token available"))
                return@flow
            }

            val user = authApiService.verifyToken(token)
            authPreferences.saveUserInfo(user.id, user.username, user.email)
            emit(Result.Success(user))

        } catch (e: HttpException) {
            Log.e(TAG, "Token verification failed with HTTP ${e.code()}")
            // Clear invalid token
            authPreferences.clearAuthData()
            emit(Result.Error("Token verification failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Token verification failed", e)
            // Clear invalid token
            authPreferences.clearAuthData()
            emit(Result.Error("Token verification failed: ${e.message}"))
        }
    }

    fun getCurrentUser(): UserDto? {
        val userId = authPreferences.getUserId()
        val username = authPreferences.getUsername()
        val email = authPreferences.getEmail()

        return if (userId != -1L && username != null && email != null) {
            UserDto(id = userId, username = username, email = email)
        } else {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        val hasToken = authPreferences.getAuthToken() != null
        val hasUserInfo = getCurrentUser() != null
        val isLoggedInFlag = authPreferences.isLoggedIn()

        Log.d(TAG, "Auth status - hasToken: $hasToken, hasUserInfo: $hasUserInfo, isLoggedInFlag: $isLoggedInFlag")

        return hasToken && hasUserInfo && isLoggedInFlag
    }

    fun isOnboardingCompleted(): Boolean {
        return authPreferences.isOnboardingCompleted()
    }

    fun setOnboardingCompleted() {
        authPreferences.setOnboardingCompleted(true)
    }

    fun signOut() {
        Log.i(TAG, "Signing out user")
        authPreferences.clearAuthData()
    }
}

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    class Loading : Result<Nothing>()
}
