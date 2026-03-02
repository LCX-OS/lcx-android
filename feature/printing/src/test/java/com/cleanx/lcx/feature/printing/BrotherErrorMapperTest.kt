package com.cleanx.lcx.feature.printing

import com.cleanx.lcx.feature.printing.data.BrotherErrorMapper
import com.cleanx.lcx.feature.printing.data.PrintResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BrotherErrorMapperTest {

    @Test
    fun `error 0x01 maps to COVER_OPEN`() {
        val result = BrotherErrorMapper.mapSdkError(0x01)
        assertEquals("COVER_OPEN", result.code)
        assertEquals("Tapa de impresora abierta", result.message)
    }

    @Test
    fun `error 0x02 maps to NO_PAPER`() {
        val result = BrotherErrorMapper.mapSdkError(0x02)
        assertEquals("NO_PAPER", result.code)
        assertEquals("Sin papel/etiquetas", result.message)
    }

    @Test
    fun `error 0x04 maps to BATTERY_LOW`() {
        val result = BrotherErrorMapper.mapSdkError(0x04)
        assertEquals("BATTERY_LOW", result.code)
        assertEquals("Batería baja", result.message)
    }

    @Test
    fun `error 0x08 maps to COMMUNICATION_ERROR`() {
        val result = BrotherErrorMapper.mapSdkError(0x08)
        assertEquals("COMMUNICATION_ERROR", result.code)
        assertEquals("Error de comunicación", result.message)
    }

    @Test
    fun `error 0x10 maps to OVERHEATING`() {
        val result = BrotherErrorMapper.mapSdkError(0x10)
        assertEquals("OVERHEATING", result.code)
        assertEquals("Impresora sobrecalentada", result.message)
    }

    @Test
    fun `error 0x20 maps to PAPER_JAM`() {
        val result = BrotherErrorMapper.mapSdkError(0x20)
        assertEquals("PAPER_JAM", result.code)
        assertEquals("Atasco de papel", result.message)
    }

    @Test
    fun `error 0x40 maps to HIGH_VOLTAGE_ADAPTER`() {
        val result = BrotherErrorMapper.mapSdkError(0x40)
        assertEquals("HIGH_VOLTAGE_ADAPTER", result.code)
        assertEquals("Adaptador incorrecto", result.message)
    }

    @Test
    fun `unknown error code maps to UNKNOWN_PRINTER_ERROR`() {
        val result = BrotherErrorMapper.mapSdkError(0xFF)
        assertEquals("UNKNOWN_PRINTER_ERROR", result.code)
        assertEquals("Error desconocido de impresora (255)", result.message)
    }

    @Test
    fun `error code 0 maps to UNKNOWN_PRINTER_ERROR`() {
        val result = BrotherErrorMapper.mapSdkError(0x00)
        assertEquals("UNKNOWN_PRINTER_ERROR", result.code)
    }

    @Test
    fun `all known error codes return PrintResult Error`() {
        val knownCodes = listOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40)
        knownCodes.forEach { code ->
            val result = BrotherErrorMapper.mapSdkError(code)
            assert(result is PrintResult.Error) {
                "Expected PrintResult.Error for code $code"
            }
        }
    }

    @Test
    fun `all known error codes have non-empty code and message`() {
        val knownCodes = listOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40)
        knownCodes.forEach { code ->
            val result = BrotherErrorMapper.mapSdkError(code)
            assert(result.code.isNotBlank()) { "Code should not be blank for $code" }
            assert(result.message.isNotBlank()) { "Message should not be blank for $code" }
        }
    }
}
