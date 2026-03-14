package com.cleanx.lcx.core.operational

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorOperationalStateTest {

    @Test
    fun `summary prioritizes water before other blocking entry tasks`() {
        val snapshot = buildOperatorOperationalSnapshot(
            signals = OperatorOperationalSignals(
                water = OperatorOperationalWaterSignal(recordedToday = false),
                cash = OperatorOperationalCashSignal(
                    openingRegisteredToday = false,
                    closingRegisteredToday = false,
                ),
                checklists = OperatorOperationalChecklistSignals(
                    entry = OperatorOperationalChecklistSignal(
                        existsToday = true,
                        progress = OperatorOperationalProgress.IN_PROGRESS,
                    ),
                ),
            ),
        )

        assertEquals(OperatorOperationalStatus.BLOCKING, snapshot.summary.status)
        assertEquals(
            OperatorOperationalTaskKey.WATER_LEVEL,
            snapshot.summary.nextTaskKey,
        )
        assertEquals("Registrar nivel de agua", snapshot.summary.recommendation)
    }

    @Test
    fun `entry checklist in progress remains blocking once prerequisites are done`() {
        val snapshot = buildOperatorOperationalSnapshot(
            signals = OperatorOperationalSignals(
                water = OperatorOperationalWaterSignal(
                    recordedToday = true,
                    lastLevelPercentage = 62,
                    lastStatusLabel = "normal",
                ),
                cash = OperatorOperationalCashSignal(
                    openingRegisteredToday = true,
                    closingRegisteredToday = false,
                ),
                checklists = OperatorOperationalChecklistSignals(
                    entry = OperatorOperationalChecklistSignal(
                        existsToday = true,
                        progress = OperatorOperationalProgress.IN_PROGRESS,
                    ),
                ),
            ),
        )

        val entryChecklist = snapshot.routine.entry.items.first { it.key == OperatorOperationalTaskKey.ENTRY_CHECKLIST }

        assertEquals(OperatorOperationalProgress.IN_PROGRESS, entryChecklist.progress)
        assertEquals(OperatorOperationalStatus.BLOCKING, entryChecklist.status)
        assertEquals(OperatorOperationalTaskKey.ENTRY_CHECKLIST, snapshot.summary.nextTaskKey)
        assertEquals("Completar checklist de entrada", snapshot.summary.recommendation)
    }

    @Test
    fun `closing cash is the first pending exit recommendation`() {
        val snapshot = buildOperatorOperationalSnapshot(
            signals = OperatorOperationalSignals(
                water = OperatorOperationalWaterSignal(
                    recordedToday = true,
                    lastLevelPercentage = 81,
                    lastStatusLabel = "optimal",
                ),
                cash = OperatorOperationalCashSignal(
                    openingRegisteredToday = true,
                    closingRegisteredToday = false,
                ),
                checklists = OperatorOperationalChecklistSignals(
                    entry = OperatorOperationalChecklistSignal(
                        existsToday = true,
                        progress = OperatorOperationalProgress.COMPLETED,
                    ),
                    exit = OperatorOperationalChecklistSignal(
                        existsToday = false,
                        progress = OperatorOperationalProgress.PENDING,
                    ),
                ),
            ),
        )

        val cashClosing = snapshot.routine.exit.items.first { it.key == OperatorOperationalTaskKey.CASH_CLOSING }

        assertEquals(OperatorOperationalProgress.IN_PROGRESS, cashClosing.progress)
        assertEquals(OperatorOperationalStatus.PENDING, cashClosing.status)
        assertEquals(OperatorOperationalStatus.PENDING, snapshot.summary.status)
        assertEquals(OperatorOperationalTaskKey.CASH_CLOSING, snapshot.summary.nextTaskKey)
        assertEquals("Registrar corte de caja", snapshot.summary.recommendation)
    }

    @Test
    fun `fully completed routine reports ok and satisfied checklist requirements`() {
        val snapshot = buildOperatorOperationalSnapshot(
            signals = OperatorOperationalSignals(
                water = OperatorOperationalWaterSignal(
                    recordedToday = true,
                    lastLevelPercentage = 90,
                    lastRecordedAt = "2026-03-13T10:15:00Z",
                    lastStatusLabel = "optimal",
                ),
                cash = OperatorOperationalCashSignal(
                    openingRegisteredToday = true,
                    closingRegisteredToday = true,
                ),
                checklists = OperatorOperationalChecklistSignals(
                    entry = OperatorOperationalChecklistSignal(
                        existsToday = true,
                        progress = OperatorOperationalProgress.COMPLETED,
                    ),
                    exit = OperatorOperationalChecklistSignal(
                        existsToday = true,
                        progress = OperatorOperationalProgress.COMPLETED,
                    ),
                ),
            ),
        )

        assertEquals(OperatorOperationalStatus.OK, snapshot.summary.status)
        assertEquals(3, snapshot.routine.entry.completedCount)
        assertEquals(2, snapshot.routine.exit.completedCount)
        assertTrue(snapshot.checklistRequirements.waterReviewedToday)
        assertTrue(snapshot.checklistRequirements.openingCashRegisteredToday)
        assertTrue(snapshot.checklistRequirements.closingCashRegisteredToday)
        assertFalse(snapshot.routine.entry.items.any { !it.isCompleted })
        assertFalse(snapshot.routine.exit.items.any { !it.isCompleted })
    }
}
