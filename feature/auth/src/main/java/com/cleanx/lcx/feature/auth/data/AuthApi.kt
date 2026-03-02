package com.cleanx.lcx.feature.auth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class SignInResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("user_id") val userId: String? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
data class AuthErrorResponse(
    val error: String? = null,
    val message: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

interface AuthApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body request: SignInRequest): Response<SignInResponse>
}
