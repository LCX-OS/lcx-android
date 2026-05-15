package com.cleanx.lcx.core.ui

object LcxTestTags {
    const val LOGIN_ROOT = "login-root"
    const val LOGIN_PIN_FIELD = "login-pin-field"
    const val LOGIN_SUBMIT_PIN = "login-submit-pin"
    const val LOGIN_GUEST_BUTTON = "login-guest-button"
    const val LOGIN_SUBMIT_GUEST = "login-submit-guest"

    const val DASHBOARD_ROOT = "dashboard-root"
    const val WATER_ROOT = "water-root"
    const val CHECKLIST_ROOT = "checklist-root"
    const val CASH_ROOT = "cash-root"
    const val TICKET_LIST_ROOT = "ticket-list-root"
    const val MORE_ROOT = "more-root"
    const val MORE_LIST = "more-list"

    const val DRAWER_MORE = "drawer-more"
    const val PAYMENT_DIAGNOSTICS_ROOT = "payment-diagnostics-root"
    const val PAYMENT_DIAGNOSTICS_CHARGE_BUTTON = "payment-diagnostics-charge-button"
    const val PAYMENT_DIAGNOSTICS_LAST_RESULT = "payment-diagnostics-last-result"

    const val PRINTER_SETTINGS_ROOT = "printer-settings-root"
    const val PRINTER_DISCOVER_BUTTON = "printer-discover-button"
    const val PRINTER_CARD = "printer-card"
    const val PRINTER_TEST_PRINT_BUTTON = "printer-test-print-button"
    const val PRINTER_STATUS_MESSAGE = "printer-status-message"

    const val TICKET_CREATE_BUTTON = "ticket-create-button"
    const val TICKET_LIST_SCROLL = "ticket-list-scroll"
    const val TICKET_DETAIL_ROOT = "ticket-detail-root"
    const val TICKET_DETAIL_CHARGE_BUTTON = "ticket-detail-charge-button"
    const val TICKET_DETAIL_INLINE_CHARGE_BUTTON = "ticket-detail-inline-charge-button"
    const val TICKET_DETAIL_QUICK_CHARGE_BUTTON = "ticket-detail-quick-charge-button"
    const val TICKET_DETAIL_PRINT_BUTTON = "ticket-detail-print-button"

    const val CHARGE_ROOT = "charge-root"
    const val CHARGE_START_BUTTON = "charge-start-button"
    const val CHARGE_SUCCESS_ROOT = "charge-success-root"
    const val CHARGE_PRINT_BUTTON = "charge-print-button"

    const val PRINT_ROOT = "print-root"
    const val PRINT_START_BUTTON = "print-start-button"
    const val PRINT_SUCCESS_ROOT = "print-success-root"

    fun loginBranch(branch: String): String = "login-branch-${branch.toLcxTestToken()}"
    fun loginOperator(operator: String): String = "login-operator-${operator.toLcxTestToken()}"
    fun bottomNav(label: String): String = "bottom-nav-${label.toLcxTestToken()}"
    fun drawerItem(label: String): String = "drawer-item-${label.toLcxTestToken()}"
    fun moreItem(label: String): String = "more-item-${label.toLcxTestToken()}"
    fun printerCard(printerName: String): String = "printer-card-${printerName.toLcxTestToken()}"
    fun ticketListItem(ticketNumber: String): String = "ticket-list-item-${ticketNumber.toLcxTestToken()}"
}

fun String.toLcxTestToken(): String = trim()
    .lowercase()
    .map { char ->
        when {
            char.isLetterOrDigit() -> char
            else -> '-'
        }
    }
    .joinToString("")
    .replace(Regex("-+"), "-")
    .trim('-')
    .ifBlank { "empty" }
