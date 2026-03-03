package com.cleanx.lcx.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.navigation.BottomNavItem
import com.cleanx.lcx.core.navigation.Screen
import com.cleanx.lcx.feature.payments.ui.ChargeScreen
import com.cleanx.lcx.feature.payments.ui.PaymentDiagnosticsScreen
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.ui.PrintScreen
import com.cleanx.lcx.feature.printing.ui.PrintViewModel
import com.cleanx.lcx.feature.cash.ui.CashViewModel
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketScreen
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketViewModel
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailScreen
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketListScreen
import com.cleanx.lcx.feature.tickets.ui.list.TicketListViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketPresetScreen
import androidx.navigation.toRoute
import com.cleanx.lcx.core.transaction.ui.TransactionScreen
import com.cleanx.lcx.ui.placeholder.ChecklistScreen
import com.cleanx.lcx.ui.placeholder.DashboardScreen
import com.cleanx.lcx.ui.more.MoreScreen
import com.cleanx.lcx.ui.ops.*
import com.cleanx.lcx.ui.placeholder.WaterScreen
import com.cleanx.lcx.feature.cash.ui.CashScreen

/**
 * Post-login shell that owns the bottom navigation bar and the nested
 * per-tab [NavHost]. Each tab has its own navigation graph so that
 * back-stack state is preserved independently when switching tabs.
 *
 * @param onSignOut called when the user triggers sign-out from any screen;
 *                  the root-level NavHost handles clearing the back-stack.
 */
