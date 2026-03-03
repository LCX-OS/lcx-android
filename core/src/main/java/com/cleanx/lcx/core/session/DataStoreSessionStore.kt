package com.cleanx.lcx.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

    override suspend fun saveUserRole(role: UserRole) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_ROLE] = role.name.lowercase()
        }
    }

    override suspend fun clearSession() {
        context.sessionDataStore.edit { preferences: MutablePreferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(USER_ROLE)
        }
    }

    private companion object {
        val ACCESS_TOKEN: Preferences.Key<String> = stringPreferencesKey("access_token")
        val USER_ROLE: Preferences.Key<String> = stringPreferencesKey("user_role")
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
