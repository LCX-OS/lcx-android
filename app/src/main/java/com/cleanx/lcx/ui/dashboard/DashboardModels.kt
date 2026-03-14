package com.cleanx.lcx.ui.dashboard

import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.operational.OperatorOperationalGroup
import com.cleanx.lcx.core.operational.OperatorOperationalProgress
import com.cleanx.lcx.core.operational.OperatorOperationalRoutine
import com.cleanx.lcx.core.operational.OperatorOperationalStatus
import com.cleanx.lcx.core.operational.OperatorOperationalSummary
import com.cleanx.lcx.core.operational.OperatorOperationalTask
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class DashboardSnapshot(
    val operatorName: String,
    val branchName: String?,
    val operationalSummary: DashboardOperationalSummary,
    val routine: DashboardRoutineSection,
    val pendingTickets: DashboardPendingTicketsSection,
    val supplyNeeds: DashboardSupplyNeedsSection,
)

data class DashboardOperationalSummary(
    val headline: String,
    val recommendation: String,
)

data class DashboardRoutineSection(
    val entryGroup: DashboardRoutineGroup,
    val exitGroup: DashboardRoutineGroup,
)

data class DashboardRoutineGroup(
    val title: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<DashboardRoutineItem>,
)

data class DashboardRoutineItem(
    val title: String,
    val state: DashboardRoutineState,
    val detail: String,
)

enum class DashboardRoutineState {
    DONE,
    IN_PROGRESS,
    PENDING,
    BLOCKING,
}

data class DashboardPendingTicketsSection(
    val totalCount: Int = 0,
    val items: List<DashboardTicketItem> = emptyList(),
    val error: String? = null,
)

data class DashboardTicketItem(
    val id: String,
    val customerName: String,
    val status: TicketStatus,
    val relativeAgeLabel: String,
    val priority: DashboardTicketPriority,
)

enum class DashboardTicketPriority {
    NORMAL,
    HIGH,
}

data class DashboardSupplyNeedsSection(
    val totalCount: Int = 0,
    val items: List<DashboardSupplyNeed> = emptyList(),
    val error: String? = null,
)

data class DashboardSupplyNeed(
    val id: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int,
    val severity: DashboardSupplySeverity,
)

enum class DashboardSupplySeverity {
    CRITICAL,
    LOW,
}

fun isOperationalPendingTicket(status: TicketStatus): Boolean = status != TicketStatus.DELIVERED

fun DashboardRoutineState.isDone(): Boolean = this == DashboardRoutineState.DONE

fun OperatorOperationalRoutine.toDashboardRoutineSection(): DashboardRoutineSection {
    return DashboardRoutineSection(
        entryGroup = entry.toDashboardRoutineGroup(),
        exitGroup = exit.toDashboardRoutineGroup(),
    )
}

fun OperatorOperationalSummary.toDashboardOperationalSummary(): DashboardOperationalSummary {
    return DashboardOperationalSummary(
        headline = headline,
        recommendation = recommendation,
    )
}

fun OperatorOperationalTask.toDashboardRoutineItem(): DashboardRoutineItem {
    return DashboardRoutineItem(
        title = title,
        state = toDashboardRoutineState(),
        detail = detail,
    )
}

fun OperatorOperationalTask.toDashboardRoutineState(): DashboardRoutineState {
    if (status == OperatorOperationalStatus.BLOCKING && progress != OperatorOperationalProgress.COMPLETED) {
        return DashboardRoutineState.BLOCKING
    }

    return when (progress) {
        OperatorOperationalProgress.COMPLETED -> DashboardRoutineState.DONE
        OperatorOperationalProgress.IN_PROGRESS -> DashboardRoutineState.IN_PROGRESS
        OperatorOperationalProgress.PENDING -> DashboardRoutineState.PENDING
    }
}

private fun OperatorOperationalGroup.toDashboardRoutineGroup(): DashboardRoutineGroup {
    return DashboardRoutineGroup(
        title = title,
        completedCount = completedCount,
        totalCount = totalCount,
        items = items.map { it.toDashboardRoutineItem() },
    )
}

fun ticketPriorityFor(
    createdAt: String?,
    now: Instant = Instant.now(),
): DashboardTicketPriority {
    val created = createdAt.toInstantOrNull() ?: return DashboardTicketPriority.NORMAL
    val waitedMinutes = Duration.between(created, now).toMinutes()
    return if (waitedMinutes >= 30) {
        DashboardTicketPriority.HIGH
    } else {
        DashboardTicketPriority.NORMAL
    }
}

fun formatRelativeTicketAge(
    createdAt: String?,
    now: Instant = Instant.now(),
): String {
    val created = createdAt.toInstantOrNull() ?: return "Fecha desconocida"
    val elapsed = Duration.between(created, now)
    val minutes = elapsed.toMinutes()
    return when {
        minutes < 1 -> "Hace menos de 1 min"
        minutes < 60 -> "Hace $minutes min"
        minutes < 1_440 -> "Hace ${elapsed.toHours()} h"
        else -> "Hace ${elapsed.toDays()} d"
    }
}

fun supplySeverityFor(
    quantity: Int,
    minQuantity: Int,
): DashboardSupplySeverity? {
    if (quantity > minQuantity) return null
    if (quantity <= 0) return DashboardSupplySeverity.CRITICAL

    if (minQuantity <= 0) {
        return DashboardSupplySeverity.LOW
    }

    val ratio = quantity.toDouble() / minQuantity.toDouble()
    return if (ratio <= 0.25) {
        DashboardSupplySeverity.CRITICAL
    } else {
        DashboardSupplySeverity.LOW
    }
}

fun Ticket.toDashboardTicketItem(now: Instant = Instant.now()): DashboardTicketItem {
    return DashboardTicketItem(
        id = id,
        customerName = customerName,
        status = status,
        relativeAgeLabel = formatRelativeTicketAge(createdAt, now),
        priority = ticketPriorityFor(createdAt, now),
    )
}

fun InventoryCatalogRecord.toDashboardSupplyNeed(): DashboardSupplyNeed? {
    val severity = supplySeverityFor(quantity, minQuantity) ?: return null
    return DashboardSupplyNeed(
        id = id,
        itemName = itemName,
        quantity = quantity,
        minQuantity = minQuantity,
        severity = severity,
    )
}

private fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null
    return try {
        Instant.parse(this)
    } catch (_: DateTimeParseException) {
        runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
    }
}
