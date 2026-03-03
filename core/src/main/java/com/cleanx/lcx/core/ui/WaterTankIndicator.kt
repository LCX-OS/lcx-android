package com.cleanx.lcx.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxGreen
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxTheme
import com.cleanx.lcx.core.theme.LcxWarning

/**
 * Visual water-tank representation with a coloured fill drawn from the bottom.
 *
 * The composable draws a rounded-rect outline and fills it from the bottom
 * according to [levelPercent].  Percentage text is centred inside the tank
 * and an optional secondary line (e.g. litres) can be supplied via the
 * [secondaryText] parameter.
 *
 * @param levelPercent Fill level from 0 to 100.
 * @param statusColor  Colour used for the fill and border.
 * @param modifier     Modifier forwarded to the root layout.
 * @param secondaryText Optional text shown below the percentage (e.g. "350 L").
 */
@Composable
fun WaterTankIndicator(
    levelPercent: Int,
    statusColor: Color,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
) {
    val clamped = levelPercent.coerceIn(0, 100)
    val fraction = clamped / 100f
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .aspectRatio(0.55f)
            .semantics {
                contentDescription = "Tanque al $clamped por ciento"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerPx = 12.dp.toPx()
            val strokePx = 2.dp.toPx()

            // Outer border
            drawRoundRect(
                color = borderColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )

            // Filled water area from bottom
            val inset = strokePx
            val availableHeight = size.height - inset * 2
            val fillHeight = availableHeight * fraction
            if (fillHeight > 0f) {
                drawRoundRect(
                    color = statusColor.copy(alpha = 0.25f),
                    topLeft = Offset(inset, size.height - inset - fillHeight),
                    size = Size(size.width - inset * 2, fillHeight),
                    cornerRadius = CornerRadius(cornerPx - inset, cornerPx - inset),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center,
            )
            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaterTankIndicatorPreview() {
    LcxTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(LcxSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaterTankIndicator(
                levelPercent = 72,
                statusColor = LcxGreen,
                modifier = Modifier.width(120.dp),
                secondaryText = "350 L",
            )
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            WaterTankIndicator(
                levelPercent = 18,
                statusColor = LcxWarning,
                modifier = Modifier.width(120.dp),
                secondaryText = "90 L",
            )
        }
    }
}
