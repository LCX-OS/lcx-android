package com.cleanx.lcx.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxError
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

    when (variant) {
        ButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = isClickable,
            ) {
                ButtonContent(text = text, isLoading = isLoading)
            }
        }

        ButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = isClickable,
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
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = isClickable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LcxError,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
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
    indicatorColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    if (isLoading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = "Cargando" },
                strokeWidth = 2.dp,
                color = if (indicatorColor != androidx.compose.ui.graphics.Color.Unspecified) {
                    indicatorColor
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
