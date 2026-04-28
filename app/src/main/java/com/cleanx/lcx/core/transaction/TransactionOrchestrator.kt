package com.cleanx.lcx.core.transaction

import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.CorrelationContext
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.printing.toLabelData
import com.cleanx.lcx.core.transaction.data.SavedTransaction
import com.cleanx.lcx.core.transaction.data.TransactionPersistence
import com.cleanx.lcx.feature.payments.data.ChargeResult
import com.cleanx.lcx.feature.payments.data.PaymentRepository
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.ErrorMessages
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
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
 *
 * State durability:
 * - Every state transition is persisted to Room via [TransactionPersistence].
 * - On app restart, [resumeTransaction] picks up from the last saved phase.
 * - SAFETY: if payment was already charged, resume only retries the API sync
 *   or later steps -- never double-charges.
 */
@Singleton
class TransactionOrchestrator @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val paymentRepository: PaymentRepository,
    private val printRepository: PrintRepository,
    private val persistence: TransactionPersistence,
) {

    private val _state = MutableStateFlow<TransactionState>(TransactionState.Idle)
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    /** Prevents double-submit / concurrent flow execution. */
    private val mutex = Mutex()

    /** Currently running flag -- checked by [startTransaction]. */
    @Volatile
    private var running = false

    /** Stored context for retry operations. */
    private var currentDraft: TicketDraft? = null
    private var currentTicket: Ticket? = null
    private var currentTransactionId: String? = null
    private var currentAmount: Double = 0.0

    /** Persistence record ID for the current flow. */
    private var persistenceId: String? = null

    /** Correlation ID for the current flow (groups related records). */
    private var correlationId: String? = null

    /**
     * Start the full E2E flow from a ticket draft.
     *
     * Returns a [StateFlow] of [TransactionState] that emits every transition.
     * The caller (typically the ViewModel) collects this flow to drive the UI.
     */
    fun observeState(): StateFlow<TransactionState> = state

    /**
     * Kick off the transaction. If already running, this is a no-op
     * (double-submit prevention).
     */
    suspend fun startTransaction(ticketDraft: TicketDraft) {
        if (running) {
            Timber.w("TransactionOrchestrator: startTransaction ignored -- already running")
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
        correlationId = UUID.randomUUID().toString()
        persistenceId = null

        CorrelationContext.set(correlationId!!)
        Timber.tag("TXN").i("[%s] Transaction started: source=%s, tickets=%d", correlationId, "encargo", 1)

        try {
            executeCreateTicket(ticketDraft)
        } finally {
            if (_state.value is TransactionState.Completed ||
                _state.value is TransactionState.Idle
            ) {
                running = false
                CorrelationContext.clear()
            }
        }
    }

    /**
     * Resume a transaction that was saved to Room after process death.
     *
     * SAFETY: this method inspects the saved phase to decide where to
     * resume. If the saved phase indicates that payment was already
     * charged (PAYMENT_CHARGED, PAYMENT_SUCCEEDED_API_FAILED,
     * PRINTING_LABEL, LABEL_PRINTED, PRINT_FAILED), the flow resumes
     * AFTER payment -- never re-charges.
     */
    suspend fun resumeTransaction(saved: SavedTransaction) {
        if (running) {
            Timber.w("TransactionOrchestrator: resumeTransaction ignored -- already running")
            return
        }
        mutex.withLock {
            if (running) return
            running = true
        }

        currentDraft = saved.draft
        currentTicket = saved.ticket
        currentTransactionId = saved.paymentTransactionId
        currentAmount = saved.paymentAmount ?: 0.0
        persistenceId = saved.id
        correlationId = saved.correlationId

        CorrelationContext.set(correlationId!!)
        Timber.tag("TXN").i("[%s] Transaction resumed from phase=%s", correlationId, saved.phase)

        try {
            when (saved.phase) {
                // Pre-ticket phases: restart from the beginning
                TransactionPhase.IDLE,
                TransactionPhase.CREATING_TICKET,
                TransactionPhase.TICKET_CREATION_FAILED -> {
                    val draft = saved.draft
                    if (draft != null) {
                        executeCreateTicket(draft)
                    } else {
                        Timber.e("Cannot resume: no draft saved for phase %s", saved.phase)
                        _state.value = TransactionState.Idle
                        running = false
                    }
                }

                // Ticket exists but payment not started / failed / cancelled
                TransactionPhase.TICKET_CREATED,
                TransactionPhase.CHARGING_PAYMENT,
                TransactionPhase.PAYMENT_FAILED,
                TransactionPhase.PAYMENT_CANCELLED -> {
                    val ticket = saved.ticket
                    if (ticket != null) {
                        executeChargePayment(ticket)
                    } else {
                        Timber.e("Cannot resume: no ticket saved for phase %s", saved.phase)
                        _state.value = TransactionState.Idle
                        running = false
                    }
                }

                // CRITICAL: payment charged but API failed -- only retry API
                TransactionPhase.PAYMENT_SUCCEEDED_API_FAILED -> {
                    val ticket = saved.ticket
                    val txnId = saved.paymentTransactionId
                    val amount = saved.paymentAmount
                    if (ticket != null && txnId != null && amount != null) {
                        retryApiSync(
                            TransactionState.PaymentSucceededApiFailed(
                                ticket = ticket,
                                transactionId = txnId,
                                amount = amount,
                                apiErrorMessage = saved.errorMessage ?: "Error desconocido",
                            )
                        )
                    } else {
                        Timber.e("Cannot resume PaymentSucceededApiFailed: missing context")
                        _state.value = TransactionState.Idle
                        running = false
                    }
                }

                // Payment done, print pending
                TransactionPhase.PAYMENT_CHARGED,
                TransactionPhase.PRINTING_LABEL,
                TransactionPhase.PRINT_FAILED -> {
                    val ticket = saved.ticket
                    if (ticket != null) {
                        executePrintLabel(ticket)
                    } else {
                        Timber.e("Cannot resume: no ticket saved for phase %s", saved.phase)
                        _state.value = TransactionState.Idle
                        running = false
                    }
                }

                // Already printed -- just finalize
                TransactionPhase.LABEL_PRINTED -> {
                    val ticket = saved.ticket
                    if (ticket != null) {
                        finalize(ticket)
                    } else {
                        _state.value = TransactionState.Idle
                        running = false
                    }
                }

                // Terminal states should not resume
                TransactionPhase.COMPLETED,
                TransactionPhase.CANCELLED -> {
                    Timber.w("resumeTransaction: ignoring terminal phase %s", saved.phase)
                    _state.value = TransactionState.Idle
                    running = false
                }
            }
        } finally {
            if (_state.value is TransactionState.Completed ||
                _state.value is TransactionState.Idle
            ) {
                running = false
                CorrelationContext.clear()
            }
        }
    }

    // -- Step 1: Create Ticket ------------------------------------------------

    private suspend fun executeCreateTicket(draft: TicketDraft) {
        emitAndPersist(TransactionState.CreatingTicket)

        when (val result = ticketRepository.createTickets(source = "encargo", tickets = listOf(draft))) {
            is ApiResult.Success -> {
                val ticket = result.data.firstOrNull()
                if (ticket == null) {
                    val failedState = TransactionState.TicketCreationFailed(
                        message = "El servidor no devolvio el ticket creado.",
                    )
                    Timber.tag("TXN").e("[%s] Transaction failed at %s: %s", correlationId, "createTicket", "empty response")
                    emitAndPersist(failedState)
                    running = false
                    return
                }
                currentTicket = ticket
                Timber.tag("TXN").i("[%s] Ticket created: id=%s, folio=%d", correlationId, ticket.id, ticket.dailyFolio)
                emitAndPersist(TransactionState.TicketCreated(ticket))

                // Auto-advance to payment
                executeChargePayment(ticket)
            }

            is ApiResult.Error -> {
                val failedState = TransactionState.TicketCreationFailed(
                    message = ErrorMessages.forCode(result.code, result.message),
                    code = result.code,
                )
                Timber.tag("TXN").e("[%s] Transaction failed at %s: %s", correlationId, "createTicket", result.message)
                emitAndPersist(failedState)
                running = false
            }
        }
    }

    // -- Step 2: Charge Payment -----------------------------------------------

    private suspend fun executeChargePayment(ticket: Ticket) {
        val amount = ticket.totalAmount ?: 0.0
        currentAmount = amount

        emitAndPersist(TransactionState.ChargingPayment(ticket))

        when (val result = paymentRepository.charge(ticketId = ticket.id, amount = amount)) {
            is ChargeResult.Success -> {
                val updatedTicket = result.ticket
                currentTicket = updatedTicket
                currentTransactionId = result.transactionId
                Timber.tag("TXN").i("[%s] Payment charged: transactionId=%s, amount=%.2f", correlationId, result.transactionId, amount)
                emitAndPersist(
                    TransactionState.PaymentCharged(
                        ticket = updatedTicket,
                        transactionId = result.transactionId,
                    )
                )

                // Auto-advance to printing
                executePrintLabel(updatedTicket)
            }

            is ChargeResult.Cancelled -> {
                Timber.tag("TXN").e("[%s] Transaction failed at %s: %s", correlationId, "chargePayment", "cancelled")
                emitAndPersist(TransactionState.PaymentCancelled(ticket))
                running = false
            }

            is ChargeResult.ReaderFailed -> {
                Timber.tag("TXN").e("[%s] Transaction failed at %s: %s", correlationId, "chargePayment", result.message)
                emitAndPersist(
                    TransactionState.PaymentFailed(
                        ticket = ticket,
                        message = result.message,
                    )
                )
                running = false
            }

            is ChargeResult.PaymentSucceededButApiCallFailed -> {
                currentTransactionId = result.transactionId
                Timber.tag("TXN").w("[%s] Payment succeeded but API failed: %s", correlationId, result.apiErrorMessage)
                emitAndPersist(
                    TransactionState.PaymentSucceededApiFailed(
                        ticket = ticket,
                        transactionId = result.transactionId,
                        amount = result.amount,
                        apiErrorMessage = result.apiErrorMessage,
                    )
                )
                running = false
            }
        }
    }

    // -- Step 3: Print Label --------------------------------------------------

    private suspend fun executePrintLabel(ticket: Ticket) {
        emitAndPersist(TransactionState.PrintingLabel(ticket))

        // Ensure printer is connected before attempting to print.
        // printWithRetry now calls ensureConnected() internally, but we log
        // the attempt for correlation tracing.
        Timber.tag("TXN").d(
            "[%s] Preparing print: connected=%b, selectedPrinter=%s",
            correlationId,
            printRepository.isConnected(),
            printRepository.getSelectedPrinter()?.name ?: "none",
        )

        val label = ticket.toLabelData()

        when (val result = printRepository.printWithRetry(label)) {
            is PrintResult.Success -> {
                Timber.tag("TXN").i("[%s] Label printed: printer=%s", correlationId, printRepository.getSelectedPrinter()?.name ?: "unknown")
                emitAndPersist(TransactionState.LabelPrinted(ticket))
                // Auto-advance to complete
                finalize(ticket)
            }

            is PrintResult.Error -> {
                Timber.tag("TXN").e("[%s] Transaction failed at %s: %s", correlationId, "printLabel", result.message)
                emitAndPersist(
                    TransactionState.PrintFailed(
                        ticket = ticket,
                        message = result.message,
                    )
                )
                // Do NOT mark running=false -- operator can skip or retry
            }
        }
    }

    // -- Step 4: Finalize -----------------------------------------------------

    private suspend fun finalize(ticket: Ticket) {
        Timber.tag("TXN").i("[%s] Transaction completed", correlationId)
        val completedState = TransactionState.Completed(ticket)
        emitAndPersist(completedState)
        persistenceId?.let { persistence.markCompleted(it) }
        running = false
        CorrelationContext.clear()
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
                correlationId?.let { CorrelationContext.set(it) }
                executeCreateTicket(draft)
            }

            is TransactionState.PaymentFailed -> {
                val ticket = current.ticket
                running = true
                correlationId?.let { CorrelationContext.set(it) }
                executeChargePayment(ticket)
            }

            is TransactionState.PaymentCancelled -> {
                val ticket = current.ticket
                running = true
                correlationId?.let { CorrelationContext.set(it) }
                executeChargePayment(ticket)
            }

            is TransactionState.PaymentSucceededApiFailed -> {
                // Only retry the API sync -- the card was already charged
                running = true
                correlationId?.let { CorrelationContext.set(it) }
                retryApiSync(current)
            }

            is TransactionState.PrintFailed -> {
                val ticket = current.ticket
                running = true
                correlationId?.let { CorrelationContext.set(it) }
                executePrintLabel(ticket)
            }

            else -> {
                Timber.w("TransactionOrchestrator: retryCurrentStep ignored in state %s", current)
            }
        }
    }

    private suspend fun retryApiSync(failedState: TransactionState.PaymentSucceededApiFailed) {
        val ticket = failedState.ticket
        emitAndPersist(TransactionState.ChargingPayment(ticket))

        val result = paymentRepository.syncPaymentToBackend(
            ticketId = ticket.id,
            transactionId = failedState.transactionId,
            amount = failedState.amount,
        )

        when (result) {
            is ChargeResult.Success -> {
                val updatedTicket = result.ticket
                currentTicket = updatedTicket
                emitAndPersist(
                    TransactionState.PaymentCharged(
                        ticket = updatedTicket,
                        transactionId = result.transactionId,
                    )
                )
                executePrintLabel(updatedTicket)
            }

            is ChargeResult.PaymentSucceededButApiCallFailed -> {
                emitAndPersist(
                    TransactionState.PaymentSucceededApiFailed(
                        ticket = ticket,
                        transactionId = result.transactionId,
                        amount = result.amount,
                        apiErrorMessage = result.apiErrorMessage,
                    )
                )
                running = false
            }

            // These shouldn't happen during API-only retry, but handle defensively
            is ChargeResult.Cancelled -> {
                emitAndPersist(TransactionState.PaymentCancelled(ticket))
                running = false
            }

            is ChargeResult.ReaderFailed -> {
                emitAndPersist(
                    TransactionState.PaymentFailed(ticket = ticket, message = result.message)
                )
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
        persistenceId = null
        correlationId = null
        CorrelationContext.clear()
    }

    /**
     * Cancel the current transaction and persist the cancellation.
     */
    suspend fun cancelAndPersist() {
        persistenceId?.let { persistence.markCancelled(it) }
        reset()
    }

    // -- Persistence helper ---------------------------------------------------

    private suspend fun emitAndPersist(newState: TransactionState) {
        _state.value = newState
        try {
            persistenceId = persistence.save(
                state = newState,
                correlationId = correlationId ?: UUID.randomUUID().toString(),
                draft = currentDraft,
                transactionId = persistenceId,
            )
        } catch (e: Exception) {
            // Persistence failure must not break the transaction flow
            Timber.e(e, "TransactionOrchestrator: failed to persist state %s", newState)
        }
    }
}
