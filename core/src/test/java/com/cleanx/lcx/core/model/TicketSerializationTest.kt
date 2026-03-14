package com.cleanx.lcx.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `deserialize ticket from API response`() {
        val raw = """
        {
            "id": "abc-123",
            "ticket_number": "T-20260302-0001",
            "ticket_date": "2026-03-02",
            "daily_folio": 1,
            "customer_name": "Test Customer",
            "service_type": "wash-fold",
            "status": "received",
            "paid_amount": 0
        }
        """.trimIndent()

        val ticket = json.decodeFromString(Ticket.serializer(), raw)
        assertEquals("abc-123", ticket.id)
        assertEquals("T-20260302-0001", ticket.ticketNumber)
        assertEquals(1, ticket.dailyFolio)
        assertEquals("Test Customer", ticket.customerName)
        assertEquals(ServiceType.WASH_FOLD, ticket.serviceType)
        assertEquals(TicketStatus.RECEIVED, ticket.status)
        assertEquals(0.0, ticket.paidAmount, 0.001)
        assertNull(ticket.paymentMethod)
    }

    @Test
    fun `deserialize ticket with legacy paid status`() {
        val raw = """
        {
            "id": "paid-123",
            "ticket_number": "T-20260313-0044",
            "ticket_date": "2026-03-13",
            "daily_folio": 44,
            "customer_name": "Cliente legado",
            "service_type": "in-store",
            "status": "paid",
            "payment_status": "paid",
            "paid_amount": 120
        }
        """.trimIndent()

        val ticket = json.decodeFromString(Ticket.serializer(), raw)

        assertEquals(TicketStatus.PAID, ticket.status)
        assertEquals(PaymentStatus.PAID, ticket.paymentStatus)
        assertEquals(120.0, ticket.paidAmount, 0.001)
    }
}
