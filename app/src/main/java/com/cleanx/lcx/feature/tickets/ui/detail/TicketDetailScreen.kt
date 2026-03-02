package com.cleanx.lcx.feature.tickets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.feature.tickets.ui.list.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    viewModel: TicketDetailViewModel,
    onBack: () -> Unit,
    onCharge: (String) -> Unit,
    onPrint: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ticket = state.ticket

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticket?.ticketNumber ?: "Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (ticket == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se encontro el ticket.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Ticket info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ticket.ticketNumber,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            StatusChip(status = ticket.status)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailRow("Cliente", ticket.customerName)
                        ticket.customerPhone?.let { DetailRow("Telefono", it) }
                        DetailRow("Tipo", serviceTypeLabel(ticket))
                        ticket.service?.let { DetailRow("Servicio", it) }
                        ticket.weight?.let { DetailRow("Peso", "${it} kg") }
                        ticket.notes?.let { DetailRow("Notas", it) }
                        DetailRow("Fecha", ticket.ticketDate)
                        DetailRow("Folio", "#${ticket.dailyFolio}")
                    }
                }

                // Payment card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pago",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Estado", paymentStatusLabel(ticket.paymentStatus))
                        ticket.paymentMethod?.let { DetailRow("Metodo", paymentMethodLabel(it)) }
                        ticket.totalAmount?.let {
                            DetailRow("Total", "$${String.format("%.2f", it)}")
                        }
                        DetailRow("Pagado", "$${String.format("%.2f", ticket.paidAmount)}")
                        ticket.paidAt?.let { DetailRow("Fecha pago", it) }
                    }
                }

                // Error message
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Loading indicator
                if (state.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                HorizontalDivider()

                // Status actions
                if (ticket.status != TicketStatus.DELIVERED) {
                    val nextLabel = when (ticket.status) {
                        TicketStatus.RECEIVED -> "Marcar En Proceso"
                        TicketStatus.PROCESSING -> "Marcar Listo"
                        TicketStatus.READY -> "Marcar Entregado"
                        TicketStatus.DELIVERED -> ""
                    }
                    Button(
                        onClick = { viewModel.advanceStatus() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                    ) {
                        Text(nextLabel)
                    }
                }

                // Payment actions
                if (ticket.paymentStatus != PaymentStatus.PAID) {
                    Text(
                        text = "Marcar como pagado:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.markAsPaid(PaymentMethod.CASH) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                        ) {
                            Text("Efectivo")
                        }
                        OutlinedButton(
                            onClick = { viewModel.markAsPaid(PaymentMethod.CARD) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                        ) {
                            Text("Tarjeta")
                        }
                        OutlinedButton(
                            onClick = { viewModel.markAsPaid(PaymentMethod.TRANSFER) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                        ) {
                            Text("Transferencia")
                        }
                    }
                }

                HorizontalDivider()

                // Charge and Print actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onCharge(ticket.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                        enabled = !state.isLoading,
                    ) {
                        Text("Cobrar (Zettle)")
                    }
                    Button(
                        onClick = { onPrint(ticket.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                        enabled = !state.isLoading,
                    ) {
                        Text("Imprimir")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun serviceTypeLabel(ticket: Ticket): String {
    return when (ticket.serviceType) {
        com.cleanx.lcx.core.model.ServiceType.IN_STORE -> "En tienda"
        com.cleanx.lcx.core.model.ServiceType.WASH_FOLD -> "Lavado y Doblado"
    }
}

private fun paymentStatusLabel(status: PaymentStatus?): String {
    return when (status) {
        PaymentStatus.PENDING -> "Pendiente"
        PaymentStatus.PREPAID -> "Prepagado"
        PaymentStatus.PAID -> "Pagado"
        null -> "Sin estado"
    }
}

private fun paymentMethodLabel(method: PaymentMethod): String {
    return when (method) {
        PaymentMethod.CASH -> "Efectivo"
        PaymentMethod.CARD -> "Tarjeta"
        PaymentMethod.TRANSFER -> "Transferencia"
    }
}
