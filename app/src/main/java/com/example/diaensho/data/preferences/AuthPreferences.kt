package com.example.diaensho.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAuthToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_JWT_TOKEN, token)
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null)
    }

    fun saveUserInfo(userId: Long, username: String, email: String) {
        sharedPreferences.edit {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
        }
    }

    fun getUserId(): Long {
        return sharedPreferences.getLong(KEY_USER_ID, -1L)
    }

    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }

    fun getEmail(): String? {
        return sharedPreferences.getString(KEY_EMAIL, null)
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false) &&
               getAuthToken() != null
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_ONBOARDING_COMPLETED, completed)
        }
    }

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun clearAuthData() {
        sharedPreferences.edit {
            remove(KEY_JWT_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            putBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun getFormattedAuthToken(): String? {
        return getAuthToken()?.let { "Bearer $it" }
    }
}
