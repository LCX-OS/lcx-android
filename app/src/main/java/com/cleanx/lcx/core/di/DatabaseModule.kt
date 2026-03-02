package com.cleanx.lcx.core.di

import android.content.Context
import androidx.room.Room
import com.cleanx.lcx.core.transaction.data.LcxDatabase
import com.cleanx.lcx.core.transaction.data.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLcxDatabase(@ApplicationContext context: Context): LcxDatabase {
        return Room.databaseBuilder(
            context,
            LcxDatabase::class.java,
            "lcx_database",
        ).build()
    }

    @Provides
    fun provideTransactionDao(database: LcxDatabase): TransactionDao {
        return database.transactionDao()
    }
}
