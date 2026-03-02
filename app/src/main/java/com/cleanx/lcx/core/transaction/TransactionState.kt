package com.cleanx.lcx.core.transaction

import com.cleanx.lcx.core.model.Ticket

/**
 * All possible states of the P0 transaction flow:
 *
 *   IDLE -> CREATING_TICKET -> TICKET_CREATED ->
 *   CHARGING_PAYMENT -> PAYMENT_CHARGED ->
 *   PRINTING_LABEL -> LABEL_PRINTED ->
 *   PERSISTING -> COMPLETED
 *
 * Error branches are explicit sealed classes with contextual data.
 */
sealed interface TransactionState {

    /** Flow has not started yet. */
    data object Idle : TransactionState

    // -- Ticket creation --

    data object CreatingTicket : TransactionState

    data class TicketCreated(val ticket: Ticket) : TransactionState

    data class TicketCreationFailed(
        val message: String,
        val code: String? = null,
    ) : TransactionState

    // -- Payment --

    data class ChargingPayment(val ticket: Ticket) : TransactionState

    data class PaymentCharged(
        val ticket: Ticket,
        val transactionId: String,
    ) : TransactionState

    data class PaymentFailed(
        val ticket: Ticket,
        val message: String,
    ) : TransactionState

    data class PaymentCancelled(val ticket: Ticket) : TransactionState

    /**
     * CRITICAL: the card was charged but the backend PATCH failed.
     * The operator MUST be warned that money was collected.
     */
    data class PaymentSucceededApiFailed(
        val ticket: Ticket,
        val transactionId: String,
        val amount: Double,
        val apiErrorMessage: String,
    ) : TransactionState

    // -- Printing --

    data class PrintingLabel(val ticket: Ticket) : TransactionState

    data class LabelPrinted(val ticket: Ticket) : TransactionState

    data class PrintFailed(
        val ticket: Ticket,
        val message: String,
    ) : TransactionState

    // -- Final --

    data class Completed(val ticket: Ticket) : TransactionState
}
