package com.cleanx.lcx.feature.printing.di

import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.printing.data.PrinterManager
import com.cleanx.lcx.feature.printing.data.StubPrinterManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module that wires [PrinterManager] to the correct implementation
 * based on the [FeatureFlags.useRealBrother] flag.
 *
 * - `dev`     → [StubPrinterManager] (no hardware required)
 * - `staging` → [StubPrinterManager] (Brother SDK not yet available)
 * - `prod`    → real Brother SDK implementation (when available)
 *
 * The flag is set per build variant in `app/build.gradle.kts`.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrintModule {

    @Provides
    @Singleton
    fun providePrinterManager(
        featureFlags: FeatureFlags,
        stubPrinterManager: StubPrinterManager,
    ): PrinterManager {
        return if (featureFlags.useRealBrother) {
            // TODO: return BrotherPrinterManager(…) once the real Brother SDK AAR
            //       is added as a dependency. For now, fall back to stub with a warning.
            Timber.w("useRealBrother=true but real Brother SDK not yet integrated; falling back to stub.")
            stubPrinterManager
        } else {
            Timber.d("PrintModule: using StubPrinterManager (useRealBrother=false)")
            stubPrinterManager
        }
    }
}
