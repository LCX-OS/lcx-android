package com.cleanx.lcx.feature.tickets.data

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.CreateTicketsRequest
import com.cleanx.lcx.core.network.TicketApi
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.network.TicketResponse
import com.cleanx.lcx.core.network.TicketsResponse
import com.cleanx.lcx.core.network.UpdatePaymentRequest
import com.cleanx.lcx.core.network.UpdateStatusRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class TicketRepositoryTest {

    private lateinit var api: TicketApi
    private lateinit var repository: TicketRepository
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val sampleTicket = Ticket(
        id = "abc-123",
        ticketNumber = "T-20260302-0001",
        ticketDate = "2026-03-02",
        dailyFolio = 1,
        customerName = "Juan Garcia",
        customerPhone = "+521234567890",
        serviceType = ServiceType.WASH_FOLD,
        service = "Lavado y Planchado",
        weight = 5.5,
        status = TicketStatus.RECEIVED,
        totalAmount = 250.0,
        paymentStatus = PaymentStatus.PENDING,
        paidAmount = 0.0,
    )

    @Before
    fun setUp() {
        api = mockk()
        repository = TicketRepository(api, json)
    }

    // ---- createTickets ----

    @Test
    fun `createTickets success returns list of tickets`() = runTest {
        val draft = TicketDraft(
            customerName = "Juan Garcia",
            customerPhone = "+521234567890",
            serviceType = "wash-fold",
            service = "Lavado y Planchado",
            totalAmount = 250.0,
        )

        coEvery { api.createTickets(any()) } returns Response.success(
            TicketsResponse(data = listOf(sampleTicket)),
        )

        val result = repository.createTickets(source = "encargo", tickets = listOf(draft))

        assertTrue(result is ApiResult.Success)
        val tickets = (result as ApiResult.Success).data
        assertEquals(1, tickets.size)
        assertEquals("T-20260302-0001", tickets[0].ticketNumber)
        assertEquals("Juan Garcia", tickets[0].customerName)
    }

    @Test
    fun `createTickets passes correct request to API`() = runTest {
        val draft = TicketDraft(
            customerName = "Maria Lopez",
            customerPhone = "+529876543210",
            serviceType = "in-store",
            service = "Planchado",
            weight = 3.0,
            notes = "Urgente",
            totalAmount = 150.0,
        )

        val requestSlot = slot<CreateTicketsRequest>()
        coEvery { api.createTickets(capture(requestSlot)) } returns Response.success(
            TicketsResponse(data = listOf(sampleTicket)),
        )

        repository.createTickets(source = "venta", tickets = listOf(draft))

        val captured = requestSlot.captured
        assertEquals("venta", captured.source)
        assertEquals(1, captured.tickets.size)
        assertEquals("Maria Lopez", captured.tickets[0].customerName)
        assertEquals("in-store", captured.tickets[0].serviceType)
        assertEquals("Planchado", captured.tickets[0].service)
    }

    @Test
    fun `createTickets error parses API error body`() = runTest {
        val errorJson = """{"error":"Opening checklist must be completed","code":"OPENING_CHECKLIST_BLOCKING_OPERATION","details":"Complete it first"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { api.createTickets(any()) } returns Response.error(409, errorBody)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Test",
                    customerPhone = "123",
                    serviceType = "wash-fold",
                    service = "Test",
                ),
            ),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(409, error.httpStatus)
        assertEquals("OPENING_CHECKLIST_BLOCKING_OPERATION", error.code)
        assertEquals("Opening checklist must be completed", error.message)
        assertEquals("Complete it first", error.details)
    }

    @Test
    fun `createTickets error with unparseable body returns fallback message`() = runTest {
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaType())

        coEvery { api.createTickets(any()) } returns Response.error(500, errorBody)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Test",
                    customerPhone = "123",
                    serviceType = "wash-fold",
                    service = "Test",
                ),
            ),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(500, error.httpStatus)
        assertEquals("No se pudo completar la operacion.", error.message)
    }

    @Test
    fun `createTickets handles network exception`() = runTest {
        coEvery { api.createTickets(any()) } throws java.io.IOException("Network unreachable")

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Test",
                    customerPhone = "123",
                    serviceType = "wash-fold",
                    service = "Test",
                ),
            ),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(0, error.httpStatus)
        assertEquals("Network unreachable", error.message)
    }

    // ---- updateStatus ----

    @Test
    fun `updateStatus success returns updated ticket`() = runTest {
        val updatedTicket = sampleTicket.copy(status = TicketStatus.PROCESSING)

        coEvery { api.updateStatus(any(), any()) } returns Response.success(
            TicketResponse(data = updatedTicket),
        )

        val result = repository.updateStatus("abc-123", TicketStatus.PROCESSING)

        assertTrue(result is ApiResult.Success)
        assertEquals(TicketStatus.PROCESSING, (result as ApiResult.Success).data.status)
    }

    @Test
    fun `updateStatus passes correct parameters`() = runTest {
        val idSlot = slot<String>()
        val requestSlot = slot<UpdateStatusRequest>()

        coEvery { api.updateStatus(capture(idSlot), capture(requestSlot)) } returns Response.success(
            TicketResponse(data = sampleTicket.copy(status = TicketStatus.READY)),
        )

        repository.updateStatus("ticket-456", TicketStatus.READY)

        assertEquals("ticket-456", idSlot.captured)
        assertEquals("ready", requestSlot.captured.status)
    }

    @Test
    fun `updateStatus maps all status values correctly`() = runTest {
        val requestSlot = slot<UpdateStatusRequest>()
        coEvery { api.updateStatus(any(), capture(requestSlot)) } returns Response.success(
            TicketResponse(data = sampleTicket),
        )

        repository.updateStatus("id", TicketStatus.RECEIVED)
        assertEquals("received", requestSlot.captured.status)

        repository.updateStatus("id", TicketStatus.PROCESSING)
        assertEquals("processing", requestSlot.captured.status)

        repository.updateStatus("id", TicketStatus.READY)
        assertEquals("ready", requestSlot.captured.status)

        repository.updateStatus("id", TicketStatus.DELIVERED)
        assertEquals("delivered", requestSlot.captured.status)
    }

    @Test
    fun `updateStatus error parses validation error`() = runTest {
        val errorJson = """{"error":"Invalid status transition","code":"INVALID_STATUS_TRANSITION","details":"Cannot go from ready to processing"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { api.updateStatus(any(), any()) } returns Response.error(422, errorBody)

        val result = repository.updateStatus("abc-123", TicketStatus.PROCESSING)

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_STATUS_TRANSITION", error.code)
        assertEquals("Invalid status transition", error.message)
    }

    // ---- updatePayment ----

    @Test
    fun `updatePayment success returns updated ticket`() = runTest {
        val paidTicket = sampleTicket.copy(
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 250.0,
        )

        coEvery { api.updatePayment(any(), any()) } returns Response.success(
            TicketResponse(data = paidTicket),
        )

        val result = repository.updatePayment(
            ticketId = "abc-123",
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 250.0,
        )

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertEquals(PaymentMethod.CARD, ticket.paymentMethod)
        assertEquals(250.0, ticket.paidAmount, 0.001)
    }

    @Test
    fun `updatePayment passes correct parameters`() = runTest {
        val idSlot = slot<String>()
        val requestSlot = slot<UpdatePaymentRequest>()

        coEvery { api.updatePayment(capture(idSlot), capture(requestSlot)) } returns Response.success(
            TicketResponse(data = sampleTicket),
        )

        repository.updatePayment(
            ticketId = "ticket-789",
            paymentStatus = PaymentStatus.PREPAID,
            paymentMethod = PaymentMethod.TRANSFER,
            paidAmount = 100.0,
        )

        assertEquals("ticket-789", idSlot.captured)
        assertEquals("prepaid", requestSlot.captured.paymentStatus)
        assertEquals("transfer", requestSlot.captured.paymentMethod)
        assertEquals(100.0, requestSlot.captured.paidAmount)
    }

    @Test
    fun `updatePayment with null method and amount`() = runTest {
        val requestSlot = slot<UpdatePaymentRequest>()

        coEvery { api.updatePayment(any(), capture(requestSlot)) } returns Response.success(
            TicketResponse(data = sampleTicket),
        )

        repository.updatePayment(
            ticketId = "abc-123",
            paymentStatus = PaymentStatus.PENDING,
        )

        assertEquals("pending", requestSlot.captured.paymentStatus)
        assertEquals(null, requestSlot.captured.paymentMethod)
        assertEquals(null, requestSlot.captured.paidAmount)
    }

    @Test
    fun `updatePayment maps all payment method values correctly`() = runTest {
        val requestSlot = slot<UpdatePaymentRequest>()
        coEvery { api.updatePayment(any(), capture(requestSlot)) } returns Response.success(
            TicketResponse(data = sampleTicket),
        )

        repository.updatePayment("id", PaymentStatus.PAID, PaymentMethod.CASH, 100.0)
        assertEquals("cash", requestSlot.captured.paymentMethod)

        repository.updatePayment("id", PaymentStatus.PAID, PaymentMethod.CARD, 100.0)
        assertEquals("card", requestSlot.captured.paymentMethod)

        repository.updatePayment("id", PaymentStatus.PAID, PaymentMethod.TRANSFER, 100.0)
        assertEquals("transfer", requestSlot.captured.paymentMethod)
    }

    @Test
    fun `updatePayment error parsing 403`() = runTest {
        val errorJson = """{"error":"Insufficient permissions","code":"INSUFFICIENT_PERMISSIONS"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { api.updatePayment(any(), any()) } returns Response.error(403, errorBody)

        val result = repository.updatePayment(
            ticketId = "abc-123",
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CASH,
            paidAmount = 250.0,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(403, error.httpStatus)
        assertEquals("INSUFFICIENT_PERMISSIONS", error.code)
    }
}