@Composable
fun MainScaffold(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Shared TicketListViewModel scoped to this composable's ViewModelStoreOwner
    // so it survives tab switches within the scaffold.
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val ticketListViewModel: TicketListViewModel = hiltViewModel(viewModelStoreOwner)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(item.graphRoute::class)
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabNavController.navigate(item.graphRoute) {
                                // Pop up to the start destination of the graph to avoid
                                // building up a large stack of destinations on the back
                                // stack when selecting the same tab repeatedly.
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = Screen.DashboardGraph,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Inicio (Dashboard) tab ──────────────────────────────
            navigation<Screen.DashboardGraph>(startDestination = Screen.Dashboard) {
                composable<Screen.Dashboard> {
                    DashboardScreen()
                }
            }

            // ── Tickets tab ─────────────────────────────────────────
            navigation<Screen.TicketsGraph>(startDestination = Screen.TicketList) {
                composable<Screen.TicketList> {
                    TicketListScreen(
                        viewModel = ticketListViewModel,
                        onCreateTicket = {
                            tabNavController.navigate(Screen.CreateTicket)
                        },
                        onTicketClick = { ticket ->
                            tabNavController.navigate(
                                Screen.TicketDetail(ticketId = ticket.id),
                            )
                        },
                        onSignOut = onSignOut,
                    )
                }

                composable<Screen.CreateTicket> {
                    val createViewModel: CreateTicketViewModel = hiltViewModel()
                    CreateTicketScreen(
                        viewModel = createViewModel,
                        onBack = { tabNavController.popBackStack() },
                        onTicketCreated = { ticket ->
                            ticketListViewModel.addCreatedTicket(ticket)
                        },
                        onNavigateBack = { tabNavController.popBackStack() },
                    )
                }

                composable<Screen.TicketDetail> {
                    val detailViewModel: TicketDetailViewModel = hiltViewModel()

                    val ticketId = detailViewModel.ticketId
                    val existingTicket = remember(ticketId) {
                        ticketListViewModel.uiState.value.tickets
                            .firstOrNull { it.id == ticketId }
                    }
                    if (existingTicket != null &&
                        detailViewModel.uiState.value.ticket == null
                    ) {
                        detailViewModel.setTicket(existingTicket)
                    }

                    TicketDetailScreen(
                        viewModel = detailViewModel,
                        onBack = {
                            val updated = detailViewModel.uiState.value.ticket
                            if (detailViewModel.uiState.value.ticketUpdated &&
                                updated != null
                            ) {
                                ticketListViewModel.updateTicketInList(updated)
                                detailViewModel.consumeUpdated()
                            }
                            tabNavController.popBackStack()
                        },
                        onCharge = { id ->
                            tabNavController.navigate(Screen.Charge(ticketId = id))
                        },
                        onPrint = { id ->
                            tabNavController.navigate(Screen.Print(ticketId = id))
                        },
                    )
                }

                composable<Screen.Charge> {
                    ChargeScreen(
                        onNavigateBack = { tabNavController.popBackStack() },
                        onNavigateToPrint = { ticketId ->
                            tabNavController.navigate(Screen.Print(ticketId = ticketId))
                        },
                    )
                }

                composable<Screen.Print> {
                    val printViewModel: PrintViewModel = hiltViewModel()
                    val ticketId = printViewModel.ticketId
                    val initialLabelData = remember(
                        ticketId,
                        ticketListViewModel.uiState.value.tickets,
                    ) {
                        ticketListViewModel.uiState.value.tickets
                            .firstOrNull { it.id == ticketId }
                            ?.toLabelData()
                    }
                    PrintScreen(
                        initialLabelData = initialLabelData,
                        viewModel = printViewModel,
                        onFinished = {
                            tabNavController.popBackStack()
                        },
                    )
                }

                composable<Screen.Transaction> {
                    TransactionScreen(
                        onCompleted = {
                            tabNavController.navigate(Screen.TicketList) {
                                popUpTo<Screen.TicketList> { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCancelled = {
                            tabNavController.popBackStack()
                        },
                    )
                }

                if (BuildConfig.DEBUG) {
                    composable<Screen.PaymentDiagnostics> {
                        PaymentDiagnosticsScreen(
                            onBack = { tabNavController.popBackStack() },
                        )
                    }
                }

                composable<Screen.TicketPreset> { backStackEntry ->
                    val preset = backStackEntry.toRoute<Screen.TicketPreset>().preset
                    TicketPresetScreen(
                        preset = preset,
                        viewModel = ticketListViewModel,
                        onTicketClick = { ticket ->
                            tabNavController.navigate(Screen.TicketDetail(ticketId = ticket.id))
                        },
                        onBack = { tabNavController.popBackStack() },
                    )
                }
            }

            // ── Agua (Water) tab ────────────────────────────────────
            navigation<Screen.WaterGraph>(startDestination = Screen.Water) {
                composable<Screen.Water> {
                    WaterScreen()
                }
            }

            // ── Checklist tab ───────────────────────────────────────
            navigation<Screen.ChecklistGraph>(startDestination = Screen.Checklist) {
                composable<Screen.Checklist> {
                    ChecklistScreen()
                }
            }

            // ── Mas (More) tab ──────────────────────────────────────
            navigation<Screen.MoreGraph>(startDestination = Screen.More) {
                composable<Screen.More> {
                    MoreScreen(
                        onNavigate = { screen ->
                            tabNavController.navigate(screen)
                        },
                    )
                }

                // ── Operator module routes (structured shells) ──────
                composable<Screen.Sales> {
                    SalesShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.IncidentsNew> {
                    IncidentsNewShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.IncidentsHistory> {
                    IncidentsHistoryShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.ShiftsControl> {
                    ShiftsControlShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.ShiftsHistory> {
                    ShiftsHistoryShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.ShiftsSchedule> {
                    ShiftsScheduleShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.ShiftsReports> {
                    ShiftsReportsShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.DamagedClothingNew> {
                    DamagedClothingNewShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.DamagedClothingHistory> {
                    DamagedClothingHistoryShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.SuppliesInventory> {
                    SuppliesInventoryShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.SuppliesLabels> {
                    SuppliesLabelsShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.SuppliesReports> {
                    SuppliesReportsShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.SuppliesBrotherDebug> {
                    SuppliesBrotherDebugShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.Vacations> {
                    VacationsShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.CalendarMonthly> {
                    CalendarMonthlyShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.CalendarEvents> {
                    CalendarEventsShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.BestPractices> {
                    BestPracticesShell(onBack = { tabNavController.popBackStack() })
                }
                composable<Screen.Help> {
                    HelpShell(onBack = { tabNavController.popBackStack() })
                }
            }

            // ── Caja (Cash) tab ──────────────────────────────────────
            navigation<Screen.CashGraph>(startDestination = Screen.Cash) {
                composable<Screen.Cash> {
                    val cashViewModel: CashViewModel = hiltViewModel()
                    CashScreen(viewModel = cashViewModel)
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

private fun Ticket.toLabelData(): LabelData {
    val serviceTypeLabel = when (serviceType) {
        ServiceType.IN_STORE -> "En tienda"
        ServiceType.WASH_FOLD -> "Lavado y doblado"
    }
    return LabelData(
        ticketNumber = ticketNumber,
        customerName = customerName,
        serviceType = service?.takeIf { it.isNotBlank() } ?: serviceTypeLabel,
        date = ticketDate,
        dailyFolio = dailyFolio,
    )
}
