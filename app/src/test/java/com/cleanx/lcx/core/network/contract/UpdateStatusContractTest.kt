package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.feature.tickets.data.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract integration tests for **PATCH /api/tickets/:id/status**.
 *
 * Exercises the full network stack: MockWebServer -> OkHttp -> Retrofit
 * -> kotlinx.serialization -> [TicketRepository].
 */
class UpdateStatusContractTest : ContractTestBase() {

    // ---------------------------------------------------------------
    //  Happy-path tests
    // ---------------------------------------------------------------

    @Test
    fun `update status to delivered returns ticket with actual_pickup_date`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_DELIVERED_SUCCESS)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.DELIVERED,
        )

        assertTrue("Expected Success, got $result", result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        assertEquals(TicketStatus.DELIVERED, ticket.status)
        // Server side-effect: actual_pickup_date set to today
        assertEquals("2026-03-02", ticket.actualPickupDate)
        // Response may also reflect payment changes
        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertNotNull(ticket.paidAt)
    }

    @Test
    fun `update status to processing returns updated ticket`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        assertEquals(TicketStatus.PROCESSING, ticket.status)
    }

    @Test
    fun `update status sends correct JSON body`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)

        repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Contract requires only { "status": "<value>" }
        assertEquals("processing", body["status"]!!.jsonPrimitive.content)
        // Body should not contain extraneous fields
        assertEquals(
            "Request body should contain only 'status' field",
            1,
            body.size,
        )
    }

    @Test
    fun `update status sends PATCH to correct path`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)

        repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.READY,
        )

        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals(
            "/api/tickets/${ContractTestFixtures.TICKET_ID}/status",
            recorded.path,
        )
    }

    @Test
    fun `status value maps received correctly in request`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)
        repository.updateStatus(ContractTestFixtures.TICKET_ID, TicketStatus.RECEIVED)
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("received", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status value maps processing correctly in request`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)
        repository.updateStatus(ContractTestFixtures.TICKET_ID, TicketStatus.PROCESSING)
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("processing", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status value maps ready correctly in request`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)
        repository.updateStatus(ContractTestFixtures.TICKET_ID, TicketStatus.READY)
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("ready", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status value maps delivered correctly in request`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_DELIVERED_SUCCESS)
        repository.updateStatus(ContractTestFixtures.TICKET_ID, TicketStatus.DELIVERED)
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("delivered", body["status"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------
    //  Error response tests
    // ---------------------------------------------------------------

    @Test
    fun `401 not authenticated returns error`() = runTest {
        enqueue(ContractTestFixtures.NOT_AUTHENTICATED_RESPONSE, code = 401)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.httpStatus)
        assertEquals("NOT_AUTHENTICATED", error.code)
    }

    @Test
    fun `422 invalid status transition is handled`() = runTest {
        enqueue(ContractTestFixtures.INVALID_STATUS_TRANSITION_RESPONSE, code = 422)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_STATUS_TRANSITION", error.code)
        assertEquals("Invalid status transition", error.message)
        assertEquals("Cannot transition from 'ready' to 'processing'", error.details)
    }

    @Test
    fun `403 forbidden returns permission error`() = runTest {
        enqueue(ContractTestFixtures.INSUFFICIENT_PERMISSIONS_RESPONSE, code = 403)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.DELIVERED,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(403, error.httpStatus)
        assertEquals("INSUFFICIENT_PERMISSIONS", error.code)
        assertEquals("Insufficient permissions to update ticket status", error.message)
    }

    @Test
    fun `404 ticket not found`() = runTest {
        enqueue(ContractTestFixtures.NOT_FOUND_RESPONSE, code = 404)

        val result = repository.updateStatus(
            ticketId = "nonexistent-id",
            status = TicketStatus.PROCESSING,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.httpStatus)
        assertEquals("NOT_FOUND", error.code)
        assertEquals("Ticket not found", error.message)
    }

    @Test
    fun `500 server error returns generic error`() = runTest {
        enqueue(ContractTestFixtures.INTERNAL_SERVER_ERROR_RESPONSE, code = 500)

        val result = repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(500, error.httpStatus)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
    }
}
