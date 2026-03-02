package com.cleanx.lcx.core.transaction.data

import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.TransactionPhase
import com.cleanx.lcx.core.transaction.TransactionState
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [com.cleanx.lcx.core.transaction.TransactionOrchestrator]
 * and Room. Persists every state transition so in-flight transactions
 * survive process death.
 */
@Singleton
class TransactionPersistence @Inject constructor(
    private val dao: TransactionDao,
    private val json: Json,
) {

    /**
     * Persist the current transaction state. If [transactionId] is null a
     * new UUID is generated (first save).
     */
    suspend fun save(
        state: TransactionState,
        correlationId: String,
        draft: TicketDraft?,
        transactionId: String? = null,
    ): String {
        val phase = TransactionPhase.fromState(state)
        val now = System.currentTimeMillis()
        val id = transactionId ?: UUID.randomUUID().toString()

        val ticket = extractTicket(state)
        val paymentTxnId = extractPaymentTransactionId(state)
        val paymentAmount = extractPaymentAmount(state)
        val errorMessage = extractErrorMessage(state)
        val errorCode = extractErrorCode(state)

        val record = TransactionRecord(
            id = id,
            correlationId = correlationId,
            phase = phase.name,
            ticketDraftJson = draft?.let { json.encodeToString(TicketDraft.serializer(), it) } ?: "",
            ticketId = ticket?.id,
            ticketJson = ticket?.let { json.encodeToString(Ticket.serializer(), it) },
            paymentTransactionId = paymentTxnId,
            paymentAmount = paymentAmount,
            errorMessage = errorMessage,
            errorCode = errorCode,
            createdAt = dao.getById(id)?.createdAt ?: now,
            updatedAt = now,
        )

        dao.upsert(record)
        Timber.d("TransactionPersistence: saved phase=%s id=%s", phase, id)
        return id
    }

    /**
     * Load the most recent active (non-completed, non-cancelled) transaction.
     */
    suspend fun loadActiveTransaction(): SavedTransaction? {
        val record = dao.getActiveTransaction() ?: return null
        return recordToSaved(record)
    }

    /**
     * Load a specific transaction by ID.
     */
    suspend fun loadById(id: String): SavedTransaction? {
        val record = dao.getById(id) ?: return null
        return recordToSaved(record)
    }

    /**
     * Mark a transaction as completed.
     */
    suspend fun markCompleted(id: String) {
        dao.updatePhase(id, TransactionPhase.COMPLETED.name, System.currentTimeMillis())
        Timber.d("TransactionPersistence: markCompleted id=%s", id)
    }

    /**
     * Mark a transaction as cancelled.
     */
    suspend fun markCancelled(id: String) {
        dao.updatePhase(id, TransactionPhase.CANCELLED.name, System.currentTimeMillis())
        Timber.d("TransactionPersistence: markCancelled id=%s", id)
    }

    /**
     * Remove old completed/cancelled records older than [maxAge] millis
     * (default 7 days).
     */
    suspend fun cleanup(maxAge: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAge
        dao.cleanupOldRecords(cutoff)
        Timber.d("TransactionPersistence: cleanup older than %d", cutoff)
    }

    // -- Internal helpers -------------------------------------------------

    private fun recordToSaved(record: TransactionRecord): SavedTransaction {
        val phase = try {
            TransactionPhase.valueOf(record.phase)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Unknown phase: %s, defaulting to IDLE", record.phase)
            TransactionPhase.IDLE
        }

        val draft = if (record.ticketDraftJson.isNotBlank()) {
            try {
                json.decodeFromString(TicketDraft.serializer(), record.ticketDraftJson)
            } catch (e: Exception) {
                Timber.w(e, "Failed to deserialize TicketDraft")
                null
            }
        } else null

        val ticket = record.ticketJson?.let {
            try {
                json.decodeFromString(Ticket.serializer(), it)
            } catch (e: Exception) {
                Timber.w(e, "Failed to deserialize Ticket")
                null
            }
        }

        return SavedTransaction(
            id = record.id,
            correlationId = record.correlationId,
            phase = phase,
            draft = draft,
            ticket = ticket,
            paymentTransactionId = record.paymentTransactionId,
            paymentAmount = record.paymentAmount,
            errorMessage = record.errorMessage,
            errorCode = record.errorCode,
        )
    }

    private fun extractTicket(state: TransactionState): Ticket? = when (state) {
        is TransactionState.TicketCreated -> state.ticket
        is TransactionState.ChargingPayment -> state.ticket
        is TransactionState.PaymentCharged -> state.ticket
        is TransactionState.PaymentFailed -> state.ticket
        is TransactionState.PaymentCancelled -> state.ticket
        is TransactionState.PaymentSucceededApiFailed -> state.ticket
        is TransactionState.PrintingLabel -> state.ticket
        is TransactionState.LabelPrinted -> state.ticket
        is TransactionState.PrintFailed -> state.ticket
        is TransactionState.Completed -> state.ticket
        else -> null
    }

    private fun extractPaymentTransactionId(state: TransactionState): String? = when (state) {
        is TransactionState.PaymentCharged -> state.transactionId
        is TransactionState.PaymentSucceededApiFailed -> state.transactionId
        else -> null
    }

    private fun extractPaymentAmount(state: TransactionState): Double? = when (state) {
        is TransactionState.PaymentSucceededApiFailed -> state.amount
        else -> null
    }

    private fun extractErrorMessage(state: TransactionState): String? = when (state) {
        is TransactionState.TicketCreationFailed -> state.message
        is TransactionState.PaymentFailed -> state.message
        is TransactionState.PaymentSucceededApiFailed -> state.apiErrorMessage
        is TransactionState.PrintFailed -> state.message
        else -> null
    }

    private fun extractErrorCode(state: TransactionState): String? = when (state) {
        is TransactionState.TicketCreationFailed -> state.code
        else -> null
    }
}
