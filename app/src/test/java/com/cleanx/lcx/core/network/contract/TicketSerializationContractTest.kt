package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.CreateTicketsRequest
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.network.UpdatePaymentRequest
import com.cleanx.lcx.core.network.UpdateStatusRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Serialization contract tests — verify that Kotlin data classes correctly
 * round-trip against the exact JSON shapes the API contract produces.
 *
 * These are pure JVM tests (no MockWebServer needed) that catch
 * deserialization drift before it reaches the network layer.
 */
class TicketSerializationContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ---------------------------------------------------------------
    //  Deserialization — API response -> Kotlin model
    // ---------------------------------------------------------------

    @Test
    fun `Ticket deserializes all fields from API response`() {
        val raw = ContractTestFixtures.ticketJson()
        val ticket = json.decodeFromString(Ticket.serializer(), raw)

        assertEquals(ContractTestFixtures.TICKET_ID, ticket.id)
        assertEquals("T-20260302-0001", ticket.ticketNumber)
        assertEquals("2026-03-02", ticket.ticketDate)
        assertEquals(1, ticket.dailyFolio)
        // source is not in the Ticket model (ignoreUnknownKeys handles it)
        assertNull(ticket.customerId)
        assertEquals("Juan García", ticket.customerName)
        assertEquals("+56912345678", ticket.customerPhone)
        assertEquals(ServiceType.WASH_FOLD, ticket.serviceType)
        assertEquals("Lavado y Planchado", ticket.service)
        assertEquals(5.5, ticket.weight!!, 0.001)
        assertEquals(TicketStatus.RECEIVED, ticket.status)
        assertEquals("Entregar mañana", ticket.notes)
        assertEquals(25000.0, ticket.totalAmount!!, 0.001)
        assertEquals(PaymentMethod.CASH, ticket.paymentMethod)
        assertEquals(PaymentStatus.PENDING, ticket.paymentStatus)
        assertEquals(0.0, ticket.paidAmount, 0.001)
        assertNull(ticket.paidAt)
        assertNull(ticket.actualPickupDate)
        assertEquals("2026-03-02T10:30:00Z", ticket.createdAt)
        assertEquals(ContractTestFixtures.USER_ID, ticket.createdBy)
        assertEquals("2026-03-02T10:30:00Z", ticket.updatedAt)
    }

    @Test
    fun `null optional fields are handled`() {
        val ticket = json.decodeFromString(
            Ticket.serializer(),
            ContractTestFixtures.TICKET_ALL_NULLS_JSON,
        )

        assertNull(ticket.customerId)
        assertNull(ticket.customerPhone)
        assertNull(ticket.service)
        assertNull(ticket.weight)
        assertNull(ticket.notes)
        assertNull(ticket.totalAmount)
        assertNull(ticket.paymentMethod)
        assertNull(ticket.paidAt)
        assertNull(ticket.actualPickupDate)
        // Non-null fields with defaults
        assertEquals(0.0, ticket.paidAmount, 0.001)
    }

    @Test
    fun `unknown fields in response are ignored`() {
        // The contract may evolve server-side; client must gracefully ignore
        val ticket = json.decodeFromString(
            Ticket.serializer(),
            ContractTestFixtures.TICKET_WITH_UNKNOWN_FIELDS_JSON,
        )

        // Should parse successfully despite extra fields
        assertEquals(ContractTestFixtures.TICKET_ID, ticket.id)
        assertEquals("Juan García", ticket.customerName)
        assertEquals(TicketStatus.RECEIVED, ticket.status)
    }

    @Test
    fun `ticket with delivered status and actual_pickup_date deserializes`() {
        val raw = ContractTestFixtures.ticketJson(
            status = "delivered",
            actualPickupDate = "2026-03-02",
        )
        val ticket = json.decodeFromString(Ticket.serializer(), raw)

        assertEquals(TicketStatus.DELIVERED, ticket.status)
        assertEquals("2026-03-02", ticket.actualPickupDate)
    }

    @Test
    fun `ticket with paid payment status and paid_at timestamp deserializes`() {
        val raw = ContractTestFixtures.ticketJson(
            paymentStatus = "paid",
            paymentMethod = "card",
            paidAmount = 25000.0,
            paidAt = "2026-03-02T14:20:00Z",
        )
        val ticket = json.decodeFromString(Ticket.serializer(), raw)

        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertEquals(PaymentMethod.CARD, ticket.paymentMethod)
        assertEquals(25000.0, ticket.paidAmount, 0.001)
        assertEquals("2026-03-02T14:20:00Z", ticket.paidAt)
    }

    @Test
    fun `ticket with prepaid payment status deserializes`() {
        val raw = ContractTestFixtures.ticketJson(
            paymentStatus = "prepaid",
            paymentMethod = "cash",
            paidAmount = 10000.0,
            paidAt = "2026-03-02T12:00:00Z",
        )
        val ticket = json.decodeFromString(Ticket.serializer(), raw)

        assertEquals(PaymentStatus.PREPAID, ticket.paymentStatus)
        assertEquals(10000.0, ticket.paidAmount, 0.001)
    }

    @Test
    fun `ticket with in-store service type deserializes`() {
        val raw = ContractTestFixtures.ticketJson(serviceType = "in-store")
        val ticket = json.decodeFromString(Ticket.serializer(), raw)
        assertEquals(ServiceType.IN_STORE, ticket.serviceType)
    }

    // ---------------------------------------------------------------
    //  Serialization — Kotlin model -> JSON request body
    // ---------------------------------------------------------------

    @Test
    fun `CreateTicketsRequest serializes to correct JSON structure`() {
        val request = CreateTicketsRequest(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Juan García",
                    customerPhone = "+56912345678",
                    serviceType = "wash-fold",
                    service = "Lavado y Planchado",
                    weight = 5.5,
                    totalAmount = 25000.0,
                    paymentMethod = "cash",
                    paymentStatus = "pending",
                    paidAmount = 0.0,
                ),
            ),
        )

        val serialized = json.encodeToString(CreateTicketsRequest.serializer(), request)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("encargo", parsed["source"]!!.jsonPrimitive.content)
        val tickets = parsed["tickets"]!!.jsonArray
        assertEquals(1, tickets.size)
        val ticket = tickets[0].jsonObject
        assertEquals("Juan García", ticket["customer_name"]!!.jsonPrimitive.content)
        assertEquals("+56912345678", ticket["customer_phone"]!!.jsonPrimitive.content)
        assertEquals("wash-fold", ticket["service_type"]!!.jsonPrimitive.content)
        assertEquals("Lavado y Planchado", ticket["service"]!!.jsonPrimitive.content)
        assertEquals("5.5", ticket["weight"]!!.jsonPrimitive.content)
        assertEquals("25000.0", ticket["total_amount"]!!.jsonPrimitive.content)
        assertEquals("cash", ticket["payment_method"]!!.jsonPrimitive.content)
        assertEquals("pending", ticket["payment_status"]!!.jsonPrimitive.content)
        assertEquals("0.0", ticket["paid_amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `UpdateStatusRequest serializes status enum correctly`() {
        val statuses = listOf("received", "processing", "ready", "delivered")

        for (status in statuses) {
            val request = UpdateStatusRequest(status = status)
            val serialized = json.encodeToString(UpdateStatusRequest.serializer(), request)
            val parsed = json.parseToJsonElement(serialized).jsonObject

            assertEquals(status, parsed["status"]!!.jsonPrimitive.content)
            assertEquals(
                "UpdateStatusRequest should only have 'status' field",
                1,
                parsed.size,
            )
        }
    }

    @Test
    fun `UpdatePaymentRequest serializes payment enums correctly`() {
        val request = UpdatePaymentRequest(
            paymentStatus = "paid",
            paymentMethod = "transfer",
            paidAmount = 15000.0,
        )

        val serialized = json.encodeToString(UpdatePaymentRequest.serializer(), request)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("paid", parsed["payment_status"]!!.jsonPrimitive.content)
        assertEquals("transfer", parsed["payment_method"]!!.jsonPrimitive.content)
        assertEquals("15000.0", parsed["paid_amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `UpdatePaymentRequest with null optional fields serializes correctly`() {
        val request = UpdatePaymentRequest(
            paymentStatus = "pending",
            paymentMethod = null,
            paidAmount = null,
        )

        val serialized = json.encodeToString(UpdatePaymentRequest.serializer(), request)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("pending", parsed["payment_status"]!!.jsonPrimitive.content)
        // Null fields: present as JSON null or absent — both acceptable
    }

    @Test
    fun `CreateTicketsRequest with customer_id serializes UUID`() {
        val request = CreateTicketsRequest(
            source = "encargo",
            tickets = listOf(
                TicketDraft(
                    customerName = "Juan García",
                    customerId = "11111111-2222-3333-4444-555555555555",
                    serviceType = "in-store",
                    service = "Planchado",
                ),
            ),
        )

        val serialized = json.encodeToString(CreateTicketsRequest.serializer(), request)
        val parsed = json.parseToJsonElement(serialized).jsonObject
        val ticket = parsed["tickets"]!!.jsonArray[0].jsonObject
        assertEquals(
            "11111111-2222-3333-4444-555555555555",
            ticket["customer_id"]!!.jsonPrimitive.content,
        )
    }
}
