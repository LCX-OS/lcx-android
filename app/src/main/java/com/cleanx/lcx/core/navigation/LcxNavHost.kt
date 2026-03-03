package com.cleanx.lcx.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cleanx.lcx.core.network.SessionExpiredInterceptor
import com.cleanx.lcx.core.session.SessionManager
import com.cleanx.lcx.core.transaction.ui.TransactionScreen
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.feature.auth.ui.LoginScreen
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.feature.payments.ui.ChargeScreen
import com.cleanx.lcx.feature.payments.ui.PaymentDiagnosticsScreen
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.ui.PrintScreen
import com.cleanx.lcx.feature.printing.ui.PrintViewModel
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketScreen
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketViewModel
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailScreen
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketListScreen
import com.cleanx.lcx.feature.tickets.ui.list.TicketListViewModel
import timber.log.Timber

private const val NAV_ANIM_DURATION = 300

@Composable
fun LcxNavHost(
    sessionExpiredInterceptor: SessionExpiredInterceptor? = null,
    sessionManager: SessionManager? = null,
) {
    val navController = rememberNavController()

    // Shared TicketListViewModel scoped to the nav host
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val ticketListViewModel: TicketListViewModel = hiltViewModel(viewModelStoreOwner)

    // Global 401 handler: clear session and redirect to Login when session expires.
    if (sessionExpiredInterceptor != null) {
        LaunchedEffect(Unit) {
            sessionExpiredInterceptor.sessionExpired.collect {
                Timber.tag("AUTH").w("Session expired — redirecting to Login")
                sessionManager?.clearSession()
                navController.navigate(Screen.Login) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_DURATION),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_DURATION),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_DURATION),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_DURATION),
            )
        },
    ) {
        composable<Screen.Login>(
            enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        ) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Screen.TicketList) {
                        popUpTo<Screen.Login> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<Screen.TicketList> {
            TicketListScreen(
                viewModel = ticketListViewModel,
                onCreateTicket = {
                    navController.navigate(Screen.CreateTicket)
                },
                onTicketClick = { ticket ->
                    navController.navigate(Screen.TicketDetail(ticketId = ticket.id))
                },
                onSignOut = {
                    navController.navigate(Screen.Login) {
                        popUpTo<Screen.TicketList> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<Screen.CreateTicket> {
            val createViewModel: CreateTicketViewModel = hiltViewModel()
            CreateTicketScreen(
                viewModel = createViewModel,
                onBack = { navController.popBackStack() },
                onTicketCreated = { ticket ->
                    ticketListViewModel.addCreatedTicket(ticket)
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Screen.TicketDetail> {
            val detailViewModel: TicketDetailViewModel = hiltViewModel()

            // Find the ticket in the list and pass it to the detail VM
            val ticketId = detailViewModel.ticketId
            val existingTicket = remember(ticketId) {
                ticketListViewModel.uiState.value.tickets.firstOrNull { it.id == ticketId }
            }
            if (existingTicket != null && detailViewModel.uiState.value.ticket == null) {
                detailViewModel.setTicket(existingTicket)
            }

            TicketDetailScreen(
                viewModel = detailViewModel,
                onBack = {
                    // If ticket was updated, propagate back to list
                    val updated = detailViewModel.uiState.value.ticket
                    if (detailViewModel.uiState.value.ticketUpdated && updated != null) {
                        ticketListViewModel.updateTicketInList(updated)
                        detailViewModel.consumeUpdated()
                    }
                    navController.popBackStack()
                },
                onCharge = { id ->
                    navController.navigate(Screen.Charge(ticketId = id))
                },
                onPrint = { id ->
                    navController.navigate(Screen.Print(ticketId = id))
                },
            )
        }

        composable<Screen.Charge> {
            ChargeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrint = { ticketId ->
                    navController.navigate(Screen.Print(ticketId = ticketId))
                },
            )
        }

        composable<Screen.Print> {
            val printViewModel: PrintViewModel = hiltViewModel()
            val ticketId = printViewModel.ticketId
            val initialLabelData = remember(ticketId, ticketListViewModel.uiState.value.tickets) {
                ticketListViewModel.uiState.value.tickets
                    .firstOrNull { it.id == ticketId }
                    ?.toLabelData()
            }
            PrintScreen(
                initialLabelData = initialLabelData,
                viewModel = printViewModel,
                onFinished = {
                    navController.popBackStack()
                },
            )
        }

        composable<Screen.Transaction> {
            TransactionScreen(
                onCompleted = {
                    // Pop back to ticket list
                    navController.navigate(Screen.TicketList) {
                        popUpTo<Screen.TicketList> { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onCancelled = {
                    navController.popBackStack()
                },
            )
        }

        // Diagnostics screen — only reachable in debug builds.
        if (BuildConfig.DEBUG) {
            composable<Screen.PaymentDiagnostics> {
                PaymentDiagnosticsScreen(
                    onBack = { navController.popBackStack() },
                )
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
