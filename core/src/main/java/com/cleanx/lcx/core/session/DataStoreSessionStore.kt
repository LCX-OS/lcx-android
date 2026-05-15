package com.cleanx.lcx.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.network.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "lcx_session")

@Singleton
class DataStoreSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionManager, TokenProvider {

    override fun getAccessToken(): String? = runBlocking {
        sessionPreferencesFlow()
            .map { preferences -> preferences[ACCESS_TOKEN] }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveAccessToken(token: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = token.trim()
        }
    }

    override fun getRefreshToken(): String? = runBlocking {
        sessionPreferencesFlow()
            .map { preferences -> preferences[REFRESH_TOKEN] }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveRefreshToken(token: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[REFRESH_TOKEN] = token.trim()
        }
    }

    override fun getTokenExpiresAtEpochSeconds(): Long? = runBlocking {
        sessionPreferencesFlow()
            .map { preferences -> preferences[TOKEN_EXPIRES_AT] }
            .first()
    }

    override suspend fun saveTokenExpiresAtEpochSeconds(expiresAt: Long?) {
        context.sessionDataStore.edit { preferences ->
            if (expiresAt == null) {
                preferences.remove(TOKEN_EXPIRES_AT)
            } else {
                preferences[TOKEN_EXPIRES_AT] = expiresAt
            }
        }
    }

    override fun getUserId(): String? = runBlocking {
        observeUserId().first()
    }

    override fun observeUserId(): Flow<String?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[USER_ID]?.takeIf { it.isNotBlank() }
        }
    }

    override fun getUserFullName(): String? = runBlocking {
        observeUserFullName().first()
    }

    override fun observeUserFullName(): Flow<String?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[USER_FULL_NAME]?.takeIf { it.isNotBlank() }
        }
    }

    override fun getUserBranch(): String? = runBlocking {
        observeUserBranch().first()
    }

    override fun observeUserBranch(): Flow<String?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[USER_BRANCH]?.takeIf { it.isNotBlank() }
        }
    }

    override fun getUserRole(): UserRole? = runBlocking {
        observeUserRole().first()
    }

    override fun observeUserRole(): Flow<UserRole?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[USER_ROLE]?.let { roleName ->
                UserRole.entries.firstOrNull { it.name.equals(roleName, ignoreCase = true) }
            }
        }
    }

    override fun getUserEmail(): String? = runBlocking {
        observeUserEmail().first()
    }

    override fun observeUserEmail(): Flow<String?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[USER_EMAIL]?.takeIf { it.isNotBlank() }
        }
    }

    override fun getSelectedBranch(): String? = runBlocking {
        observeSelectedBranch().first()
    }

    override fun observeSelectedBranch(): Flow<String?> {
        return sessionPreferencesFlow().map { preferences ->
            preferences[SELECTED_BRANCH]?.takeIf { it.isNotBlank() }
        }
    }

    override suspend fun saveSelectedBranch(branch: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[SELECTED_BRANCH] = branch.trim()
        }
    }

    override suspend fun clearSelectedBranch() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(SELECTED_BRANCH)
        }
    }

    override suspend fun saveUserEmail(email: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_EMAIL] = email.trim()
        }
    }

    override suspend fun saveUserId(userId: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_ID] = userId.trim()
        }
    }

    override suspend fun saveUserFullName(fullName: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_FULL_NAME] = fullName.trim()
        }
    }

    override suspend fun saveUserBranch(branch: String?) {
        context.sessionDataStore.edit { preferences ->
            val normalized = branch?.trim()?.takeIf { it.isNotBlank() }
            if (normalized == null) {
                preferences.remove(USER_BRANCH)
            } else {
                preferences[USER_BRANCH] = normalized
            }
        }
    }

    override suspend fun saveUserRole(role: UserRole) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_ROLE] = role.name.lowercase()
        }
    }

    override suspend fun clearSession() {
        context.sessionDataStore.edit { preferences: MutablePreferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
            preferences.remove(TOKEN_EXPIRES_AT)
            preferences.remove(USER_ID)
            preferences.remove(USER_ROLE)
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_FULL_NAME)
            preferences.remove(USER_BRANCH)
        }
    }

    private companion object {
        val ACCESS_TOKEN: Preferences.Key<String> = stringPreferencesKey("access_token")
        val REFRESH_TOKEN: Preferences.Key<String> = stringPreferencesKey("refresh_token")
        val TOKEN_EXPIRES_AT: Preferences.Key<Long> = longPreferencesKey("token_expires_at")
        val USER_ID: Preferences.Key<String> = stringPreferencesKey("user_id")
        val USER_ROLE: Preferences.Key<String> = stringPreferencesKey("user_role")
        val USER_EMAIL: Preferences.Key<String> = stringPreferencesKey("user_email")
        val USER_FULL_NAME: Preferences.Key<String> = stringPreferencesKey("user_full_name")
        val USER_BRANCH: Preferences.Key<String> = stringPreferencesKey("user_branch")
        val SELECTED_BRANCH: Preferences.Key<String> = stringPreferencesKey("selected_branch")
    }

    private fun sessionPreferencesFlow(): Flow<Preferences> {
        return context.sessionDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
    }
}
