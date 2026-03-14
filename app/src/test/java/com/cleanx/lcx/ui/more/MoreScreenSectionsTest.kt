package com.cleanx.lcx.ui.more

import com.cleanx.lcx.core.navigation.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreScreenSectionsTest {

    @Test
    fun `buildSections includes payment diagnostics in debug`() {
        val sections = buildSections(includePaymentDiagnostics = true)

        val hasDiagnostics = sections
            .flatMap { it.items }
            .any { it.screen == Screen.PaymentDiagnostics }

        assertTrue(hasDiagnostics)
    }

    @Test
    fun `buildSections omits payment diagnostics outside debug`() {
        val sections = buildSections(includePaymentDiagnostics = false)

        val hasDiagnostics = sections
            .flatMap { it.items }
            .any { it.screen == Screen.PaymentDiagnostics }

        assertFalse(hasDiagnostics)
    }
}
