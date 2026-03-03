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

    // ── Ticket preset routes ─────────────────────────────────────────
    @Serializable data class TicketPreset(val preset: String) : Screen

    // ── Sales (Ventas) ───────────────────────────────────────────────
    @Serializable data object Sales : Screen

    // ── Incidents (Incidencias) ──────────────────────────────────────
    @Serializable data object IncidentsNew : Screen
    @Serializable data object IncidentsHistory : Screen

    // ── Shifts (Turnos) ──────────────────────────────────────────────
    @Serializable data object ShiftsControl : Screen
    @Serializable data object ShiftsHistory : Screen
    @Serializable data object ShiftsSchedule : Screen
    @Serializable data object ShiftsReports : Screen

    // ── Damaged Clothing (Ropa Dañada) ───────────────────────────────
    @Serializable data object DamagedClothingNew : Screen
    @Serializable data object DamagedClothingHistory : Screen

    // ── Supplies (Insumos) ───────────────────────────────────────────
    @Serializable data object SuppliesInventory : Screen
    @Serializable data object SuppliesLabels : Screen
    @Serializable data object SuppliesReports : Screen
    @Serializable data object SuppliesBrotherDebug : Screen

    // ── Vacations (Vacaciones) ───────────────────────────────────────
    @Serializable data object Vacations : Screen

    // ── Calendar (Calendario) ────────────────────────────────────────
    @Serializable data object CalendarMonthly : Screen
    @Serializable data object CalendarEvents : Screen

    // ── Info / Help ──────────────────────────────────────────────────
    @Serializable data object BestPractices : Screen
    @Serializable data object Help : Screen

    // ── Tab graph route markers (used as nested-graph routes) ─────────
    @Serializable data object DashboardGraph : Screen
    @Serializable data object TicketsGraph : Screen
    @Serializable data object WaterGraph : Screen
    @Serializable data object ChecklistGraph : Screen
    @Serializable data object MoreGraph : Screen
    @Serializable data object CashGraph : Screen

    // ── Domain graph markers (nested within More tab) ────────────────
    @Serializable data object SalesGraph : Screen
    @Serializable data object IncidentsGraph : Screen
    @Serializable data object ShiftsGraph : Screen
    @Serializable data object DamagedClothingGraph : Screen
    @Serializable data object SuppliesGraph : Screen
    @Serializable data object CalendarGraph : Screen

    // ── Main shell (post-login) ──────────────────────────────────────
    @Serializable data object Main : Screen
}
