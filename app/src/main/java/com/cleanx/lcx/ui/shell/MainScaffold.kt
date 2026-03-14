package com.cleanx.lcx.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import androidx.navigation.toRoute
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.R
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.navigation.BottomNavItem
import com.cleanx.lcx.core.navigation.RouteAccess
import com.cleanx.lcx.core.navigation.Screen
import com.cleanx.lcx.core.transaction.ui.TransactionScreen
import com.cleanx.lcx.feature.cash.ui.CashScreen
import com.cleanx.lcx.feature.cash.ui.CashViewModel
import com.cleanx.lcx.feature.checklist.ui.ChecklistScreen as FeatureChecklistScreen
import com.cleanx.lcx.feature.checklist.ui.ChecklistViewModel
import com.cleanx.lcx.feature.payments.ui.ChargeScreen
import com.cleanx.lcx.feature.payments.ui.PaymentDiagnosticsScreen
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.ui.PrintScreen
import com.cleanx.lcx.feature.printing.ui.PrintViewModel
import com.cleanx.lcx.feature.sales.ui.SalesScreen
import com.cleanx.lcx.feature.sales.ui.SalesViewModel
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketScreen
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketViewModel
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailScreen
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketListScreen
import com.cleanx.lcx.feature.tickets.ui.list.TicketListViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketPresetScreen
import com.cleanx.lcx.feature.water.ui.WaterScreen
import com.cleanx.lcx.feature.water.ui.WaterViewModel
import com.cleanx.lcx.ui.dashboard.DashboardScreen
import com.cleanx.lcx.ui.dashboard.DashboardViewModel
import com.cleanx.lcx.ui.more.MoreScreen
import com.cleanx.lcx.ui.ops.BestPracticesShell
import com.cleanx.lcx.ui.ops.CalendarEventsShell
import com.cleanx.lcx.ui.ops.CalendarMonthlyShell
import com.cleanx.lcx.ui.ops.DamagedClothingHistoryShell
import com.cleanx.lcx.ui.ops.DamagedClothingNewShell
import com.cleanx.lcx.ui.ops.HelpShell
import com.cleanx.lcx.ui.ops.IncidentsHistoryShell
import com.cleanx.lcx.ui.ops.IncidentsNewShell
import com.cleanx.lcx.ui.ops.ShiftsControlShell
import com.cleanx.lcx.ui.ops.ShiftsHistoryShell
import com.cleanx.lcx.ui.ops.ShiftsReportsShell
import com.cleanx.lcx.ui.ops.ShiftsScheduleShell
import com.cleanx.lcx.ui.ops.SuppliesBrotherDebugShell
import com.cleanx.lcx.ui.ops.SuppliesInventoryShell
import com.cleanx.lcx.ui.ops.SuppliesLabelsShell
import com.cleanx.lcx.ui.ops.SuppliesReportsShell
import com.cleanx.lcx.ui.ops.VacationsShell
import kotlinx.coroutines.launch

private data class DrawerItem(
    val label: String,
    val target: Screen,
)

