package com.cleanx.lcx.feature.payments.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.payments.data.ChargeResult
import com.cleanx.lcx.feature.payments.data.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class ChargeUiState(
    val ticketId: String = "",
    val amount: Double = 0.0,
    val customerName: String = "",
    val phase: ChargePhase = ChargePhase.Idle,
    /** Non-null when phase is [ChargePhase.Success]. */
    val transactionId: String? = null,
    /** Non-null when phase is [ChargePhase.Failed] or [ChargePhase.PaymentSucceededApiCallFailed]. */
    val errorMessage: String? = null,
)

sealed interface ChargePhase {
    data object Idle : ChargePhase
    data object Processing : ChargePhase
    data object Success : ChargePhase
    data object Cancelled : ChargePhase
    data object Failed : ChargePhase

    /**
     * Critical: the card was charged but the backend PATCH failed.
     * The operator must know the card was debited.
     */
    data object PaymentSucceededApiCallFailed : ChargePhase
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChargeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val ticketId: String = checkNotNull(savedStateHandle["ticketId"])

    private val _uiState = MutableStateFlow(
        ChargeUiState(
            ticketId = ticketId,
            amount = savedStateHandle.get<String>("amount")?.toDoubleOrNull() ?: 0.0,
            customerName = savedStateHandle.get<String>("customerName") ?: "",
        ),
    )
    val uiState: StateFlow<ChargeUiState> = _uiState.asStateFlow()

    // -- actions --

    fun startCharge() {
        val snapshot = _uiState.value
        if (snapshot.phase == ChargePhase.Processing) return // debounce

        viewModelScope.launch {
            _uiState.update { it.copy(phase = ChargePhase.Processing, errorMessage = null) }

            val result = paymentRepository.charge(
                ticketId = snapshot.ticketId,
                amount = snapshot.amount,
            )

            _uiState.update { state ->
                when (result) {
                    is ChargeResult.Success -> state.copy(
                        phase = ChargePhase.Success,
                        transactionId = result.transactionId,
                        errorMessage = null,
                    )

                    is ChargeResult.Cancelled -> state.copy(
                        phase = ChargePhase.Cancelled,
                        errorMessage = null,
                    )

                    is ChargeResult.ReaderFailed -> state.copy(
                        phase = ChargePhase.Failed,
                        errorMessage = result.message,
                    )

                    is ChargeResult.PaymentSucceededButApiCallFailed -> state.copy(
                        phase = ChargePhase.PaymentSucceededApiCallFailed,
                        transactionId = result.transactionId,
                        errorMessage = result.apiErrorMessage,
                    )
                }
            }
        }
    }

    /**
     * Retry depends on the current phase:
     * - [ChargePhase.Failed] / [ChargePhase.Cancelled] → full charge flow
     * - [ChargePhase.PaymentSucceededApiCallFailed] → only the API call (card was already charged)
     */
    fun retry() {
        val snapshot = _uiState.value
        when (snapshot.phase) {
            is ChargePhase.PaymentSucceededApiCallFailed -> retryApiSync(snapshot)
            else -> startCharge()
        }
    }

    fun cancel() {
        _uiState.update { it.copy(phase = ChargePhase.Cancelled) }
    }

    // -- internal --

    private fun retryApiSync(snapshot: ChargeUiState) {
        val txnId = snapshot.transactionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ChargePhase.Processing, errorMessage = null) }

            val result = paymentRepository.syncPaymentToBackend(
                ticketId = snapshot.ticketId,
                transactionId = txnId,
                amount = snapshot.amount,
            )

            _uiState.update { state ->
                when (result) {
                    is ChargeResult.Success -> state.copy(
                        phase = ChargePhase.Success,
                        transactionId = result.transactionId,
                        errorMessage = null,
                    )

                    is ChargeResult.PaymentSucceededButApiCallFailed -> state.copy(
                        phase = ChargePhase.PaymentSucceededApiCallFailed,
                        transactionId = result.transactionId,
                        errorMessage = result.apiErrorMessage,
                    )

                    // These shouldn't happen during an API-only retry, but handle defensively.
                    is ChargeResult.Cancelled -> state.copy(
                        phase = ChargePhase.Cancelled,
                        errorMessage = null,
                    )

                    is ChargeResult.ReaderFailed -> state.copy(
                        phase = ChargePhase.Failed,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}
