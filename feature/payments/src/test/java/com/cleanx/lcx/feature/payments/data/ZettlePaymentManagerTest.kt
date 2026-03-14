package com.cleanx.lcx.feature.payments.data

import com.cleanx.lcx.core.config.BuildConfigProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZettlePaymentManagerTest {

    @Test
    fun `capability reports application id mismatch before launch`() {
        val manager = ZettlePaymentManager(
            buildConfig(
                applicationId = "com.cleanx.lcx.dev",
                approvedApplicationId = "com.cleanx.app",
            ),
            ZettleActivityLauncherBridge(),
        )

        val capability = manager.capability()

        assertEquals(PaymentBackendType.ZETTLE_REAL, capability.backendType)
        assertEquals("Zettle SDK bloqueado", capability.backendLabel)
        assertFalse(capability.canAcceptPayments)
        assertTrue(capability.statusMessage.contains("com.cleanx.lcx.dev"))
        assertTrue(capability.statusMessage.contains("com.cleanx.app"))
    }

    @Test
    fun `requestPayment fails fast when sdk is not initialized`() = kotlinx.coroutines.test.runTest {
        val manager = ZettlePaymentManager(
            buildConfig(
                applicationId = "com.cleanx.app",
                approvedApplicationId = "com.cleanx.app",
            ),
            ZettleActivityLauncherBridge(),
        )

        val result = manager.requestPayment(amount = 10.0, reference = "venta-1")

        assertTrue(result is PaymentResult.Failed)
        result as PaymentResult.Failed
        assertEquals("ZETTLE_NOT_INITIALIZED", result.errorCode)
    }

    @Test
    fun `initialize keeps sdk disabled when application id does not match`() = kotlinx.coroutines.test.runTest {
        val manager = ZettlePaymentManager(
            buildConfig(
                applicationId = "com.cleanx.lcx.dev",
                approvedApplicationId = "com.cleanx.app",
            ),
            ZettleActivityLauncherBridge(),
        )

        manager.initialize(mockk(relaxed = true))

        assertFalse(manager.isInitialized())
    }

    private fun buildConfig(
        applicationId: String,
        approvedApplicationId: String,
    ): BuildConfigProvider = object : BuildConfigProvider {
        override val applicationId: String = applicationId
        override val apiBaseUrl: String = "http://localhost"
        override val notificationsBaseUrl: String = "http://localhost"
        override val supabaseUrl: String = "http://localhost"
        override val supabaseAnonKey: String = "anon"
        override val zettleClientId: String = "client-id"
        override val zettleRedirectUrl: String = "com.cleanx.app://oauth/callback"
        override val zettleApprovedApplicationId: String = approvedApplicationId
        override val isDebug: Boolean = true
        override val useRealZettle: Boolean = true
        override val useRealBrother: Boolean = false
    }
}
