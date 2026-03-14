package com.cleanx.lcx.feature.payments.di

import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.StubPaymentManager
import com.cleanx.lcx.feature.payments.data.ZettlePaymentManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module that wires [PaymentManager].
 *
 * The binding is controlled by [FeatureFlags.useRealZettle]:
 * - `false` (dev) -> [StubPaymentManager]
 * - `true` -> [ZettlePaymentManager]
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    @Provides
    @Singleton
    fun providePaymentManager(
        featureFlags: FeatureFlags,
        stubPaymentManager: StubPaymentManager,
        zettlePaymentManager: ZettlePaymentManager,
    ): PaymentManager {
        return if (featureFlags.useRealZettle) {
            Timber.i("Using ZettlePaymentManager (USE_REAL_ZETTLE=true)")
            zettlePaymentManager
        } else {
            Timber.d("Using StubPaymentManager (USE_REAL_ZETTLE=false)")
            stubPaymentManager
        }
    }
}
