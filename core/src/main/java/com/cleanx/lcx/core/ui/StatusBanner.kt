package com.cleanx.lcx.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxError
import com.cleanx.lcx.core.theme.LcxInfo
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.theme.LcxTheme
import com.cleanx.lcx.core.theme.LcxWarning

/**
 * Severity level for [StatusBanner].
 */
enum class BannerSeverity { Info, Warning, Error, Success }

/**
 * Alert / warning banner used for critical notifications such as low water
 * level or incomplete safety requirements.
 *
 * @param message     Primary message text.
 * @param severity    Visual severity level.
 * @param modifier    Modifier forwarded to the root layout.
 * @param description Optional secondary text with more detail.
 * @param icon        Leading icon; when null a default is chosen based on [severity].
 */
@Composable
fun StatusBanner(
    message: String,
    severity: BannerSeverity,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
) {
    val tint = severityColor(severity)
    val resolvedIcon = icon ?: defaultIcon(severity)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(LcxSpacing.md)
            .semantics {
                contentDescription = buildString {
                    append(severityLabel(severity))
                    append(": ")
                    append(message)
                    if (description != null) {
                        append(". ")
                        append(description)
                    }
                }
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = resolvedIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )

        Spacer(modifier = Modifier.width(LcxSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun severityColor(severity: BannerSeverity): Color = when (severity) {
    BannerSeverity.Info -> LcxInfo
    BannerSeverity.Warning -> LcxWarning
    BannerSeverity.Error -> LcxError
    BannerSeverity.Success -> LcxSuccess
}

private fun defaultIcon(severity: BannerSeverity): ImageVector = when (severity) {
    BannerSeverity.Info -> Icons.Filled.Info
    BannerSeverity.Warning -> Icons.Filled.Warning
    BannerSeverity.Error -> Icons.Filled.Warning
    BannerSeverity.Success -> Icons.Filled.Info
}

private fun severityLabel(severity: BannerSeverity): String = when (severity) {
    BannerSeverity.Info -> "Información"
    BannerSeverity.Warning -> "Advertencia"
    BannerSeverity.Error -> "Error"
    BannerSeverity.Success -> "Éxito"
}

@Preview(showBackground = true)
@Composable
private fun StatusBannerPreview() {
    LcxTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            StatusBanner(
                message = "Nivel de agua crítico",
                severity = BannerSeverity.Error,
                description = "El tanque principal está al 12%. Requiere atención inmediata.",
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            StatusBanner(
                message = "Checklist incompleto",
                severity = BannerSeverity.Warning,
                description = "Faltan 3 ítems por completar antes del cierre.",
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            StatusBanner(
                message = "Sistema actualizado",
                severity = BannerSeverity.Info,
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            StatusBanner(
                message = "Checklist completado",
                severity = BannerSeverity.Success,
                description = "Todos los ítems fueron verificados exitosamente.",
            )
        }
    }
}
