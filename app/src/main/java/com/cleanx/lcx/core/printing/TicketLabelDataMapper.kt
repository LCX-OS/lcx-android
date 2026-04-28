package com.cleanx.lcx.core.printing

import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.feature.printing.data.LabelData

fun Ticket.toLabelData(): LabelData {
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
        ticketId = id,
        promisedPickupDate = promisedPickupDate,
        paymentLabel = paymentLabel(),
    )
}

private fun Ticket.paymentLabel(): String {
    return when (paymentStatus) {
        PaymentStatus.PAID -> "PAGO: PAGADO"
        PaymentStatus.PREPAID -> "PAGO: PENDIENTE (ANTICIPO)"
        PaymentStatus.PENDING,
        null -> LabelData.DEFAULT_PAYMENT_LABEL
    }
}
