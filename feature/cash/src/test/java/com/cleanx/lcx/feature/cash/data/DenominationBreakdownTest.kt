package com.cleanx.lcx.feature.cash.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DenominationBreakdown.total] calculation.
 *
 * Verifies that the denomination counting logic correctly multiplies
 * each denomination by its face value and sums everything up,
 * including sub-peso coins (50 centavos).
 */
class DenominationBreakdownTest {

    @Test
    fun `total with all zeros returns 0`() {
        val breakdown = DenominationBreakdown()
        assertEquals(0.0, breakdown.total(), 0.001)
    }

    @Test
    fun `total with one bill of each denomination`() {
        val breakdown = DenominationBreakdown(
            bills1000 = 1,
            bills500 = 1,
            bills200 = 1,
            bills100 = 1,
            bills50 = 1,
            bills20 = 1,
        )
        // 1000 + 500 + 200 + 100 + 50 + 20 = 1870
        assertEquals(1870.0, breakdown.total(), 0.001)
    }

    @Test
    fun `total with mixed bills and coins`() {
        val breakdown = DenominationBreakdown(
            bills500 = 2,   // 1000
            bills100 = 3,   // 300
            coins10 = 5,    // 50
            coins5 = 2,     // 10
            coins1 = 4,     // 4
        )
        // 1000 + 300 + 50 + 10 + 4 = 1364
        assertEquals(1364.0, breakdown.total(), 0.001)
    }

    @Test
    fun `total with only coins including 50c`() {
        val breakdown = DenominationBreakdown(
            coins20 = 1,    // 20
            coins10 = 2,    // 20
            coins5 = 1,     // 5
            coins2 = 3,     // 6
            coins1 = 4,     // 4
            coins50c = 3,   // 1.5
        )
        // 20 + 20 + 5 + 6 + 4 + 1.5 = 56.5
        assertEquals(56.5, breakdown.total(), 0.001)
    }

    @Test
    fun `total precision check - coins50c produces decimal not integer`() {
        val breakdown = DenominationBreakdown(coins50c = 1)
        assertEquals(0.5, breakdown.total(), 0.0001)

        val breakdown3 = DenominationBreakdown(coins50c = 3)
        assertEquals(1.5, breakdown3.total(), 0.0001)

        val breakdown7 = DenominationBreakdown(coins50c = 7)
        assertEquals(3.5, breakdown7.total(), 0.0001)
    }

    @Test
    fun `total with all denominations populated`() {
        val breakdown = DenominationBreakdown(
            bills1000 = 1,  // 1000
            bills500 = 1,   // 500
            bills200 = 1,   // 200
            bills100 = 1,   // 100
            bills50 = 1,    // 50
            bills20 = 1,    // 20
            coins20 = 1,    // 20
            coins10 = 1,    // 10
            coins5 = 1,     // 5
            coins2 = 1,     // 2
            coins1 = 1,     // 1
            coins50c = 1,   // 0.5
        )
        // 1000 + 500 + 200 + 100 + 50 + 20 + 20 + 10 + 5 + 2 + 1 + 0.5 = 1908.5
        assertEquals(1908.5, breakdown.total(), 0.001)
    }

    @Test
    fun `total with large quantities`() {
        val breakdown = DenominationBreakdown(
            bills1000 = 10,  // 10000
            bills500 = 20,   // 10000
            coins50c = 100,  // 50
        )
        assertEquals(20050.0, breakdown.total(), 0.001)
    }
}
