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

    // ── Bottom-nav tab root screens ───────────────────────────────────
    @Serializable data object Dashboard : Screen
    @Serializable data object Water : Screen
    @Serializable data object Checklist : Screen
    @Serializable data object More : Screen
    @Serializable data object Cash : Screen
    @Serializable data object CashRegister : Screen
    @Serializable data object CashHistory : Screen

    // ── Tab graph route markers (used as nested-graph routes) ─────────
    @Serializable data object DashboardGraph : Screen
    @Serializable data object TicketsGraph : Screen
    @Serializable data object WaterGraph : Screen
    @Serializable data object ChecklistGraph : Screen
    @Serializable data object MoreGraph : Screen
    @Serializable data object CashGraph : Screen

    // ── Main shell (post-login) ──────────────────────────────────────
    @Serializable data object Main : Screen
}
