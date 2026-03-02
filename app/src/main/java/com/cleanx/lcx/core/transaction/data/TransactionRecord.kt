package com.cleanx.lcx.core.transaction.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted in-flight transaction.
 *
 * Stores enough context to resume the transaction flow after process death
 * or app restart. Completed/cancelled records are kept for auditing and
 * cleaned up periodically.
 */
@Entity(tableName = "active_transactions")
data class TransactionRecord(
    @PrimaryKey val id: String,
    val correlationId: String,
    val phase: String,
    val ticketDraftJson: String,
    val ticketId: String?,
    val ticketJson: String?,
    val paymentTransactionId: String?,
    val paymentAmount: Double?,
    val errorMessage: String?,
    val errorCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
