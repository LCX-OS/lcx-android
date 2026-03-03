package com.cleanx.lcx.feature.cash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.feature.cash.data.CashMovementRow
import com.cleanx.lcx.feature.cash.data.DenominationBreakdown
import com.cleanx.lcx.feature.cash.data.MovementType
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
fun CashHistoryTab(
    state: CashUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoadingHistory -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.historyError != null -> {
            ErrorState(
                message = state.historyError,
                modifier = modifier.padding(LcxSpacing.md),
                onRetry = onRetry,
            )
        }

        state.history.isEmpty() -> {
            EmptyState(
                title = "Sin movimientos",
                description = "No hay movimientos de caja registrados todavia.",
                modifier = modifier.padding(LcxSpacing.md),
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = LcxSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                item { Spacer(modifier = Modifier.height(LcxSpacing.sm)) }

                items(
                    items = state.history,
                    key = { it.id ?: it.hashCode().toString() },
                ) { movement ->
                    MovementHistoryItem(movement = movement)
                }

                item { Spacer(modifier = Modifier.height(LcxSpacing.lg)) }
            }
        }
    }
}

@Composable
private fun MovementHistoryItem(movement: CashMovementRow) {
    val color = movementTypeColor(movement.type)
    val typeLabel = movementTypeLabel(movement.type)
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    var expanded by remember { mutableStateOf(false) }
    val hasDenominations = movement.type == MovementType.OPENING ||
        movement.type == MovementType.CLOSING

    LcxCard(
        modifier = if (hasDenominations) {
            Modifier.clickable { expanded = !expanded }
        } else {
            Modifier
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            // Color-coded circle icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                // Type badge + amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MovementTypeBadge(type = movement.type)
                    Text(
                        text = currencyFormat.format(movement.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Date/time
                Text(
                    text = formatDateTime(movement.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // User name
                movement.profiles?.fullName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Notes
                if (!movement.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))
                    Text(
                        text = movement.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }

                // Expandable denomination breakdown
                if (hasDenominations) {
                    AnimatedVisibility(visible = expanded) {
                        movement.metadata?.denominations?.let { breakdown ->
                            DenominationBreakdownView(
                                breakdown = breakdown,
                                currencyFormat = currencyFormat,
                            )
                        }
                    }
                    if (!expanded && hasDenominations) {
                        Spacer(modifier = Modifier.height(LcxSpacing.xs))
                        Text(
                            text = "Toca para ver desglose",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementTypeBadge(type: MovementType) {
    val color = movementTypeColor(type)
    val label = movementTypeLabel(type)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun DenominationBreakdownView(
    breakdown: DenominationBreakdown,
    currencyFormat: NumberFormat,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LcxSpacing.sm)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(LcxSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
    ) {
        Text(
            text = "Desglose de Denominaciones",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

        // Bills
        if (breakdown.bills1000 > 0) BreakdownRow("$1,000", breakdown.bills1000, 1000.0)
        if (breakdown.bills500 > 0) BreakdownRow("$500", breakdown.bills500, 500.0)
        if (breakdown.bills200 > 0) BreakdownRow("$200", breakdown.bills200, 200.0)
        if (breakdown.bills100 > 0) BreakdownRow("$100", breakdown.bills100, 100.0)
        if (breakdown.bills50 > 0) BreakdownRow("$50", breakdown.bills50, 50.0)
        if (breakdown.bills20 > 0) BreakdownRow("$20", breakdown.bills20, 20.0)

        // Coins
        if (breakdown.coins20 > 0) BreakdownRow("$20 (moneda)", breakdown.coins20, 20.0)
        if (breakdown.coins10 > 0) BreakdownRow("$10", breakdown.coins10, 10.0)
        if (breakdown.coins5 > 0) BreakdownRow("$5", breakdown.coins5, 5.0)
        if (breakdown.coins2 > 0) BreakdownRow("$2", breakdown.coins2, 2.0)
        if (breakdown.coins1 > 0) BreakdownRow("$1", breakdown.coins1, 1.0)
        if (breakdown.coins50c > 0) BreakdownRow("$0.50", breakdown.coins50c, 0.5)

        // Total
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LcxSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = currencyFormat.format(breakdown.total()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$label x $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$${String.format("%.2f", value * count)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// -- Helpers ------------------------------------------------------------------

private val BLUE = Color(0xFF1A73E8)
private val PURPLE = Color(0xFF7C3AED)
private val GREEN = Color(0xFF22C55E)
private val RED = Color(0xFFEF4444)

fun movementTypeColor(type: MovementType): Color = when (type) {
    MovementType.OPENING -> BLUE
    MovementType.CLOSING -> PURPLE
    MovementType.INCOME -> GREEN
    MovementType.EXPENSE -> RED
}

fun movementTypeLabel(type: MovementType): String = when (type) {
    MovementType.OPENING -> "Apertura"
    MovementType.CLOSING -> "Cierre"
    MovementType.INCOME -> "Ingreso"
    MovementType.EXPENSE -> "Gasto"
}

/**
 * Formats an ISO 8601 timestamp string into a user-friendly date/time.
 * Falls back to the raw string if parsing fails.
 */
private fun formatDateTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val zdt = ZonedDateTime.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        zdt.format(formatter)
    } catch (_: DateTimeParseException) {
        // Try without timezone info
        try {
            val ldt = java.time.LocalDateTime.parse(
                isoString.substringBefore("+").substringBefore("Z"),
            )
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            ldt.format(formatter)
        } catch (_: Exception) {
            isoString
        }
    }
}
