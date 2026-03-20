package com.cleanx.lcx.core.ui

import androidx.compose.runtime.Composable
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.theme.LcxPaymentPaid
import com.cleanx.lcx.core.theme.LcxPaymentPending
import com.cleanx.lcx.core.theme.LcxPaymentPrepaid

@Composable
fun PaymentStatusChip(status: PaymentStatus) {
    val (label, tint) = when (status) {
        PaymentStatus.PENDING -> "Pendiente" to LcxPaymentPending
        PaymentStatus.PREPAID -> "Prepagado" to LcxPaymentPrepaid
        PaymentStatus.PAID -> "Pagado" to LcxPaymentPaid
    }

    BrandChip(label = label, tint = tint, description = "Pago: $label")
}
