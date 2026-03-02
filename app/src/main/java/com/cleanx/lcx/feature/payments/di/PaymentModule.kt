package com.cleanx.lcx.feature.payments.di

import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.StubPaymentManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires [PaymentManager].
 *
 * Currently binds [StubPaymentManager] for development.
 * When the real Zettle SDK is integrated, swap the binding to
 * `ZettlePaymentManager` (and remove the stub, or keep it behind
 * a BuildConfig flag for automated tests).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentModule {

    @Binds
    @Singleton
    abstract fun bindPaymentManager(impl: StubPaymentManager): PaymentManager
}
