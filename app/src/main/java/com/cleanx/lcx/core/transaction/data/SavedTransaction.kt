package com.cleanx.lcx.core.transaction.data

import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.TransactionPhase

/**
 * Domain representation of a persisted transaction, loaded from Room.
 * Contains all the context needed to resume the flow from [phase].
 */
data class SavedTransaction(
    val id: String,
    val correlationId: String,
    val phase: TransactionPhase,
    val draft: TicketDraft?,
    val ticket: Ticket?,
    val paymentTransactionId: String?,
    val paymentAmount: Double?,
    val errorMessage: String?,
    val errorCode: String?,
)
