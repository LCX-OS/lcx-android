package com.cleanx.lcx.feature.checklist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRulesTest {

    @Test
    fun `entry system requirements track water and opening cash`() {
        val expectations = ChecklistType.ENTRADA.systemRequirementExpectations(
            ChecklistOperationalStatus(
                waterReviewedToday = true,
                openingCashRegisteredToday = false,
                closingCashRegisteredToday = true,
            ),
        )

        assertEquals(true, expectations[TEMPLATE_WATER_LEVEL])
        assertEquals(false, expectations[TEMPLATE_OPENING_CASH])
        assertFalse(expectations.containsKey(TEMPLATE_CLOSING_CASH))
    }

    @Test
    fun `exit system requirements only track closing cash`() {
        val expectations = ChecklistType.SALIDA.systemRequirementExpectations(
            ChecklistOperationalStatus(
                waterReviewedToday = true,
                openingCashRegisteredToday = true,
                closingCashRegisteredToday = false,
            ),
        )

        assertEquals(false, expectations[TEMPLATE_CLOSING_CASH])
        assertFalse(expectations.containsKey(TEMPLATE_WATER_LEVEL))
        assertFalse(expectations.containsKey(TEMPLATE_OPENING_CASH))
    }

    @Test
    fun `evaluate checklist completion blocks incomplete required items`() {
        val gate = evaluateChecklistCompletion(
            listOf(
                checklistItem(templateId = TEMPLATE_WATER_LEVEL, required = true, completed = true),
                checklistItem(templateId = TEMPLATE_OPENING_CASH, required = true, completed = false),
                checklistItem(templateId = "entry-7", required = false, completed = false),
            ),
        )

        assertFalse(gate.canComplete)
        assertEquals("Faltan 1 tareas requeridas por completar", gate.reason)
    }

    @Test
    fun `evaluate checklist completion allows optional pending items`() {
        val gate = evaluateChecklistCompletion(
            listOf(
                checklistItem(templateId = TEMPLATE_CLOSING_CASH, required = true, completed = true),
                checklistItem(templateId = "exit-7", required = false, completed = false),
            ),
        )

        assertTrue(gate.canComplete)
        assertNull(gate.reason)
    }

    private fun checklistItem(
        templateId: String,
        required: Boolean,
        completed: Boolean,
    ): ChecklistItem {
        return ChecklistItem(
            id = templateId,
            checklistId = "checklist-1",
            itemDescription = "Item $templateId: descripcion",
            isCompleted = completed,
            notes = """{"templateId":"$templateId","category":"admin","required":$required}""",
        )
    }
}
