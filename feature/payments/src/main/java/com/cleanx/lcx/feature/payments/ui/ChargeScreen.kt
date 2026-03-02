package com.cleanx.lcx.feature.payments.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LoadingOverlay

@Composable
fun ChargeScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPrint: (String) -> Unit = {},
    viewModel: ChargeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val haptic = LocalHapticFeedback.current

    // Disable back button during processing
    BackHandler(enabled = state.phase is ChargePhase.Processing) {
        // Block back navigation during processing — do nothing
    }

    // Haptic feedback on successful charge
    LaunchedEffect(state.phase) {
        if (state.phase is ChargePhase.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LcxSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.phase) {
                is ChargePhase.Idle -> IdleContent(
                    state = state,
                    onCharge = viewModel::startCharge,
                    onCancel = onNavigateBack,
                )

                is ChargePhase.Processing -> {
                    // Content behind the loading overlay
                    Text(
                        text = "$${String.format("%.2f", state.amount)}",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(LcxSpacing.md))
                    Text(
                        text = "Siga las instrucciones del lector de tarjetas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                is ChargePhase.Success -> SuccessContent(
                    amount = state.amount,
                    transactionId = state.transactionId,
                    onContinue = { onNavigateToPrint(state.ticketId) },
                    onBack = onNavigateBack,
                )

                is ChargePhase.Cancelled -> CancelledContent(
                    onRetry = viewModel::retry,
                    onBack = onNavigateBack,
                )

                is ChargePhase.Failed -> FailedContent(
                    message = state.errorMessage,
                    onRetry = viewModel::retry,
                    onBack = onNavigateBack,
                )

                is ChargePhase.PaymentSucceededApiCallFailed -> ApiFailedContent(
                    amount = state.amount,
                    transactionId = state.transactionId,
                    message = state.errorMessage,
                    onRetry = viewModel::retry,
                    onBack = onNavigateBack,
                )
            }
        }

        // Loading overlay on top
        if (state.phase is ChargePhase.Processing) {
            LoadingOverlay(message = "Procesando cobro...")
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-screens
// ---------------------------------------------------------------------------

@Composable
private fun IdleContent(
    state: ChargeUiState,
    onCharge: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "Cobro con tarjeta",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )

    Spacer(Modifier.height(LcxSpacing.lg))

    LcxCard {
        if (state.customerName.isNotBlank()) {
            Text(
                text = "Cliente: ${state.customerName}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(LcxSpacing.xs))
        }
        Text(
            text = "Ticket: ${state.ticketId.take(8)}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(LcxSpacing.xl))

    Text(
        text = "$${String.format("%.2f", state.amount)}",
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(Modifier.height(LcxSpacing.xl))

    LcxButton(
        text = "Cobrar",
        onClick = onCharge,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(LcxSpacing.sm))

    LcxButton(
        text = "Cancelar",
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
    )
}

@Composable
private fun SuccessContent(
    amount: Double,
    transactionId: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.displayLarge,
                color = LcxSuccess,
            )
            Spacer(Modifier.height(LcxSpacing.md))
            Text(
                text = "Cobro exitoso",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(LcxSpacing.sm))
            Text(
                text = "$${String.format("%.2f", amount)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (transactionId != null) {
                Spacer(Modifier.height(LcxSpacing.xs))
                Text(
                    text = "Transaccion: ${transactionId.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(LcxSpacing.xl))

            LcxButton(
                text = "Imprimir etiqueta",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(LcxSpacing.sm))

            LcxButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun CancelledContent(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Cobro cancelado",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(LcxSpacing.sm))
    Text(
        text = "No se realizo ningun cargo a la tarjeta.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(LcxSpacing.xl))

    LcxButton(
        text = "Reintentar",
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(LcxSpacing.sm))

    LcxButton(
        text = "Volver",
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
    )
}

@Composable
private fun FailedContent(
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Error en el cobro",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(LcxSpacing.sm))
    Text(
        text = mapErrorToUserMessage(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(LcxSpacing.xs))
    Text(
        text = "No se realizo ningun cargo a la tarjeta.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(LcxSpacing.xl))

    LcxButton(
        text = "Reintentar",
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(LcxSpacing.sm))

    LcxButton(
        text = "Volver",
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
    )
}

@Composable
private fun ApiFailedContent(
    amount: Double,
    transactionId: String?,
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Atencion",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(LcxSpacing.sm))

    LcxCard {
        Text(
            text = "El cobro de $${String.format("%.2f", amount)} se realizo exitosamente, " +
                "pero no se pudo registrar en el sistema.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (transactionId != null) {
            Spacer(Modifier.height(LcxSpacing.sm))
            Text(
                text = "ID transaccion: $transactionId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    Spacer(Modifier.height(LcxSpacing.sm))

    if (message != null) {
        Text(
            text = mapErrorToUserMessage(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(LcxSpacing.sm))
    }

    Text(
        text = "Presione Reintentar para sincronizar con el servidor.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(LcxSpacing.xl))

    LcxButton(
        text = "Reintentar sincronizacion",
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Danger,
    )

    Spacer(Modifier.height(LcxSpacing.sm))

    LcxButton(
        text = "Volver (el cargo YA fue realizado)",
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
        variant = ButtonVariant.Secondary,
    )
}

/**
 * Maps raw error messages to user-friendly Spanish text.
 * Prevents showing raw exception messages to field operators.
 */
private fun mapErrorToUserMessage(raw: String?): String {
    if (raw == null) return "Ocurrio un error desconocido."
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") || lower.contains("timed out") ->
            "La operacion tardo demasiado. Verifique la conexion e intente de nuevo."
        lower.contains("network") || lower.contains("connect") || lower.contains("unreachable") ||
            lower.contains("no address") || lower.contains("socket") ->
            "Sin conexion. Verifique la red e intente de nuevo."
        lower.contains("cancelled") || lower.contains("canceled") ->
            "La operacion fue cancelada."
        lower.contains("bluetooth") ->
            "Error de conexion Bluetooth con el lector."
        lower.contains("declined") || lower.contains("rechaz") ->
            "Tarjeta rechazada. Intente con otro metodo de pago."
        else -> raw
    }
}
