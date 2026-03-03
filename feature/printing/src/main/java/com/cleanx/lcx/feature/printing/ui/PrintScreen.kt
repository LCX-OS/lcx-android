package com.cleanx.lcx.feature.printing.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LoadingOverlay
import com.cleanx.lcx.feature.printing.data.ConnectionType
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrinterInfo

@Composable
fun PrintScreen(
    onFinished: () -> Unit,
    initialLabelData: LabelData? = null,
    viewModel: PrintViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var pendingAutoDiscover by remember { mutableStateOf(false) }
    var pendingPrinterSelection by remember { mutableStateOf<PrinterInfo?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val printer = pendingPrinterSelection
            when {
                printer != null -> viewModel.selectPrinter(printer)
                pendingAutoDiscover -> viewModel.autoConnectOrDiscover()
            }
        } else {
            viewModel.onBluetoothPermissionDenied()
        }
        pendingAutoDiscover = false
        pendingPrinterSelection = null
    }

    // Ensure label payload is available before connecting/printing.
    LaunchedEffect(initialLabelData) {
        initialLabelData?.let(viewModel::setLabelData)
    }

    // Navigate away once the flow is complete (skip or success acknowledged).
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    // Auto-start: try auto-connect to saved printer first, fall back to discovery.
    LaunchedEffect(Unit) {
        if (state.phase == PrintPhase.IDLE && !state.finished) {
            if (needsBluetoothPermission() && !hasBluetoothConnectPermission(context)) {
                pendingAutoDiscover = true
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                viewModel.autoConnectOrDiscover()
            }
        }
    }

    // Haptic feedback on successful print
    LaunchedEffect(state.phase) {
        if (state.phase == PrintPhase.SUCCESS) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LcxSpacing.md),
        ) {
            Text(
                text = "Imprimir etiqueta",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(LcxSpacing.md))

            when (state.phase) {
                PrintPhase.IDLE -> { /* waiting */ }
                PrintPhase.DISCOVERING -> { /* Loading overlay shows on top */ }

                PrintPhase.SELECTING -> SelectingContent(
                    printers = state.printers,
                    onSelect = { printer ->
                        val requiresPermission =
                            printer.connectionType == ConnectionType.BLUETOOTH &&
                                needsBluetoothPermission() &&
                                !hasBluetoothConnectPermission(context)
                        if (requiresPermission) {
                            pendingPrinterSelection = printer
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            viewModel.selectPrinter(printer)
                        }
                    },
                    onSkip = viewModel::skip,
                )

                PrintPhase.CONNECTING -> { /* Loading overlay shows on top */ }
                PrintPhase.PRINTING -> { /* Loading overlay shows on top */ }

                PrintPhase.SUCCESS -> SuccessContent(onDone = viewModel::skip)

                PrintPhase.ERROR -> ErrorContent(
                    message = mapPrintErrorToUserMessage(state.errorMessage ?: "Error desconocido"),
                    onRetry = viewModel::retry,
                    onSkip = viewModel::skip,
                )
            }
        }

        // Loading overlay for async phases
        when (state.phase) {
            PrintPhase.DISCOVERING -> LoadingOverlay(message = "Buscando impresoras...")
            PrintPhase.CONNECTING -> LoadingOverlay(message = "Conectando a impresora...")
            PrintPhase.PRINTING -> LoadingOverlay(message = "Imprimiendo etiqueta...")
            else -> {}
        }
    }
}

// -- Sub-screens --------------------------------------------------------------

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
        Spacer(modifier = Modifier.height(LcxSpacing.sm))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            items(printers, key = { it.address }) { printer ->
                PrinterCard(printer = printer, onClick = { onSelect(printer) })
            }
        }

        Spacer(modifier = Modifier.height(LcxSpacing.md))

        LcxButton(
            text = "Omitir impresion",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
        )
    }
}

@Composable
private fun PrinterCard(printer: PrinterInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    ) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
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
private fun SuccessContent(onDone: () -> Unit) {
    CenteredColumn {
        // Animated checkmark with scale-in effect
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn() + fadeIn(),
        ) {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.displayLarge,
                color = LcxSuccess,
            )
        }
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        Text(
            text = "Etiqueta impresa",
            style = MaterialTheme.typography.headlineSmall,
            color = LcxSuccess,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(LcxSpacing.lg))
        LcxButton(
            text = "Continuar",
            onClick = onDone,
        )
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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.lg))
        LcxButton(
            text = "Reintentar",
            onClick = onRetry,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        LcxButton(
            text = "Omitir",
            onClick = onSkip,
            variant = ButtonVariant.Secondary,
        )
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

/**
 * Maps raw print error messages to user-friendly Spanish text.
 */
private fun mapPrintErrorToUserMessage(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") || lower.contains("timed out") ->
            "La impresora no responde. Verifique que esta encendida e intente de nuevo."
        lower.contains("bluetooth") || lower.contains("bt ") ->
            "Error de conexion Bluetooth. Verifique que la impresora esta encendida y cerca."
        lower.contains("paper") || lower.contains("papel") ->
            "Verifique que la impresora tiene papel y esta lista."
        lower.contains("connect") || lower.contains("conexion") ->
            "No se pudo conectar a la impresora. Verifique la conexion."
        lower.contains("not found") || lower.contains("no se encontr") ->
            "No se encontraron impresoras. Verifique que la impresora esta encendida."
        else -> raw
    }
}

private fun needsBluetoothPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}
