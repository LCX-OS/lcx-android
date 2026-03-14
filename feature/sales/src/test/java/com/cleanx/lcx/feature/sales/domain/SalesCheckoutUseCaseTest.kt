package com.cleanx.lcx.feature.sales.domain

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.feature.payments.data.PaymentBackendType
import com.cleanx.lcx.feature.payments.data.PaymentCapability
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.PaymentResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.CustomerDraft
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SalesCheckoutUseCaseTest {

    private val ticketRepository = mockk<TicketRepository>()
    private val paymentManager = mockk<PaymentManager>()
    private val useCase = SalesCheckoutUseCase(
        ticketRepository = ticketRepository,
        paymentManager = paymentManager,
    )

    @Test
    fun `cash checkout creates venta batch without card charge`() = runTest {
        every { paymentManager.capability() } returns unavailableCapability()
        coEvery { ticketRepository.createTickets(any(), any(), any()) } returns ApiResult.Success(
            listOf(ticket()),
        )

        val result = useCase(
            request = checkoutRequest(paymentMethod = PaymentMethod.CASH),
            now = Instant.parse("2026-03-13T12:00:00Z"),
        )

        assertTrue(result is SalesCheckoutResult.Success)
        coVerify(exactly = 1) { ticketRepository.createTickets("venta", any(), false) }
        coVerify(exactly = 0) { paymentManager.requestPayment(any(), any()) }
    }

    @Test
    fun `card checkout charges total before creating venta tickets`() = runTest {
        every { paymentManager.capability() } returns readyCardCapability()
        coEvery { paymentManager.requestPayment(74.0, any()) } returns PaymentResult.Success(
            transactionId = "txn-123",
            amount = 74.0,
            reference = "venta-ref",
        )
        coEvery { ticketRepository.createTickets(any(), any(), any()) } returns ApiResult.Success(
            listOf(ticket()),
        )

        val result = useCase(
            request = checkoutRequest(paymentMethod = PaymentMethod.CARD),
            now = Instant.parse("2026-03-13T12:00:00Z"),
        )

        assertTrue(result is SalesCheckoutResult.Success)
        assertEquals("txn-123", (result as SalesCheckoutResult.Success).transactionId)
        coVerify(exactly = 1) { paymentManager.requestPayment(74.0, any()) }
        coVerify(exactly = 1) { ticketRepository.createTickets("venta", any(), true) }
    }

    @Test
    fun `card checkout surfaces critical failure when charge succeeds but venta create fails`() = runTest {
        every { paymentManager.capability() } returns readyCardCapability()
        coEvery { paymentManager.requestPayment(74.0, any()) } returns PaymentResult.Success(
            transactionId = "txn-123",
            amount = 74.0,
            reference = "venta-ref",
        )
        coEvery { ticketRepository.createTickets(any(), any(), any()) } returns ApiResult.Error(
            code = "OPENING_CHECKLIST_BLOCKING_OPERATION",
            message = "Opening checklist must be completed before creating tickets",
            httpStatus = 409,
        )

        val result = useCase(
            request = checkoutRequest(paymentMethod = PaymentMethod.CARD),
            now = Instant.parse("2026-03-13T12:00:00Z"),
        )

        assertTrue(result is SalesCheckoutResult.CardCapturedCreateFailed)
        val failure = (result as SalesCheckoutResult.CardCapturedCreateFailed).failure
        assertEquals("txn-123", failure.transactionId)
        assertEquals(74.0, failure.amount, 0.001)
        assertTrue(failure.correlationId.isNotBlank())
        assertEquals(
            "Debes completar el checklist de apertura antes de crear tickets.",
            failure.message,
        )
        coVerify(exactly = 1) { ticketRepository.createTickets("venta", any(), true) }
    }

    @Test
    fun `card checkout fails fast when real backend is unavailable`() = runTest {
        every { paymentManager.capability() } returns unavailableCapability()

        val result = useCase(
            request = checkoutRequest(paymentMethod = PaymentMethod.CARD),
            now = Instant.parse("2026-03-13T12:00:00Z"),
        )

        assertTrue(result is SalesCheckoutResult.PaymentFailed)
        assertEquals(
            "USE_REAL_ZETTLE=true, pero Android aun no integra el SDK real de Zettle. " +
                "Faltan dependencias del SDK, OAuth/callback y credenciales validas; " +
                "tarjeta real no disponible en este build.",
            (result as SalesCheckoutResult.PaymentFailed).message,
        )
        coVerify(exactly = 0) { paymentManager.requestPayment(any(), any()) }
        coVerify(exactly = 0) { ticketRepository.createTickets(any(), any(), any()) }
    }

    private fun checkoutRequest(paymentMethod: PaymentMethod): SalesCheckoutRequest {
        return SalesCheckoutRequest(
            customer = CustomerDraft(
                customerId = null,
                fullName = "Cliente QA",
                phone = "5551234567",
                email = "",
            ),
            cart = mapOf(
                "washer" to 1,
                "soap" to 2,
            ),
            catalogs = SalesCatalogSnapshot(
                equipmentServices = listOf(
                    com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord(
                        id = "washer",
                        name = "Lavadora 18kg",
                        description = null,
                        category = "MAQUINARIA",
                        price = 50.0,
                        unit = "pieza",
                        active = true,
                    ),
                ),
                productAddOns = listOf(
                    com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord(
                        id = "soap",
                        name = "Jabón",
                        description = null,
                        price = 12.0,
                        active = true,
                    ),
                ),
                inventoryItems = emptyList(),
            ),
            paymentMethod = paymentMethod,
        )
    }

    private fun readyCardCapability() = PaymentCapability(
        backendType = PaymentBackendType.STUB,
        backendLabel = "Stub (simulado)",
        canAcceptPayments = true,
        isInitialized = true,
        statusMessage = "Smoke funcional habilitado.",
        currentScenario = "AlwaysSuccess",
    )

    private fun unavailableCapability() = PaymentCapability(
        backendType = PaymentBackendType.ZETTLE_REAL,
        backendLabel = "SDK real no integrado",
        canAcceptPayments = false,
        isInitialized = false,
        statusMessage =
            "USE_REAL_ZETTLE=true, pero Android aun no integra el SDK real de Zettle. " +
                "Faltan dependencias del SDK, OAuth/callback y credenciales validas; " +
                "tarjeta real no disponible en este build.",
    )

    private fun ticket(): Ticket {
        return Ticket(
            id = "ticket-1",
            ticketNumber = "T-20260313-0001",
            ticketDate = "2026-03-13",
            dailyFolio = 1,
            customerName = "Cliente QA",
            customerPhone = "5551234567",
            customerEmail = null,
            customerId = null,
            serviceType = ServiceType.IN_STORE,
            service = "Lavadora 18kg",
            weight = 1.0,
            status = TicketStatus.DELIVERED,
            notes = null,
            totalAmount = 50.0,
            subtotal = 50.0,
            addOnsTotal = 0.0,
            addOns = emptyList(),
            promisedPickupDate = "2026-03-13T12:00:00Z",
            actualPickupDate = "2026-03-13",
            specialInstructions = null,
            photos = emptyList(),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = PaymentStatus.PAID,
            paidAmount = 50.0,
            paidAt = "2026-03-13T12:00:00Z",
            prepaidAmount = null,
            createdBy = null,
            assignedTo = null,
            createdAt = "2026-03-13T12:00:00Z",
            updatedAt = "2026-03-13T12:00:00Z",
        )
    }
}
