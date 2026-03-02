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
) {

    /**
     * Execute the full charge flow for [ticketId].
     *
     * @param ticketId  Ticket UUID.
     * @param amount    Amount in the merchant's currency (e.g. 150.00 MXN).
     */
    suspend fun charge(ticketId: String, amount: Double): ChargeResult {
        Timber.tag("PAYMENT").i("Starting charge: amount=%.2f, reference=%s", amount, ticketId)
        // --- Step 1: card reader ---
        val paymentResult = paymentManager.requestPayment(
            amount = amount,
            reference = ticketId,
        )

        return when (paymentResult) {
            is PaymentResult.Cancelled -> {
                Timber.tag("PAYMENT").i("Charge result: %s", "Cancelled")
                ChargeResult.Cancelled(reference = ticketId)
            }

            is PaymentResult.Failed -> {
                Timber.tag("PAYMENT").i("Charge result: %s", "Failed")
                ChargeResult.ReaderFailed(
                    errorCode = paymentResult.errorCode,
                    message = paymentResult.message,
                )
            }

            is PaymentResult.Success -> {
                Timber.tag("PAYMENT").i("Charge result: %s", "Success")
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
                ChargeResult.Success(
                    ticket = apiResult.data,
                    transactionId = transactionId,
                )
            }

            is ApiResult.Error -> {
                Timber.tag("PAYMENT").w("Payment succeeded but API sync failed: %s", apiResult.message)
                ChargeResult.PaymentSucceededButApiCallFailed(
                    transactionId = transactionId,
                    amount = amount,
                    apiErrorMessage = apiResult.message,
                )
            }
        }
    }
}
