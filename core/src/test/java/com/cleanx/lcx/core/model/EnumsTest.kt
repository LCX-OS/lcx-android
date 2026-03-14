package com.cleanx.lcx.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EnumsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `TicketStatus serializes to snake_case`() {
        assertEquals("\"received\"", json.encodeToString(TicketStatus.serializer(), TicketStatus.RECEIVED))
        assertEquals("\"processing\"", json.encodeToString(TicketStatus.serializer(), TicketStatus.PROCESSING))
        assertEquals("\"paid\"", json.encodeToString(TicketStatus.serializer(), TicketStatus.PAID))
    }

    @Test
    fun `TicketStatus deserializes legacy paid status`() {
        assertEquals(TicketStatus.PAID, json.decodeFromString(TicketStatus.serializer(), "\"paid\""))
    }

    @Test
    fun `PaymentStatus deserializes from API values`() {
        assertEquals(PaymentStatus.PENDING, json.decodeFromString(PaymentStatus.serializer(), "\"pending\""))
        assertEquals(PaymentStatus.PAID, json.decodeFromString(PaymentStatus.serializer(), "\"paid\""))
    }

    @Test
    fun `ServiceType maps correctly`() {
        assertEquals("\"in-store\"", json.encodeToString(ServiceType.serializer(), ServiceType.IN_STORE))
        assertEquals("\"wash-fold\"", json.encodeToString(ServiceType.serializer(), ServiceType.WASH_FOLD))
    }
}
