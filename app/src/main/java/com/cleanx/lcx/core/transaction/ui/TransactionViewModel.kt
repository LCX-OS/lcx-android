package com.cleanx.lcx.core.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.TransactionOrchestrator
import com.cleanx.lcx.core.transaction.TransactionPhase
import com.cleanx.lcx.core.transaction.TransactionState
import com.cleanx.lcx.core.transaction.data.SavedTransaction
import com.cleanx.lcx.core.transaction.data.TransactionPersistence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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

/**
 * State for the pending-transaction recovery dialog.
 */
data class PendingTransactionState(
    val visible: Boolean = false,
    val saved: SavedTransaction? = null,
    val phaseLabel: String = "",
    val ticketInfo: String = "",
    val isCritical: Boolean = false,
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val orchestrator: TransactionOrchestrator,
    private val persistence: TransactionPersistence,
) : ViewModel() {

    val uiState: StateFlow<TransactionUiState> = orchestrator.observeState()
        .map { state -> mapToUiState(state) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionUiState(),
        )

    private val _pendingTransaction = MutableStateFlow(PendingTransactionState())
    val pendingTransaction: StateFlow<PendingTransactionState> = _pendingTransaction.asStateFlow()

    init {
        checkForPendingTransaction()
        cleanupOldRecords()
    }

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
        viewModelScope.launch {
            orchestrator.cancelAndPersist()
        }
    }

    /**
     * Resume the pending transaction from where it left off.
     */
    fun resumePendingTransaction() {
        val saved = _pendingTransaction.value.saved ?: return
        _pendingTransaction.value = PendingTransactionState()
        viewModelScope.launch {
            orchestrator.resumeTransaction(saved)
        }
    }

    /**
     * Cancel the pending transaction and dismiss the dialog.
     */
    fun cancelPendingTransaction() {
        val saved = _pendingTransaction.value.saved ?: return
        _pendingTransaction.value = PendingTransactionState()
        viewModelScope.launch {
            persistence.markCancelled(saved.id)
        }
    }

    /**
     * Dismiss the recovery dialog without taking action.
     */
    fun dismissPendingDialog() {
        _pendingTransaction.value = PendingTransactionState()
    }

    private fun checkForPendingTransaction() {
        viewModelScope.launch {
            try {
                val saved = persistence.loadActiveTransaction()
                if (saved != null) {
                    Timber.i(
                        "Found pending transaction: id=%s phase=%s",
                        saved.id, saved.phase,
                    )
                    _pendingTransaction.value = PendingTransactionState(
                        visible = true,
                        saved = saved,
                        phaseLabel = phaseToLabel(saved.phase),
                        ticketInfo = buildTicketInfo(saved),
                        isCritical = saved.phase == TransactionPhase.PAYMENT_SUCCEEDED_API_FAILED,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for pending transactions")
            }
        }
    }

    private fun cleanupOldRecords() {
        viewModelScope.launch {
            try {
                persistence.cleanup()
            } catch (e: Exception) {
                Timber.e(e, "Failed to cleanup old transaction records")
            }
        }
    }

    private fun phaseToLabel(phase: TransactionPhase): String = when (phase) {
        TransactionPhase.IDLE -> "Iniciando"
        TransactionPhase.CREATING_TICKET -> "Creando ticket"
        TransactionPhase.TICKET_CREATED -> "Ticket creado"
        TransactionPhase.TICKET_CREATION_FAILED -> "Error al crear ticket"
        TransactionPhase.CHARGING_PAYMENT -> "Procesando cobro"
        TransactionPhase.PAYMENT_CHARGED -> "Cobro realizado"
        TransactionPhase.PAYMENT_FAILED -> "Error en cobro"
        TransactionPhase.PAYMENT_CANCELLED -> "Cobro cancelado"
        TransactionPhase.PAYMENT_SUCCEEDED_API_FAILED -> "Cobro realizado - Error de sincronizacion"
        TransactionPhase.PRINTING_LABEL -> "Imprimiendo etiqueta"
        TransactionPhase.LABEL_PRINTED -> "Etiqueta impresa"
        TransactionPhase.PRINT_FAILED -> "Error de impresion"
        TransactionPhase.COMPLETED -> "Completado"
        TransactionPhase.CANCELLED -> "Cancelado"
    }

    private fun buildTicketInfo(saved: SavedTransaction): String {
        val ticket = saved.ticket
        return if (ticket != null) {
            "${ticket.ticketNumber} - ${ticket.customerName}" +
                (ticket.totalAmount?.let { " - \$${String.format("%.2f", it)}" } ?: "")
        } else {
            saved.draft?.let { "${it.customerName} - ${it.service}" } ?: "Sin informacion"
        }
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
