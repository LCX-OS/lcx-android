package com.cleanx.lcx.feature.tickets.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.PaymentStatusChip
import com.cleanx.lcx.core.ui.StatusChip

/**
 * Displays a filtered view of tickets based on a preset filter.
 *
 * Presets:
 * - "active"    -> RECEIVED, PROCESSING  (title: "Activos")
 * - "ready"     -> READY                 (title: "Listos")
 * - "completed" -> DELIVERED             (title: "Completados")
 * - "all"       -> no filter             (title: "Todos")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPresetScreen(
    preset: String,
    viewModel: TicketListViewModel,
    onTicketClick: (Ticket) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val title = remember(preset) {
        when (preset) {
            "active" -> "Activos"
            "ready" -> "Listos"
            "completed" -> "Completados"
            "all" -> "Todos"
            else -> "Tickets"
        }
    }

    val filteredTickets = remember(preset, state.tickets) {
        when (preset) {
            "active" -> state.tickets.filter {
                it.status == TicketStatus.RECEIVED || it.status == TicketStatus.PROCESSING
            }
            "ready" -> state.tickets.filter { it.status == TicketStatus.READY }
            "completed" -> state.tickets.filter {
                it.status == TicketStatus.DELIVERED || it.status == TicketStatus.PAID
            }
            "all" -> state.tickets
            else -> state.tickets
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && filteredTickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null && filteredTickets.isEmpty()) {
            ErrorState(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh,
            )
        } else if (filteredTickets.isEmpty()) {
            EmptyState(
                title = "Sin encargos",
                modifier = Modifier.padding(padding),
                description = "No hay encargos en esta categoría",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = LcxSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                item { Spacer(modifier = Modifier.height(LcxSpacing.sm)) }
                items(filteredTickets, key = { it.id }) { ticket ->
                    PresetTicketListItem(
                        ticket = ticket,
                        onClick = { onTicketClick(ticket) },
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PresetTicketListItem(
    ticket: Ticket,
    onClick: () -> Unit,
) {
    LcxCard(
        modifier = Modifier.clickable(
            onClick = onClick,
            onClickLabel = "Ver detalle del ticket ${ticket.ticketNumber}",
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ticket.ticketNumber,
                style = MaterialTheme.typography.titleMedium,
            )
            StatusChip(status = ticket.status)
        }
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = ticket.customerName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ticket.service ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ticket.totalAmount?.let { amount ->
                Text(
                    text = "$${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        ticket.paymentStatus?.let { paymentStatus ->
            PaymentStatusChip(status = paymentStatus)
        }
    }
}
