package com.cleanx.lcx.feature.payments.data

import android.content.Context

/**
 * Abstraction over a card-payment SDK (currently PayPal Zettle).
 *
 * The real implementation will delegate to [CardReaderAction.Payment] from
 * `com.zettle.sdk.feature.cardreader`, but all callers depend only on this
 * interface so the SDK can be swapped or stubbed without touching domain logic.
 */
interface PaymentManager {

    /**
     * One-time SDK bootstrap.  Must be called from [android.app.Application.onCreate]
     * (or before the first payment attempt).
     */
    suspend fun initialize(context: Context)

    /** `true` after [initialize] completes successfully. */
    fun isInitialized(): Boolean

    /**
     * Launch the card reader flow for the given [amount] (in the merchant's
     * currency, as a decimal — e.g. 150.00 for $150 MXN).
     *
     * @param amount   Charge amount in the local currency.
     * @param reference An idempotency / tracing key (typically the ticket ID).
     * @return a sealed [PaymentResult] — callers must handle all three branches.
     */
    suspend fun requestPayment(amount: Double, reference: String): PaymentResult
}

/**
 * Outcome of a single card-reader payment attempt.
 *
 * [Success]   — card was charged; includes the SDK transaction ID.
 * [Cancelled] — operator or customer dismissed the reader UI.
 * [Failed]    — an error the SDK reported (network, reader, auth, etc.).
 */
sealed class PaymentResult {

    data class Success(
        val transactionId: String,
        val amount: Double,
        val reference: String,
    ) : PaymentResult()

    data class Cancelled(
        val reference: String,
    ) : PaymentResult()

    data class Failed(
        val errorCode: String,
        val message: String,
        val reference: String,
    ) : PaymentResult()
}
