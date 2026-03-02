package com.cleanx.lcx.feature.payments.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Debug-only diagnostics screen for the payment subsystem.
 *
 * Shows the current payment mode (stub vs. real), the last transaction
 * result, and provides buttons to trigger a test payment or switch
 * the simulated scenario at runtime.
 *
 * This screen should only be reachable in the `dev` flavor / debug builds.
 */
@Composable
fun PaymentDiagnosticsScreen(
    onBack: () -> Unit = {},
    viewModel: PaymentDiagnosticsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Diagnosticos de pagos",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(16.dp))

        // -- Mode card --
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Modo actual",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.useRealZettle) "SDK Real (Zettle)" else "Stub (simulado)",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (state.useRealZettle)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Escenario: ${state.currentScenario}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Inicializado: ${if (state.isInitialized) "Si" else "No"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- Scenario buttons (only meaningful in stub mode) --
        if (!state.useRealZettle) {
            Text(
                text = "Escenario simulado",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.setScenario("Random") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Aleatorio")
                }
                OutlinedButton(
                    onClick = { viewModel.setScenario("AlwaysSuccess") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Exito")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.setScenario("AlwaysCancelled") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancelado")
                }
                OutlinedButton(
                    onClick = { viewModel.setScenario("AlwaysFailed") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Error")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // -- Test payment --
        Text(
            text = "Pago de prueba",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.triggerTestPayment() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isProcessing,
        ) {
            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Cobrar $1.00 de prueba")
        }

        // -- Last result --
        if (state.lastResult != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.lastResult.startsWith("Exito") -> MaterialTheme.colorScheme.secondaryContainer
                        state.lastResult.startsWith("Cancelado") -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ultimo resultado",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.lastResult,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }
}
