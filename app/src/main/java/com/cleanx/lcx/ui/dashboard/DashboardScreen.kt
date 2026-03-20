package com.cleanx.lcx.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cleanx.lcx.core.theme.LcxError
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.theme.LcxWarning
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.QuickActionCard

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenChecklist: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenCreateTicket: () -> Unit,
    onOpenTicket: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when {
        state.isLoading && state.snapshot == null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.error != null && state.snapshot == null -> {
            ErrorState(
                message = state.error.orEmpty(),
                modifier = modifier,
                onRetry = viewModel::refresh,
            )
        }

        else -> {
            val snapshot = state.snapshot ?: return
            DashboardContent(
                snapshot = snapshot,
                isRefreshing = state.isRefreshing,
                globalError = state.error,
                onRefresh = viewModel::refresh,
                onOpenChecklist = onOpenChecklist,
                onOpenWater = onOpenWater,
                onOpenCash = onOpenCash,
                onOpenCreateTicket = onOpenCreateTicket,
                onOpenTicket = onOpenTicket,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    snapshot: DashboardSnapshot,
    isRefreshing: Boolean,
    globalError: String?,
    onRefresh: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenCreateTicket: () -> Unit,
    onOpenTicket: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = LcxSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        item { Spacer(modifier = Modifier.height(LcxSpacing.xs)) }

        item {
            HeroCard(
                snapshot = snapshot,
                isRefreshing = isRefreshing,
                globalError = globalError,
                onRefresh = onRefresh,
            )
        }

        item {
            QuickActionsSection(
                onOpenChecklist = onOpenChecklist,
                onOpenWater = onOpenWater,
                onOpenCash = onOpenCash,
                onOpenCreateTicket = onOpenCreateTicket,
            )
        }

        item {
            RoutineSection(snapshot.routine)
        }

        item {
            PendingTicketsSection(
                section = snapshot.pendingTickets,
                onOpenCreateTicket = onOpenCreateTicket,
                onOpenTicket = onOpenTicket,
            )
        }

        item {
            SupplyNeedsSection(snapshot.supplyNeeds)
        }

        item { Spacer(modifier = Modifier.height(LcxSpacing.xl)) }
    }
}

@Composable
private fun HeroCard(
    snapshot: DashboardSnapshot,
    isRefreshing: Boolean,
    globalError: String?,
    onRefresh: () -> Unit,
) {
    LcxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                Text(
                    text = "Centro operativo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = snapshot.operatorName,
                    style = MaterialTheme.typography.titleMedium,
                )
                snapshot.branchName?.let { branch ->
                    Text(
                        text = "Sucursal: $branch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = snapshot.operationalSummary.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = snapshot.operationalSummary.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                globalError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            TextButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                Text(if (isRefreshing) "Actualizando..." else "Actualizar")
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onOpenChecklist: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenCreateTicket: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
        Text(
            text = "Acciones rapidas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    icon = Icons.Outlined.Checklist,
                    title = "Checklist entrada",
                    description = "Arranque operativo del turno",
                    onClick = onOpenChecklist,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    icon = Icons.Outlined.WaterDrop,
                    title = "Agua",
                    description = "Registrar el nivel del tanque",
                    onClick = onOpenWater,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Caja",
                    description = "Apertura, gastos y corte",
                    onClick = onOpenCash,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    icon = Icons.Outlined.AddTask,
                    title = "Nuevo encargo",
                    description = "Registrar ticket de mostrador",
                    onClick = onOpenCreateTicket,
                )
            }
        }
    }
}

@Composable
private fun RoutineSection(section: DashboardRoutineSection) {
    LcxCard(title = "Rutina operativa del dia") {
        RoutineGroupCard(section.entryGroup)
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        RoutineGroupCard(section.exitGroup)
    }
}

