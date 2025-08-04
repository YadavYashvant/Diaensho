package com.example.diaensho.data.repository

import android.util.Log
import com.example.diaensho.data.network.AuthApiService
import com.example.diaensho.data.network.model.*
import com.example.diaensho.data.preferences.AuthPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
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
        try {
            emit(Result.loading())
            val request = SignUpRequest(username, email, password)
            val response = authApiService.signUp(request)

            // Save auth data
            authPreferences.saveAuthToken(response.token)
            authPreferences.saveUserInfo(response.user.id, response.user.username, response.user.email)

            Log.i(TAG, "Sign up successful for user: ${response.user.username}")
            emit(Result.success(response))
        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                400 -> "Invalid input data"
                409 -> "User already exists"
                else -> "Server error: ${e.message()}"
            }
            Log.e(TAG, "Sign up failed: $errorMessage", e)
            emit(Result.error(errorMessage))
        } catch (e: IOException) {
            Log.e(TAG, "Network error during sign up", e)
            emit(Result.error("Network error. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign up", e)
            emit(Result.error("An unexpected error occurred"))
        }
    }

    suspend fun signIn(email: String, password: String): Flow<Result<AuthResponse>> = flow {
        try {
            emit(Result.loading())
            val request = SignInRequest(email, password)
            val response = authApiService.signIn(request)

            // Save auth data
            authPreferences.saveAuthToken(response.token)
            authPreferences.saveUserInfo(response.user.id, response.user.username, response.user.email)

            Log.i(TAG, "Sign in successful for user: ${response.user.username}")
            emit(Result.success(response))
        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                401 -> "Invalid email or password"
                404 -> "User not found"
                else -> "Server error: ${e.message()}"
            }
            Log.e(TAG, "Sign in failed: $errorMessage", e)
            emit(Result.error(errorMessage))
        } catch (e: IOException) {
            Log.e(TAG, "Network error during sign in", e)
            emit(Result.error("Network error. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign in", e)
            emit(Result.error("An unexpected error occurred"))
        }
    }

    suspend fun verifyToken(): Flow<Result<UserDto>> = flow {
        try {
            val token = authPreferences.getFormattedAuthToken()
            if (token == null) {
                emit(Result.error("No auth token found"))
                return@flow
            }

            emit(Result.loading())
            val user = authApiService.verifyToken(token)

            // Update user info
            authPreferences.saveUserInfo(user.id, user.username, user.email)

            Log.i(TAG, "Token verification successful for user: ${user.username}")
            emit(Result.success(user))
        } catch (e: HttpException) {
            if (e.code() == 401) {
                Log.w(TAG, "Token expired or invalid")
                signOut()
                emit(Result.error("Session expired. Please sign in again."))
            } else {
                Log.e(TAG, "Token verification failed", e)
                emit(Result.error("Verification failed"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during token verification", e)
            emit(Result.error("Network error"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during token verification", e)
            emit(Result.error("Verification failed"))
        }
    }

    fun signOut() {
        Log.i(TAG, "User signed out")
        authPreferences.clearAuthData()
    }

    fun isLoggedIn(): Boolean = authPreferences.isLoggedIn()

    fun getCurrentUser(): UserDto? {
        val userId = authPreferences.getUserId()
        val username = authPreferences.getUsername()
        val email = authPreferences.getEmail()

        return if (userId != -1L && username != null && email != null) {
            UserDto(userId, username, email)
        } else {
            null
        }
    }

    fun isOnboardingCompleted(): Boolean = authPreferences.isOnboardingCompleted()

    fun setOnboardingCompleted() {
        authPreferences.setOnboardingCompleted(true)
    }
}

// Result wrapper class for handling loading, success, and error states
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(message: String): Result<Nothing> = Error(message)
        fun loading(): Result<Nothing> = Loading
    }

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
}