/**
 * Post-login shell with:
 * - top header (hamburger + logo + notifications + user)
 * - bottom navigation tabs
 * - nested navigation graphs per tab
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScaffold(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val ticketListViewModel: TicketListViewModel = hiltViewModel(viewModelStoreOwner)
    val mainScaffoldViewModel: MainScaffoldViewModel = hiltViewModel(viewModelStoreOwner)
    val userRole by mainScaffoldViewModel.userRole.collectAsState()
    val userBadgeText by mainScaffoldViewModel.userBadgeText.collectAsState()

    val visibleTabs = remember(userRole) {
        BottomNavItem.entries.filter { RouteAccess.canAccessTab(userRole, it.graphRoute) }
    }

    val drawerItems = remember(userRole) {
        listOf(
            DrawerItem("Agua", Screen.WaterGraph),
            DrawerItem("Checklist", Screen.ChecklistGraph),
            DrawerItem("Más módulos", Screen.MoreGraph),
        ).filter { RouteAccess.canAccessTab(userRole, it.target) }
    }

    val showShellTopBar = currentDestination?.let { destination ->
        destination.hasRoute(Screen.Dashboard::class) ||
            destination.hasRoute(Screen.Sales::class) ||
            destination.hasRoute(Screen.TicketList::class) ||
            destination.hasRoute(Screen.ShiftsControl::class) ||
            destination.hasRoute(Screen.Cash::class) ||
            destination.hasRoute(Screen.Water::class) ||
            destination.hasRoute(Screen.Checklist::class)
    } == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Navegación",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
                HorizontalDivider()
                drawerItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(item.target::class)
                    } == true
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                tabNavController.navigate(item.target) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    icon = { Icon(Icons.Filled.Logout, contentDescription = "Cerrar sesión") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (showShellTopBar) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Menú",
                                )
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.cleanx_logo),
                                    contentDescription = "Logo Clean X",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(72.dp),
                                )
                                Text(
                                    text = "Clean X",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    tabNavController.navigate(Screen.MoreGraph) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = "Notificaciones",
                                )
                            }
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonOutline,
                                    contentDescription = "Usuario",
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = userBadgeText,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        },
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    visibleTabs.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.graphRoute::class)
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                tabNavController.navigate(item.graphRoute) {
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
                // ── Inicio ───────────────────────────────────────────────
                navigation<Screen.DashboardGraph>(startDestination = Screen.Dashboard) {
                    composable<Screen.Dashboard> {
                        val dashboardViewModel: DashboardViewModel = hiltViewModel()
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenChecklist = {
                                tabNavController.navigate(Screen.ChecklistGraph) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenWater = {
                                tabNavController.navigate(Screen.WaterGraph) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenCash = {
                                tabNavController.navigate(Screen.CashGraph) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenCreateTicket = {
                                tabNavController.navigate(Screen.CreateTicket)
                            },
                            onOpenTicket = { ticketId ->
                                tabNavController.navigate(Screen.TicketDetail(ticketId = ticketId))
                            },
                        )
                    }
                }

                // ── Ventas ───────────────────────────────────────────────
                navigation<Screen.SalesGraph>(startDestination = Screen.Sales) {
                    composable<Screen.Sales> {
                        val salesViewModel: SalesViewModel = hiltViewModel()
                        SalesScreen(
                            viewModel = salesViewModel,
                            showTopBar = false,
                        )
                    }
                }

                // ── Encargos ─────────────────────────────────────────────
                navigation<Screen.TicketsGraph>(startDestination = Screen.TicketList) {
                    composable<Screen.TicketList> {
                        TicketListScreen(
                            viewModel = ticketListViewModel,
                            onCreateTicket = {
                                tabNavController.navigate(Screen.CreateTicket)
                            },
                            onOpenPreset = { preset ->
                                tabNavController.navigate(Screen.TicketPreset(preset = preset))
                            },
                            onTicketClick = { ticket ->
                                tabNavController.navigate(
                                    Screen.TicketDetail(ticketId = ticket.id),
                                )
                            },
                            onSignOut = onSignOut,
                            showTopBar = false,
                        )
                    }

                    composable<Screen.CreateTicket> {
                        val createViewModel: CreateTicketViewModel = hiltViewModel()
                        CreateTicketScreen(
                            viewModel = createViewModel,
                            onBack = { tabNavController.popBackStack() },
                            onTicketCreated = { ticket ->
                                ticketListViewModel.addCreatedTicket(ticket)
                                tabNavController.popBackStack()
                                tabNavController.navigate(
                                    Screen.TicketDetail(
                                        ticketId = ticket.id,
                                        quickActions = true,
                                    ),
                                )
                            },
                        )
                    }

                    composable<Screen.TicketDetail> { backStackEntry ->
                        val detailViewModel: TicketDetailViewModel = hiltViewModel()
                        val route = backStackEntry.toRoute<Screen.TicketDetail>()

                        val ticketId = detailViewModel.ticketId
                        val existingTicket = remember(ticketId) {
                            ticketListViewModel.uiState.value.tickets
                                .firstOrNull { it.id == ticketId }
                        }
                        if (existingTicket != null && detailViewModel.uiState.value.ticket == null) {
                            detailViewModel.setTicket(existingTicket)
                        }

                        TicketDetailScreen(
                            viewModel = detailViewModel,
                            showQuickActionsOnLaunch = route.quickActions,
                            onBack = {
                                val updated = detailViewModel.uiState.value.ticket
                                if (detailViewModel.uiState.value.ticketUpdated && updated != null) {
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

                // ── Turnos ───────────────────────────────────────────────
                navigation<Screen.ShiftsGraph>(startDestination = Screen.ShiftsControl) {
                    composable<Screen.ShiftsControl> {
                        ShiftsControlShell(
                            onBack = {},
                            showTopBar = false,
                        )
                    }
                }

                // ── Agua / Checklist / Más (drawer) ─────────────────────
                navigation<Screen.WaterGraph>(startDestination = Screen.Water) {
                    composable<Screen.Water> {
                        val waterViewModel: WaterViewModel = hiltViewModel()
                        WaterScreen(
                            viewModel = waterViewModel,
                            showTopBar = false,
                        )
                    }
                }

                navigation<Screen.ChecklistGraph>(startDestination = Screen.Checklist) {
                    composable<Screen.Checklist> {
                        val checklistViewModel: ChecklistViewModel = hiltViewModel()
                        FeatureChecklistScreen(
                            viewModel = checklistViewModel,
                            onNavigateToWater = {
                                tabNavController.navigate(Screen.WaterGraph) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToCash = {
                                tabNavController.navigate(Screen.CashGraph) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            showTopBar = false,
                        )
                    }
                }

                navigation<Screen.MoreGraph>(startDestination = Screen.More) {
                    composable<Screen.More> {
                        MoreScreen(
                            userRole = userRole,
                            onNavigate = { screen ->
                                if (RouteAccess.canAccess(userRole, screen)) {
                                    tabNavController.navigate(screen)
                                }
                            },
                        )
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

                // ── Caja ────────────────────────────────────────────────
                navigation<Screen.CashGraph>(startDestination = Screen.Cash) {
                    composable<Screen.Cash> {
                        val cashViewModel: CashViewModel = hiltViewModel()
                        CashScreen(
                            viewModel = cashViewModel,
                            showTopBar = false,
                        )
                    }
                }
            }
        }
    }
}

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
