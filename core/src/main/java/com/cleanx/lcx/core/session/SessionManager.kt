package com.cleanx.lcx.core.session

import com.cleanx.lcx.core.model.UserRole

interface SessionManager {
    fun getAccessToken(): String?
    suspend fun saveAccessToken(token: String)
    fun getUserRole(): UserRole?
    suspend fun saveUserRole(role: UserRole)
    suspend fun clearSession()
}
