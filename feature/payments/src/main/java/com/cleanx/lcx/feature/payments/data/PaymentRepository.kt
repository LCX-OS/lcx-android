package com.cleanx.lcx.feature.payments.data

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of the full charge flow (reader + API update).
 */
sealed interface ChargeResult {

    /** Card charged AND backend updated successfully. */
    data class Success(
        val ticket: Ticket,
        val transactionId: String,
    ) : ChargeResult

    /** Operator or customer cancelled the card reader. */
    data class Cancelled(val reference: String) : ChargeResult

    /** Card reader reported an error — no charge was made. */
    data class ReaderFailed(
        val errorCode: String,
        val message: String,
    ) : ChargeResult

    /**
     * CRITICAL EDGE CASE: the card was charged successfully but the
     * backend PATCH failed.  The operator MUST be told the card was charged
     * so they can resolve this manually or retry the API call.
     */
    data class PaymentSucceededButApiCallFailed(
        val transactionId: String,
        val amount: Double,
        val apiErrorMessage: String,
    ) : ChargeResult
}

/**
 * Coordinates:
 *   1. [PaymentManager.requestPayment]  — card reader / Zettle SDK
 *   2. [TicketRepository.updatePayment] — backend PATCH
 *
 * If step 1 succeeds but step 2 fails, the result is
 * [ChargeResult.PaymentSucceededButApiCallFailed] so the caller can
 * warn the operator that the card WAS charged.
 */