@Composable
private fun RoutineGroupCard(group: DashboardRoutineGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(LcxSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            StatusPill(
                label = "${group.completedCount}/${group.totalCount}",
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                content = MaterialTheme.colorScheme.primary,
            )
        }

        group.items.forEachIndexed { index, item ->
            RoutineRow(item)
            if (index != group.items.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RoutineRow(item: DashboardRoutineItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusPill(
            label = when (item.state) {
                DashboardRoutineState.DONE -> "OK"
                DashboardRoutineState.IN_PROGRESS -> "En curso"
                DashboardRoutineState.PENDING -> "Pendiente"
                DashboardRoutineState.BLOCKING -> "Bloqueante"
            },
            background = when (item.state) {
                DashboardRoutineState.DONE -> LcxSuccess.copy(alpha = 0.14f)
                DashboardRoutineState.IN_PROGRESS -> LcxWarning.copy(alpha = 0.14f)
                DashboardRoutineState.PENDING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                DashboardRoutineState.BLOCKING -> LcxError.copy(alpha = 0.14f)
            },
            content = when (item.state) {
                DashboardRoutineState.DONE -> LcxSuccess
                DashboardRoutineState.IN_PROGRESS -> LcxWarning
                DashboardRoutineState.PENDING -> MaterialTheme.colorScheme.onPrimaryContainer
                DashboardRoutineState.BLOCKING -> LcxError
            },
        )
    }
}

@Composable
private fun PendingTicketsSection(
    section: DashboardPendingTicketsSection,
    onOpenCreateTicket: () -> Unit,
    onOpenTicket: (String) -> Unit,
) {
    LcxCard(title = "Tickets pendientes") {
        when {
            section.error != null -> {
                Text(
                    text = section.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            section.totalCount == 0 -> {
                Text(
                    text = "No hay tickets abiertos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenCreateTicket) {
                    Text("Crear nuevo encargo")
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Abiertos: ${section.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Recibidos, en proceso y listos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                section.items.forEachIndexed { index, item ->
                    TicketRow(
                        item = item,
                        onOpenTicket = { onOpenTicket(item.id) },
                    )
                    if (index != section.items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = LcxSpacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRow(
    item: DashboardTicketItem,
    onOpenTicket: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${ticketStatusLabel(item.status)} - ${item.relativeAgeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            if (item.priority == DashboardTicketPriority.HIGH) {
                StatusPill(
                    label = "Alta",
                    background = LcxError.copy(alpha = 0.14f),
                    content = LcxError,
                )
            }
            TextButton(onClick = onOpenTicket) {
                Text("Abrir")
            }
        }
    }
}

@Composable
private fun SupplyNeedsSection(section: DashboardSupplyNeedsSection) {
    LcxCard(title = "Necesidades de suministros") {
        when {
            section.error != null -> {
                Text(
                    text = section.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            section.totalCount == 0 -> {
                Text(
                    text = "No hay suministros por reordenar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Alertas: ${section.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    IconLabel(
                        icon = Icons.Outlined.Inventory2,
                        label = "Top ${section.items.size}",
                    )
                }
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                section.items.forEachIndexed { index, item ->
                    SupplyRow(item)
                    if (index != section.items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = LcxSpacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplyRow(item: DashboardSupplyNeed) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Stock ${item.quantity} / Minimo ${item.minQuantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusPill(
            label = if (item.severity == DashboardSupplySeverity.CRITICAL) {
                "Critico"
            } else {
                "Bajo"
            },
            background = if (item.severity == DashboardSupplySeverity.CRITICAL) {
                LcxError.copy(alpha = 0.14f)
            } else {
                LcxWarning.copy(alpha = 0.14f)
            },
            content = if (item.severity == DashboardSupplySeverity.CRITICAL) {
                LcxError
            } else {
                LcxWarning
            },
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    background: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .background(
                color = background,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IconLabel(
    icon: ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ticketStatusLabel(status: TicketStatus): String {
    return when (status) {
        TicketStatus.RECEIVED -> "Recibido"
        TicketStatus.PROCESSING -> "En proceso"
        TicketStatus.READY -> "Listo"
        TicketStatus.DELIVERED -> "Entregado"
        TicketStatus.PAID -> "Pagado"
    }
}
