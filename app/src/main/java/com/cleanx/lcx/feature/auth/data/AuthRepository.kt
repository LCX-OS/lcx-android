package com.cleanx.lcx.feature.auth.data

import com.cleanx.lcx.core.session.SessionManager
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Supabase authentication via the Next.js-proxied API or directly
 * against the Supabase GoTrue endpoint.
 *
 * Stores the access token in [SessionManager] (DataStore-backed) which also
 * implements [com.cleanx.lcx.core.network.TokenProvider], so the
 * [com.cleanx.lcx.core.network.AuthInterceptor] automatically attaches it
 * to every request.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val json: Json,
) {

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val response = authApi.signIn(SignInRequest(email = email, password = password))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val userId = body.user?.id ?: body.userId ?: ""
                    sessionManager.saveAccessToken(body.accessToken)
                    Timber.i("Auth: signed in user %s", userId)
                    AuthResult.Success(
                        userId = userId,
                        accessToken = body.accessToken,
                    )
                } else {
                    AuthResult.Error("Respuesta vacia del servidor.")
                }
            } else {
                val raw = response.errorBody()?.string()
                val message = try {
                    if (raw.isNullOrBlank()) null
                    else {
                        val err = json.decodeFromString(AuthErrorResponse.serializer(), raw)
                        err.errorDescription ?: err.message ?: err.error
                    }
                } catch (_: Exception) {
                    null
                }
                AuthResult.Error(message ?: "Credenciales invalidas.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Auth: sign-in failed")
            AuthResult.Error(e.message ?: "Error de conexion.")
        }
    }

    suspend fun signOut() {
        sessionManager.clearSession()
        Timber.i("Auth: signed out")
    }

    fun isAuthenticated(): Boolean {
        return !sessionManager.getAccessToken().isNullOrBlank()
    }

    fun getCurrentUserId(): String? {
        // For now we don't persist userId separately; the token is the proof.
        // A future enhancement could decode the JWT to extract sub.
        return if (isAuthenticated()) "authenticated" else null
    }
}
