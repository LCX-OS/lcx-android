package com.cleanx.lcx.feature.water.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterHistoryFormattingTest {

    @Test
    fun `formatLevelSummary renders percentage and grouped liters without formatter crash`() {
        assertEquals("17% - 1,700 L", formatLevelSummary(levelPercentage = 17, liters = 1700))
    }

    @Test
    fun `formatLevelSummary falls back to zeroes for missing values`() {
        assertEquals("0% - 0 L", formatLevelSummary(levelPercentage = null, liters = null))
    }
}
