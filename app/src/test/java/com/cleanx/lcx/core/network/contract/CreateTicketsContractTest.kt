package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.feature.tickets.data.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract integration tests for **POST /api/tickets**.
 *
 * Uses MockWebServer + real Retrofit + real kotlinx.serialization
 * + real [TicketRepository] — no mocks at the repository level.
 */
class CreateTicketsContractTest : ContractTestBase() {

    // ---------------------------------------------------------------
    //  Happy-path tests
    // ---------------------------------------------------------------

    @Test
    fun `create encargo ticket returns ticket with auto-generated fields`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        val draft = TicketDraft(
            customerName = "Juan García",
            customerPhone = "+56912345678",
            serviceType = "wash-fold",
            service = "Lavado y Planchado",
            weight = 5.5,
            totalAmount = 25000.0,
            paymentMethod = "cash",
        )

        val result = repository.createTickets(source = "encargo", tickets = listOf(draft))

        assertTrue("Expected Success, got $result", result is ApiResult.Success)
        val tickets = (result as ApiResult.Success).data
        assertEquals(1, tickets.size)

        val ticket = tickets[0]
        // Auto-generated fields come from the server
        assertEquals(ContractTestFixtures.TICKET_ID, ticket.id)
        assertEquals("T-20260302-0001", ticket.ticketNumber)
        assertEquals("2026-03-02", ticket.ticketDate)
        assertEquals(1, ticket.dailyFolio)
        // Client-provided fields are echoed back
        assertEquals("Juan García", ticket.customerName)
        assertEquals("+56912345678", ticket.customerPhone)
        assertEquals(ServiceType.WASH_FOLD, ticket.serviceType)
        assertEquals("Lavado y Planchado", ticket.service)
        assertEquals(5.5, ticket.weight!!, 0.001)
        assertEquals(TicketStatus.RECEIVED, ticket.status)
        assertEquals(25000.0, ticket.totalAmount!!, 0.001)
        assertEquals(PaymentStatus.PENDING, ticket.paymentStatus)
        assertEquals(0.0, ticket.paidAmount, 0.001)
        assertNull(ticket.paidAt)
        assertNull(ticket.actualPickupDate)
        assertNotNull(ticket.createdAt)
        assertNotNull(ticket.createdBy)
    }

    @Test
    fun `create venta ticket defaults status to delivered`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_VENTA_DELIVERED_SUCCESS)

        val draft = TicketDraft(
            customerName = "Juan García",
            customerPhone = "+56912345678",
            serviceType = "wash-fold",
            service = "Lavado y Planchado",
        )

        val result = repository.createTickets(source = "venta", tickets = listOf(draft))

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data[0]
        assertEquals(TicketStatus.DELIVERED, ticket.status)
        assertEquals("2026-03-02", ticket.actualPickupDate)
    }

    @Test
    fun `batch create returns all tickets in order`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKETS_BATCH_SUCCESS)

        val drafts = listOf(
            TicketDraft(
                customerName = "Juan García",
                customerPhone = "+56912345678",
                serviceType = "wash-fold",
                service = "Lavado y Planchado",
            ),
            TicketDraft(
                customerName = "Maria Lopez",
                customerPhone = "+56987654321",
                serviceType = "wash-fold",
                service = "Lavado y Planchado",
            ),
        )

        val result = repository.createTickets(source = "encargo", tickets = drafts)

        assertTrue(result is ApiResult.Success)
        val tickets = (result as ApiResult.Success).data
        assertEquals(2, tickets.size)
        assertEquals("T-20260302-0001", tickets[0].ticketNumber)
        assertEquals(1, tickets[0].dailyFolio)
        assertEquals("Juan García", tickets[0].customerName)
        assertEquals("T-20260302-0002", tickets[1].ticketNumber)
        assertEquals(2, tickets[1].dailyFolio)
        assertEquals("Maria Lopez", tickets[1].customerName)
    }

    // ---------------------------------------------------------------
    //  Error response tests — every error code from the contract
    // ---------------------------------------------------------------

    @Test
    fun `401 returns NOT_AUTHENTICATED error`() = runTest {
        enqueue(ContractTestFixtures.NOT_AUTHENTICATED_RESPONSE, code = 401)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.httpStatus)
        assertEquals("NOT_AUTHENTICATED", error.code)
        assertEquals("Missing or invalid authentication token", error.message)
    }

    @Test
    fun `409 OPENING_CHECKLIST returns specific error code`() = runTest {
        enqueue(ContractTestFixtures.OPENING_CHECKLIST_BLOCKING_RESPONSE, code = 409)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(409, error.httpStatus)
        assertEquals("OPENING_CHECKLIST_BLOCKING_OPERATION", error.code)
        assertEquals(
            "Opening checklist must be completed before creating tickets",
            error.message,
        )
    }

    @Test
    fun `409 TICKET_NUMBER_CONFLICT returns specific error code with details`() = runTest {
        enqueue(ContractTestFixtures.TICKET_NUMBER_CONFLICT_RESPONSE, code = 409)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(409, error.httpStatus)
        assertEquals("TICKET_NUMBER_CONFLICT", error.code)
        assertEquals("Ticket number collision; server generated duplicate", error.message)
        assertEquals("Retry with exponential backoff", error.details)
    }

    @Test
    fun `422 INVALID_CUSTOMER_NAME validation error is parsed with code and details`() = runTest {
        enqueue(ContractTestFixtures.INVALID_CUSTOMER_NAME_RESPONSE, code = 422)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_CUSTOMER_NAME", error.code)
        assertEquals("Validation failed", error.message)
        assertEquals("customer_name is required", error.details)
    }

    @Test
    fun `422 INVALID_CUSTOMER_PHONE validation error is parsed`() = runTest {
        enqueue(ContractTestFixtures.INVALID_CUSTOMER_PHONE_RESPONSE, code = 422)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_CUSTOMER_PHONE", error.code)
    }

    @Test
    fun `422 INVALID_SERVICE_TYPE validation error is parsed`() = runTest {
        enqueue(ContractTestFixtures.INVALID_SERVICE_TYPE_RESPONSE, code = 422)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_SERVICE_TYPE", error.code)
    }

    @Test
    fun `422 INVALID_PAYMENT_METHOD validation error is parsed`() = runTest {
        enqueue(ContractTestFixtures.INVALID_PAYMENT_METHOD_RESPONSE, code = 422)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_PAYMENT_METHOD", error.code)
    }

    @Test
    fun `422 INVALID_PAYMENT_STATUS validation error is parsed`() = runTest {
        enqueue(ContractTestFixtures.INVALID_PAYMENT_STATUS_RESPONSE, code = 422)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_PAYMENT_STATUS", error.code)
    }

    @Test
    fun `500 server error returns generic error`() = runTest {
        enqueue(ContractTestFixtures.INTERNAL_SERVER_ERROR_RESPONSE, code = 500)

        val result = repository.createTickets(
            source = "encargo",
            tickets = listOf(minimalDraft()),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(500, error.httpStatus)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
        assertEquals("Unexpected server error", error.message)
    }

    // ---------------------------------------------------------------
    //  Request body verification tests
    // ---------------------------------------------------------------

    @Test
    fun `request body matches contract schema`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        val draft = TicketDraft(
            customerName = "Juan García",
            customerPhone = "+56912345678",
            serviceType = "wash-fold",
            service = "Lavado y Planchado",
            weight = 5.5,
            notes = "Urgente",
            totalAmount = 25000.0,
            paymentMethod = "cash",
            paymentStatus = "pending",
            paidAmount = 0.0,
        )

        repository.createTickets(source = "encargo", tickets = listOf(draft))

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Top-level required fields per contract
        assertEquals("encargo", body["source"]!!.jsonPrimitive.content)
        assertTrue("Body must contain 'tickets' array", body["tickets"] is JsonArray)

        val ticketJson = body["tickets"]!!.jsonArray[0].jsonObject
        assertEquals("Juan García", ticketJson["customer_name"]!!.jsonPrimitive.content)
        assertEquals("+56912345678", ticketJson["customer_phone"]!!.jsonPrimitive.content)
        assertEquals("wash-fold", ticketJson["service_type"]!!.jsonPrimitive.content)
        assertEquals("Lavado y Planchado", ticketJson["service"]!!.jsonPrimitive.content)
        assertEquals("5.5", ticketJson["weight"]!!.jsonPrimitive.content)
        assertEquals("Urgente", ticketJson["notes"]!!.jsonPrimitive.content)
        assertEquals("25000.0", ticketJson["total_amount"]!!.jsonPrimitive.content)
        assertEquals("cash", ticketJson["payment_method"]!!.jsonPrimitive.content)
        assertEquals("pending", ticketJson["payment_status"]!!.jsonPrimitive.content)
        assertEquals("0.0", ticketJson["paid_amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `request has correct Content-Type header`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        repository.createTickets(source = "encargo", tickets = listOf(minimalDraft()))

        val recorded = server.takeRequest()
        val contentType = recorded.getHeader("Content-Type")
        assertNotNull("Content-Type header must be present", contentType)
        assertTrue(
            "Content-Type must be application/json, was: $contentType",
            contentType!!.contains("application/json"),
        )
    }

    @Test
    fun `request sends POST to correct path`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        repository.createTickets(source = "encargo", tickets = listOf(minimalDraft()))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/tickets", recorded.path)
    }

    @Test
    fun `request body omits null optional fields`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        val draft = TicketDraft(
            customerName = "Juan García",
            customerPhone = "+56912345678",
            serviceType = "wash-fold",
            service = "Lavado y Planchado",
            // All optional fields left as null/default
        )

        repository.createTickets(source = "encargo", tickets = listOf(draft))

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val ticketJson = body["tickets"]!!.jsonArray[0].jsonObject

        // Required fields present
        assertEquals("Juan García", ticketJson["customer_name"]!!.jsonPrimitive.content)
        assertEquals("wash-fold", ticketJson["service_type"]!!.jsonPrimitive.content)
        assertEquals("Lavado y Planchado", ticketJson["service"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private fun minimalDraft() = TicketDraft(
        customerName = "Test",
        customerPhone = "+56900000000",
        serviceType = "wash-fold",
        service = "Test Service",
    )
}
