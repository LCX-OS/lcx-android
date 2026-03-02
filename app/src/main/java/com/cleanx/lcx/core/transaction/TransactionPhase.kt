package com.cleanx.lcx.core.transaction

/**
 * Enum representation of [TransactionState] phases for Room persistence.
 *
 * Each value maps 1:1 to a sealed-interface variant. This is stored as a
 * plain string in the database and used to determine where to resume a
 * transaction after process death.
 */
enum class TransactionPhase {
    IDLE,
    CREATING_TICKET,
    TICKET_CREATED,
    TICKET_CREATION_FAILED,
    CHARGING_PAYMENT,
    PAYMENT_CHARGED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PAYMENT_SUCCEEDED_API_FAILED,
    PRINTING_LABEL,
    LABEL_PRINTED,
    PRINT_FAILED,
    COMPLETED,
    CANCELLED,
    ;

    companion object {
        fun fromState(state: TransactionState): TransactionPhase = when (state) {
            is TransactionState.Idle -> IDLE
            is TransactionState.CreatingTicket -> CREATING_TICKET
            is TransactionState.TicketCreated -> TICKET_CREATED
            is TransactionState.TicketCreationFailed -> TICKET_CREATION_FAILED
            is TransactionState.ChargingPayment -> CHARGING_PAYMENT
            is TransactionState.PaymentCharged -> PAYMENT_CHARGED
            is TransactionState.PaymentFailed -> PAYMENT_FAILED
            is TransactionState.PaymentCancelled -> PAYMENT_CANCELLED
            is TransactionState.PaymentSucceededApiFailed -> PAYMENT_SUCCEEDED_API_FAILED
            is TransactionState.PrintingLabel -> PRINTING_LABEL
            is TransactionState.LabelPrinted -> LABEL_PRINTED
            is TransactionState.PrintFailed -> PRINT_FAILED
            is TransactionState.Completed -> COMPLETED
        }
    }
}
