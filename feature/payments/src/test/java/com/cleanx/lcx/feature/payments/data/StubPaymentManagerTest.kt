package com.cleanx.lcx.feature.payments.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [StubPaymentManager] behaviour:
 * - configurable delay (fast for tests)
 * - configurable success rate
 * - deterministic [SimulatedScenario] modes
 */
class StubPaymentManagerTest {

    private lateinit var stub: StubPaymentManager

    @Before
    fun setUp() {
        stub = StubPaymentManager()
        // Use zero delay for fast tests.
        stub.delayMs = 0
    }

    // -- SimulatedScenario --

    @Test
    fun `AlwaysSuccess scenario always returns Success`() = runTest {
        stub.scenario = SimulatedScenario.AlwaysSuccess
        repeat(20) {
            val result = stub.requestPayment(100.0, "ref-$it")
            assertTrue("Expected Success but got $result", result is PaymentResult.Success)
            assertEquals(100.0, (result as PaymentResult.Success).amount, 0.001)
            assertEquals("ref-$it", result.reference)
        }
    }

    @Test
    fun `AlwaysCancelled scenario always returns Cancelled`() = runTest {
        stub.scenario = SimulatedScenario.AlwaysCancelled
        repeat(20) {
            val result = stub.requestPayment(50.0, "ref-$it")
            assertTrue("Expected Cancelled but got $result", result is PaymentResult.Cancelled)
        }
    }

    @Test
    fun `AlwaysFailed scenario always returns Failed`() = runTest {
        stub.scenario = SimulatedScenario.AlwaysFailed
        repeat(20) {
            val result = stub.requestPayment(75.0, "ref-$it")
            assertTrue("Expected Failed but got $result", result is PaymentResult.Failed)
            assertEquals("STUB_ERROR", (result as PaymentResult.Failed).errorCode)
        }
    }

    // -- Random scenario with configurable success rate --

    @Test
    fun `100 percent success rate always succeeds in Random mode`() = runTest {
        stub.scenario = SimulatedScenario.Random
        stub.successRate = 100
        repeat(50) {
            val result = stub.requestPayment(10.0, "ref")
            assertTrue("Expected Success but got $result", result is PaymentResult.Success)
        }
    }

    @Test
    fun `0 percent success rate never succeeds in Random mode`() = runTest {
        stub.scenario = SimulatedScenario.Random
        stub.successRate = 0
        repeat(50) {
            val result = stub.requestPayment(10.0, "ref")
            assertTrue(
                "Expected non-Success but got $result",
                result is PaymentResult.Cancelled || result is PaymentResult.Failed,
            )
        }
    }

    // -- Delay --

    @Test
    fun `configurable delay is respected`() = runTest {
        stub.scenario = SimulatedScenario.AlwaysSuccess
        // With zero delay, this should complete almost instantly.
        // We just verify it does not throw or hang.
        stub.delayMs = 0
        val result = stub.requestPayment(1.0, "fast")
        assertTrue(result is PaymentResult.Success)
    }

    // -- Initialization --

    @Test
    fun `isInitialized returns false before initialize`() {
        assertTrue(!stub.isInitialized())
    }

    @Test
    fun `isInitialized returns true after initialize`() = runTest {
        stub.initialize(mockContext())
        assertTrue(stub.isInitialized())
    }

    // -- Helper --
    private fun mockContext(): android.content.Context {
        return io.mockk.mockk(relaxed = true)
    }
}
