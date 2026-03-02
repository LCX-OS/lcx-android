package com.cleanx.lcx.core.transaction.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog shown when an active (in-flight) transaction is found on app
 * startup. Gives the operator the option to resume or cancel.
 */
@Composable
fun PendingTransactionDialog(
    state: PendingTransactionState,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!state.visible) return

    AlertDialog(
        onDismissRequest = { /* Block dismiss by tapping outside */ },
        title = {
            Text(
                text = "Se encontro una transaccion pendiente",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                // Ticket info
                if (state.ticketInfo.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Text(
                            text = state.ticketInfo,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Phase label
                Text(
                    text = "Ultimo paso: ${state.phaseLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Critical warning
                if (state.isCritical) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = "ATENCION: Se registro un cobro con tarjeta que no se " +
                                "pudo sincronizar con el servidor. Se recomienda continuar " +
                                "para completar la sincronizacion.",
                            modifier = Modifier
                                .padding(12.dp)
                                .semantics { liveRegion = LiveRegionMode.Assertive },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onResume,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = if (state.isCritical) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text("Continuar")
            }
        },
        dismissButton = {
            if (!state.isCritical) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Cancelar")
                }
            }
        },
    )
}
