package com.cleanx.lcx.core.session

interface SessionManager {
    fun getAccessToken(): String?
    suspend fun saveAccessToken(token: String)
    suspend fun clearSession()
}
