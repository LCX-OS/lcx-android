package com.cleanx.lcx.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.theme.LcxPaymentPaid
import com.cleanx.lcx.core.theme.LcxPaymentPending
import com.cleanx.lcx.core.theme.LcxPaymentPrepaid
import com.cleanx.lcx.core.theme.LcxSpacing

@Composable
fun PaymentStatusChip(status: PaymentStatus) {
    val (label, backgroundColor) = when (status) {
        PaymentStatus.PENDING -> "Pendiente" to LcxPaymentPending
        PaymentStatus.PREPAID -> "Prepagado" to LcxPaymentPrepaid
        PaymentStatus.PAID -> "Pagado" to LcxPaymentPaid
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.15f))
            .padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs)
            .semantics { contentDescription = "Pago: $label" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = backgroundColor,
        )
    }
}
