package com.cleanx.lcx.feature.cash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for cash register validation rules.
 *
 * These rules are extracted from the PWA caja module and encapsulated
 * in the [CashValidation] helper object for testability. The same logic
 * will be used in the ViewModel to validate submissions.
 */
class CashValidationTest {

    // =========================================================================
    // Validation helper extracted from ViewModel logic for testability.
    // If C2 creates a standalone validation util, replace this with an import.
    // =========================================================================

    object CashValidation {

        fun validateSubmission(
            type: MovementType,
            amount: Double,
            notes: String,
            canClose: Boolean,
            totalSalesForDay: Double?,
        ): String? {
            if (amount <= 0) return "El monto debe ser mayor a cero"
            if (type == MovementType.EXPENSE && notes.isBlank()) return "Por favor describe el gasto realizado"
            if (type == MovementType.CLOSING && !canClose) return "No se puede cerrar la caja en este momento"
            if (type == MovementType.CLOSING && totalSalesForDay != null && totalSalesForDay < 0)
                return "Las ventas del día no pueden ser negativas"
            return null
        }

        fun calculateDiscrepancy(
            actualAmount: Double,
            openingAmount: Double,
            totalSalesForDay: Double?,
            totalIncome: Double,
            totalExpenses: Double,
        ): Triple<Double, Double, String>? {
            val sales = totalSalesForDay ?: return null
            val expected = openingAmount + sales + totalIncome - totalExpenses
            val diff = actualAmount - expected
            val type = when {
                diff > 0 -> "overage"
                diff < 0 -> "shortage"
                else -> "balanced"
            }
            return Triple(expected, diff, type)
        }

        fun canCloseRegister(hasOpening: Boolean, hasClosure: Boolean): Pair<Boolean, String?> =
            when {
                hasClosure -> false to "La caja ya fue cerrada hoy"
                !hasOpening -> false to "No hay apertura de caja para hoy"
                else -> true to null
            }
    }

    // =========================================================================
    // validateSubmission tests
    // =========================================================================

