package com.cleanx.lcx.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxTheme

/**
 * Themed slider for adjusting numeric values (e.g. water level input).
 *
 * @param value          Current value within [range].
 * @param onValueChange  Callback for value changes during drag.
 * @param modifier       Modifier forwarded to the root layout.
 * @param range          Allowed value range, defaults to 0f..1f.
 * @param steps          Number of discrete steps (0 = continuous).
 * @param label          Optional label shown above the slider.
 * @param enabled        Whether the slider is interactive.
 * @param onValueChangeFinished Callback invoked when the user finishes dragging.
 */
@Composable
fun LcxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    if (label != null) append("$label: ")
                    append("${"%.0f".format(value)}")
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
                    text = "%.0f".format(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(LcxSpacing.xs))
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LcxSliderPreview() {
    LcxTheme(dynamicColor = false) {
        var level by remember { mutableFloatStateOf(350f) }
        Column(modifier = Modifier.padding(LcxSpacing.md)) {
            LcxSlider(
                value = level,
                onValueChange = { level = it },
                range = 0f..500f,
                steps = 0,
                label = "Nivel de agua (L)",
            )
        }
    }
}
