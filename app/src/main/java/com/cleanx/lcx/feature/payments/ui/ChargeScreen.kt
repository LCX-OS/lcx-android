package com.cleanx.lcx.feature.payments.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChargeScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPrint: (String) -> Unit = {},
    viewModel: ChargeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.phase) {
            is ChargePhase.Idle -> IdleContent(
                state = state,
                onCharge = viewModel::startCharge,
                onCancel = onNavigateBack,
            )

            is ChargePhase.Processing -> ProcessingContent(amount = state.amount)

            is ChargePhase.Success -> SuccessContent(
                amount = state.amount,
                transactionId = state.transactionId,
                onContinue = { onNavigateToPrint(state.ticketId) },
                onBack = onNavigateBack,
            )

            is ChargePhase.Cancelled -> CancelledContent(
                onRetry = viewModel::retry,
                onBack = onNavigateBack,
            )

            is ChargePhase.Failed -> FailedContent(
                message = state.errorMessage,
                onRetry = viewModel::retry,
                onBack = onNavigateBack,
            )

            is ChargePhase.PaymentSucceededApiCallFailed -> ApiFailedContent(
                amount = state.amount,
                transactionId = state.transactionId,
                message = state.errorMessage,
                onRetry = viewModel::retry,
                onBack = onNavigateBack,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-screens
// ---------------------------------------------------------------------------

@Composable
private fun IdleContent(
    state: ChargeUiState,
    onCharge: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "Cobro con tarjeta",
        style = MaterialTheme.typography.headlineSmall,
    )

    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.customerName.isNotBlank()) {
                Text(
                    text = "Cliente: ${state.customerName}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = "Ticket: ${state.ticketId.take(8)}...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text = "$${String.format("%.2f", state.amount)}",
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onCharge,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = "Cobrar", style = MaterialTheme.typography.titleMedium)
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Cancelar")
    }
}

@Composable
private fun ProcessingContent(amount: Double) {
    CircularProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Procesando cobro...",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "$${String.format("%.2f", amount)}",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Siga las instrucciones del lector de tarjetas.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SuccessContent(
    amount: Double,
    transactionId: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "\u2713", // checkmark
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Cobro exitoso",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "$${String.format("%.2f", amount)}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (transactionId != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Transaccion: ${transactionId.take(8)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = "Imprimir etiqueta")
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Volver")
    }
}

@Composable
private fun CancelledContent(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Cobro cancelado",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "No se realizo ningun cargo a la tarjeta.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = "Reintentar")
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Volver")
    }
}

@Composable
private fun FailedContent(
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Error en el cobro",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message ?: "Ocurrio un error desconocido.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "No se realizo ningun cargo a la tarjeta.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = "Reintentar")
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Volver")
    }
}

@Composable
private fun ApiFailedContent(
    amount: Double,
    transactionId: String?,
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Atencion",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "El cobro de $${String.format("%.2f", amount)} se realizo exitosamente, " +
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
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
    }

    Text(
        text = "Presione Reintentar para sincronizar con el servidor.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(text = "Reintentar sincronizacion")
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Volver (el cargo YA fue realizado)")
    }
}
