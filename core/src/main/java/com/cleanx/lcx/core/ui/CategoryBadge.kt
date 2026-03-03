package com.cleanx.lcx.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxBlue
import com.cleanx.lcx.core.theme.LcxGreen
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxStatusProcessing
import com.cleanx.lcx.core.theme.LcxTheme
import com.cleanx.lcx.core.theme.LcxWarning

/**
 * Small coloured badge that labels a category (e.g. cleaning, maintenance,
 * safety, admin).
 *
 * The badge colour is determined by [category] through a simple heuristic
 * mapping.  Callers can override the colour via [color].
 *
 * @param category The category label text.
 * @param modifier Modifier forwarded to the root layout.
 * @param color    Optional explicit badge colour; when null a default is derived
 *                 from [category].
 */
@Composable
fun CategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val badgeColor = color ?: categoryColor(category)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs)
            .semantics { contentDescription = "Categoría: $category" },
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
        )
    }
}

/**
 * Returns a deterministic colour for well-known categories.
 * Falls back to [LcxBlue] for unknown categories.
 */
private fun categoryColor(category: String): Color {
    val key = category.lowercase().trim()
    return when {
        key.contains("limp") || key.contains("clean") -> LcxGreen
        key.contains("manten") || key.contains("maint") -> LcxStatusProcessing
        key.contains("segur") || key.contains("safe") -> LcxWarning
        key.contains("admin") -> LcxBlue
        else -> LcxBlue
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryBadgePreview() {
    LcxTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(LcxSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            CategoryBadge(category = "Limpieza")
            CategoryBadge(category = "Mantenimiento")
            CategoryBadge(category = "Seguridad")
            CategoryBadge(category = "Admin")
        }
    }
}
