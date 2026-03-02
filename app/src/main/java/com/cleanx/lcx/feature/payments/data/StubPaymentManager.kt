package com.cleanx.lcx.feature.payments.data

import android.content.Context
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Development / testing stub that simulates the Zettle card reader flow.
 *
 * Distribution: 80 % success · 10 % cancel · 10 % error.
 * Adds a 2-second delay to mimic the reader interaction time.
 *
 * Replace with [ZettlePaymentManager] once the real SDK dependency is wired.
 */
@Singleton
class StubPaymentManager @Inject constructor() : PaymentManager {

    @Volatile
    private var initialized = false

    override suspend fun initialize(context: Context) {
        Timber.d("StubPaymentManager: initialize (no-op)")
        initialized = true
    }

    override fun isInitialized(): Boolean = initialized

    override suspend fun requestPayment(amount: Double, reference: String): PaymentResult {
        Timber.d("StubPaymentManager: requestPayment amount=%.2f ref=%s", amount, reference)

        // Simulate the reader interaction time.
        delay(SIMULATED_DELAY_MS)

        val roll = Random.nextInt(100)
        return when {
            roll < SUCCESS_THRESHOLD -> PaymentResult.Success(
                transactionId = UUID.randomUUID().toString(),
                amount = amount,
                reference = reference,
            )
            roll < CANCEL_THRESHOLD -> PaymentResult.Cancelled(reference = reference)
            else -> PaymentResult.Failed(
                errorCode = "STUB_ERROR",
                message = "Error simulado del lector de tarjetas.",
                reference = reference,
            )
        }
    }

    private companion object {
        const val SIMULATED_DELAY_MS = 2_000L
        const val SUCCESS_THRESHOLD = 80   // 0..79  → success (80 %)
        const val CANCEL_THRESHOLD = 90    // 80..89 → cancel  (10 %)
        // 90..99 → failure (10 %)
    }
}
