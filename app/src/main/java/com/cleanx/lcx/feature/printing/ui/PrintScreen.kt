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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.feature.printing.data.PrinterInfo

@Composable
fun PrintScreen(
    onFinished: () -> Unit,
    viewModel: PrintViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    // Navigate away once the flow is complete (skip or success acknowledged).
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    // Auto-start discovery on first composition.
    LaunchedEffect(Unit) {
        if (state.phase == PrintPhase.IDLE && !state.finished) {
            viewModel.discoverPrinters()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Imprimir etiqueta",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (state.phase) {
            PrintPhase.IDLE -> { /* waiting */ }

            PrintPhase.DISCOVERING -> DiscoveringContent()

            PrintPhase.SELECTING -> SelectingContent(
                printers = state.printers,
                onSelect = viewModel::selectPrinter,
                onSkip = viewModel::skip,
            )

            PrintPhase.CONNECTING -> ProgressContent(message = "Conectando a impresora...")

            PrintPhase.PRINTING -> ProgressContent(message = "Imprimiendo etiqueta...")

            PrintPhase.SUCCESS -> SuccessContent(onDone = viewModel::skip)

            PrintPhase.ERROR -> ErrorContent(
                message = state.errorMessage ?: "Error desconocido",
                onRetry = viewModel::retry,
                onSkip = viewModel::skip,
            )
        }
    }
}

// -- Sub-screens --------------------------------------------------------------

@Composable
private fun DiscoveringContent() {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Buscando impresoras...",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SelectingContent(
    printers: List<PrinterInfo>,
    onSelect: (PrinterInfo) -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Seleccionar impresora",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(printers, key = { it.address }) { printer ->
                PrinterCard(printer = printer, onClick = { onSelect(printer) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Omitir impresion")
        }
    }
}

@Composable
private fun PrinterCard(printer: PrinterInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
    }
}

@Composable
private fun ProgressContent(message: String) {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SuccessContent(onDone: () -> Unit) {
    CenteredColumn {
        Text(
            text = "\u2713",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Etiqueta impresa",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone) {
            Text("Continuar")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    CenteredColumn {
        Text(
            text = "Error de impresion",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSkip) {
                Text("Omitir")
            }
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}
