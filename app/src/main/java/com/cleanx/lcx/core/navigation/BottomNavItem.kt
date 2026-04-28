package com.cleanx.lcx.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines the five bottom-navigation tabs shown after login.
 *
 * Each item carries:
 *  - a user-visible [label] (Spanish, matching the PWA)
 *  - an [icon] (Material 3 filled variant)
 *  - a [graphRoute] used as the nested-graph's route key for
 *    [NavController.navigate] with `saveState / restoreState`
 */
enum class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val graphRoute: Screen,
) {
    INICIO(
        label = "Inicio",
        icon = Icons.Filled.Home,
        graphRoute = Screen.DashboardGraph,
    ),
    VENTAS(
        label = "Ventas",
        icon = Icons.Filled.PointOfSale,
        graphRoute = Screen.SalesGraph,
    ),
    ENCARGOS(
        label = "Encargos",
        icon = Icons.Filled.ConfirmationNumber,
        graphRoute = Screen.TicketsGraph,
    ),
    CHECKLIST(
        label = "Checklist",
        icon = Icons.Filled.Checklist,
        graphRoute = Screen.ChecklistGraph,
    ),
    CAJA(
        label = "Caja",
        icon = Icons.Filled.AttachMoney,
        graphRoute = Screen.CashGraph,
    ),
}
