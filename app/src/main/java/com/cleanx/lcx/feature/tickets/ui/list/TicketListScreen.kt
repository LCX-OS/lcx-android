package com.cleanx.lcx.feature.tickets.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    viewModel: TicketListViewModel,
    onCreateTicket: () -> Unit,
    onTicketClick: (Ticket) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tickets") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTicket) {
                Icon(Icons.Default.Add, contentDescription = "Crear ticket")
            }
        },
    ) { padding ->
        if (state.tickets.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sin tickets recientes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Presiona + para crear un ticket nuevo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(state.tickets, key = { it.id }) { ticket ->
                    TicketListItem(
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
private fun TicketListItem(
    ticket: Ticket,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = ticket.customerName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
            Spacer(modifier = Modifier.height(4.dp))
            PaymentStatusText(paymentStatus = ticket.paymentStatus)
        }
    }
}

@Composable
fun StatusChip(status: TicketStatus) {
    val (label, color) = when (status) {
        TicketStatus.RECEIVED -> "Recibido" to MaterialTheme.colorScheme.primary
        TicketStatus.PROCESSING -> "En proceso" to MaterialTheme.colorScheme.tertiary
        TicketStatus.READY -> "Listo" to MaterialTheme.colorScheme.secondary
        TicketStatus.DELIVERED -> "Entregado" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun PaymentStatusText(paymentStatus: PaymentStatus?) {
    val label = when (paymentStatus) {
        PaymentStatus.PENDING -> "Pago pendiente"
        PaymentStatus.PREPAID -> "Prepagado"
        PaymentStatus.PAID -> "Pagado"
        null -> "Sin estado de pago"
    }
    val color = when (paymentStatus) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.secondary
        PaymentStatus.PREPAID -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
