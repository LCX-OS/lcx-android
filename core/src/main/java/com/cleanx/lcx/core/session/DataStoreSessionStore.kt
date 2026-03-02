package com.cleanx.lcx.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cleanx.lcx.core.network.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
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
        context.sessionDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences -> preferences[ACCESS_TOKEN] }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveAccessToken(token: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = token.trim()
        }
    }

    override suspend fun clearSession() {
        context.sessionDataStore.edit { preferences: MutablePreferences ->
            preferences.remove(ACCESS_TOKEN)
        }
    }

    private companion object {
        val ACCESS_TOKEN: Preferences.Key<String> = stringPreferencesKey("access_token")
    }
}
