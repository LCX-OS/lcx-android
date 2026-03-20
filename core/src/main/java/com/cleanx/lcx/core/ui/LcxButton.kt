package com.cleanx.lcx.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxError
import com.cleanx.lcx.core.theme.LcxPillShape
import com.cleanx.lcx.core.theme.LcxSpacing

enum class ButtonVariant { Primary, Secondary, Danger }

@Composable
fun LcxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Primary,
) {
    val isClickable = enabled && !isLoading
    val minSizeModifier = modifier.defaultMinSize(minHeight = LcxSpacing.controlMinHeight)
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

    when (variant) {
        ButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = minSizeModifier,
                enabled = isClickable,
                shape = LcxPillShape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                ButtonContent(text = text, isLoading = isLoading)
            }
        }

        ButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = minSizeModifier,
                enabled = isClickable,
                shape = LcxPillShape,
                contentPadding = contentPadding,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ButtonVariant.Danger -> {
            Button(
                onClick = onClick,
                modifier = minSizeModifier,
                enabled = isClickable,
                shape = LcxPillShape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LcxError,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                ButtonContent(text = text, isLoading = isLoading)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    indicatorColor: Color = LocalContentColor.current,
) {
    if (isLoading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .semantics { contentDescription = "Cargando" },
                strokeWidth = 2.dp,
                color = indicatorColor,
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
