package com.cleanx.lcx.feature.checklist.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.feature.checklist.data.Checklist
import com.cleanx.lcx.feature.checklist.data.ChecklistItemUi
import com.cleanx.lcx.feature.checklist.data.ChecklistStatus
import com.cleanx.lcx.feature.checklist.data.ChecklistType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reusable checklist content for both Entrada and Salida tabs.
 */
@Composable
fun ChecklistContent(
    type: ChecklistType,
    isLoading: Boolean,
    checklist: Checklist?,
    items: List<ChecklistItemUi>,
    progress: Float,
    requiredDone: Int,
    requiredTotal: Int,
    canComplete: Boolean,
    notes: String,
    error: String?,
    isCompleting: Boolean,
    onToggleItem: (ChecklistItemUi) -> Unit,
    onNotesChanged: (String) -> Unit,
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    onActionClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            ErrorState(
                message = error,
                modifier = modifier,
                onRetry = onRetry,
            )
        }

        checklist == null -> {
            EmptyState(
                title = "Sin checklist",
                description = "No se encontro un checklist para hoy",
                modifier = modifier,
            )
        }

        else -> {
            ChecklistBody(
                type = type,
                checklist = checklist,
                items = items,
                progress = progress,
                requiredDone = requiredDone,
                requiredTotal = requiredTotal,
                canComplete = canComplete,
                notes = notes,
                isCompleting = isCompleting,
                onToggleItem = onToggleItem,
                onNotesChanged = onNotesChanged,
                onComplete = onComplete,
                onRefresh = onRefresh,
                onActionClick = onActionClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ChecklistBody(
    type: ChecklistType,
    checklist: Checklist,
    items: List<ChecklistItemUi>,
    progress: Float,
    requiredDone: Int,
    requiredTotal: Int,
    canComplete: Boolean,
    notes: String,
    isCompleting: Boolean,
    onToggleItem: (ChecklistItemUi) -> Unit,
    onNotesChanged: (String) -> Unit,
    onComplete: () -> Unit,
    onRefresh: (() -> Unit)?,
    onActionClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isCompleted = checklist.status == ChecklistStatus.COMPLETED
    val typeLabel = when (type) {
        ChecklistType.ENTRADA -> "entrada"
        ChecklistType.SALIDA -> "salida"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = LcxSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        // -- Header card: date, time, status badge
        item {
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            ChecklistHeaderCard(
                checklist = checklist,
                typeLabel = typeLabel,
                showRefreshAction = !isCompleted,
                onRefresh = onRefresh,
            )
        }

        // -- Progress section
        item {
            ProgressSection(
                progress = progress,
                requiredDone = requiredDone,
                requiredTotal = requiredTotal,
            )
        }

        // -- Divider
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = LcxSpacing.xs))
        }

        // -- Checklist items
        items(items, key = { it.item.id ?: it.metadata.templateId ?: it.title }) { itemUi ->
            ChecklistItemRow(
                itemUi = itemUi,
                checklistStatus = checklist.status,
                onToggle = { onToggleItem(itemUi) },
                onActionClick = onActionClick,
            )
        }

        // -- Notes
        if (!isCompleted) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Notas (opcional)") },
                    placeholder = { Text("Agregar observaciones...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
            }
        }

        // -- Complete button
        if (!isCompleted) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.md))
                LcxButton(
                    text = "Completar checklist de $typeLabel",
                    onClick = onComplete,
                    enabled = canComplete,
                    isLoading = isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(LcxSpacing.xl))
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.md))
                CompletedBanner()
                Spacer(modifier = Modifier.height(LcxSpacing.xl))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

@Composable
private fun ChecklistHeaderCard(
    checklist: Checklist,
    typeLabel: String,
    showRefreshAction: Boolean,
    onRefresh: (() -> Unit)?,
) {
    LcxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Checklist de $typeLabel",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                val dateText = try {
                    val date = LocalDate.parse(checklist.date)
                    date.format(
                        DateTimeFormatter.ofPattern(
                            "EEEE d 'de' MMMM, yyyy",
                            Locale.forLanguageTag("es"),
                        )
                    )
                } catch (_: Exception) {
                    checklist.date
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val timeText = LocalTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm")
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (showRefreshAction && onRefresh != null) {
                    TextButton(onClick = onRefresh) {
                        Text(text = "Verificar")
                    }
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))
                }

                StatusBadge(status = checklist.status)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ChecklistStatus) {
    val (label, color) = when (status) {
        ChecklistStatus.PENDING -> "Pendiente" to Color(0xFFE8710A)
        ChecklistStatus.IN_PROGRESS -> "En progreso" to Color(0xFF1A73E8)
        ChecklistStatus.COMPLETED -> "Completado" to Color(0xFF34A853)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun ProgressSection(
    progress: Float,
    requiredDone: Int,
    requiredTotal: Int,
) {
    LcxCard {
        Text(
            text = "Progreso",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Tareas requeridas: $requiredDone de $requiredTotal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompletedBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF34A853).copy(alpha = 0.12f))
            .padding(LcxSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Checklist completado",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF34A853),
        )
    }
}
