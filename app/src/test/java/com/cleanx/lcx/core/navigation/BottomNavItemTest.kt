package com.cleanx.lcx.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavItemTest {

    @Test
    fun `bottom nav exposes checklist instead of shell-only shifts`() {
        val graphRoutes = BottomNavItem.entries.map { it.graphRoute }

        assertTrue(Screen.ChecklistGraph in graphRoutes)
        assertFalse(Screen.ShiftsGraph in graphRoutes)
    }
}
