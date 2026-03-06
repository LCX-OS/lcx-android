package com.cleanx.lcx.core.navigation

import com.cleanx.lcx.core.model.UserRole

/**
 * Mirrors PWA's `lib/auth/route-access.ts` — defines which roles can access
 * which screens. All operator-level screens are accessible by every
 * authenticated user; gerencia requires manager+; admin requires superadmin.
 *
 * Currently Android only exposes operator-level screens (via bottom nav + More
 * hub), but this registry is forward-compatible for when gerencia/admin screens
 * are ported.
 */
object RouteAccess {

    private val ALL_ROLES = setOf(UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.SUPERADMIN)
    private val MANAGER_AND_ABOVE = setOf(UserRole.MANAGER, UserRole.SUPERADMIN)
    private val SUPERADMIN_ONLY = setOf(UserRole.SUPERADMIN)

    /**
     * Per-screen role requirements.  Screens not listed here are considered
     * accessible by all authenticated users (i.e. same as [ALL_ROLES]).
     */
    private val screenRoles: Map<Class<out Screen>, Set<UserRole>> = buildMap {
        // ── Operator screens (all roles) ───────────────────────────────
        // Explicitly listed for clarity and forward compatibility.
        put(Screen.Dashboard::class.java, ALL_ROLES)
        put(Screen.TicketList::class.java, ALL_ROLES)
        put(Screen.CreateTicket::class.java, ALL_ROLES)
        put(Screen.TicketDetail::class.java, ALL_ROLES)
        put(Screen.TicketPreset::class.java, ALL_ROLES)
        put(Screen.Water::class.java, ALL_ROLES)
        put(Screen.Checklist::class.java, ALL_ROLES)
        put(Screen.Cash::class.java, ALL_ROLES)
        put(Screen.CashRegister::class.java, ALL_ROLES)
        put(Screen.CashHistory::class.java, ALL_ROLES)
        put(Screen.Sales::class.java, ALL_ROLES)
        put(Screen.IncidentsNew::class.java, ALL_ROLES)
        put(Screen.IncidentsHistory::class.java, ALL_ROLES)
        put(Screen.ShiftsControl::class.java, ALL_ROLES)
        put(Screen.ShiftsHistory::class.java, ALL_ROLES)
        put(Screen.ShiftsSchedule::class.java, ALL_ROLES)
        put(Screen.ShiftsReports::class.java, MANAGER_AND_ABOVE)
        put(Screen.DamagedClothingNew::class.java, ALL_ROLES)
        put(Screen.DamagedClothingHistory::class.java, ALL_ROLES)
        put(Screen.SuppliesInventory::class.java, ALL_ROLES)
        put(Screen.SuppliesLabels::class.java, ALL_ROLES)
        put(Screen.SuppliesReports::class.java, MANAGER_AND_ABOVE)
        put(Screen.SuppliesBrotherDebug::class.java, ALL_ROLES)
        put(Screen.Vacations::class.java, ALL_ROLES)
        put(Screen.CalendarMonthly::class.java, ALL_ROLES)
        put(Screen.CalendarEvents::class.java, ALL_ROLES)
        put(Screen.BestPractices::class.java, ALL_ROLES)
        put(Screen.Help::class.java, ALL_ROLES)
        put(Screen.Charge::class.java, ALL_ROLES)
        put(Screen.Print::class.java, ALL_ROLES)
        put(Screen.Transaction::class.java, ALL_ROLES)
        put(Screen.PaymentDiagnostics::class.java, ALL_ROLES)
    }

    /**
     * Per-tab graph role requirements. Tabs not listed are accessible by all.
     */
    private val tabGraphRoles: Map<Class<out Screen>, Set<UserRole>> = buildMap {
        put(Screen.DashboardGraph::class.java, ALL_ROLES)
        put(Screen.TicketsGraph::class.java, ALL_ROLES)
        put(Screen.WaterGraph::class.java, ALL_ROLES)
        put(Screen.ChecklistGraph::class.java, ALL_ROLES)
        put(Screen.CashGraph::class.java, ALL_ROLES)
        put(Screen.MoreGraph::class.java, ALL_ROLES)
        put(Screen.SalesGraph::class.java, ALL_ROLES)
        put(Screen.ShiftsGraph::class.java, ALL_ROLES)
    }

    /** Returns `true` if [role] is allowed to access [screen]. */
    fun canAccess(role: UserRole?, screen: Screen): Boolean {
        if (role == null) return false
        val allowed = screenRoles[screen::class.java] ?: ALL_ROLES
        return role in allowed
    }

    /** Returns `true` if [role] is allowed to see the bottom nav tab for [graphRoute]. */
    fun canAccessTab(role: UserRole?, graphRoute: Screen): Boolean {
        if (role == null) return false
        val allowed = tabGraphRoles[graphRoute::class.java] ?: ALL_ROLES
        return role in allowed
    }

    /** Returns the set of roles allowed for [screen], or [ALL_ROLES] if unrestricted. */
    fun allowedRoles(screen: Screen): Set<UserRole> {
        return screenRoles[screen::class.java] ?: ALL_ROLES
    }
}
