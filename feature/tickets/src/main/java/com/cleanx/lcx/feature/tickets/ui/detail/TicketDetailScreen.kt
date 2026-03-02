package com.cleanx.lcx.feature.tickets.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.PaymentStatusChip
import com.cleanx.lcx.core.ui.StatusChip

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
                title = {
                    Text(
                        ticket?.ticketNumber ?: "Detalle",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            if (ticket != null) {
                QuickActionBar(
                    ticket = ticket,
                    isLoading = state.isLoading,
                    onAdvanceStatus = { viewModel.advanceStatus() },
                    onCharge = { onCharge(ticket.id) },
                    onPrint = { onPrint(ticket.id) },
                )
            }
        },
    ) { padding ->
        if (ticket == null) {
            ErrorState(
                message = "No se encontro el ticket.",
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = LcxSpacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))

                // Ticket info card
                LcxCard {
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
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    DetailRow("Cliente", ticket.customerName)
                    ticket.customerPhone?.let { DetailRow("Telefono", it) }
                    DetailRow("Tipo", serviceTypeLabel(ticket))
                    ticket.service?.let { DetailRow("Servicio", it) }
                    ticket.weight?.let { DetailRow("Peso", "${it} kg") }
                    ticket.notes?.let { DetailRow("Notas", it) }
                    DetailRow("Fecha", ticket.ticketDate)
                    DetailRow("Folio", "#${ticket.dailyFolio}")
                }

                // Payment card
                LcxCard(title = "Pago") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Estado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ticket.paymentStatus?.let { PaymentStatusChip(status = it) }
                            ?: Text(
                                text = "Sin estado",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                    }
                    ticket.paymentMethod?.let { DetailRow("Metodo", paymentMethodLabel(it)) }
                    ticket.totalAmount?.let {
                        DetailRow("Total", "$${String.format("%.2f", it)}")
                    }
                    DetailRow("Pagado", "$${String.format("%.2f", ticket.paidAmount)}")
                    ticket.paidAt?.let { DetailRow("Fecha pago", it) }
                }

                // Error message with retry
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    state.error?.let { error ->
                        LcxCard {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                            Spacer(modifier = Modifier.height(LcxSpacing.sm))
                            LcxButton(
                                text = "Reintentar",
                                onClick = { viewModel.clearError() },
                                variant = ButtonVariant.Secondary,
                            )
                        }
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
                    LcxButton(
                        text = nextLabel,
                        onClick = { viewModel.advanceStatus() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        isLoading = state.isLoading,
                    )
                }

                // Payment actions
                if (ticket.paymentStatus != PaymentStatus.PAID) {
                    Text(
                        text = "Marcar como pagado:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                    ) {
                        LcxButton(
                            text = "Efectivo",
                            onClick = { viewModel.markAsPaid(PaymentMethod.CASH) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                            variant = ButtonVariant.Secondary,
                        )
                        LcxButton(
                            text = "Tarjeta",
                            onClick = { viewModel.markAsPaid(PaymentMethod.CARD) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                            variant = ButtonVariant.Secondary,
                        )
                        LcxButton(
                            text = "Transferencia",
                            onClick = { viewModel.markAsPaid(PaymentMethod.TRANSFER) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading,
                            variant = ButtonVariant.Secondary,
                        )
                    }
                }

                HorizontalDivider()

                // Charge and Print actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                ) {
                    LcxButton(
                        text = "Cobrar (Zettle)",
                        onClick = { onCharge(ticket.id) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading,
                        variant = ButtonVariant.Secondary,
                    )
                    LcxButton(
                        text = "Imprimir",
                        onClick = { onPrint(ticket.id) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading,
                        variant = ButtonVariant.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(LcxSpacing.lg))
            }
        }
    }
}

/**
 * Quick action bar pinned to the bottom of TicketDetailScreen.
 * Shows contextual actions based on ticket state — the most important
 * operations are always one tap away without scrolling.
 */
@Composable
private fun QuickActionBar(
    ticket: Ticket,
    isLoading: Boolean,
    onAdvanceStatus: () -> Unit,
    onCharge: () -> Unit,
    onPrint: () -> Unit,
) {
    val canAdvance = ticket.status != TicketStatus.DELIVERED
    val canCharge = ticket.paymentStatus != PaymentStatus.PAID
    val showBar = canAdvance || canCharge

    // Only show bottom bar when there are meaningful quick actions
    if (!showBar) return

    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LcxSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            if (canAdvance) {
                val label = when (ticket.status) {
                    TicketStatus.RECEIVED -> "Avanzar estado"
                    TicketStatus.PROCESSING -> "Marcar Listo"
                    TicketStatus.READY -> "Entregar"
                    TicketStatus.DELIVERED -> ""
                }
                LcxButton(
                    text = label,
                    onClick = onAdvanceStatus,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    isLoading = isLoading,
                )
            }
            if (canCharge) {
                LcxButton(
                    text = "Cobrar",
                    onClick = onCharge,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    variant = if (canAdvance) ButtonVariant.Secondary else ButtonVariant.Primary,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LcxSpacing.xs),
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

private fun paymentMethodLabel(method: PaymentMethod): String {
    return when (method) {
        PaymentMethod.CASH -> "Efectivo"
        PaymentMethod.CARD -> "Tarjeta"
        PaymentMethod.TRANSFER -> "Transferencia"
    }
}
