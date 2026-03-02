package com.cleanx.lcx.feature.payments.data

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentRepositoryTest {

    private lateinit var paymentManager: PaymentManager
    private lateinit var ticketRepository: TicketRepository
    private lateinit var paymentRepository: PaymentRepository

    private val ticketId = "ticket-123"
    private val amount = 150.0
    private val transactionId = "txn-abc"

    private val paidTicket = Ticket(
        id = ticketId,
        ticketNumber = "LCX-20260302-001",
        ticketDate = "2026-03-02",
        dailyFolio = 1,
        customerName = "Juan Perez",
        serviceType = ServiceType.WASH_FOLD,
        status = TicketStatus.RECEIVED,
        totalAmount = amount,
        paymentMethod = PaymentMethod.CARD,
        paymentStatus = PaymentStatus.PAID,
        paidAmount = amount,
    )

    @Before
    fun setUp() {
        paymentManager = mockk()
        ticketRepository = mockk()
        paymentRepository = PaymentRepository(paymentManager, ticketRepository)
    }

    // -- Success flow: payment succeeds -> API update succeeds --

    @Test
    fun `charge returns Success when payment and API both succeed`() = runTest {
        coEvery { paymentManager.requestPayment(amount, ticketId) } returns
            PaymentResult.Success(transactionId, amount, ticketId)

        coEvery {
            ticketRepository.updatePayment(
                ticketId = ticketId,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CARD,
                paidAmount = amount,
            )
        } returns ApiResult.Success(paidTicket)

        val result = paymentRepository.charge(ticketId, amount)

        assertTrue(result is ChargeResult.Success)
        val success = result as ChargeResult.Success
        assertEquals(transactionId, success.transactionId)
        assertEquals(ticketId, success.ticket.id)

        coVerify(exactly = 1) {
            ticketRepository.updatePayment(any(), any(), any(), any())
        }
    }

    // -- Cancel flow: payment cancelled -> no API call --

    @Test
    fun `charge returns Cancelled when payment is cancelled`() = runTest {
        coEvery { paymentManager.requestPayment(amount, ticketId) } returns
            PaymentResult.Cancelled(ticketId)

        val result = paymentRepository.charge(ticketId, amount)

        assertTrue(result is ChargeResult.Cancelled)
        assertEquals(ticketId, (result as ChargeResult.Cancelled).reference)

        // CRITICAL: no API call should have been made.
        coVerify(exactly = 0) {
            ticketRepository.updatePayment(any(), any(), any(), any())
        }
    }

    // -- Failure flow: payment fails -> no API call --

    @Test
    fun `charge returns ReaderFailed when payment fails`() = runTest {
        coEvery { paymentManager.requestPayment(amount, ticketId) } returns
            PaymentResult.Failed("NETWORK", "Error de red", ticketId)

        val result = paymentRepository.charge(ticketId, amount)

        assertTrue(result is ChargeResult.ReaderFailed)
        val failed = result as ChargeResult.ReaderFailed
        assertEquals("NETWORK", failed.errorCode)
        assertEquals("Error de red", failed.message)

        // CRITICAL: no API call should have been made.
        coVerify(exactly = 0) {
            ticketRepository.updatePayment(any(), any(), any(), any())
        }
    }

    // -- Critical edge case: payment succeeds but API call fails --

    @Test
    fun `charge returns PaymentSucceededButApiCallFailed when API PATCH fails`() = runTest {
        coEvery { paymentManager.requestPayment(amount, ticketId) } returns
            PaymentResult.Success(transactionId, amount, ticketId)

        coEvery {
            ticketRepository.updatePayment(
                ticketId = ticketId,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CARD,
                paidAmount = amount,
            )
        } returns ApiResult.Error(
            httpStatus = 500,
            message = "Internal Server Error",
        )

        val result = paymentRepository.charge(ticketId, amount)

        assertTrue(result is ChargeResult.PaymentSucceededButApiCallFailed)
        val apiFailure = result as ChargeResult.PaymentSucceededButApiCallFailed
        assertEquals(transactionId, apiFailure.transactionId)
        assertEquals(amount, apiFailure.amount, 0.001)
        assertEquals("Internal Server Error", apiFailure.apiErrorMessage)
    }

    // -- syncPaymentToBackend standalone retry --

    @Test
    fun `syncPaymentToBackend returns Success when API call succeeds`() = runTest {
        coEvery {
            ticketRepository.updatePayment(
                ticketId = ticketId,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CARD,
                paidAmount = amount,
            )
        } returns ApiResult.Success(paidTicket)

        val result = paymentRepository.syncPaymentToBackend(ticketId, transactionId, amount)

        assertTrue(result is ChargeResult.Success)
        assertEquals(transactionId, (result as ChargeResult.Success).transactionId)
    }

    @Test
    fun `syncPaymentToBackend returns PaymentSucceededButApiCallFailed when API fails`() = runTest {
        coEvery {
            ticketRepository.updatePayment(
                ticketId = ticketId,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CARD,
                paidAmount = amount,
            )
        } returns ApiResult.Error(
            httpStatus = 503,
            message = "Service Unavailable",
        )

        val result = paymentRepository.syncPaymentToBackend(ticketId, transactionId, amount)

        assertTrue(result is ChargeResult.PaymentSucceededButApiCallFailed)
        val failure = result as ChargeResult.PaymentSucceededButApiCallFailed
        assertEquals("Service Unavailable", failure.apiErrorMessage)
    }

    // -- Verify correct payment method and status are sent --

    @Test
    fun `charge sends PaymentStatus PAID and PaymentMethod CARD to API`() = runTest {
        coEvery { paymentManager.requestPayment(amount, ticketId) } returns
            PaymentResult.Success(transactionId, amount, ticketId)

        coEvery {
            ticketRepository.updatePayment(any(), any(), any(), any())
        } returns ApiResult.Success(paidTicket)

        paymentRepository.charge(ticketId, amount)

        coVerify {
            ticketRepository.updatePayment(
                ticketId = ticketId,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.CARD,
                paidAmount = amount,
            )
        }
    }
}
