package com.cleanx.lcx.feature.printing.di

import com.cleanx.lcx.feature.printing.data.PrinterManager
import com.cleanx.lcx.feature.printing.data.StubPrinterManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires [PrinterManager] to its current implementation.
 *
 * Swap [StubPrinterManager] for the real Brother SDK implementation once the
 * physical AAR is available.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrintModule {

    @Binds
    @Singleton
    abstract fun bindPrinterManager(impl: StubPrinterManager): PrinterManager
}
