package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.TicketDraft
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request validation contract tests — verify that requests sent by the
 * client match exactly what the API contract requires.
 *
 * Uses MockWebServer to capture outgoing request bodies and inspect them
 * against the contract spec.
 */
class RequestValidationContractTest : ContractTestBase() {

    // ---------------------------------------------------------------
    //  POST /api/tickets — request shape
    // ---------------------------------------------------------------

    @Test
    fun `create tickets request has source and tickets array`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Test",
                    customerPhone = "+56900000000",
                    serviceType = "wash-fold",
                    service = "Test Service",
                ),
            ),
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Contract: top-level must have "source" (string) and "tickets" (array)
        assertNotNull("'source' field must be present", body["source"])
        assertEquals("encargo", body["source"]!!.jsonPrimitive.content)
        assertNotNull("'tickets' field must be present", body["tickets"])
        assertTrue("'tickets' must be an array", body["tickets"]!!.jsonArray.size > 0)
    }

    @Test
    fun `ticket draft includes all required fields`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKET_ENCARGO_SUCCESS)

        repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Juan García",
                    customerPhone = "+56912345678",
                    serviceType = "in-store",
                    service = "Planchado",
                    weight = 3.0,
                    totalAmount = 15000.0,
                ),
            ),
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val ticket = body["tickets"]!!.jsonArray[0].jsonObject

        // Contract-required fields in TicketDraft
        assertNotNull("customer_name is required", ticket["customer_name"])
        assertEquals("Juan García", ticket["customer_name"]!!.jsonPrimitive.content)

        assertNotNull("customer_phone is required (no customer_id)", ticket["customer_phone"])
        assertEquals("+56912345678", ticket["customer_phone"]!!.jsonPrimitive.content)

        assertNotNull("service_type is required", ticket["service_type"])
        assertEquals("in-store", ticket["service_type"]!!.jsonPrimitive.content)

        assertNotNull("service is required", ticket["service"])
        assertEquals("Planchado", ticket["service"]!!.jsonPrimitive.content)
    }

    @Test
    fun `create tickets with multiple drafts sends all in array`() = runTest {
        enqueue(ContractTestFixtures.CREATE_TICKETS_BATCH_SUCCESS)

        repository.createTickets(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Customer A",
                    customerPhone = "+56911111111",
                    serviceType = "wash-fold",
                    service = "Lavado",
                ),
                TicketDraft(
                    customerName = "Customer B",
                    customerPhone = "+56922222222",
                    serviceType = "in-store",
                    service = "Planchado",
                ),
            ),
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val tickets = body["tickets"]!!.jsonArray

        assertEquals(2, tickets.size)
        assertEquals("Customer A", tickets[0].jsonObject["customer_name"]!!.jsonPrimitive.content)
        assertEquals("Customer B", tickets[1].jsonObject["customer_name"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/status — request shape
    // ---------------------------------------------------------------

    @Test
    fun `status update sends only status field`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)

        repository.updateStatus(
            ticketId = ContractTestFixtures.TICKET_ID,
            status = TicketStatus.PROCESSING,
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Contract: body must contain exactly { "status": "<value>" }
        assertEquals(
            "Status update body must contain exactly 1 field",
            1,
            body.size,
        )
        assertNotNull("'status' field must be present", body["status"])
        assertEquals("processing", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status update path includes ticket id`() = runTest {
        enqueue(ContractTestFixtures.STATUS_UPDATE_PROCESSING_SUCCESS)

        val ticketId = "my-test-id-123"
        repository.updateStatus(ticketId = ticketId, status = TicketStatus.PROCESSING)

        val recorded = server.takeRequest()
        assertEquals("/api/tickets/$ticketId/status", recorded.path)
    }

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/payment — request shape
    // ---------------------------------------------------------------

    @Test
    fun `payment update sends payment_status, optionally method and amount`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CARD,
            paidAmount = 25000.0,
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Contract: payment_status (required), payment_method (optional), paid_amount (optional)
        assertEquals("paid", body["payment_status"]!!.jsonPrimitive.content)
        assertEquals("card", body["payment_method"]!!.jsonPrimitive.content)
        assertEquals("25000.0", body["paid_amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `payment update with only payment_status sends minimal body`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PENDING_SUCCESS)

        repository.updatePayment(
            ticketId = ContractTestFixtures.TICKET_ID,
            paymentStatus = PaymentStatus.PENDING,
        )

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        // Required field present
        assertEquals("pending", body["payment_status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `payment update path includes ticket id`() = runTest {
        enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

        val ticketId = "payment-test-id-456"
        repository.updatePayment(
            ticketId = ticketId,
            paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.CASH,
            paidAmount = 100.0,
        )

        val recorded = server.takeRequest()
        assertEquals("/api/tickets/$ticketId/payment", recorded.path)
    }

    @Test
    fun `payment update maps all PaymentMethod values to correct strings`() = runTest {
        val mappings = mapOf(
            PaymentMethod.CASH to "cash",
            PaymentMethod.CARD to "card",
            PaymentMethod.TRANSFER to "transfer",
        )

        for ((enumValue, expectedString) in mappings) {
            enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

            repository.updatePayment(
                ticketId = ContractTestFixtures.TICKET_ID,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = enumValue,
                paidAmount = 100.0,
            )

            val recorded = server.takeRequest()
            val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals(
                "PaymentMethod.$enumValue should map to '$expectedString'",
                expectedString,
                body["payment_method"]!!.jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `payment update maps all PaymentStatus values to correct strings`() = runTest {
        val mappings = mapOf(
            PaymentStatus.PENDING to "pending",
            PaymentStatus.PREPAID to "prepaid",
            PaymentStatus.PAID to "paid",
        )

        for ((enumValue, expectedString) in mappings) {
            enqueue(ContractTestFixtures.PAYMENT_UPDATE_PAID_CARD_SUCCESS)

            repository.updatePayment(
                ticketId = ContractTestFixtures.TICKET_ID,
                paymentStatus = enumValue,
            )

            val recorded = server.takeRequest()
            val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals(
                "PaymentStatus.$enumValue should map to '$expectedString'",
                expectedString,
                body["payment_status"]!!.jsonPrimitive.content,
            )
        }
    }
}
