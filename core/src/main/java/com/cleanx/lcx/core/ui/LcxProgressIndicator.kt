package com.cleanx.lcx.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxTheme

/**
 * Linear progress bar with an optional label and percentage text.
 *
 * Use this component for water-tank fill levels, checklist completion
 * progress, or any other bounded 0-to-100 % metric.
 *
 * @param progress Current progress value between 0f and 1f.
 * @param label    Optional text shown above the bar.
 * @param color    The fill colour of the progress bar.
 * @param modifier Modifier forwarded to the root layout.
 */
@Composable
fun LcxProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 400),
        label = "progressAnimation",
    )
    val percentText = "${(clampedProgress * 100).toInt()}%"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    if (label != null) append("$label: ")
                    append(percentText)
                }
            },
    ) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(LcxSpacing.xs))
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )

        if (label == null) {
            Spacer(modifier = Modifier.height(LcxSpacing.xs))
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LcxProgressIndicatorPreview() {
    LcxTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            LcxProgressIndicator(
                progress = 0.65f,
                label = "Nivel del tanque",
            )
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            LcxProgressIndicator(
                progress = 0.3f,
            )
        }
    }
}
