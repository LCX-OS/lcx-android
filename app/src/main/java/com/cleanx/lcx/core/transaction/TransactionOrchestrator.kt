package com.cleanx.lcx.core.transaction

import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.feature.payments.data.ChargeResult
import com.cleanx.lcx.feature.payments.data.PaymentRepository
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.ErrorMessages
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the full P0 transaction flow:
 *
 *   1. Create ticket (API)
 *   2. Charge payment (Zettle card reader + API update)
 *   3. Print label (Brother printer)
 *   4. Persist / finalize
 *
 * Invariants:
 * - Cancel/fail at payment NEVER marks ticket as paid.
 * - Print failure is non-blocking (operator can skip).
 * - Payment success + API failure is a CRITICAL state the operator must resolve.
 * - Double-submit is prevented via [mutex].
 */
@Singleton
class TransactionOrchestrator @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val paymentRepository: PaymentRepository,
    private val printRepository: PrintRepository,
) {

    private val _state = MutableStateFlow<TransactionState>(TransactionState.Idle)
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    /** Prevents double-submit / concurrent flow execution. */
    private val mutex = Mutex()

    /** Currently running flag — checked by [startTransaction]. */
    @Volatile
    private var running = false

    /** Stored context for retry operations. */
    private var currentDraft: TicketDraft? = null
    private var currentTicket: Ticket? = null
    private var currentTransactionId: String? = null
    private var currentAmount: Double = 0.0

    /**
     * Start the full E2E flow from a ticket draft.
     *
     * Returns a [Flow] of [TransactionState] that emits every transition.
     * The caller (typically the ViewModel) collects this flow to drive the UI.
     */
    fun observeState(): StateFlow<TransactionState> = state

    /**
     * Kick off the transaction. If already running, this is a no-op
     * (double-submit prevention).
     */
    suspend fun startTransaction(ticketDraft: TicketDraft) {
        if (running) {
            Timber.w("TransactionOrchestrator: startTransaction ignored — already running")
            return
        }
        mutex.withLock {
            if (running) return
            running = true
        }

        currentDraft = ticketDraft
        currentTicket = null
        currentTransactionId = null
        currentAmount = 0.0

        try {
            executeCreateTicket(ticketDraft)
        } finally {
            if (_state.value is TransactionState.Completed ||
                _state.value is TransactionState.Idle
            ) {
                running = false
            }
        }
    }

    // -- Step 1: Create Ticket ------------------------------------------------

    private suspend fun executeCreateTicket(draft: TicketDraft) {
        _state.value = TransactionState.CreatingTicket

        when (val result = ticketRepository.createTickets(source = "encargo", tickets = listOf(draft))) {
            is ApiResult.Success -> {
                val ticket = result.data.firstOrNull()
                if (ticket == null) {
                    _state.value = TransactionState.TicketCreationFailed(
                        message = "El servidor no devolvio el ticket creado.",
                    )
                    running = false
                    return
                }
                currentTicket = ticket
                _state.value = TransactionState.TicketCreated(ticket)

                // Auto-advance to payment
                executeChargePayment(ticket)
            }

            is ApiResult.Error -> {
                _state.value = TransactionState.TicketCreationFailed(
                    message = ErrorMessages.forCode(result.code, result.message),
                    code = result.code,
                )
                running = false
            }
        }
    }

    // -- Step 2: Charge Payment -----------------------------------------------

    private suspend fun executeChargePayment(ticket: Ticket) {
        val amount = ticket.totalAmount ?: 0.0
        currentAmount = amount

        _state.value = TransactionState.ChargingPayment(ticket)

        when (val result = paymentRepository.charge(ticketId = ticket.id, amount = amount)) {
            is ChargeResult.Success -> {
                val updatedTicket = result.ticket
                currentTicket = updatedTicket
                currentTransactionId = result.transactionId
                _state.value = TransactionState.PaymentCharged(
                    ticket = updatedTicket,
                    transactionId = result.transactionId,
                )

                // Auto-advance to printing
                executePrintLabel(updatedTicket)
            }

            is ChargeResult.Cancelled -> {
                _state.value = TransactionState.PaymentCancelled(ticket)
                running = false
            }

            is ChargeResult.ReaderFailed -> {
                _state.value = TransactionState.PaymentFailed(
                    ticket = ticket,
                    message = result.message,
                )
                running = false
            }

            is ChargeResult.PaymentSucceededButApiCallFailed -> {
                currentTransactionId = result.transactionId
                _state.value = TransactionState.PaymentSucceededApiFailed(
                    ticket = ticket,
                    transactionId = result.transactionId,
                    amount = result.amount,
                    apiErrorMessage = result.apiErrorMessage,
                )
                running = false
            }
        }
    }

    // -- Step 3: Print Label --------------------------------------------------

    private suspend fun executePrintLabel(ticket: Ticket) {
        _state.value = TransactionState.PrintingLabel(ticket)

        val label = LabelData(
            ticketNumber = ticket.ticketNumber,
            customerName = ticket.customerName,
            serviceType = ticket.serviceType.name,
            date = ticket.ticketDate,
            dailyFolio = ticket.dailyFolio,
        )

        when (val result = printRepository.printWithRetry(label)) {
            is PrintResult.Success -> {
                _state.value = TransactionState.LabelPrinted(ticket)
                // Auto-advance to complete
                finalize(ticket)
            }

            is PrintResult.Error -> {
                _state.value = TransactionState.PrintFailed(
                    ticket = ticket,
                    message = result.message,
                )
                // Do NOT mark running=false — operator can skip or retry
            }
        }
    }

    // -- Step 4: Finalize -----------------------------------------------------

    private fun finalize(ticket: Ticket) {
        _state.value = TransactionState.Completed(ticket)
        running = false
    }

    // -- User actions ---------------------------------------------------------

    /**
     * Skip the print step and advance to completed.
     * Only valid when in [TransactionState.PrintFailed] or [TransactionState.LabelPrinted].
     */
    suspend fun skipPrint() {
        val ticket = currentTicket ?: return
        val current = _state.value
        if (current is TransactionState.PrintFailed ||
            current is TransactionState.LabelPrinted
        ) {
            finalize(ticket)
        }
    }

    /**
     * Retry the current failed step.
     */
    suspend fun retryCurrentStep() {
        when (val current = _state.value) {
            is TransactionState.TicketCreationFailed -> {
                val draft = currentDraft ?: return
                running = true
                executeCreateTicket(draft)
            }

            is TransactionState.PaymentFailed -> {
                val ticket = current.ticket
                running = true
                executeChargePayment(ticket)
            }

            is TransactionState.PaymentCancelled -> {
                val ticket = current.ticket
                running = true
                executeChargePayment(ticket)
            }

            is TransactionState.PaymentSucceededApiFailed -> {
                // Only retry the API sync — the card was already charged
                running = true
                retryApiSync(current)
            }

            is TransactionState.PrintFailed -> {
                val ticket = current.ticket
                running = true
                executePrintLabel(ticket)
            }

            else -> {
                Timber.w("TransactionOrchestrator: retryCurrentStep ignored in state %s", current)
            }
        }
    }

    private suspend fun retryApiSync(failedState: TransactionState.PaymentSucceededApiFailed) {
        val ticket = failedState.ticket
        _state.value = TransactionState.ChargingPayment(ticket)

        val result = paymentRepository.syncPaymentToBackend(
            ticketId = ticket.id,
            transactionId = failedState.transactionId,
            amount = failedState.amount,
        )

        when (result) {
            is ChargeResult.Success -> {
                val updatedTicket = result.ticket
                currentTicket = updatedTicket
                _state.value = TransactionState.PaymentCharged(
                    ticket = updatedTicket,
                    transactionId = result.transactionId,
                )
                executePrintLabel(updatedTicket)
            }

            is ChargeResult.PaymentSucceededButApiCallFailed -> {
                _state.value = TransactionState.PaymentSucceededApiFailed(
                    ticket = ticket,
                    transactionId = result.transactionId,
                    amount = result.amount,
                    apiErrorMessage = result.apiErrorMessage,
                )
                running = false
            }

            // These shouldn't happen during API-only retry, but handle defensively
            is ChargeResult.Cancelled -> {
                _state.value = TransactionState.PaymentCancelled(ticket)
                running = false
            }

            is ChargeResult.ReaderFailed -> {
                _state.value = TransactionState.PaymentFailed(ticket = ticket, message = result.message)
                running = false
            }
        }
    }

    /**
     * Reset the orchestrator back to idle.
     * Safe to call at any time (e.g., when the user navigates away).
     */
    fun reset() {
        _state.value = TransactionState.Idle
        running = false
        currentDraft = null
        currentTicket = null
        currentTransactionId = null
        currentAmount = 0.0
    }
}
