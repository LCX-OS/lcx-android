package com.cleanx.lcx.feature.printing.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.feature.printing.data.PrinterInfo

/**
 * Printer settings screen — all UI text in Spanish.
 *
 * Allows the operator to:
 * - View the saved printer
 * - Discover and select a new printer
 * - Set print copies (1-3)
 * - Toggle auto-connect
 * - Run a test print
 * - Forget the saved printer
 */
@Composable
fun PrinterSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: PrinterSettingsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // -- Header -----------------------------------------------------------
        item {
            Text(
                text = "Configuracion de impresora",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        // -- Saved Printer Section --------------------------------------------
        item {
            SectionTitle("Impresora guardada")
        }

        item {
            if (state.savedPrinter != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.savedPrinter.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${state.savedPrinter.connectionType.name} - ${state.savedPrinter.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                Text(
                    text = "Ninguna impresora guardada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // -- Discovery Section ------------------------------------------------
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Buscar impresoras")
        }

        item {
            Button(
                onClick = viewModel::discoverPrinters,
                enabled = !state.isDiscovering && !state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Buscar impresoras")
            }
        }

        if (state.discoveredPrinters.isNotEmpty()) {
            items(state.discoveredPrinters, key = { it.address }) { printer ->
                DiscoveredPrinterCard(
                    printer = printer,
                    isConnecting = state.isConnecting,
                    onClick = { viewModel.selectPrinter(printer) },
                )
            }
        }

        // -- Settings Section -------------------------------------------------
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Ajustes")
        }

        // Print copies
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Copias por impresion",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(1, 2, 3).forEach { count ->
                        val isSelected = state.printCopies == count
                        if (isSelected) {
                            Button(
                                onClick = { },
                                modifier = Modifier.size(48.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.setPrintCopies(count) },
                                modifier = Modifier.size(48.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }
            }
        }

        // Auto-connect toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Conexion automatica",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Conectar a la ultima impresora al iniciar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoConnect,
                    onCheckedChange = viewModel::setAutoConnect,
                )
            }
        }

        // -- Actions Section --------------------------------------------------
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Acciones")
        }

        // Test print
        item {
            OutlinedButton(
                onClick = viewModel::testPrint,
                enabled = !state.isPrinting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Imprimir prueba")
            }
        }

        // Forget printer
        if (state.savedPrinter != null) {
            item {
                OutlinedButton(
                    onClick = viewModel::forgetPrinter,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Olvidar impresora")
                }
            }
        }

        // -- Status message ---------------------------------------------------
        if (state.message != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DiscoveredPrinterCard(
    printer: PrinterInfo,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !isConnecting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = printer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${printer.connectionType.name} - ${printer.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
