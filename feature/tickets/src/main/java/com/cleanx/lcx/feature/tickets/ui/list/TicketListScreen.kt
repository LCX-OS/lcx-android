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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.core.ui.PaymentStatusChip
import com.cleanx.lcx.core.ui.StatusChip

private data class TicketPresetShortcut(
    val preset: String,
    val label: String,
    val count: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    viewModel: TicketListViewModel,
    onCreateTicket: () -> Unit,
    onOpenPreset: (String) -> Unit,
    onTicketClick: (Ticket) -> Unit,
    onSignOut: () -> Unit,
    showTopBar: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shortcuts = remember(state.tickets) {
        listOf(
            TicketPresetShortcut(
                preset = "active",
                label = "Activos",
                count = state.tickets.count { ticket ->
                    ticket.status == com.cleanx.lcx.core.model.TicketStatus.RECEIVED ||
                        ticket.status == com.cleanx.lcx.core.model.TicketStatus.PROCESSING
                },
            ),
            TicketPresetShortcut(
                preset = "ready",
                label = "Listos",
                count = state.tickets.count { it.status == com.cleanx.lcx.core.model.TicketStatus.READY },
            ),
            TicketPresetShortcut(
                preset = "completed",
                label = "Entregados",
                count = state.tickets.count {
                    it.status == com.cleanx.lcx.core.model.TicketStatus.DELIVERED ||
                        it.status == com.cleanx.lcx.core.model.TicketStatus.PAID
                },
            ),
            TicketPresetShortcut(
                preset = "all",
                label = "Todos",
                count = state.tickets.size,
            ),
        )
    }
    val filteredTickets = remember(state.tickets, query) {
        filterTicketsForQuery(state.tickets, query)
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                LcxTopAppBar(
                    title = {
                        Text(
                            "Encargos",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                viewModel.signOut(onSignedOut = onSignOut)
                            },
                        ) {
                            Text("Salir")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTicket) {
                Icon(Icons.Default.Add, contentDescription = "Crear encargo nuevo")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.tickets.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LcxSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                item { Spacer(modifier = Modifier.height(LcxSpacing.sm)) }
                item {
                    LcxTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Buscar folio, cliente o servicio",
                    )
                }
                item {
                    LcxCard(title = "Vistas rápidas") {
                        Text(
                            text = "Salta directo a los encargos activos, listos o entregados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(LcxSpacing.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
                            items(shortcuts, key = { it.preset }) { shortcut ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onOpenPreset(shortcut.preset) },
                                    label = { Text("${shortcut.label} (${shortcut.count})") },
                                )
                            }
                        }
                    }
                }
                if (state.error != null && state.tickets.isNotEmpty()) {
                    item {
                        LcxCard {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(LcxSpacing.sm))
                            TextButton(onClick = viewModel::refresh) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                when {
                    state.isLoading && state.tickets.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    state.error != null && state.tickets.isEmpty() -> {
                        item {
                            ErrorState(
                                message = state.error.orEmpty(),
                                onRetry = viewModel::refresh,
                            )
                        }
                    }

                    state.tickets.isEmpty() -> {
                        item {
                            EmptyState(
                                title = "Sin encargos recientes",
                                description = "Presiona + para crear un encargo nuevo.",
                            )
                        }
                    }

                    filteredTickets.isEmpty() -> {
                        item {
                            EmptyState(
                                title = "Sin resultados",
                                description = "No hay encargos que coincidan con la busqueda.",
                            )
                        }
                    }

                    else -> {
                        items(filteredTickets, key = { it.id }) { ticket ->
                            TicketListItem(
                                ticket = ticket,
                                onClick = { onTicketClick(ticket) },
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

private fun filterTicketsForQuery(
    tickets: List<Ticket>,
    query: String,
): List<Ticket> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return tickets

    return tickets.filter { ticket ->
        listOfNotNull(
            ticket.ticketNumber,
            ticket.customerName,
            ticket.customerPhone,
            ticket.service,
        ).any { value -> value.lowercase().contains(normalized) }
    }
}

@Composable
private fun TicketListItem(
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
