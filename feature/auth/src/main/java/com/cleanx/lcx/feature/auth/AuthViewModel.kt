package com.cleanx.lcx.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.auth.data.AuthRepository
import com.cleanx.lcx.feature.auth.data.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
)

/**
 * Legacy AuthViewModel kept for backward compatibility.
 * New code should use [com.cleanx.lcx.feature.auth.ui.LoginViewModel].
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isAuthenticated()) {
            _uiState.update { it.copy(isAuthenticated = true) }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, error = null, isAuthenticated = false) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null, isAuthenticated = false) }
    }

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Correo y contrasena son obligatorios.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = authRepository.signIn(state.email.trim(), state.password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, isAuthenticated = true) }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { AuthUiState() }
        }
    }
}
