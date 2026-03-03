package com.cleanx.lcx.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxTheme

/**
 * Section header with optional leading icon.
 *
 * Used throughout the app to separate visual sections (e.g. "Agua",
 * "Checklist del día", "Acciones rápidas").
 *
 * @param title    Header text.
 * @param modifier Modifier forwarded to the root layout.
 * @param icon     Optional leading icon.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LcxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(LcxSpacing.sm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    LcxTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            SectionHeader(title = "Acciones rápidas")
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            SectionHeader(
                title = "Estado del agua",
                icon = Icons.Outlined.Info,
            )
        }
    }
}
