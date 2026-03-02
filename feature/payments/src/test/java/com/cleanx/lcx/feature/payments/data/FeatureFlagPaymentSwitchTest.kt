package com.cleanx.lcx.feature.payments.data

import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.payments.di.PaymentModule
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies that the feature-flag-driven PaymentModule correctly
 * selects between Stub and Real (fail-fast) payment managers.
 */
class FeatureFlagPaymentSwitchTest {

    private val stubPaymentManager = StubPaymentManager()

    private fun flagsWith(useRealZettle: Boolean) = object : FeatureFlags {
        override val useRealZettle: Boolean = useRealZettle
        override val useRealBrother: Boolean = false
    }

    @Test
    fun `when useRealZettle is false, provides StubPaymentManager`() {
        val flags = flagsWith(useRealZettle = false)
        val manager = PaymentModule.providePaymentManager(flags, stubPaymentManager)
        assertSame(stubPaymentManager, manager)
    }

    @Test
    fun `when useRealZettle is true, throws IllegalStateException`() {
        val flags = flagsWith(useRealZettle = true)
        try {
            PaymentModule.providePaymentManager(flags, stubPaymentManager)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Zettle"))
        }
    }
}
