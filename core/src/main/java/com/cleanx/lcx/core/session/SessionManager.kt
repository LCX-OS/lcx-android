package com.cleanx.lcx.core.session

import com.cleanx.lcx.core.model.UserRole
import kotlinx.coroutines.flow.Flow

interface SessionManager {
    fun getAccessToken(): String?
    suspend fun saveAccessToken(token: String)
    fun getUserRole(): UserRole?
    fun observeUserRole(): Flow<UserRole?>
    suspend fun saveUserRole(role: UserRole)
    suspend fun clearSession()
}
