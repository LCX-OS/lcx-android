package com.cleanx.lcx.core.transaction.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM active_transactions
        WHERE phase NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getActiveTransaction(): TransactionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: TransactionRecord)

    @Query("UPDATE active_transactions SET phase = :phase, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePhase(id: String, phase: String, updatedAt: Long)

    @Query(
        """
        DELETE FROM active_transactions
        WHERE phase IN ('COMPLETED', 'CANCELLED')
        AND updatedAt < :olderThan
        """
    )
    suspend fun cleanupOldRecords(olderThan: Long)

    @Query("SELECT * FROM active_transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionRecord?
}
