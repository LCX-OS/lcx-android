package com.cleanx.lcx.feature.payments.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val PAYMENT_REQUIRES_RECONCILIATION_ERROR_CODE = "PAYMENT_REQUIRES_RECONCILIATION"

enum class PaymentAttemptStatus {
    PAYMENT_IN_PROGRESS,
    BACKEND_SYNC_FAILED,
    REQUIRES_RECONCILIATION,
}

data class PaymentAttempt(
    val ticketId: String,
    val amount: Double,
    val reference: String,
    val status: PaymentAttemptStatus,
    val createdAtMillis: Long,
    val transactionId: String? = null,
    val reason: String? = null,
)

sealed interface BeginPaymentAttemptResult {
    data class Started(val attempt: PaymentAttempt) : BeginPaymentAttemptResult
    data class Blocked(val attempt: PaymentAttempt) : BeginPaymentAttemptResult
    data class Failed(val message: String) : BeginPaymentAttemptResult
}

interface PaymentAttemptStore {
    fun get(ticketId: String): PaymentAttempt?
    fun begin(ticketId: String, amount: Double, reference: String): BeginPaymentAttemptResult
    fun markBackendSyncFailed(
        ticketId: String,
        amount: Double,
        reference: String,
        transactionId: String,
        reason: String,
    ): Boolean
    fun markRequiresReconciliation(ticketId: String, reference: String, reason: String): Boolean
    fun clear(ticketId: String, reference: String): Boolean
}

@Singleton
class SharedPreferencesPaymentAttemptStore @Inject constructor(
    @ApplicationContext context: Context,
) : PaymentAttemptStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun get(ticketId: String): PaymentAttempt? = synchronized(lock) {
        read(ticketId)
    }

    override fun begin(
        ticketId: String,
        amount: Double,
        reference: String,
    ): BeginPaymentAttemptResult = synchronized(lock) {
        val existing = read(ticketId)
        if (existing != null) return@synchronized BeginPaymentAttemptResult.Blocked(existing)

        val attempt = PaymentAttempt(
            ticketId = ticketId,
            amount = amount,
            reference = reference,
            status = PaymentAttemptStatus.PAYMENT_IN_PROGRESS,
            createdAtMillis = System.currentTimeMillis(),
        )
        if (write(attempt)) {
            BeginPaymentAttemptResult.Started(attempt)
        } else {
            BeginPaymentAttemptResult.Failed(
                "No se pudo guardar el bloqueo local de cobro. No inicies el cargo hasta revisar el dispositivo.",
            )
        }
    }

    override fun markBackendSyncFailed(
        ticketId: String,
        amount: Double,
        reference: String,
        transactionId: String,
        reason: String,
    ): Boolean = synchronized(lock) {
        val existing = read(ticketId)
        if (existing != null && existing.reference != reference) return@synchronized false
        val attempt = existing?.copy(
            status = PaymentAttemptStatus.BACKEND_SYNC_FAILED,
            transactionId = transactionId,
            reason = reason,
        ) ?: PaymentAttempt(
            ticketId = ticketId,
            amount = amount,
            reference = reference,
            status = PaymentAttemptStatus.BACKEND_SYNC_FAILED,
            createdAtMillis = System.currentTimeMillis(),
            transactionId = transactionId,
            reason = reason,
        )
        write(attempt)
    }

    override fun markRequiresReconciliation(
        ticketId: String,
        reference: String,
        reason: String,
    ): Boolean = synchronized(lock) {
        val existing = read(ticketId) ?: return@synchronized false
        if (existing.reference != reference) return@synchronized false
        write(
            existing.copy(
                status = PaymentAttemptStatus.REQUIRES_RECONCILIATION,
                reason = reason,
            ),
        )
    }

    override fun clear(ticketId: String, reference: String): Boolean = synchronized(lock) {
        val existing = read(ticketId) ?: return@synchronized true
        if (existing.reference != reference) return@synchronized false
        prefs.edit()
            .remove(key(ticketId, KEY_AMOUNT))
            .remove(key(ticketId, KEY_REFERENCE))
            .remove(key(ticketId, KEY_STATUS))
            .remove(key(ticketId, KEY_CREATED_AT))
            .remove(key(ticketId, KEY_TRANSACTION_ID))
            .remove(key(ticketId, KEY_REASON))
            .commit()
    }

    private fun read(ticketId: String): PaymentAttempt? {
        val reference = prefs.getString(key(ticketId, KEY_REFERENCE), null) ?: return null
        val statusName = prefs.getString(key(ticketId, KEY_STATUS), null) ?: return null
        val status = runCatching { PaymentAttemptStatus.valueOf(statusName) }.getOrNull() ?: return null
        return PaymentAttempt(
            ticketId = ticketId,
            amount = Double.fromBits(prefs.getLong(key(ticketId, KEY_AMOUNT), 0L)),
            reference = reference,
            status = status,
            createdAtMillis = prefs.getLong(key(ticketId, KEY_CREATED_AT), 0L),
            transactionId = prefs.getString(key(ticketId, KEY_TRANSACTION_ID), null),
            reason = prefs.getString(key(ticketId, KEY_REASON), null),
        )
    }

    private fun write(attempt: PaymentAttempt): Boolean {
        return prefs.edit()
            .putLong(key(attempt.ticketId, KEY_AMOUNT), attempt.amount.toBits())
            .putString(key(attempt.ticketId, KEY_REFERENCE), attempt.reference)
            .putString(key(attempt.ticketId, KEY_STATUS), attempt.status.name)
            .putLong(key(attempt.ticketId, KEY_CREATED_AT), attempt.createdAtMillis)
            .putString(key(attempt.ticketId, KEY_TRANSACTION_ID), attempt.transactionId)
            .putString(key(attempt.ticketId, KEY_REASON), attempt.reason)
            .commit()
    }

    private fun key(ticketId: String, field: String): String = "$KEY_PREFIX$ticketId.$field"

    private companion object {
        const val PREFS_NAME = "lcx_payment_attempts"
        const val KEY_PREFIX = "payment_attempt."
        const val KEY_AMOUNT = "amount"
        const val KEY_REFERENCE = "reference"
        const val KEY_STATUS = "status"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_TRANSACTION_ID = "transaction_id"
        const val KEY_REASON = "reason"
    }
}
