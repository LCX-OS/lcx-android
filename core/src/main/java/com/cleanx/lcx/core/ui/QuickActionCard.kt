package com.cleanx.lcx.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxTheme

/**
 * Dashboard card with icon, title, description, and clickable action.
 *
 * Designed for the home/dashboard screen where operators can quickly jump
 * to frequent actions.
 *
 * @param icon        Leading icon.
 * @param title       Card title.
 * @param description Supporting text.
 * @param onClick     Callback when the card is tapped.
 * @param modifier    Modifier forwarded to the root layout.
 */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LcxSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(LcxSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickActionCardPreview() {
    LcxTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            QuickActionCard(
                icon = Icons.Outlined.PlayArrow,
                title = "Iniciar checklist",
                description = "Completar la lista de verificación diaria",
                onClick = {},
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            QuickActionCard(
                icon = Icons.Outlined.CheckCircle,
                title = "Revisar agua",
                description = "Verificar niveles del tanque principal",
                onClick = {},
            )
        }
    }
}
