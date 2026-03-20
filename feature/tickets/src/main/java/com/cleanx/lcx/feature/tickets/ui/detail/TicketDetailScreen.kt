package com.cleanx.lcx.feature.tickets.ui.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.core.ui.PaymentStatusChip
import com.cleanx.lcx.core.ui.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    viewModel: TicketDetailViewModel,
    showQuickActionsOnLaunch: Boolean = false,
    onBack: () -> Unit,
    onCharge: (String) -> Unit,
    onPrint: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ticket = state.ticket
    val context = LocalContext.current
    var showQuickActionsCard by rememberSaveable(ticket?.id, showQuickActionsOnLaunch) {
        mutableStateOf(false)
    }
    var quickPaymentMethod by remember(ticket?.id) {
        mutableStateOf(ticket?.paymentMethod ?: PaymentMethod.CARD)
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    LaunchedEffect(ticket?.id, showQuickActionsOnLaunch) {
        if (ticket != null && showQuickActionsOnLaunch) {
            quickPaymentMethod = ticket.paymentMethod ?: PaymentMethod.CARD
            showQuickActionsCard = true
        }
    }

    Scaffold(
        topBar = {
            LcxTopAppBar(
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
                )
            }
        },
    ) { padding ->
        when {
            ticket == null && state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            ticket == null -> {
                ErrorState(
                    message = state.error ?: "No se encontro el ticket.",
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.loadTicket(force = true) },
                )
            }

            else -> {
                val addOns = ticket.addOns
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = LcxSpacing.md)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                ) {
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))

                    AnimatedVisibility(
                        visible = showQuickActionsCard,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        QuickActionsLaunchCard(
                            ticket = ticket,
                            selectedPaymentMethod = quickPaymentMethod,
                            isLoading = state.isLoading,
                            onPaymentMethodChanged = { quickPaymentMethod = it },
                            onMarkPaid = {
                                showQuickActionsCard = false
                                viewModel.markAsPaid(quickPaymentMethod)
                            },
                            onCharge = {
                                showQuickActionsCard = false
                                onCharge(ticket.id)
                            },
                            onPrint = {
                                showQuickActionsCard = false
                                onPrint(ticket.id)
                            },
                            onDismiss = { showQuickActionsCard = false },
                        )
                    }

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

                    if (
                        ticket.promisedPickupDate != null ||
                        ticket.actualPickupDate != null ||
                        ticket.specialInstructions != null ||
                        !addOns.isNullOrEmpty()
                    ) {
                        LcxCard(title = "Entrega y extras") {
                            ticket.promisedPickupDate?.let { DetailRow("Promesa", it) }
                            ticket.actualPickupDate?.let { DetailRow("Retiro real", it) }
                            ticket.specialInstructions?.let { DetailRow("Indicaciones", it) }
                            if (!addOns.isNullOrEmpty()) {
                                DetailRow("Add-ons", addOns.joinToString(", "))
                            }
                        }
                    }

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
                        ticket.subtotal?.let { DetailRow("Subtotal", "$${String.format("%.2f", it)}") }
                        ticket.addOnsTotal?.let { DetailRow("Add-ons", "$${String.format("%.2f", it)}") }
                        ticket.totalAmount?.let { DetailRow("Total", "$${String.format("%.2f", it)}") }
                        DetailRow("Pagado", "$${String.format("%.2f", ticket.paidAmount)}")
                        ticket.prepaidAmount?.let { DetailRow("Anticipo", "$${String.format("%.2f", it)}") }
                        ticket.paidAt?.let { DetailRow("Fecha pago", it) }
                    }

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
                                    onClick = { viewModel.loadTicket(force = true) },
                                    variant = ButtonVariant.Secondary,
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    if (ticket.status != TicketStatus.DELIVERED && ticket.status != TicketStatus.PAID) {
                        val nextLabel = when (ticket.status) {
                            TicketStatus.RECEIVED -> "Marcar En Proceso"
                            TicketStatus.PROCESSING -> "Marcar Listo"
                            TicketStatus.READY -> "Marcar Entregado"
                            TicketStatus.DELIVERED -> ""
                            TicketStatus.PAID -> ""
                        }
                        LcxButton(
                            text = nextLabel,
                            onClick = { viewModel.advanceStatus() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            isLoading = state.isLoading,
                        )
                    }

                    if (ticket.status == TicketStatus.READY) {
                        LcxButton(
                            text = "Enviar recordatorio SMS",
                            onClick = { viewModel.sendPickupReminder() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            isLoading = state.isLoading,
                            variant = ButtonVariant.Secondary,
                        )
                    }

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsLaunchCard(
    ticket: Ticket,
    selectedPaymentMethod: PaymentMethod,
    isLoading: Boolean,
    onPaymentMethodChanged: (PaymentMethod) -> Unit,
    onMarkPaid: () -> Unit,
    onCharge: () -> Unit,
    onPrint: () -> Unit,
    onDismiss: () -> Unit,
) {
    LcxCard(title = "Acciones rápidas") {
        Text(
            text = "Ticket ${ticket.ticketNumber}. Puedes cobrar, registrar pago o imprimir sin salir de esta pantalla.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (ticket.paymentStatus != PaymentStatus.PAID) {
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            PaymentMethodDropdown(
                selected = selectedPaymentMethod,
                onSelected = onPaymentMethodChanged,
                enabled = !isLoading,
            )
        }

        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        LcxButton(
            text = "Imprimir etiquetas",
            onClick = onPrint,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            variant = ButtonVariant.Secondary,
        )

        if (ticket.paymentStatus != PaymentStatus.PAID) {
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            LcxButton(
                text = "Marcar pagado",
                onClick = onMarkPaid,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            LcxButton(
                text = "Cobrar (Zettle)",
                onClick = onCharge,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                variant = ButtonVariant.Secondary,
            )
        }

        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        LcxButton(
            text = "Cerrar",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            variant = ButtonVariant.Secondary,
        )
    }
}

@Composable
private fun QuickActionBar(
    ticket: Ticket,
    isLoading: Boolean,
    onAdvanceStatus: () -> Unit,
    onCharge: () -> Unit,
) {
    val canAdvance = ticket.status != TicketStatus.DELIVERED && ticket.status != TicketStatus.PAID
    val canCharge = ticket.paymentStatus != PaymentStatus.PAID
    val showBar = canAdvance || canCharge

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
                    TicketStatus.PAID -> ""
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodDropdown(
    selected: PaymentMethod,
    onSelected: (PaymentMethod) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        PaymentMethod.CARD to "Tarjeta",
        PaymentMethod.CASH to "Efectivo",
        PaymentMethod.TRANSFER to "Transferencia",
    )
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Tarjeta"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        LcxTextField(
            value = selectedLabel,
            onValueChange = {},
            label = "Método de pago",
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun serviceTypeLabel(ticket: Ticket): String {
    return when (ticket.serviceType) {
        ServiceType.IN_STORE -> "En tienda"
        ServiceType.WASH_FOLD -> "Lavado y Doblado"
    }
}

private fun paymentMethodLabel(method: PaymentMethod): String {
    return when (method) {
        PaymentMethod.CASH -> "Efectivo"
        PaymentMethod.CARD -> "Tarjeta"
        PaymentMethod.TRANSFER -> "Transferencia"
    }
}
