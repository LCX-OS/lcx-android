package com.cleanx.lcx.core.operational

enum class OperatorOperationalStatus {
    OK,
    PENDING,
    BLOCKING,
}

enum class OperatorOperationalProgress {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ;

    companion object {
        fun fromPersisted(value: String?): OperatorOperationalProgress = when (value?.lowercase()) {
            "completed" -> COMPLETED
            "in_progress" -> IN_PROGRESS
            else -> PENDING
        }
    }
}

enum class OperatorOperationalTaskKey {
    ENTRY_CHECKLIST,
    WATER_LEVEL,
    CASH_OPENING,
    EXIT_CHECKLIST,
    CASH_CLOSING,
}

enum class OperatorOperationalAction {
    OPEN_ENTRY_CHECKLIST,
    OPEN_EXIT_CHECKLIST,
    OPEN_WATER,
    OPEN_CASH,
}

data class ChecklistRoutineRequirements(
    val waterReviewedToday: Boolean = false,
    val openingCashRegisteredToday: Boolean = false,
    val closingCashRegisteredToday: Boolean = false,
)

data class OperatorOperationalWaterSignal(
    val recordedToday: Boolean = false,
    val lastLevelPercentage: Int? = null,
    val lastRecordedAt: String? = null,
    val lastStatusLabel: String? = null,
)

data class OperatorOperationalCashSignal(
    val openingRegisteredToday: Boolean = false,
    val closingRegisteredToday: Boolean = false,
)

data class OperatorOperationalChecklistSignal(
    val existsToday: Boolean = false,
    val progress: OperatorOperationalProgress = OperatorOperationalProgress.PENDING,
)

data class OperatorOperationalChecklistSignals(
    val entry: OperatorOperationalChecklistSignal = OperatorOperationalChecklistSignal(),
    val exit: OperatorOperationalChecklistSignal = OperatorOperationalChecklistSignal(),
)

data class OperatorOperationalSignals(
    val water: OperatorOperationalWaterSignal = OperatorOperationalWaterSignal(),
    val cash: OperatorOperationalCashSignal = OperatorOperationalCashSignal(),
    val checklists: OperatorOperationalChecklistSignals = OperatorOperationalChecklistSignals(),
)

data class OperatorOperationalTask(
    val key: OperatorOperationalTaskKey,
    val title: String,
    val progress: OperatorOperationalProgress,
    val status: OperatorOperationalStatus,
    val detail: String,
    val action: OperatorOperationalAction,
    val actionLabel: String,
) {
    val isCompleted: Boolean
        get() = progress == OperatorOperationalProgress.COMPLETED
}

data class OperatorOperationalGroup(
    val title: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<OperatorOperationalTask>,
)

data class OperatorOperationalRoutine(
    val entry: OperatorOperationalGroup,
    val exit: OperatorOperationalGroup,
)

data class OperatorOperationalSummary(
    val status: OperatorOperationalStatus,
    val headline: String,
    val recommendation: String,
    val nextTaskKey: OperatorOperationalTaskKey? = null,
    val nextAction: OperatorOperationalAction? = null,
)

data class OperatorOperationalSnapshot(
    val signals: OperatorOperationalSignals,
    val routine: OperatorOperationalRoutine,
    val summary: OperatorOperationalSummary,
) {
    val checklistRequirements: ChecklistRoutineRequirements
        get() = ChecklistRoutineRequirements(
            waterReviewedToday = signals.water.recordedToday,
            openingCashRegisteredToday = signals.cash.openingRegisteredToday,
            closingCashRegisteredToday = signals.cash.closingRegisteredToday,
        )
}

internal fun buildOperatorOperationalSnapshot(
    signals: OperatorOperationalSignals,
): OperatorOperationalSnapshot {
    val entryItems = listOf(
        buildChecklistTask(
            key = OperatorOperationalTaskKey.ENTRY_CHECKLIST,
            title = "Checklist de entrada",
            signal = signals.checklists.entry,
            status = OperatorOperationalStatus.BLOCKING,
            action = OperatorOperationalAction.OPEN_ENTRY_CHECKLIST,
        ),
        buildWaterTask(signals.water),
        buildCashOpeningTask(signals.cash),
    )
    val exitItems = listOf(
        buildChecklistTask(
            key = OperatorOperationalTaskKey.EXIT_CHECKLIST,
            title = "Checklist de salida",
            signal = signals.checklists.exit,
            status = OperatorOperationalStatus.PENDING,
            action = OperatorOperationalAction.OPEN_EXIT_CHECKLIST,
        ),
        buildCashClosingTask(signals.cash),
    )

    val routine = OperatorOperationalRoutine(
        entry = entryItems.toRoutineGroup(title = "Inicio de turno"),
        exit = exitItems.toRoutineGroup(title = "Cierre de turno"),
    )

    return OperatorOperationalSnapshot(
        signals = signals,
        routine = routine,
        summary = buildOperationalSummary(entryItems + exitItems),
    )
}

private fun List<OperatorOperationalTask>.toRoutineGroup(title: String): OperatorOperationalGroup {
    return OperatorOperationalGroup(
        title = title,
        completedCount = count { it.isCompleted },
        totalCount = size,
        items = this,
    )
}

