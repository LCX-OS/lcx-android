package com.cleanx.lcx.feature.cash.ui

import com.cleanx.lcx.feature.cash.data.CashSummary
import com.cleanx.lcx.feature.cash.data.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [CashUiState] computed properties.
 *
 * These tests verify the derived values (billsTotal, coinsTotal, grandTotal,
 * parsedTotalSalesForDay, expectedClosingFromSales, discrepancyPreview)
 * that the UI state object calculates from its raw inputs.
 */
class CashUiStateTest {

    // =========================================================================
    // UI state with computed properties, matching the specification from C3.
    // Once C3 delivers the real CashUiState, replace this with an import.
    // =========================================================================

    data class CashUiState(
        val isLoadingSummary: Boolean = true,
        val summary: CashSummary = CashSummary(),
        val selectedType: MovementType = MovementType.OPENING,
        val billCounts: Map<Double, Int> = MXN_BILLS.associateWith { 0 },
        val coinCounts: Map<Double, Int> = MXN_COINS.associateWith { 0 },
        val notes: String = "",
        val totalSalesForDay: String = "",
        val expenseAmount: String = "",
        val isSubmitting: Boolean = false,
        val submitSuccess: Boolean = false,
        val submitError: String? = null,
    ) {
        val billsTotal: Double
            get() = billCounts.entries.sumOf { (denom, count) -> denom * count }

        val coinsTotal: Double
            get() = coinCounts.entries.sumOf { (denom, count) -> denom * count }

        val grandTotal: Double
            get() = if (selectedType == MovementType.EXPENSE) {
                expenseAmount.toDoubleOrNull() ?: 0.0
            } else {
                billsTotal + coinsTotal
            }

        val parsedTotalSalesForDay: Double?
            get() = totalSalesForDay.toDoubleOrNull()

        val expectedClosingFromSales: Double?
            get() {
                if (selectedType != MovementType.CLOSING) return null
                val sales = parsedTotalSalesForDay ?: return null
                return summary.openingAmount + sales + summary.totalIncome - summary.totalExpenses
            }

        val discrepancyPreview: Double?
            get() {
                val expected = expectedClosingFromSales ?: return null
                return grandTotal - expected
            }

        companion object {
            val MXN_BILLS = listOf(1000.0, 500.0, 200.0, 100.0, 50.0, 20.0)
            val MXN_COINS = listOf(20.0, 10.0, 5.0, 2.0, 1.0, 0.5)
        }
    }

    // =========================================================================
    // billsTotal tests
    // =========================================================================

    @Test
    fun `billsTotal calculates correctly`() {
        val state = CashUiState(
            billCounts = mapOf(
                1000.0 to 2,  // 2000
                500.0 to 3,   // 1500
                200.0 to 0,
                100.0 to 5,   // 500
                50.0 to 1,    // 50
                20.0 to 0,
            ),
        )
        assertEquals(4050.0, state.billsTotal, 0.001)
    }

    @Test
    fun `billsTotal with all zeros returns 0`() {
        val state = CashUiState()
        assertEquals(0.0, state.billsTotal, 0.001)
    }

    // =========================================================================
    // coinsTotal tests
    // =========================================================================

    @Test
    fun `coinsTotal calculates correctly`() {
        val state = CashUiState(
            coinCounts = mapOf(
                20.0 to 1,   // 20
                10.0 to 3,   // 30
                5.0 to 2,    // 10
                2.0 to 4,    // 8
                1.0 to 5,    // 5
                0.5 to 6,    // 3
            ),
        )
        assertEquals(76.0, state.coinsTotal, 0.001)
    }

    @Test
    fun `coinsTotal with 50 centavo coins produces decimal`() {
        val state = CashUiState(
            coinCounts = mapOf(
                20.0 to 0,
                10.0 to 0,
                5.0 to 0,
                2.0 to 0,
                1.0 to 0,
                0.5 to 3,  // 1.5
            ),
        )
        assertEquals(1.5, state.coinsTotal, 0.0001)
    }

