package com.cleanx.lcx.core.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.TransactionOrchestrator
import com.cleanx.lcx.core.transaction.TransactionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state that wraps [TransactionState] with step progress info.
 */
data class TransactionUiState(
    val transactionState: TransactionState = TransactionState.Idle,
    val currentStep: Int = 0,
    val totalSteps: Int = 4,
    val stepLabel: String = "",
    val isProcessing: Boolean = false,
    val ticket: Ticket? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val canSkip: Boolean = false,
    val canCancel: Boolean = false,
    val isCritical: Boolean = false,
    val transactionId: String? = null,
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val orchestrator: TransactionOrchestrator,
) : ViewModel() {

    val uiState: StateFlow<TransactionUiState> = orchestrator.observeState()
        .map { state -> mapToUiState(state) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionUiState(),
        )

    fun start(draft: TicketDraft) {
        viewModelScope.launch {
            orchestrator.startTransaction(draft)
        }
    }

    fun retry() {
        viewModelScope.launch {
            orchestrator.retryCurrentStep()
        }
    }

    fun skipPrint() {
        viewModelScope.launch {
            orchestrator.skipPrint()
        }
    }

    fun cancel() {
        orchestrator.reset()
    }

    private fun mapToUiState(state: TransactionState): TransactionUiState {
        return when (state) {
            is TransactionState.Idle -> TransactionUiState(
                transactionState = state,
                currentStep = 0,
                stepLabel = "",
                canCancel = true,
            )

            is TransactionState.CreatingTicket -> TransactionUiState(
                transactionState = state,
                currentStep = 1,
                stepLabel = "Creando ticket...",
                isProcessing = true,
            )

            is TransactionState.TicketCreated -> TransactionUiState(
                transactionState = state,
                currentStep = 1,
                stepLabel = "Ticket creado",
                ticket = state.ticket,
                isProcessing = true, // Auto-advances
            )

            is TransactionState.TicketCreationFailed -> TransactionUiState(
                transactionState = state,
                currentStep = 1,
                stepLabel = "Error al crear ticket",
                errorMessage = state.message,
                canRetry = true,
                canCancel = true,
            )

            is TransactionState.ChargingPayment -> TransactionUiState(
                transactionState = state,
                currentStep = 2,
                stepLabel = "Procesando cobro...",
                ticket = state.ticket,
                isProcessing = true,
            )

            is TransactionState.PaymentCharged -> TransactionUiState(
                transactionState = state,
                currentStep = 2,
                stepLabel = "Cobro exitoso",
                ticket = state.ticket,
                transactionId = state.transactionId,
                isProcessing = true, // Auto-advances
            )

            is TransactionState.PaymentFailed -> TransactionUiState(
                transactionState = state,
                currentStep = 2,
                stepLabel = "Error en el cobro",
                ticket = state.ticket,
                errorMessage = state.message,
                canRetry = true,
                canCancel = true,
            )

            is TransactionState.PaymentCancelled -> TransactionUiState(
                transactionState = state,
                currentStep = 2,
                stepLabel = "Cobro cancelado",
                ticket = state.ticket,
                canRetry = true,
                canCancel = true,
            )

            is TransactionState.PaymentSucceededApiFailed -> TransactionUiState(
                transactionState = state,
                currentStep = 2,
                stepLabel = "Cobro realizado - Error de sincronizacion",
                ticket = state.ticket,
                errorMessage = state.apiErrorMessage,
                transactionId = state.transactionId,
                canRetry = true,
                isCritical = true,
            )

            is TransactionState.PrintingLabel -> TransactionUiState(
                transactionState = state,
                currentStep = 3,
                stepLabel = "Imprimiendo etiqueta...",
                ticket = state.ticket,
                isProcessing = true,
            )

            is TransactionState.LabelPrinted -> TransactionUiState(
                transactionState = state,
                currentStep = 3,
                stepLabel = "Etiqueta impresa",
                ticket = state.ticket,
                isProcessing = true, // Auto-advances
            )

            is TransactionState.PrintFailed -> TransactionUiState(
                transactionState = state,
                currentStep = 3,
                stepLabel = "Error de impresion",
                ticket = state.ticket,
                errorMessage = state.message,
                canRetry = true,
                canSkip = true,
            )

            is TransactionState.Completed -> TransactionUiState(
                transactionState = state,
                currentStep = 4,
                stepLabel = "Completado",
                ticket = state.ticket,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.reset()
    }
}
