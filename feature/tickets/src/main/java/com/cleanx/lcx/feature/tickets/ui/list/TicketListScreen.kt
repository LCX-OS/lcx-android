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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.PaymentStatusChip
import com.cleanx.lcx.core.ui.StatusChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    viewModel: TicketListViewModel,
    onCreateTicket: () -> Unit,
    onTicketClick: (Ticket) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tickets",
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTicket) {
                Icon(Icons.Default.Add, contentDescription = "Crear ticket nuevo")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.tickets.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "Sin tickets recientes",
                modifier = Modifier.padding(padding),
                description = "Presiona + para crear un ticket nuevo",
            )
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        // Simulate refresh — no real endpoint yet
                        delay(800)
                        isRefreshing = false
                        snackbarHostState.showSnackbar("Lista actualizada")
                    }
                },
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
