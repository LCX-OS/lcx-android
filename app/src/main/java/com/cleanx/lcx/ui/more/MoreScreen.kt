package com.cleanx.lcx.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.navigation.RouteAccess
import com.cleanx.lcx.core.navigation.Screen

/**
 * Data class representing a single navigable item within the More hub.
 */
internal data class MoreItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
    val isAvailable: Boolean = false,
)

/**
 * Data class representing a section (group) of related items in the More hub.
 */
internal data class MoreSection(
    val title: String,
    val items: List<MoreItem>,
)

/**
 * Builds the ordered list of sections displayed in the More hub.
 */
internal fun buildSections(
    includePaymentDiagnostics: Boolean = BuildConfig.DEBUG,
): List<MoreSection> = buildList {
    add(
        MoreSection(
            title = "Ventas",
            items = listOf(
                MoreItem("Ventas", Icons.Filled.PointOfSale, Screen.SalesGraph, isAvailable = true),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Incidencias",
            items = listOf(
                MoreItem("Nueva Incidencia", Icons.Filled.ReportProblem, Screen.IncidentsNew),
                MoreItem("Historial de Incidencias", Icons.Filled.History, Screen.IncidentsHistory),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Turnos",
            items = listOf(
                MoreItem("Control de Turnos", Icons.Filled.SwapHoriz, Screen.ShiftsControl),
                MoreItem("Historial", Icons.Filled.History, Screen.ShiftsHistory),
                MoreItem("Horarios", Icons.Filled.Schedule, Screen.ShiftsSchedule),
                MoreItem("Reportes", Icons.Filled.Summarize, Screen.ShiftsReports),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Ropa Dañada",
            items = listOf(
                MoreItem("Nuevo Reporte", Icons.AutoMirrored.Filled.NoteAdd, Screen.DamagedClothingNew),
                MoreItem("Historial", Icons.Filled.History, Screen.DamagedClothingHistory),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Insumos",
            items = buildList {
                add(MoreItem("Inventario", Icons.Filled.Inventory, Screen.SuppliesInventory))
                add(MoreItem("Etiquetas", Icons.AutoMirrored.Filled.Label, Screen.SuppliesLabels))
                add(MoreItem("Reportes", Icons.Filled.Summarize, Screen.SuppliesReports))
                if (includePaymentDiagnostics) {
                    add(MoreItem("Debug Brother", Icons.Filled.BugReport, Screen.SuppliesBrotherDebug))
                }
            },
        ),
    )
    add(
        MoreSection(
            title = "Vacaciones",
            items = listOf(
                MoreItem("Vacaciones", Icons.Filled.WbSunny, Screen.Vacations),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Calendario",
            items = listOf(
                MoreItem("Vista Mensual", Icons.Filled.CalendarMonth, Screen.CalendarMonthly),
                MoreItem("Eventos", Icons.AutoMirrored.Filled.EventNote, Screen.CalendarEvents),
            ),
        ),
    )
    add(
        MoreSection(
            title = "Información",
            items = listOf(
                MoreItem("Mejores Prácticas", Icons.Filled.CheckCircle, Screen.BestPractices),
                MoreItem("Ayuda", Icons.AutoMirrored.Filled.Help, Screen.Help),
            ),
        ),
    )
    if (includePaymentDiagnostics) {
        add(
            MoreSection(
                title = "Debug",
                items = listOf(
                    MoreItem(
                        "Diagnosticos de pagos",
                        Icons.Filled.BugReport,
                        Screen.PaymentDiagnostics,
                        isAvailable = true,
                    ),
                ),
            ),
        )
    }
}

/**
 * The More hub screen. Displays a scrollable list of operator module accesses
 * grouped by section. Tapping an item triggers navigation to the corresponding
 * [Screen] route via [onNavigate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    userRole: UserRole,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Filter sections: only show items the user's role can access.
    val sections = remember(userRole) {
        buildSections()
            .map { section ->
                section.copy(
                    items = section.items.filter { RouteAccess.canAccess(userRole, it.screen) },
                )
            }
            .filter { it.items.isNotEmpty() }
    }
    val availableSections = sections.mapNotNull { section ->
        section.copy(items = section.items.filter { it.isAvailable })
            .takeIf { it.items.isNotEmpty() }
    }
    val upcomingSections = sections.mapNotNull { section ->
        section.copy(items = section.items.filterNot { it.isAvailable })
            .takeIf { it.items.isNotEmpty() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LcxTopAppBar(
                title = { Text("Más") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
        ) {
            renderSectionGroup(
                title = "Disponible",
                sections = availableSections,
                onNavigate = onNavigate,
            )
            renderSectionGroup(
                title = "Proximamente",
                sections = upcomingSections,
                onNavigate = onNavigate,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderSectionGroup(
    title: String,
    sections: List<MoreSection>,
    onNavigate: (Screen) -> Unit,
) {
    if (sections.isEmpty()) return

    item(key = "group-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 4.dp,
            ),
        )
    }

    sections.forEach { section ->
        item(key = "header-$title-${section.title}") {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 2.dp,
                ),
            )
        }

        items(
            items = section.items,
            key = { "$title-${section.title}-${it.label}" },
        ) { item ->
            ListItem(
                headlineContent = { Text(item.label) },
                supportingContent = if (item.isAvailable) {
                    null
                } else {
                    { Text("Modulo en preparacion") }
                },
                leadingContent = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                modifier = Modifier.clickable { onNavigate(item.screen) },
            )
        }

        item(key = "divider-$title-${section.title}") {
            HorizontalDivider()
        }
    }
}
