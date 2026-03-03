package com.cleanx.lcx.ui.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ── Reusable shell composable ──────────────────────────────────────────

/**
 * Consistent shell screen for operator routes that are not yet implemented.
 * Displays a [TopAppBar] with back navigation, a centered icon, the screen
 * title, a description, and a "Proximamente" badge so operators know the
 * feature is coming soon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorShellScreen(
    title: String,
    description: String,
    icon: ImageVector,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "Proximamente",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ── Sales ───────────────────────────────────────────────────────────────

@Composable
fun SalesShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Ventas",
        description = "Registro y consulta de ventas",
        icon = Icons.Filled.ShoppingCart,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Incidents ───────────────────────────────────────────────────────────

@Composable
fun IncidentsNewShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Nueva Incidencia",
        description = "Reportar una incidencia",
        icon = Icons.Filled.ReportProblem,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun IncidentsHistoryShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Historial de Incidencias",
        description = "Consultar incidencias anteriores",
        icon = Icons.Filled.History,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Shifts ──────────────────────────────────────────────────────────────

@Composable
fun ShiftsControlShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Control de Turnos",
        description = "Registrar entrada y salida",
        icon = Icons.Filled.AccessTime,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ShiftsHistoryShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Historial de Turnos",
        description = "Ver turnos anteriores",
        icon = Icons.Filled.History,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ShiftsScheduleShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Horarios",
        description = "Consultar horarios programados",
        icon = Icons.Filled.CalendarMonth,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ShiftsReportsShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Reportes de Turnos",
        description = "Reportes y estadisticas de turnos",
        icon = Icons.Filled.Assessment,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Damaged Clothing ────────────────────────────────────────────────────

@Composable
fun DamagedClothingNewShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Ropa Danada - Nuevo",
        description = "Reportar prenda danada",
        icon = Icons.Filled.Warning,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun DamagedClothingHistoryShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Ropa Danada - Historial",
        description = "Consultar reportes de ropa danada",
        icon = Icons.Filled.History,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Supplies ────────────────────────────────────────────────────────────

@Composable
fun SuppliesInventoryShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Inventario de Insumos",
        description = "Consultar inventario actual",
        icon = Icons.Filled.Inventory2,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SuppliesLabelsShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Etiquetas",
        description = "Imprimir etiquetas de insumos",
        icon = Icons.Filled.Label,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SuppliesReportsShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Reportes de Insumos",
        description = "Reportes de consumo de insumos",
        icon = Icons.Filled.Assessment,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SuppliesBrotherDebugShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Debug Brother",
        description = "Diagnostico de impresora Brother",
        icon = Icons.Filled.BugReport,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Vacations ───────────────────────────────────────────────────────────

@Composable
fun VacationsShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Vacaciones",
        description = "Solicitar y consultar vacaciones",
        icon = Icons.Filled.BeachAccess,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Calendar ────────────────────────────────────────────────────────────

@Composable
fun CalendarMonthlyShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Calendario Mensual",
        description = "Vista mensual del calendario",
        icon = Icons.Filled.CalendarMonth,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun CalendarEventsShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Eventos",
        description = "Eventos y recordatorios",
        icon = Icons.Filled.Event,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── Info / Help ─────────────────────────────────────────────────────────

@Composable
fun BestPracticesShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Mejores Practicas",
        description = "Guias y mejores practicas",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun HelpShell(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OperatorShellScreen(
        title = "Ayuda",
        description = "Centro de ayuda y soporte",
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        onBack = onBack,
        modifier = modifier,
    )
}
