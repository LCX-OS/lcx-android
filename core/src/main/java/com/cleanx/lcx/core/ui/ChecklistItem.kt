package com.cleanx.lcx.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxGreen
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.theme.LcxTheme

/**
 * A single checklist row with checkbox, title, optional description, category
 * badge, and system-validated indicator.
 *
 * @param title             Main text.
 * @param isCompleted       Whether the item is currently checked.
 * @param onToggle          Callback when the user toggles the checkbox.
 * @param modifier          Modifier forwarded to the root layout.
 * @param description       Optional secondary text.
 * @param isSystemValidated If true, shows a validated icon instead of the checkbox.
 * @param category          Optional category name rendered as a [CategoryBadge].
 * @param enabled           Whether toggling is allowed.
 */
@Composable
fun ChecklistItem(
    title: String,
    isCompleted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    isSystemValidated: Boolean = false,
    category: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LcxSpacing.sm)
            .semantics {
                contentDescription = buildString {
                    append(title)
                    if (isCompleted) append(", completado")
                    if (isSystemValidated) append(", validado por sistema")
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        if (isSystemValidated) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Validado por sistema",
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp),
                tint = LcxSuccess,
            )
        } else {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        Spacer(modifier = Modifier.width(LcxSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (category != null) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                CategoryBadge(category = category)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistItemPreview() {
    LcxTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            ChecklistItem(
                title = "Verificar nivel de agua",
                isCompleted = false,
                onToggle = {},
                description = "Revisar que el tanque tenga al menos 50%",
                category = "Mantenimiento",
            )
            ChecklistItem(
                title = "Desinfectar superficies",
                isCompleted = true,
                onToggle = {},
                category = "Limpieza",
            )
            ChecklistItem(
                title = "Sensor de temperatura OK",
                isCompleted = true,
                onToggle = {},
                isSystemValidated = true,
            )
        }
    }
}
