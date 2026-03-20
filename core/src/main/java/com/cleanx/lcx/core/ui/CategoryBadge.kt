package com.cleanx.lcx.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.cleanx.lcx.core.theme.LcxBrandInk
import com.cleanx.lcx.core.theme.LcxBlue
import com.cleanx.lcx.core.theme.LcxGreen
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxStatusProcessing
import com.cleanx.lcx.core.theme.LcxTheme
import com.cleanx.lcx.core.theme.LcxWarning

/**
 * Small coloured badge that labels a category.
 */
@Composable
fun CategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val badgeColor = color ?: categoryColor(category)

    BrandChip(
        label = category,
        tint = badgeColor,
        description = "Categoría: $category",
        modifier = modifier,
    )
}

private fun categoryColor(category: String): Color {
    val key = category.lowercase().trim()
    return when {
        key.contains("limp") || key.contains("clean") -> LcxGreen
        key.contains("manten") || key.contains("maint") -> LcxStatusProcessing
        key.contains("segur") || key.contains("safe") -> LcxWarning
        key.contains("admin") -> LcxBrandInk
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
