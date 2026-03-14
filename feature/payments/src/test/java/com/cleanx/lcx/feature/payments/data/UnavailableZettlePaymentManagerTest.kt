package com.cleanx.lcx.feature.payments.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnavailableZettlePaymentManagerTest {

    private val manager = UnavailableZettlePaymentManager(
        config = config(
            applicationId = "com.cleanx.lcx.staging",
            zettleApprovedApplicationId = "com.cleanx.app",
        ),
    )

    @Test
    fun `capability marks real backend as unavailable`() {
        val capability = manager.capability()

        assertEquals(PaymentBackendType.ZETTLE_REAL, capability.backendType)
        assertEquals("SDK real no integrado", capability.backendLabel)
        assertFalse(capability.canAcceptPayments)
        assertFalse(capability.isInitialized)
        assertTrue(capability.statusMessage.contains("Client ID cargado"))
        assertTrue(capability.statusMessage.contains("Redirect URL configurado"))
        assertTrue(capability.statusMessage.contains("com.cleanx.lcx.staging"))
        assertTrue(capability.statusMessage.contains("com.cleanx.app"))
    }

    @Test
    fun `requestPayment returns explicit failure instead of throwing`() = runTest {
        val result = manager.requestPayment(amount = 42.0, reference = "venta-123")

        assertTrue(result is PaymentResult.Failed)
        result as PaymentResult.Failed
        assertEquals("ZETTLE_SDK_NOT_INTEGRATED", result.errorCode)
        assertEquals("venta-123", result.reference)
        assertTrue(result.message.contains("GITHUB_TOKEN"))
    }

    @Test
    fun `buildUnavailableStatus highlights missing config when needed`() {
        val status = buildUnavailableStatus(
            config(
                applicationId = "com.cleanx.app",
                zettleClientId = "",
                zettleRedirectUrl = "",
                zettleApprovedApplicationId = "com.cleanx.app",
            ),
        )

        assertTrue(status.contains("LCX_ZETTLE_CLIENT_ID"))
        assertTrue(status.contains("LCX_ZETTLE_REDIRECT_URL"))
    }

    private fun config(
        applicationId: String,
        zettleClientId: String = "client-id",
        zettleRedirectUrl: String = "com.cleanx.app://oauth/callback",
        zettleApprovedApplicationId: String = "com.cleanx.app",
    ) = object : com.cleanx.lcx.core.config.BuildConfigProvider {
        override val applicationId: String = applicationId
        override val apiBaseUrl: String = "http://localhost"
        override val notificationsBaseUrl: String = "http://localhost"
        override val supabaseUrl: String = "http://localhost"
        override val supabaseAnonKey: String = "anon"
        override val zettleClientId: String = zettleClientId
        override val zettleRedirectUrl: String = zettleRedirectUrl
        override val zettleApprovedApplicationId: String = zettleApprovedApplicationId
        override val isDebug: Boolean = true
        override val useRealZettle: Boolean = true
        override val useRealBrother: Boolean = false
    }
}
