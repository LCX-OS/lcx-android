package com.cleanx.lcx.ui.dashboard

import com.cleanx.lcx.core.operational.OperatorOperationalAction
import com.cleanx.lcx.core.operational.OperatorOperationalProgress
import com.cleanx.lcx.core.operational.OperatorOperationalStatus
import com.cleanx.lcx.core.operational.OperatorOperationalTask
import com.cleanx.lcx.core.operational.OperatorOperationalTaskKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DashboardModelsTest {

    @Test
    fun `ticket priority turns high after thirty minutes`() {
        val now = Instant.parse("2026-03-13T22:00:00Z")

        assertEquals(
            DashboardTicketPriority.NORMAL,
            ticketPriorityFor("2026-03-13T21:45:30Z", now),
        )
        assertEquals(
            DashboardTicketPriority.HIGH,
            ticketPriorityFor("2026-03-13T21:29:59Z", now),
        )
    }

    @Test
    fun `supply severity only flags low stock rows`() {
        assertNull(supplySeverityFor(quantity = 8, minQuantity = 5))
        assertEquals(
            DashboardSupplySeverity.LOW,
            supplySeverityFor(quantity = 2, minQuantity = 5),
        )
        assertEquals(
            DashboardSupplySeverity.CRITICAL,
            supplySeverityFor(quantity = 0, minQuantity = 5),
        )
    }

    @Test
    fun `operational task maps blocking and progress to dashboard states`() {
        val blockingTask = OperatorOperationalTask(
            key = OperatorOperationalTaskKey.WATER_LEVEL,
            title = "Nivel de agua",
            progress = OperatorOperationalProgress.PENDING,
            status = OperatorOperationalStatus.BLOCKING,
            detail = "Sin registro de agua hoy",
            action = OperatorOperationalAction.OPEN_WATER,
            actionLabel = "Registrar nivel de agua",
        )
        val inProgressTask = OperatorOperationalTask(
            key = OperatorOperationalTaskKey.CASH_CLOSING,
            title = "Corte de caja",
            progress = OperatorOperationalProgress.IN_PROGRESS,
            status = OperatorOperationalStatus.PENDING,
            detail = "Apertura registrada, falta corte",
            action = OperatorOperationalAction.OPEN_CASH,
            actionLabel = "Registrar corte de caja",
        )

        assertEquals(DashboardRoutineState.BLOCKING, blockingTask.toDashboardRoutineState())
        assertEquals(DashboardRoutineState.IN_PROGRESS, inProgressTask.toDashboardRoutineState())
    }

    @Test
    fun `operational summary maps to next dashboard action`() {
        val summary = com.cleanx.lcx.core.operational.OperatorOperationalSummary(
            status = OperatorOperationalStatus.BLOCKING,
            headline = "Hay tareas obligatorias",
            recommendation = "Registrar nivel de agua",
            nextAction = OperatorOperationalAction.OPEN_WATER,
        ).toDashboardOperationalSummary()

        val next = summary.toNextAction()

        assertEquals("Registrar agua", next.title)
        assertEquals("Registrar nivel de agua", next.ctaLabel)
        assertEquals(OperatorOperationalAction.OPEN_WATER, next.action)
    }
}
