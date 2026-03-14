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
    val (label, backgroundColor) = when (status) {
        TicketStatus.RECEIVED -> "Recibido" to LcxStatusReceived
        TicketStatus.PROCESSING -> "En proceso" to LcxStatusProcessing
        TicketStatus.READY -> "Listo" to LcxStatusReady
        TicketStatus.DELIVERED -> "Entregado" to LcxStatusDelivered
        TicketStatus.PAID -> "Pagado" to LcxStatusDelivered
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.15f))
            .padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs)
            .semantics { contentDescription = "Estado: $label" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = backgroundColor,
        )
    }
}
