package com.cleanx.lcx.feature.auth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class SignInResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val user: AuthUser? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
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

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(@Body request: RefreshTokenRequest): Response<SignInResponse>
}

@Serializable
data class DeviceBranchesResponse(
    val data: List<String> = emptyList(),
)

@Serializable
data class DeviceOperatorsResponse(
    val data: List<DeviceOperatorResponse> = emptyList(),
)

@Serializable
data class DeviceOperatorResponse(
    val id: String,
    val fullName: String,
    val branch: String,
    val hasPin: Boolean = false,
)

@Serializable
data class DevicePinSignInRequest(
    val profileId: String,
    val branch: String,
    val pin: String,
)

@Serializable
data class DeviceGuestSignInRequest(
    val branch: String,
    val code: String,
)

@Serializable
data class DeviceAuthSessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("token_type") val tokenType: String = "bearer",
    val profile: DeviceAuthProfileResponse,
)

@Serializable
data class DeviceAuthProfileResponse(
    val id: String,
    val fullName: String,
    val role: String,
    val branch: String? = null,
)

interface DeviceAuthApi {
    @GET("api/device-auth/branches")
    suspend fun branches(): Response<DeviceBranchesResponse>

    @GET("api/device-auth/operators")
    suspend fun operators(
        @Header("X-LCX-Device-Auth-Token") deviceAuthToken: String,
        @Query("branch") branch: String,
    ): Response<DeviceOperatorsResponse>

    @POST("api/device-auth/pin")
    suspend fun signInWithPin(@Body request: DevicePinSignInRequest): Response<DeviceAuthSessionResponse>

    @POST("api/device-auth/guest")
    suspend fun signInGuest(@Body request: DeviceGuestSignInRequest): Response<DeviceAuthSessionResponse>
}
