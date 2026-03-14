package com.cleanx.lcx.feature.auth.data

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.session.SessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Base64
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ProfileRow(
    @SerialName("role") val role: String,
)

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
    private val supabaseClient: SupabaseClient,
    private val config: BuildConfigProvider,
    private val json: Json,
) {

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val response = authApi.signIn(SignInRequest(email = email, password = password))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val userId = body.user?.id ?: body.userId ?: ""
                    val userEmail = body.user?.email?.takeIf { it.isNotBlank() } ?: email
                    sessionManager.saveAccessToken(body.accessToken)
                    sessionManager.saveUserEmail(userEmail)

                    // Fetch and persist the user role from the profiles table.
                    fetchAndSaveUserRole(userId)

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
        val token = sessionManager.getAccessToken()
        if (token.isNullOrBlank()) return false

        val tokenIssuerHost = token.issuerHostOrNull()
        val configuredHost = runCatching { URI(config.supabaseUrl).host.orEmpty() }
            .getOrNull()
            .orEmpty()
        if (configuredHost.isBlank()) {
            Timber.tag("AUTH").w("Invalid supabase host in config. Forcing re-login.")
            return false
        }

        // JWT format with no parseable issuer is treated as invalid/stale.
        if (token.count { it == '.' } >= 2 && tokenIssuerHost == null) {
            Timber.tag("AUTH").w("JWT without parseable issuer. Forcing re-login.")
            return false
        }

        if (tokenIssuerHost != null && !hostsMatchOrEquivalent(tokenIssuerHost, configuredHost)) {
            Timber.tag("AUTH").w(
                "Token issuer mismatch; tokenHost=%s configuredHost=%s. Forcing re-login.",
                tokenIssuerHost,
                configuredHost,
            )
            return false
        }

        return true
    }

    fun getCurrentUserId(): String? {
        // For now we don't persist userId separately; the token is the proof.
        // A future enhancement could decode the JWT to extract sub.
        return if (isAuthenticated()) "authenticated" else null
    }

    fun getUserRole(): UserRole? = sessionManager.getUserRole()

    private suspend fun fetchAndSaveUserRole(userId: String) {
        try {
            val profile = supabaseClient.postgrest["profiles"]
                .select { filter { eq("id", userId) } }
                .decodeSingle<ProfileRow>()

            val role = UserRole.entries.firstOrNull {
                it.name.equals(profile.role, ignoreCase = true)
            } ?: UserRole.EMPLOYEE

            sessionManager.saveUserRole(role)
            Timber.i("Auth: user role = %s", role)
        } catch (e: Exception) {
            // Default to employee if profile fetch fails — non-blocking.
            Timber.w(e, "Auth: failed to fetch user role, defaulting to EMPLOYEE")
            sessionManager.saveUserRole(UserRole.EMPLOYEE)
        }
    }
}

private fun hostsMatchOrEquivalent(left: String, right: String): Boolean {
    return canonicalHost(left) == canonicalHost(right)
}

private fun canonicalHost(host: String): String {
    return when (host.lowercase()) {
        "127.0.0.1",
        "localhost",
        "10.0.2.2",
        "::1",
        "[::1]" -> "loopback"
        else -> host.lowercase()
    }
}

private fun String.issuerHostOrNull(): String? {
    return runCatching {
        val parts = split('.')
        if (parts.size < 2) return null
        val payloadBase64Url = parts[1]
        val padded = payloadBase64Url + "=".repeat((4 - payloadBase64Url.length % 4) % 4)
        val payloadJson = String(Base64.getUrlDecoder().decode(padded))
        val iss = Regex("\"iss\"\\s*:\\s*\"([^\"]+)\"")
            .find(payloadJson)
            ?.groupValues
            ?.getOrNull(1)
        iss?.let { runCatching { URI(it).host }.getOrNull() }
    }.getOrNull()
}