    @Test
    fun `expense without notes returns error`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.EXPENSE,
            amount = 100.0,
            notes = "",
            canClose = true,
            totalSalesForDay = null,
        )
        assertNotNull(error)
        assertEquals("Por favor describe el gasto realizado", error)
    }

    @Test
    fun `expense with blank notes returns error`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.EXPENSE,
            amount = 50.0,
            notes = "   ",
            canClose = true,
            totalSalesForDay = null,
        )
        assertNotNull(error)
        assertEquals("Por favor describe el gasto realizado", error)
    }

    @Test
    fun `expense with notes returns null (valid)`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.EXPENSE,
            amount = 150.0,
            notes = "Compra de detergente",
            canClose = true,
            totalSalesForDay = null,
        )
        assertNull(error)
    }

    @Test
    fun `closing when canClose is false returns error`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.CLOSING,
            amount = 1000.0,
            notes = "",
            canClose = false,
            totalSalesForDay = null,
        )
        assertNotNull(error)
        assertEquals("No se puede cerrar la caja en este momento", error)
    }

    @Test
    fun `closing when canClose is true returns null`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.CLOSING,
            amount = 5000.0,
            notes = "",
            canClose = true,
            totalSalesForDay = null,
        )
        assertNull(error)
    }

    @Test
    fun `closing with negative totalSalesForDay returns error`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.CLOSING,
            amount = 500.0,
            notes = "",
            canClose = true,
            totalSalesForDay = -100.0,
        )
        assertNotNull(error)
        assertEquals("Las ventas del día no pueden ser negativas", error)
    }

    @Test
    fun `closing with zero totalSalesForDay returns null`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.CLOSING,
            amount = 1000.0,
            notes = "",
            canClose = true,
            totalSalesForDay = 0.0,
        )
        assertNull(error)
    }

    @Test
    fun `closing with null totalSalesForDay returns null`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.CLOSING,
            amount = 1000.0,
            notes = "",
            canClose = true,
            totalSalesForDay = null,
        )
        assertNull(error)
    }

    @Test
    fun `opening with positive amount returns null`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.OPENING,
            amount = 2500.0,
            notes = "",
            canClose = false,
            totalSalesForDay = null,
        )
        assertNull(error)
    }

    @Test
    fun `any type with zero amount returns error`() {
        for (type in MovementType.values()) {
            val error = CashValidation.validateSubmission(
                type = type,
                amount = 0.0,
                notes = "some notes",
                canClose = true,
                totalSalesForDay = 100.0,
            )
            assertNotNull("Expected error for $type with zero amount", error)
            assertEquals("El monto debe ser mayor a cero", error)
        }
    }

    @Test
    fun `any type with negative amount returns error`() {
        for (type in MovementType.values()) {
            val error = CashValidation.validateSubmission(
                type = type,
                amount = -50.0,
                notes = "some notes",
                canClose = true,
                totalSalesForDay = 100.0,
            )
            assertNotNull("Expected error for $type with negative amount", error)
            assertEquals("El monto debe ser mayor a cero", error)
        }
    }

    @Test
    fun `income with positive amount and no notes returns null`() {
        val error = CashValidation.validateSubmission(
            type = MovementType.INCOME,
            amount = 300.0,
            notes = "",
            canClose = false,
            totalSalesForDay = null,
        )
        assertNull(error)
    }

    // =========================================================================
    // calculateDiscrepancy tests
    // =========================================================================

    @Test
    fun `null totalSalesForDay returns null`() {
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 5000.0,
            openingAmount = 2000.0,
            totalSalesForDay = null,
            totalIncome = 500.0,
            totalExpenses = 100.0,
        )
        assertNull(result)
    }

    @Test
    fun `exact match returns balanced`() {
        // expected = 2000 + 3000 + 500 - 200 = 5300
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 5300.0,
            openingAmount = 2000.0,
            totalSalesForDay = 3000.0,
            totalIncome = 500.0,
            totalExpenses = 200.0,
        )
        assertNotNull(result)
        assertEquals(5300.0, result!!.first, 0.001)  // expected
        assertEquals(0.0, result.second, 0.001)       // diff
        assertEquals("balanced", result.third)
    }

    @Test
    fun `overage when actual exceeds expected`() {
        // expected = 1000 + 2000 + 0 - 0 = 3000, actual = 3500
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 3500.0,
            openingAmount = 1000.0,
            totalSalesForDay = 2000.0,
            totalIncome = 0.0,
            totalExpenses = 0.0,
        )
        assertNotNull(result)
        assertEquals(3000.0, result!!.first, 0.001)   // expected
        assertEquals(500.0, result.second, 0.001)      // diff (positive = overage)
        assertEquals("overage", result.third)
    }

    @Test
    fun `shortage when actual less than expected`() {
        // expected = 1000 + 2000 + 300 - 100 = 3200, actual = 2800
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 2800.0,
            openingAmount = 1000.0,
            totalSalesForDay = 2000.0,
            totalIncome = 300.0,
            totalExpenses = 100.0,
        )
        assertNotNull(result)
        assertEquals(3200.0, result!!.first, 0.001)    // expected
        assertEquals(-400.0, result.second, 0.001)     // diff (negative = shortage)
        assertEquals("shortage", result.third)
    }

    @Test
    fun `calculation with all components (opening + sales + income - expenses)`() {
        // expected = 500 + 1500 + 200 - 350 = 1850, actual = 1900
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 1900.0,
            openingAmount = 500.0,
            totalSalesForDay = 1500.0,
            totalIncome = 200.0,
            totalExpenses = 350.0,
        )
        assertNotNull(result)
        assertEquals(1850.0, result!!.first, 0.001)    // expected
        assertEquals(50.0, result.second, 0.001)       // diff
        assertEquals("overage", result.third)
    }

    @Test
    fun `discrepancy with zero sales returns expected equal to opening + income - expenses`() {
        // expected = 2000 + 0 + 100 - 50 = 2050
        val result = CashValidation.calculateDiscrepancy(
            actualAmount = 2050.0,
            openingAmount = 2000.0,
            totalSalesForDay = 0.0,
            totalIncome = 100.0,
            totalExpenses = 50.0,
        )
        assertNotNull(result)
        assertEquals(2050.0, result!!.first, 0.001)
        assertEquals(0.0, result.second, 0.001)
        assertEquals("balanced", result.third)
    }

    // =========================================================================
    // canCloseRegister tests
    // =========================================================================

    @Test
    fun `can close when has opening and no closure`() {
        val (canClose, reason) = CashValidation.canCloseRegister(
            hasOpening = true,
            hasClosure = false,
        )
        assertTrue(canClose)
        assertNull(reason)
    }

    @Test
    fun `cannot close when already closed`() {
        val (canClose, reason) = CashValidation.canCloseRegister(
            hasOpening = true,
            hasClosure = true,
        )
        assertTrue(!canClose)
        assertNotNull(reason)
        assertEquals("La caja ya fue cerrada hoy", reason)
    }

    @Test
    fun `cannot close when no opening`() {
        val (canClose, reason) = CashValidation.canCloseRegister(
            hasOpening = false,
            hasClosure = false,
        )
        assertTrue(!canClose)
        assertNotNull(reason)
        assertEquals("No hay apertura de caja para hoy", reason)
    }

    @Test
    fun `cannot close when no opening and already closed`() {
        val (canClose, reason) = CashValidation.canCloseRegister(
            hasOpening = false,
            hasClosure = true,
        )
        assertTrue(!canClose)
        assertNotNull(reason)
        // hasClosure is checked first, so the message is about already being closed
        assertEquals("La caja ya fue cerrada hoy", reason)
    }
}
