package com.cleanx.lcx.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cleanx.lcx.core.transaction.ui.TransactionScreen
import com.cleanx.lcx.feature.auth.ui.LoginScreen
import com.cleanx.lcx.feature.payments.ui.ChargeScreen
import com.cleanx.lcx.feature.printing.ui.PrintScreen
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketScreen
import com.cleanx.lcx.feature.tickets.ui.create.CreateTicketViewModel
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailScreen
import com.cleanx.lcx.feature.tickets.ui.detail.TicketDetailViewModel
import com.cleanx.lcx.feature.tickets.ui.list.TicketListScreen
import com.cleanx.lcx.feature.tickets.ui.list.TicketListViewModel

@Composable
fun LcxNavHost() {
    val navController = rememberNavController()

    // Shared TicketListViewModel scoped to the nav host
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val ticketListViewModel: TicketListViewModel = hiltViewModel(viewModelStoreOwner)

    NavHost(
        navController = navController,
        startDestination = Screen.Login,
    ) {
        composable<Screen.Login> {
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
            )
        }

        composable<Screen.CreateTicket> {
            val createViewModel: CreateTicketViewModel = hiltViewModel()
            CreateTicketScreen(
                viewModel = createViewModel,
                onBack = { navController.popBackStack() },
                onTicketCreated = { ticket ->
                    ticketListViewModel.addCreatedTicket(ticket)
                    navController.popBackStack()
                },
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
            PrintScreen(
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
    }
}
