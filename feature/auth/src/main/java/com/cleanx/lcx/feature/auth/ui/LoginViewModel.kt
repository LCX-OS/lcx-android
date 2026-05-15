package com.cleanx.lcx.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.session.SessionManager
import com.cleanx.lcx.feature.auth.data.AuthRepository
import com.cleanx.lcx.feature.auth.data.AuthResult
import com.cleanx.lcx.feature.auth.data.DeviceOperatorResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val branches: List<String> = emptyList(),
    val operators: List<DeviceOperatorResponse> = emptyList(),
    val selectedBranch: String? = null,
    val selectedOperator: DeviceOperatorResponse? = null,
    val pin: String = "",
    val guestCode: String = "",
    val phase: LoginPhase = LoginPhase.Loading,
    val error: String? = null,
)

sealed interface LoginPhase {
    data object Loading : LoginPhase
    data object BranchSelection : LoginPhase
    data object OperatorSelection : LoginPhase
    data object PinEntry : LoginPhase
    data object GuestEntry : LoginPhase
    data object Submitting : LoginPhase
    data object Success : LoginPhase
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (authRepository.restoreSession()) {
                _uiState.update { it.copy(phase = LoginPhase.Success) }
                return@launch
            }

            loadInitialState()
        }
    }

    fun retry() {
        viewModelScope.launch { loadInitialState() }
    }

    fun selectBranch(branch: String) {
        viewModelScope.launch {
            sessionManager.saveSelectedBranch(branch)
            _uiState.update {
                it.copy(
                    selectedBranch = branch,
                    selectedOperator = null,
                    pin = "",
                    guestCode = "",
                    error = null,
                )
            }
            loadOperators(branch)
        }
    }

    fun changeBranch() {
        viewModelScope.launch {
            sessionManager.clearSession()
            sessionManager.clearSelectedBranch()
            _uiState.update {
                it.copy(
                    selectedBranch = null,
                    selectedOperator = null,
                    operators = emptyList(),
                    pin = "",
                    guestCode = "",
                    phase = LoginPhase.BranchSelection,
                    error = null,
                )
            }
        }
    }

    fun selectOperator(operator: DeviceOperatorResponse) {
        if (!operator.hasPin) {
            _uiState.update { it.copy(error = "Este usuario aun no tiene PIN Android configurado.") }
            return
        }

        _uiState.update {
            it.copy(
                selectedOperator = operator,
                pin = "",
                phase = LoginPhase.PinEntry,
                error = null,
            )
        }
    }

    fun backToOperators() {
        val branch = _uiState.value.selectedBranch
        _uiState.update {
            it.copy(
                selectedOperator = null,
                pin = "",
                guestCode = "",
                phase = if (branch == null) LoginPhase.BranchSelection else LoginPhase.OperatorSelection,
                error = null,
            )
        }
    }

    fun showGuestEntry() {
        _uiState.update {
            it.copy(
                selectedOperator = null,
                guestCode = "",
                phase = LoginPhase.GuestEntry,
                error = null,
            )
        }
    }

    fun onPinChanged(value: String) {
        _uiState.update {
            it.copy(
                pin = value.filter(Char::isDigit).take(6),
                error = null,
            )
        }
    }

    fun onGuestCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                guestCode = value.filter(Char::isDigit).take(6),
                error = null,
            )
        }
    }

    fun submitPin() {
        val state = _uiState.value
        val operator = state.selectedOperator ?: return
        val branch = state.selectedBranch ?: return
        if (state.pin.length !in 4..6) {
            _uiState.update { it.copy(error = "El PIN debe tener de 4 a 6 digitos.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(phase = LoginPhase.Submitting, error = null) }
            when (val result = authRepository.signInWithPin(operator.id, branch, state.pin)) {
                is AuthResult.Success -> _uiState.update { it.copy(phase = LoginPhase.Success) }
                is AuthResult.Error -> _uiState.update {
                    it.copy(phase = LoginPhase.PinEntry, error = result.message, pin = "")
                }
            }
        }
    }

    fun submitGuestCode() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.guestCode.length != 6) {
            _uiState.update { it.copy(error = "El codigo invitado debe tener 6 digitos.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(phase = LoginPhase.Submitting, error = null) }
            when (val result = authRepository.signInGuest(branch, state.guestCode)) {
                is AuthResult.Success -> _uiState.update { it.copy(phase = LoginPhase.Success) }
                is AuthResult.Error -> _uiState.update {
                    it.copy(phase = LoginPhase.GuestEntry, error = result.message, guestCode = "")
                }
            }
        }
    }

    private suspend fun loadInitialState() {
        _uiState.update { it.copy(phase = LoginPhase.Loading, error = null) }
        val selectedBranch = sessionManager.getSelectedBranch()
        val branches = authRepository.loadDeviceBranches().getOrElse { error ->
            _uiState.update {
                it.copy(
                    phase = LoginPhase.BranchSelection,
                    error = error.message ?: "No se pudieron cargar las sucursales.",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                branches = branches,
                selectedBranch = selectedBranch?.takeIf { branch -> branch in branches },
                phase = LoginPhase.BranchSelection,
            )
        }

        val branchToLoad = _uiState.value.selectedBranch
        if (branchToLoad != null) {
            loadOperators(branchToLoad)
        }
    }

    private suspend fun loadOperators(branch: String) {
        _uiState.update { it.copy(phase = LoginPhase.Loading, error = null) }
        authRepository.loadDeviceOperators(branch)
            .onSuccess { operators ->
                _uiState.update {
                    it.copy(
                        selectedBranch = branch,
                        operators = operators,
                        phase = LoginPhase.OperatorSelection,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        phase = LoginPhase.BranchSelection,
                        error = error.message ?: "No se pudieron cargar los usuarios.",
                    )
                }
            }
    }
}
