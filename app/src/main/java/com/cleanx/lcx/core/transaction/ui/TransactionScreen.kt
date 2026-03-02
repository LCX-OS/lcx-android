package com.cleanx.lcx.core.transaction.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.core.transaction.TransactionState

@Composable
fun TransactionScreen(
    onCompleted: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Proceso de ticket",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(16.dp))

        // Step indicator
        StepIndicator(currentStep = state.currentStep, totalSteps = state.totalSteps)

        Spacer(Modifier.height(24.dp))

        // Current step label
        if (state.stepLabel.isNotBlank()) {
            Text(
                text = state.stepLabel,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Processing spinner
        if (state.isProcessing) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Ticket info (once created)
        state.ticket?.let { ticket ->
            TicketSummaryCard(
                ticketNumber = ticket.ticketNumber,
                dailyFolio = ticket.dailyFolio,
                customerName = ticket.customerName,
                amount = ticket.totalAmount,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Critical warning (payment succeeded but API failed)
        if (state.isCritical) {
            CriticalWarningCard(
                transactionId = state.transactionId,
                errorMessage = state.errorMessage,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Error message (non-critical)
        if (!state.isCritical && state.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = state.errorMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Completed state
        if (state.transactionState is TransactionState.Completed) {
            CompletedContent(ticket = state.ticket)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCompleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Volver a tickets", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Action buttons
        if (state.canRetry || state.canSkip || state.canCancel) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (state.canRetry) {
            Button(
                onClick = { viewModel.retry() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = if (state.isCritical) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    text = if (state.isCritical) "Reintentar sincronizacion" else "Reintentar",
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.canSkip) {
            OutlinedButton(
                onClick = { viewModel.skipPrint() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Omitir impresion")
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.canCancel && state.transactionState !is TransactionState.Completed) {
            OutlinedButton(
                onClick = {
                    viewModel.cancel()
                    onCancelled()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.isCritical) {
                        "Volver (el cargo YA fue realizado)"
                    } else {
                        "Cancelar"
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// -- Sub-components -----------------------------------------------------------

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    val labels = listOf("Crear", "Cobrar", "Imprimir", "Listo")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val stepNum = index + 1
            val isActive = stepNum == currentStep
            val isCompleted = stepNum < currentStep
            val color = when {
                isCompleted -> MaterialTheme.colorScheme.secondary
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCompleted) "\u2713" else "$stepNum",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive || isCompleted) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (index < labels.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.width(16.dp),
                    color = if (stepNum < currentStep) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun TicketSummaryCard(
    ticketNumber: String,
    dailyFolio: Int,
    customerName: String,
    amount: Double?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = ticketNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Folio #$dailyFolio",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = customerName,
                style = MaterialTheme.typography.bodyLarge,
            )
            amount?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.2f", it)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CriticalWarningCard(
    transactionId: String?,
    errorMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ATENCION: Cobro realizado",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "El cobro con tarjeta se realizo exitosamente, " +
                    "pero no se pudo registrar en el sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (transactionId != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "ID transaccion: $transactionId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Error: $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Presione Reintentar para sincronizar con el servidor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CompletedContent(ticket: com.cleanx.lcx.core.model.Ticket?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "\u2713",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ticket completado",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        ticket?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${it.ticketNumber} - Folio #${it.dailyFolio}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
