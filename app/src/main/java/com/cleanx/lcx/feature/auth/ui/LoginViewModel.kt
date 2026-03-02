package com.cleanx.lcx.feature.auth.ui

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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val phase: LoginPhase = LoginPhase.Idle,
    val error: String? = null,
)

sealed interface LoginPhase {
    data object Idle : LoginPhase
    data object Loading : LoginPhase
    data object Success : LoginPhase
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // If already authenticated, skip straight to success.
        if (authRepository.isAuthenticated()) {
            _uiState.update { it.copy(phase = LoginPhase.Success) }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun signIn() {
        val state = _uiState.value
        if (state.phase == LoginPhase.Loading) return // debounce

        // Client-side validation
        if (state.email.isBlank()) {
            _uiState.update { it.copy(error = "El correo es obligatorio.") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "La contrasena es obligatoria.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(phase = LoginPhase.Loading, error = null) }

            when (val result = authRepository.signIn(state.email.trim(), state.password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(phase = LoginPhase.Success, error = null) }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(phase = LoginPhase.Idle, error = result.message)
                    }
                }
            }
        }
    }
}
