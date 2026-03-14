package com.cleanx.lcx.feature.payments.data

import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.payments.di.PaymentModule
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the feature-flag-driven PaymentModule correctly
 * selects between Stub and Real backends without crashing DI.
 */
class FeatureFlagPaymentSwitchTest {

    private val stubPaymentManager = StubPaymentManager()
    private val unavailableZettlePaymentManager = UnavailableZettlePaymentManager(buildConfig())

    private fun flagsWith(useRealZettle: Boolean) = object : FeatureFlags {
        override val useRealZettle: Boolean = useRealZettle
        override val useRealBrother: Boolean = false
    }

    private fun buildConfig() = object : com.cleanx.lcx.core.config.BuildConfigProvider {
        override val applicationId: String = "com.cleanx.app"
        override val apiBaseUrl: String = "http://localhost"
        override val notificationsBaseUrl: String = "http://localhost"
        override val supabaseUrl: String = "http://localhost"
        override val supabaseAnonKey: String = "anon"
        override val zettleClientId: String = "client-id"
        override val zettleRedirectUrl: String = "com.cleanx.app://oauth/callback"
        override val zettleApprovedApplicationId: String = "com.cleanx.app"
        override val isDebug: Boolean = true
        override val useRealZettle: Boolean = true
        override val useRealBrother: Boolean = false
    }

    @Test
    fun `when useRealZettle is false, provides StubPaymentManager`() {
        val flags = flagsWith(useRealZettle = false)
        val manager = PaymentModule.providePaymentManager(
            flags,
            stubPaymentManager,
            unavailableZettlePaymentManager,
        )
        assertSame(stubPaymentManager, manager)
    }

    @Test
    fun `when useRealZettle is true, provides unavailable real manager`() {
        val flags = flagsWith(useRealZettle = true)
        val manager = PaymentModule.providePaymentManager(
            flags,
            stubPaymentManager,
            unavailableZettlePaymentManager,
        )

        assertSame(unavailableZettlePaymentManager, manager)
        assertTrue(!manager.capability().canAcceptPayments)
        assertTrue(manager.capability().statusMessage.contains("Zettle"))
    }
}