@Singleton
class PaymentRepository @Inject constructor(
    private val paymentManager: PaymentManager,
    private val ticketRepository: TicketRepository,
    private val paymentAttemptStore: PaymentAttemptStore,
) {

    /**
     * Execute the full charge flow for [ticketId].
     *
     * @param ticketId  Ticket UUID.
     * @param amount    Amount in the merchant's currency (e.g. 150.00 MXN).
     */
    suspend fun charge(ticketId: String, amount: Double): ChargeResult {
        Timber.tag("PAYMENT").i("Starting charge: amount=%.2f, reference=%s", amount, ticketId)
        val attempt = when (
            val beginResult = paymentAttemptStore.begin(
                ticketId = ticketId,
                amount = amount,
                reference = ticketId,
            )
        ) {
            is BeginPaymentAttemptResult.Started -> beginResult.attempt
            is BeginPaymentAttemptResult.Blocked -> {
                Timber.tag("PAYMENT").w(
                    "Blocking charge because payment attempt already exists: ticketId=%s status=%s reference=%s",
                    ticketId,
                    beginResult.attempt.status,
                    beginResult.attempt.reference,
                )
                return ChargeResult.ReaderFailed(
                    errorCode = PAYMENT_REQUIRES_RECONCILIATION_ERROR_CODE,
                    message = beginResult.attempt.toBlockedChargeMessage(),
                )
            }
            is BeginPaymentAttemptResult.Failed -> {
                Timber.tag("PAYMENT").e(
                    "Blocking charge because payment attempt guard could not be persisted: ticketId=%s",
                    ticketId,
                )
                return ChargeResult.ReaderFailed(
                    errorCode = PAYMENT_REQUIRES_RECONCILIATION_ERROR_CODE,
                    message = beginResult.message,
                )
            }
        }

        // --- Step 1: card reader ---
        val paymentResult = paymentManager.requestPayment(
            amount = amount,
            reference = ticketId,
        )

        return when (paymentResult) {
            is PaymentResult.Cancelled -> {
                Timber.tag("PAYMENT").i("Charge result: %s", "Cancelled")
                paymentAttemptStore.clear(ticketId = ticketId, reference = attempt.reference)
                ChargeResult.Cancelled(reference = ticketId)
            }

            is PaymentResult.Failed -> {
                Timber.tag("PAYMENT").i(
                    "Charge result: Failed code=%s reference=%s",
                    paymentResult.errorCode,
                    ticketId,
                )
                if (paymentResult.requiresReconciliation()) {
                    val persisted = paymentAttemptStore.markRequiresReconciliation(
                        ticketId = ticketId,
                        reference = attempt.reference,
                        reason = paymentResult.message,
                    )
                    if (!persisted) {
                        Timber.tag("PAYMENT").e(
                            "Failed to persist reconciliation guard for ticketId=%s reference=%s",
                            ticketId,
                            attempt.reference,
                        )
                    }
                    ChargeResult.ReaderFailed(
                        errorCode = PAYMENT_REQUIRES_RECONCILIATION_ERROR_CODE,
                        message = if (persisted) {
                            "Zettle no devolvio un resultado confiable para el ticket $ticketId. " +
                                "No intentes cobrar de nuevo hasta conciliar este intento."
                        } else {
                            "Zettle no devolvio un resultado confiable y no se pudo guardar el bloqueo local. " +
                                "No intentes cobrar de nuevo hasta conciliar este intento."
                        },
                    )
                } else {
                    paymentAttemptStore.clear(ticketId = ticketId, reference = attempt.reference)
                    ChargeResult.ReaderFailed(
                        errorCode = paymentResult.errorCode,
                        message = paymentResult.message,
                    )
                }
            }

            is PaymentResult.Success -> {
                Timber.tag("PAYMENT").i(
                    "Charge result: Success transactionId=%s reference=%s",
                    paymentResult.transactionId,
                    ticketId,
                )
                // --- Step 2: persist to backend ---
                syncPaymentToBackend(
                    ticketId = ticketId,
                    transactionId = paymentResult.transactionId,
                    amount = paymentResult.amount,
                )
            }
        }
    }

    /**
     * Attempt to PATCH the ticket's payment status in the backend.
     * Can also be called standalone to retry a previously-failed sync.
     */
    suspend fun syncPaymentToBackend(
        ticketId: String,
        transactionId: String,
        amount: Double,
    ): ChargeResult {
        val apiResult = ticketRepository.updatePayment(
            ticketId = ticketId,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = amount,
        )

        return when (apiResult) {
            is ApiResult.Success -> {
                Timber.tag("PAYMENT").i("Charge result: %s", "Success")
                paymentAttemptStore.clear(ticketId = ticketId, reference = ticketId)
                ChargeResult.Success(
                    ticket = apiResult.data,
                    transactionId = transactionId,
                )
            }

            is ApiResult.Error -> {
                Timber.tag("PAYMENT").w("Payment succeeded but API sync failed: %s", apiResult.message)
                val persisted = paymentAttemptStore.markBackendSyncFailed(
                    ticketId = ticketId,
                    amount = amount,
                    reference = ticketId,
                    transactionId = transactionId,
                    reason = apiResult.message,
                )
                if (!persisted) {
                    Timber.tag("PAYMENT").e(
                        "Failed to persist backend-sync-failed guard for ticketId=%s transactionId=%s",
                        ticketId,
                        transactionId,
                    )
                }
                ChargeResult.PaymentSucceededButApiCallFailed(
                    transactionId = transactionId,
                    amount = amount,
                    apiErrorMessage = if (persisted) {
                        apiResult.message
                    } else {
                        "${apiResult.message} Ademas, no se pudo guardar el bloqueo local contra doble cobro."
                    },
                )
            }
        }
    }
}

private fun PaymentResult.Failed.requiresReconciliation(): Boolean =
    errorCode == "ZETTLE_TIMEOUT" || errorCode == "ZETTLE_EMPTY_RESULT"

private fun PaymentAttempt.toBlockedChargeMessage(): String = when (status) {
    PaymentAttemptStatus.PAYMENT_IN_PROGRESS ->
        "Ya existe un intento de cobro en progreso para este ticket. " +
            "No se puede iniciar otro cargo hasta cancelar o conciliar el intento anterior."
    PaymentAttemptStatus.BACKEND_SYNC_FAILED ->
        "El cobro ya fue aprobado por Zettle, pero falta sincronizarlo con LCX. " +
            "No se puede iniciar otro cargo; reintenta la sincronizacion o concilia el ticket."
    PaymentAttemptStatus.REQUIRES_RECONCILIATION ->
        "Este ticket requiere conciliacion antes de intentar otro cobro. " +
            "Verifica Zettle con la referencia $reference."
}
