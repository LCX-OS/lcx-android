package com.cleanx.lcx.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxStatusDelivered
import com.cleanx.lcx.core.theme.LcxStatusProcessing
import com.cleanx.lcx.core.theme.LcxStatusReady
import com.cleanx.lcx.core.theme.LcxStatusReceived

@Composable
fun StatusChip(status: TicketStatus) {
    val (label, tint) = when (status) {
        TicketStatus.RECEIVED -> "Recibido" to LcxStatusReceived
        TicketStatus.PROCESSING -> "En proceso" to LcxStatusProcessing
        TicketStatus.READY -> "Listo" to LcxStatusReady
        TicketStatus.DELIVERED -> "Entregado" to LcxStatusDelivered
        TicketStatus.PAID -> "Pagado" to LcxStatusDelivered
    }

    BrandChip(label = label, tint = tint, description = "Estado: $label")
}

@Composable
internal fun BrandChip(
    label: String,
    tint: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs),
        )
    }
}
