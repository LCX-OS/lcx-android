package com.cleanx.lcx.feature.payments.data

import android.content.Context
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Simulated scenario mode for the stub payment manager.
 *
 * When set to a value other than [Random], the stub will always return
 * the corresponding result — useful for demos and deterministic testing.
 */
enum class SimulatedScenario {
    /** Outcome is chosen randomly based on [StubPaymentManager.successRate]. */
    Random,
    /** Always returns [PaymentResult.Success]. */
    AlwaysSuccess,
    /** Always returns [PaymentResult.Cancelled]. */
    AlwaysCancelled,
    /** Always returns [PaymentResult.Failed]. */
    AlwaysFailed,
}

/**
 * Development / testing stub that simulates the Zettle card reader flow.
 *
 * Default distribution: 80 % success, 10 % cancel, 10 % error.
 * Adds a configurable delay to mimic the reader interaction time.
 *
 * All operations are logged via Timber with the `[STUB-ZETTLE]` tag prefix.
 *
 * Replace with [ZettlePaymentManager] once the real SDK dependency is wired.
 */
@Singleton
class StubPaymentManager @Inject constructor() : PaymentManager {

    @Volatile
    private var initialized = false

    /**
     * Simulated delay in milliseconds before returning a payment result.
     * Set to a lower value for fast unit tests, or a higher value for
     * realistic demo behavior.
     */
    var delayMs: Long = DEFAULT_DELAY_MS

    /**
     * Success rate as a percentage (0..100).
     * The remaining percentage is split equally between cancel and failure.
     */
    var successRate: Int = DEFAULT_SUCCESS_RATE

    /**
     * When set to a value other than [SimulatedScenario.Random], forces
     * a deterministic outcome for every payment request.
     */
    var scenario: SimulatedScenario = SimulatedScenario.Random

    override suspend fun initialize(context: Context) {
        Timber.d("[STUB-ZETTLE] initialize (no-op)")
        initialized = true
    }

    override fun isInitialized(): Boolean = initialized

    override suspend fun requestPayment(amount: Double, reference: String): PaymentResult {
        Timber.d("[STUB-ZETTLE] requestPayment amount=%.2f ref=%s scenario=%s", amount, reference, scenario)

        delay(delayMs)

        val result = when (scenario) {
            SimulatedScenario.AlwaysSuccess -> buildSuccess(amount, reference)
            SimulatedScenario.AlwaysCancelled -> buildCancelled(reference)
            SimulatedScenario.AlwaysFailed -> buildFailed(reference)
            SimulatedScenario.Random -> randomOutcome(amount, reference)
        }

        Timber.d("[STUB-ZETTLE] requestPayment result=%s", result)
        return result
    }

    // -- internal helpers --

    private fun randomOutcome(amount: Double, reference: String): PaymentResult {
        val roll = Random.nextInt(100)
        val cancelThreshold = successRate + (100 - successRate) / 2
        return when {
            roll < successRate -> buildSuccess(amount, reference)
            roll < cancelThreshold -> buildCancelled(reference)
            else -> buildFailed(reference)
        }
    }

    private fun buildSuccess(amount: Double, reference: String) = PaymentResult.Success(
        transactionId = UUID.randomUUID().toString(),
        amount = amount,
        reference = reference,
    )

    private fun buildCancelled(reference: String) = PaymentResult.Cancelled(
        reference = reference,
    )

    private fun buildFailed(reference: String) = PaymentResult.Failed(
        errorCode = "STUB_ERROR",
        message = "Error simulado del lector de tarjetas.",
        reference = reference,
    )

    companion object {
        const val DEFAULT_DELAY_MS = 2_000L
        const val DEFAULT_SUCCESS_RATE = 80
    }
}