    // =========================================================================
    // grandTotal tests
    // =========================================================================

    @Test
    fun `grandTotal uses expenseAmount for expense type`() {
        val state = CashUiState(
            selectedType = MovementType.EXPENSE,
            expenseAmount = "350.50",
            billCounts = mapOf(
                1000.0 to 5,  // these should be ignored
                500.0 to 0, 200.0 to 0, 100.0 to 0, 50.0 to 0, 20.0 to 0,
            ),
        )
        assertEquals(350.50, state.grandTotal, 0.001)
    }

    @Test
    fun `grandTotal uses denomination totals for non-expense types`() {
        val state = CashUiState(
            selectedType = MovementType.OPENING,
            expenseAmount = "9999.99",  // should be ignored for OPENING
            billCounts = mapOf(
                1000.0 to 1, 500.0 to 0, 200.0 to 0,
                100.0 to 0, 50.0 to 0, 20.0 to 0,
            ),
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 0, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 2,  // 1.0
            ),
        )
        assertEquals(1001.0, state.grandTotal, 0.001)
    }

    @Test
    fun `grandTotal returns 0 for expense with invalid expenseAmount`() {
        val state = CashUiState(
            selectedType = MovementType.EXPENSE,
            expenseAmount = "not-a-number",
        )
        assertEquals(0.0, state.grandTotal, 0.001)
    }

    @Test
    fun `grandTotal returns 0 for expense with empty expenseAmount`() {
        val state = CashUiState(
            selectedType = MovementType.EXPENSE,
            expenseAmount = "",
        )
        assertEquals(0.0, state.grandTotal, 0.001)
    }

    @Test
    fun `grandTotal uses denomination totals for INCOME type`() {
        val state = CashUiState(
            selectedType = MovementType.INCOME,
            billCounts = mapOf(
                1000.0 to 0, 500.0 to 1, 200.0 to 0,
                100.0 to 2, 50.0 to 0, 20.0 to 0,
            ),
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 0, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 0,
            ),
        )
        // 500 + 200 = 700
        assertEquals(700.0, state.grandTotal, 0.001)
    }

    @Test
    fun `grandTotal uses denomination totals for CLOSING type`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            billCounts = mapOf(
                1000.0 to 3, 500.0 to 0, 200.0 to 0,
                100.0 to 0, 50.0 to 0, 20.0 to 0,
            ),
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 5, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 0,
            ),
        )
        // 3000 + 50 = 3050
        assertEquals(3050.0, state.grandTotal, 0.001)
    }

    // =========================================================================
    // parsedTotalSalesForDay tests
    // =========================================================================

    @Test
    fun `parsedTotalSalesForDay parses valid number`() {
        val state = CashUiState(totalSalesForDay = "4500.75")
        assertNotNull(state.parsedTotalSalesForDay)
        assertEquals(4500.75, state.parsedTotalSalesForDay!!, 0.001)
    }

    @Test
    fun `parsedTotalSalesForDay returns null for empty string`() {
        val state = CashUiState(totalSalesForDay = "")
        assertNull(state.parsedTotalSalesForDay)
    }

    @Test
    fun `parsedTotalSalesForDay returns null for non-numeric string`() {
        val state = CashUiState(totalSalesForDay = "abc")
        assertNull(state.parsedTotalSalesForDay)
    }

    @Test
    fun `parsedTotalSalesForDay parses zero`() {
        val state = CashUiState(totalSalesForDay = "0")
        assertNotNull(state.parsedTotalSalesForDay)
        assertEquals(0.0, state.parsedTotalSalesForDay!!, 0.001)
    }

    // =========================================================================
    // expectedClosingFromSales tests
    // =========================================================================

    @Test
    fun `expectedClosingFromSales computed only for closing type`() {
        val summary = CashSummary(
            openingAmount = 2000.0,
            totalIncome = 500.0,
            totalExpenses = 200.0,
        )

        // CLOSING type with valid sales
        val closingState = CashUiState(
            selectedType = MovementType.CLOSING,
            summary = summary,
            totalSalesForDay = "3000",
        )
        // expected = 2000 + 3000 + 500 - 200 = 5300
        assertNotNull(closingState.expectedClosingFromSales)
        assertEquals(5300.0, closingState.expectedClosingFromSales!!, 0.001)

        // OPENING type with same data should return null
        val openingState = closingState.copy(selectedType = MovementType.OPENING)
        assertNull(openingState.expectedClosingFromSales)

        // INCOME type with same data should return null
        val incomeState = closingState.copy(selectedType = MovementType.INCOME)
        assertNull(incomeState.expectedClosingFromSales)

        // EXPENSE type with same data should return null
        val expenseState = closingState.copy(selectedType = MovementType.EXPENSE)
        assertNull(expenseState.expectedClosingFromSales)
    }

    @Test
    fun `expectedClosingFromSales returns null when totalSalesForDay is empty`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            summary = CashSummary(openingAmount = 1000.0),
            totalSalesForDay = "",
        )
        assertNull(state.expectedClosingFromSales)
    }

    // =========================================================================
    // discrepancyPreview tests
    // =========================================================================

    @Test
    fun `discrepancyPreview computed correctly - overage`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            summary = CashSummary(
                openingAmount = 1000.0,
                totalIncome = 200.0,
                totalExpenses = 100.0,
            ),
            totalSalesForDay = "2000",
            // expected = 1000 + 2000 + 200 - 100 = 3100
            billCounts = mapOf(
                1000.0 to 3, 500.0 to 1, 200.0 to 0,
                100.0 to 0, 50.0 to 0, 20.0 to 0,
            ),  // 3500
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 0, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 0,
            ),
        )
        // discrepancy = 3500 - 3100 = 400 (overage)
        assertNotNull(state.discrepancyPreview)
        assertEquals(400.0, state.discrepancyPreview!!, 0.001)
    }

    @Test
    fun `discrepancyPreview computed correctly - shortage`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            summary = CashSummary(
                openingAmount = 1000.0,
                totalIncome = 0.0,
                totalExpenses = 0.0,
            ),
            totalSalesForDay = "2000",
            // expected = 1000 + 2000 + 0 - 0 = 3000
            billCounts = mapOf(
                1000.0 to 2, 500.0 to 0, 200.0 to 0,
                100.0 to 5, 50.0 to 0, 20.0 to 0,
            ),  // 2500
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 0, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 0,
            ),
        )
        // discrepancy = 2500 - 3000 = -500 (shortage)
        assertNotNull(state.discrepancyPreview)
        assertEquals(-500.0, state.discrepancyPreview!!, 0.001)
    }

    @Test
    fun `discrepancyPreview returns null for non-closing types`() {
        val state = CashUiState(
            selectedType = MovementType.OPENING,
            totalSalesForDay = "1000",
        )
        assertNull(state.discrepancyPreview)
    }

    @Test
    fun `discrepancyPreview returns null when totalSalesForDay is empty`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            totalSalesForDay = "",
        )
        assertNull(state.discrepancyPreview)
    }

    @Test
    fun `discrepancyPreview balanced when actual equals expected`() {
        val state = CashUiState(
            selectedType = MovementType.CLOSING,
            summary = CashSummary(
                openingAmount = 500.0,
                totalIncome = 100.0,
                totalExpenses = 50.0,
            ),
            totalSalesForDay = "450",
            // expected = 500 + 450 + 100 - 50 = 1000
            billCounts = mapOf(
                1000.0 to 1, 500.0 to 0, 200.0 to 0,
                100.0 to 0, 50.0 to 0, 20.0 to 0,
            ),  // 1000
            coinCounts = mapOf(
                20.0 to 0, 10.0 to 0, 5.0 to 0,
                2.0 to 0, 1.0 to 0, 0.5 to 0,
            ),
        )
        // discrepancy = 1000 - 1000 = 0
        assertNotNull(state.discrepancyPreview)
        assertEquals(0.0, state.discrepancyPreview!!, 0.001)
    }
}
