package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.feature.tickets.data.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract integration tests for **PATCH /api/tickets/:id/payment**.
 *
 * Full-stack: MockWebServer -> OkHttp -> Retrofit -> kotlinx.serialization
 * -> [TicketRepository].
 */
class UpdatePaymentContractTest : ContractTestBase() {

    // ---------------------------------------------------------------
    //  Happy-path tests
    // ---------------------------------------------------------------

    @Test
    fun `mark as paid with card returns updated payment fields`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 25000.0,
        )

        assertTrue("Expected Success, got $result", result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertEquals(PaymentMethod.CARD, ticket.paymentMethod)
        assertEquals(25000.0, ticket.paidAmount, 0.001)
        assertNotNull(ticket.paidAt)
    }

    @Test
    fun `pending payment resets paid_amount to 0`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PENDING_SUCCESS)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PENDING,
        )

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        assertEquals(PaymentStatus.PENDING, ticket.paymentStatus)
        assertEquals(0.0, ticket.paidAmount, 0.001)
        assertNull("paid_at must be null when pending", ticket.paidAt)
    }

    @Test
    fun `prepaid auto-promotes to paid when amount equals or exceeds total`() = runTest {
        // CRITICAL: Server returns "paid" even though client sent "prepaid"
        enqueue(ContractTestFixtures.PREPAID_AUTOPROMOTE_RESPONSE)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PREPAID,
            paymentMethod = PaymentMethod.CASH,
            paidAmount = 25000.0,
        )

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        // Response is source of truth — promoted from prepaid to paid
        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertEquals(25000.0, ticket.paidAmount, 0.001)
        assertEquals(PaymentMethod.CASH, ticket.paymentMethod)
        assertNotNull(ticket.paidAt)
    }

    @Test
    fun `prepaid without auto-promotion keeps prepaid status`() = runTest {
        enqueue(ContractTestFixtures.PREPAID_NO_PROMOTION_RESPONSE)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PREPAID,
            paymentMethod = PaymentMethod.CASH,
            paidAmount = 10000.0,
        )

        assertTrue(result is ApiResult.Success)
        val ticket = (result as ApiResult.Success).data
        // paid_amount < total_amount => no promotion
        assertEquals(PaymentStatus.PREPAID, ticket.paymentStatus)
        assertEquals(10000.0, ticket.paidAmount, 0.001)
    }

    @Test
    fun `payment_method is optional`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PENDING_SUCCESS)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PENDING,
            paymentMethod = null,
            paidAmount = null,
        )

        assertTrue(result is ApiResult.Success)

        // Verify the request body did not include payment_method
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("pending", body["payment_status"]!!.jsonPrimitive.content)
        // payment_method should be absent (null fields excluded) or null
        val methodField = body["payment_method"]
        assertTrue(
            "payment_method should be null or absent",
            methodField == null || methodField.jsonPrimitive.content == "null",
        )
    }

    // ---------------------------------------------------------------
    //  Request body verification
    // ---------------------------------------------------------------

    @Test
    fun `request body matches contract schema exactly`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 25000.0,
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Contract requires: payment_status, optionally payment_method and paid_amount
        assertEquals("paid", body["payment_status"]!!.jsonPrimitive.content)
        assertEquals("card", body["payment_method"]!!.jsonPrimitive.content)
        assertEquals("25000.0", body["paid_amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `request sends PATCH to correct path`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 25000.0,
        )

        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals(
            "/api/tickets/${ContractTestFixtures.TICKET_ID}/payment",
            recorded.path,
        )
    }

    @Test
    fun `request has correct Content-Type header`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 25000.0,
        )

        val recorded = server.takeRequest()
        val contentType = recorded.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue(contentType!!.contains("application/json"))
    }

    // ---------------------------------------------------------------
    //  Error response tests
    // ---------------------------------------------------------------

    @Test
    fun `401 not authenticated returns error`() = runTest {
        enqueue(ContractTestFixtures.NOT_AUTHENTICATED_RESPONSE, code = 401)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CASH,
            paidAmount = 25000.0,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.httpStatus)
        assertEquals("NOT_AUTHENTICATED", error.code)
    }

    @Test
    fun `422 invalid payment payload`() = runTest {
        enqueue(ContractTestFixtures.INVALID_PAYMENT_STATUS_PATCH_RESPONSE, code = 422)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(422, error.httpStatus)
        assertEquals("INVALID_PAYMENT_STATUS", error.code)
        assertEquals("Invalid payment status", error.message)
        assertEquals(
            "payment_status must be 'pending', 'prepaid', or 'paid'",
            error.details,
        )
    }

    @Test
    fun `403 forbidden returns permission error`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_INSUFFICIENT_PERMISSIONS_RESPONSE, code = 403)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(403, error.httpStatus)
        assertEquals("INSUFFICIENT_PERMISSIONS", error.code)
    }

    @Test
    fun `404 ticket not found`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_NOT_FOUND_RESPONSE, code = 404)

        val result = repository.updatePayment(
            ticketId = "nonexistent-id",
            paymentStatus = PaymentStatus.PAID,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.httpStatus)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun `500 server error returns generic error`() = runTest {
        enqueue(ContractTestFixtures.INTERNAL_SERVER_ERROR_RESPONSE, code = 500)

        val result = repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(500, error.httpStatus)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
    }
}
