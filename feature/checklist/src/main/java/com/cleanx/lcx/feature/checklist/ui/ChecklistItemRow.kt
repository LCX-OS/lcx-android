package com.cleanx.lcx.feature.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.feature.checklist.data.ChecklistItemUi
import com.cleanx.lcx.feature.checklist.data.ChecklistStatus
import com.cleanx.lcx.feature.checklist.data.ItemCategory
import com.cleanx.lcx.feature.checklist.data.TEMPLATE_CASH_REGISTER
import com.cleanx.lcx.feature.checklist.data.TEMPLATE_WATER_LEVEL

// ---------------------------------------------------------------------------
// Category colors
// ---------------------------------------------------------------------------

private val CleaningColor = Color(0xFF1A73E8)   // Blue
private val MaintenanceColor = Color(0xFFFBBC04) // Yellow/Amber
private val SafetyColor = Color(0xFFEA4335)      // Red
private val AdminColor = Color(0xFF7B1FA2)       // Purple

private fun categoryColor(category: ItemCategory): Color = when (category) {
    ItemCategory.CLEANING -> CleaningColor
    ItemCategory.MAINTENANCE -> MaintenanceColor
    ItemCategory.SAFETY -> SafetyColor
    ItemCategory.ADMIN -> AdminColor
}

private fun categoryLabel(category: ItemCategory): String = when (category) {
    ItemCategory.CLEANING -> "Limpieza"
    ItemCategory.MAINTENANCE -> "Mantenimiento"
    ItemCategory.SAFETY -> "Seguridad"
    ItemCategory.ADMIN -> "Administrativo"
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChecklistItemRow(
    itemUi: ChecklistItemUi,
    checklistStatus: ChecklistStatus?,
    onToggle: () -> Unit,
    onActionClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCompleted = itemUi.item.isCompleted
    val isReadOnly = itemUi.isSystemValidated
        || checklistStatus == ChecklistStatus.COMPLETED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LcxSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { if (!isReadOnly) onToggle() },
            enabled = !isReadOnly,
            modifier = Modifier.semantics {
                contentDescription = if (isCompleted) {
                    "${itemUi.title} completado"
                } else {
                    "${itemUi.title} pendiente"
                }
            },
        )

        Spacer(modifier = Modifier.width(LcxSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            // Title
            Text(
                text = itemUi.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            // Description
            if (itemUi.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                Text(
                    text = itemUi.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(LcxSpacing.xs))

            // Badges row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                // Category badge
                CategoryBadge(category = itemUi.metadata.category)

                // Required badge
                if (itemUi.metadata.required) {
                    Badge(
                        text = "Requerido",
                        backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        textColor = MaterialTheme.colorScheme.error,
                    )
                }

                // System validation badge
                if (itemUi.isSystemValidated) {
                    Badge(
                        text = "Validacion automatica",
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        textColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // System validation status text
            if (itemUi.isSystemValidated) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                val statusText = when (itemUi.metadata.templateId) {
                    TEMPLATE_WATER_LEVEL -> if (isCompleted) {
                        "Nivel de agua registrado hoy"
                    } else {
                        "Pendiente: registrar nivel de agua"
                    }
                    TEMPLATE_CASH_REGISTER -> if (isCompleted) {
                        "Caja registradora abierta hoy"
                    } else {
                        "Pendiente: abrir caja registradora"
                    }
                    else -> null
                }
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCompleted) {
                            Color(0xFF34A853) // green
                        } else {
                            Color(0xFFE8710A) // orange
                        },
                    )
                }
            }

            // Action link for system items
            if (itemUi.isSystemValidated && !isCompleted) {
                val actionRoute = when (itemUi.metadata.templateId) {
                    TEMPLATE_WATER_LEVEL -> "water"
                    TEMPLATE_CASH_REGISTER -> "cash"
                    else -> null
                }
                val actionLabel = when (itemUi.metadata.templateId) {
                    TEMPLATE_WATER_LEVEL -> "Registrar nivel de agua"
                    TEMPLATE_CASH_REGISTER -> "Abrir caja registradora"
                    else -> null
                }
                if (actionRoute != null && actionLabel != null && onActionClick != null) {
                    TextButton(
                        onClick = { onActionClick(actionRoute) },
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Completion info
            if (isCompleted && itemUi.item.completedBy != null) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                val timeText = itemUi.item.completedAt?.let { at ->
                    try {
                        val instant = java.time.OffsetDateTime.parse(at)
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        " a las ${instant.format(formatter)}"
                    } catch (_: Exception) {
                        ""
                    }
                } ?: ""
                Text(
                    text = "Completado$timeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Badge composables
// ---------------------------------------------------------------------------

@Composable
private fun CategoryBadge(category: ItemCategory) {
    val color = categoryColor(category)
    Badge(
        text = categoryLabel(category),
        backgroundColor = color.copy(alpha = 0.15f),
        textColor = color,
    )
}

@Composable
private fun Badge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = LcxSpacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
