package com.cleanx.lcx.feature.payments.di

import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.StubPaymentManager
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
 * - `true` (staging/prod) -> fails fast until the real Zettle SDK is integrated
 *
 * When the real Zettle SDK dependency is added, replace the `true` branch
 * with `ZettlePaymentManager(...)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    @Provides
    @Singleton
    fun providePaymentManager(
        featureFlags: FeatureFlags,
        stubPaymentManager: StubPaymentManager,
    ): PaymentManager {
        return if (featureFlags.useRealZettle) {
            Timber.w("USE_REAL_ZETTLE is true but SDK not yet integrated — failing fast")
            throw IllegalStateException(
                "El SDK real de Zettle aun no esta integrado. " +
                    "Establezca USE_REAL_ZETTLE=false en la configuracion de build."
            )
        } else {
            Timber.d("Using StubPaymentManager (USE_REAL_ZETTLE=false)")
            stubPaymentManager
        }
    }
}