private fun buildChecklistTask(
    key: OperatorOperationalTaskKey,
    title: String,
    signal: OperatorOperationalChecklistSignal,
    status: OperatorOperationalStatus,
    action: OperatorOperationalAction,
): OperatorOperationalTask {
    val typeLabel = when (key) {
        OperatorOperationalTaskKey.ENTRY_CHECKLIST -> "entrada"
        OperatorOperationalTaskKey.EXIT_CHECKLIST -> "salida"
        else -> error("Unsupported checklist task key: $key")
    }
    val progress = signal.progress
    val detail = when (progress) {
        OperatorOperationalProgress.COMPLETED -> "Completado hoy"
        OperatorOperationalProgress.IN_PROGRESS -> "En progreso"
        OperatorOperationalProgress.PENDING -> {
            if (signal.existsToday) {
                "Pendiente por completar"
            } else {
                "Aun no iniciado hoy"
            }
        }
    }
    val actionLabel = when {
        progress == OperatorOperationalProgress.COMPLETED -> "Checklist de $typeLabel al dia"
        signal.existsToday -> "Completar checklist de $typeLabel"
        else -> "Abrir checklist de $typeLabel"
    }

    return OperatorOperationalTask(
        key = key,
        title = title,
        progress = progress,
        status = if (progress == OperatorOperationalProgress.COMPLETED) {
            OperatorOperationalStatus.OK
        } else {
            status
        },
        detail = detail,
        action = action,
        actionLabel = actionLabel,
    )
}

private fun buildWaterTask(
    signal: OperatorOperationalWaterSignal,
): OperatorOperationalTask {
    val detail = when {
        !signal.recordedToday -> "Sin registro de agua hoy"
        signal.lastLevelPercentage != null && signal.lastStatusLabel != null ->
            "${signal.lastLevelPercentage}% - ${signal.lastStatusLabel}"
        else -> "Nivel validado hoy"
    }

    return OperatorOperationalTask(
        key = OperatorOperationalTaskKey.WATER_LEVEL,
        title = "Nivel de agua",
        progress = if (signal.recordedToday) {
            OperatorOperationalProgress.COMPLETED
        } else {
            OperatorOperationalProgress.PENDING
        },
        status = if (signal.recordedToday) {
            OperatorOperationalStatus.OK
        } else {
            OperatorOperationalStatus.BLOCKING
        },
        detail = detail,
        action = OperatorOperationalAction.OPEN_WATER,
        actionLabel = "Registrar nivel de agua",
    )
}

private fun buildCashOpeningTask(
    signal: OperatorOperationalCashSignal,
): OperatorOperationalTask {
    return OperatorOperationalTask(
        key = OperatorOperationalTaskKey.CASH_OPENING,
        title = "Apertura de caja",
        progress = if (signal.openingRegisteredToday) {
            OperatorOperationalProgress.COMPLETED
        } else {
            OperatorOperationalProgress.PENDING
        },
        status = if (signal.openingRegisteredToday) {
            OperatorOperationalStatus.OK
        } else {
            OperatorOperationalStatus.BLOCKING
        },
        detail = if (signal.openingRegisteredToday) {
            "Apertura registrada hoy"
        } else {
            "Pendiente de registrar apertura"
        },
        action = OperatorOperationalAction.OPEN_CASH,
        actionLabel = "Registrar apertura de caja",
    )
}

private fun buildCashClosingTask(
    signal: OperatorOperationalCashSignal,
): OperatorOperationalTask {
    val progress = when {
        signal.closingRegisteredToday -> OperatorOperationalProgress.COMPLETED
        signal.openingRegisteredToday -> OperatorOperationalProgress.IN_PROGRESS
        else -> OperatorOperationalProgress.PENDING
    }
    val detail = when {
        signal.closingRegisteredToday -> "Corte de caja registrado hoy"
        signal.openingRegisteredToday -> "Apertura registrada, falta corte"
        else -> "Aun no hay apertura de caja hoy"
    }

    return OperatorOperationalTask(
        key = OperatorOperationalTaskKey.CASH_CLOSING,
        title = "Corte de caja",
        progress = progress,
        status = if (progress == OperatorOperationalProgress.COMPLETED) {
            OperatorOperationalStatus.OK
        } else {
            OperatorOperationalStatus.PENDING
        },
        detail = detail,
        action = OperatorOperationalAction.OPEN_CASH,
        actionLabel = "Registrar corte de caja",
    )
}

private fun buildOperationalSummary(
    items: List<OperatorOperationalTask>,
): OperatorOperationalSummary {
    val blockingItem = items
        .filter { !it.isCompleted && it.status == OperatorOperationalStatus.BLOCKING }
        .minByOrNull { it.summaryPriority() }
    if (blockingItem != null) {
        return OperatorOperationalSummary(
            status = OperatorOperationalStatus.BLOCKING,
            headline = "Hay tareas obligatorias que bloquean el turno",
            recommendation = blockingItem.actionLabel,
            nextTaskKey = blockingItem.key,
            nextAction = blockingItem.action,
        )
    }

    val pendingItem = items
        .filter { !it.isCompleted && it.status == OperatorOperationalStatus.PENDING }
        .minByOrNull { it.summaryPriority() }
    if (pendingItem != null) {
        return OperatorOperationalSummary(
            status = OperatorOperationalStatus.PENDING,
            headline = "Quedan pendientes por cerrar del turno",
            recommendation = pendingItem.actionLabel,
            nextTaskKey = pendingItem.key,
            nextAction = pendingItem.action,
        )
    }

    return OperatorOperationalSummary(
        status = OperatorOperationalStatus.OK,
        headline = "Turno operativo al dia",
        recommendation = "Agua, caja y checklists estan al corriente",
    )
}

private fun OperatorOperationalTask.summaryPriority(): Int = when (key) {
    OperatorOperationalTaskKey.WATER_LEVEL -> 0
    OperatorOperationalTaskKey.CASH_OPENING -> 1
    OperatorOperationalTaskKey.ENTRY_CHECKLIST -> 2
    OperatorOperationalTaskKey.CASH_CLOSING -> 3
    OperatorOperationalTaskKey.EXIT_CHECKLIST -> 4
}
