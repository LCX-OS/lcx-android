package com.cleanx.lcx.feature.water.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.WaterLevelStatus
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.feature.water.data.WaterLevelWithUser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun WaterHistoryTab(
    state: WaterUiState,
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
                title = "Sin registros",
                description = "No hay registros de nivel de agua todavia.",
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
                ) { record ->
                    HistoryItem(record = record)
                }

                item { Spacer(modifier = Modifier.height(LcxSpacing.lg)) }
            }
        }
    }
}

@Composable
private fun HistoryItem(record: WaterLevelWithUser) {
    val status = record.status ?: WaterLevelStatus.NORMAL
    val color = statusColor(status)

    LcxCard {
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
                // Action
                Text(
                    text = record.action ?: "Nivel registrado",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Date/time
                Text(
                    text = formatDateTime(record.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // User name
                record.user?.fullName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(LcxSpacing.xs))

                // Level info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatLevelSummary(
                            levelPercentage = record.levelPercentage,
                            liters = record.liters,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // Status badge
                    StatusBadge(status = status)
                }
            }
        }
    }
}

internal fun formatLevelSummary(levelPercentage: Int?, liters: Int?): String {
    val safePercentage = levelPercentage ?: 0
    val safeLiters = liters ?: 0
    return "$safePercentage% - ${"%,d".format(safeLiters)} L"
}

@Composable
private fun StatusBadge(status: WaterLevelStatus) {
    val color = statusColor(status)
    val label = statusLabel(status)

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
            val ldt = java.time.LocalDateTime.parse(isoString.substringBefore("+").substringBefore("Z"))
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            ldt.format(formatter)
        } catch (_: Exception) {
            isoString
        }
    }
}
