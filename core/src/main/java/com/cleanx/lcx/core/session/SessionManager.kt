package com.cleanx.lcx.core.session

import com.cleanx.lcx.core.model.UserRole
import kotlinx.coroutines.flow.Flow

interface SessionManager {
    fun getAccessToken(): String?
    suspend fun saveAccessToken(token: String)
    fun getRefreshToken(): String?
    suspend fun saveRefreshToken(token: String)
    fun getTokenExpiresAtEpochSeconds(): Long?
    suspend fun saveTokenExpiresAtEpochSeconds(expiresAt: Long?)
    fun getUserId(): String?
    fun observeUserId(): Flow<String?>
    fun getUserFullName(): String?
    fun observeUserFullName(): Flow<String?>
    fun getUserBranch(): String?
    fun observeUserBranch(): Flow<String?>
    fun getUserRole(): UserRole?
    fun observeUserRole(): Flow<UserRole?>
    fun getUserEmail(): String?
    fun observeUserEmail(): Flow<String?>
    fun getSelectedBranch(): String?
    fun observeSelectedBranch(): Flow<String?>
    suspend fun saveSelectedBranch(branch: String)
    suspend fun clearSelectedBranch()
    suspend fun saveUserEmail(email: String)
    suspend fun saveUserId(userId: String)
    suspend fun saveUserFullName(fullName: String)
    suspend fun saveUserBranch(branch: String?)
    suspend fun saveUserRole(role: UserRole)
    suspend fun clearSession()
}
