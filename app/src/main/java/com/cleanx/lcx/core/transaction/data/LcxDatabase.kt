package com.cleanx.lcx.core.transaction.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class LcxDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
