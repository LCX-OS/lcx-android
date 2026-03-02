package com.cleanx.lcx.core.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable data object Login : Screen
    @Serializable data object TicketList : Screen
    @Serializable data class TicketDetail(val ticketId: String) : Screen
    @Serializable data object CreateTicket : Screen
    @Serializable data class Charge(
        val ticketId: String,
        val amount: String = "0",
        val customerName: String = "",
    ) : Screen
    @Serializable data class Print(val ticketId: String) : Screen
    @Serializable data object Transaction : Screen
    @Serializable data object PaymentDiagnostics : Screen
}
