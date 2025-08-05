package com.example.diaensho.data.network

import com.example.diaensho.data.network.model.*
import retrofit2.http.*

interface AuthApiService {
    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Map<String, Any>

    @POST("api/auth/signin")
    suspend fun signIn(@Body request: SignInRequest): Map<String, Any>

    @GET("api/auth/verify")
    suspend fun verifyToken(@Header("Authorization") token: String): UserDto
}
