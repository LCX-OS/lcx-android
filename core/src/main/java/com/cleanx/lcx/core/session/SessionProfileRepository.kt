package com.cleanx.lcx.core.session

import com.cleanx.lcx.core.network.SupabaseTableClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class SessionProfile(
    val userId: String,
    val branch: String? = null,
    val fullName: String? = null,
)

@Serializable
private data class ProfileRow(
    val id: String,
    val branch: String? = null,
    @SerialName("full_name") val fullName: String? = null,
)

@Singleton
class SessionProfileRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val supabase: SupabaseTableClient,
) {

    suspend fun getCurrentProfile(): SessionProfile {
        val userId = currentUserIdFromToken()
            ?: throw IllegalStateException("No hay sesion valida para resolver el perfil.")

        val profile = supabase.selectSingle<ProfileRow>("profiles") {
            eq("id", userId)
        }.getOrThrow()
            ?: throw IllegalStateException("No se encontro el perfil del usuario actual.")

        val branch = profile.branch?.trim()?.takeIf { it.isNotEmpty() }
        return SessionProfile(
            userId = userId,
            branch = branch,
            fullName = profile.fullName,
        ).also { context ->
            Timber.tag("AUTH").d(
                "Resolved session profile: userId=%s branch=%s",
                context.userId,
                context.branch ?: "<none>",
            )
        }
    }

    private fun currentUserIdFromToken(): String? {
        sessionManager.getUserId()?.takeIf { it.isNotBlank() }?.let { return it }
        val token = sessionManager.getAccessToken()?.takeIf { it.isNotBlank() } ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null

        return runCatching {
            val payloadBase64Url = parts[1]
            val padded = payloadBase64Url + "=".repeat((4 - payloadBase64Url.length % 4) % 4)
            val payloadJson = String(Base64.getUrlDecoder().decode(padded))
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"")
                .find(payloadJson)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
