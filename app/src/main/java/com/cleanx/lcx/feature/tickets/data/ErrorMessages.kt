package com.cleanx.lcx.feature.tickets.data

/**
 * Maps API error codes to user-facing Spanish messages.
 */
object ErrorMessages {

    fun forCode(code: String?, fallback: String): String {
        return when (code) {
            "NOT_AUTHENTICATED" -> "Tu sesion ha expirado. Inicia sesion de nuevo."
            "OPENING_CHECKLIST_BLOCKING_OPERATION" -> "Debes completar el checklist de apertura antes de crear tickets."
            "TICKET_NUMBER_CONFLICT" -> "Conflicto al generar numero de ticket. Intenta de nuevo."
            "INVALID_CUSTOMER_NAME" -> "El nombre del cliente es obligatorio."
            "INVALID_CUSTOMER_PHONE" -> "El telefono del cliente es obligatorio."
            "INVALID_SERVICE_TYPE" -> "El tipo de servicio no es valido."
            "INVALID_PAYMENT_METHOD" -> "El metodo de pago no es valido."
            "INVALID_PAYMENT_STATUS" -> "El estado de pago no es valido."
            "INVALID_STATUS_TRANSITION" -> "No se puede cambiar a ese estado."
            "INSUFFICIENT_PERMISSIONS" -> "No tienes permisos para realizar esta accion."
            "NOT_FOUND" -> "Ticket no encontrado."
            else -> fallback
        }
    }
}
