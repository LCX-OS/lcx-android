package com.cleanx.lcx.feature.auth.data

/**
 * Outcome of an authentication attempt.
 */
sealed interface AuthResult {
    data class Success(
        val userId: String,
        val accessToken: String,
    ) : AuthResult

    data class Error(val message: String) : AuthResult
}
