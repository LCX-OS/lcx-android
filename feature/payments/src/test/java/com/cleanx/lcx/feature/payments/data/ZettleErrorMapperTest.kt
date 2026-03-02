package com.cleanx.lcx.feature.payments.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that [ZettleErrorMapper] maps all known Zettle error codes
 * to the correct domain error codes and Spanish user-facing messages.
 */
class ZettleErrorMapperTest {

    private val reference = "ticket-42"

    @Test
    fun `maps CARD_DECLINED`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.CARD_DECLINED, "raw", reference,
        )
        assertEquals("CARD_DECLINED", result.errorCode)
        assertEquals("Tarjeta rechazada", result.message)
        assertEquals(reference, result.reference)
    }

    @Test
    fun `maps READER_DISCONNECTED`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.READER_DISCONNECTED, "raw", reference,
        )
        assertEquals("READER_DISCONNECTED", result.errorCode)
        assertEquals("Lector desconectado", result.message)
    }

    @Test
    fun `maps NETWORK_ERROR`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.NETWORK_ERROR, "raw", reference,
        )
        assertEquals("NETWORK_ERROR", result.errorCode)
        assertEquals("Error de conexion", result.message)
    }

    @Test
    fun `maps SESSION_EXPIRED`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.SESSION_EXPIRED, "raw", reference,
        )
        assertEquals("SESSION_EXPIRED", result.errorCode)
        assertEquals("Sesion expirada, vuelva a iniciar sesion", result.message)
    }

    @Test
    fun `maps AMOUNT_TOO_LOW`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.AMOUNT_TOO_LOW, "raw", reference,
        )
        assertEquals("AMOUNT_TOO_LOW", result.errorCode)
        assertEquals("El monto es inferior al minimo permitido", result.message)
    }

    @Test
    fun `maps AMOUNT_TOO_HIGH`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.AMOUNT_TOO_HIGH, "raw", reference,
        )
        assertEquals("AMOUNT_TOO_HIGH", result.errorCode)
        assertEquals("El monto excede el maximo permitido", result.message)
    }

    @Test
    fun `maps READER_BUSY`() {
        val result = ZettleErrorMapper.mapSdkError(
            ZettleErrorMapper.READER_BUSY, "raw", reference,
        )
        assertEquals("READER_BUSY", result.errorCode)
        assertEquals("El lector esta ocupado, intente de nuevo", result.message)
    }

    @Test
    fun `unknown code falls back to UNKNOWN_ZETTLE_ERROR with raw message`() {
        val rawMsg = "Something unexpected happened"
        val result = ZettleErrorMapper.mapSdkError(9999, rawMsg, reference)
        assertEquals("UNKNOWN_ZETTLE_ERROR", result.errorCode)
        assertEquals(rawMsg, result.message)
        assertEquals(reference, result.reference)
    }
}
