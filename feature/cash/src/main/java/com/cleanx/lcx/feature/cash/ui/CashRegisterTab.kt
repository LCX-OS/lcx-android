package com.cleanx.lcx.feature.cash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.feature.cash.data.BILL_DENOMINATIONS
import com.cleanx.lcx.feature.cash.data.COIN_DENOMINATIONS
import com.cleanx.lcx.feature.cash.data.MovementType
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashRegisterTab(
    state: CashUiState,
    onSelectType: (MovementType) -> Unit,
    onBillCountChange: (Double, String) -> Unit,
    onCoinCountChange: (Double, String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTotalSalesForDayChange: (String) -> Unit,
    onExpenseAmountChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onClearSubmitSuccess: () -> Unit,
    onClearSubmitError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    // Success snackbar
    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) {
            snackbarHostState.showSnackbar(
                message = "Movimiento registrado correctamente",
                duration = SnackbarDuration.Short,
            )
            onClearSubmitSuccess()
        }
    }

    // Error snackbar
    LaunchedEffect(state.submitError) {
        if (state.submitError != null) {
            snackbarHostState.showSnackbar(
                message = state.submitError,
                duration = SnackbarDuration.Short,
            )
            onClearSubmitError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LcxSpacing.md, vertical = LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
        ) {
            // Summary card
            SummaryCard(summary = state.summary, currencyFormat = currencyFormat)

            // Warning when no movements today
            if (state.summary.movementCount == 0) {
                NoMovementsWarning()
            }

            // Type selector
            LcxCard(title = "Tipo de Movimiento") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                ) {
                    FilterChip(
                        selected = state.selectedType == MovementType.OPENING,
                        onClick = { onSelectType(MovementType.OPENING) },
                        label = { Text("Apertura") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = state.selectedType == MovementType.CLOSING,
                        onClick = { onSelectType(MovementType.CLOSING) },
                        label = { Text("Cierre") },
                        enabled = state.summary.canClose,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = state.selectedType == MovementType.EXPENSE,
                        onClick = { onSelectType(MovementType.EXPENSE) },
                        label = { Text("Gasto") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }

            // Denomination counting (for Opening and Closing)
            if (state.selectedType != MovementType.EXPENSE) {
                // Bills section
                LcxCard(title = "Billetes") {
                    DenominationGrid(
                        denominations = BILL_DENOMINATIONS,
                        counts = state.billCounts,
                        onCountChange = onBillCountChange,
                    )
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Subtotal Billetes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = currencyFormat.format(state.billsTotal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Coins section
                LcxCard(title = "Monedas") {
                    DenominationGrid(
                        denominations = COIN_DENOMINATIONS,
                        counts = state.coinCounts,
                        onCountChange = onCoinCountChange,
                    )
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Subtotal Monedas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = currencyFormat.format(state.coinsTotal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Expense amount input
            if (state.selectedType == MovementType.EXPENSE) {
                LcxCard(title = "Monto del Gasto") {
                    LcxTextField(
                        value = state.expenseAmount,
                        onValueChange = onExpenseAmountChange,
                        label = "Monto ($)",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Grand total
            LcxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Total en Caja",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currencyFormat.format(state.grandTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Closing: sales for day + discrepancy
            if (state.selectedType == MovementType.CLOSING) {
                LcxCard(title = "Resumen de Cierre") {
                    LcxTextField(
                        value = state.totalSalesForDay,
                        onValueChange = onTotalSalesForDayChange,
                        label = "Ventas del dia (opcional)",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Discrepancy preview
                    AnimatedVisibility(visible = state.discrepancyPreview != null) {
                        val discrepancy = state.discrepancyPreview ?: 0.0
                        val discrepancyColor = when {
                            discrepancy > 0 -> LcxSuccess
                            discrepancy < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val discrepancyLabel = when {
                            discrepancy > 0 -> "Sobrante"
                            discrepancy < 0 -> "Faltante"
                            else -> "Exacto"
                        }

                        Column(modifier = Modifier.padding(top = LcxSpacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Monto esperado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = currencyFormat.format(
                                        state.expectedClosingFromSales ?: 0.0,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(LcxSpacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Diferencia ($discrepancyLabel)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = discrepancyColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = currencyFormat.format(discrepancy),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = discrepancyColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Notes field
            LcxCard(
                title = if (state.selectedType == MovementType.EXPENSE) {
                    "Descripcion del Gasto *"
                } else {
                    "Notas del Turno (Opcional)"
                },
            ) {
                LcxTextField(
                    value = state.notes,
                    onValueChange = onNotesChange,
                    label = if (state.selectedType == MovementType.EXPENSE) {
                        "Descripcion del gasto"
                    } else {
                        "Notas"
                    },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                LcxButton(
                    text = "Limpiar",
                    onClick = onClear,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                LcxButton(
                    text = if (state.isSubmitting) "Guardando..." else "Guardar",
                    onClick = onSubmit,
                    isLoading = state.isSubmitting,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(LcxSpacing.lg))
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(LcxSpacing.md),
        )
    }
}

@Composable
private fun SummaryCard(
    summary: com.cleanx.lcx.feature.cash.data.CashSummary,
    currencyFormat: NumberFormat,
) {
    LcxCard(title = "Caja del Dia") {
        Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
            SummaryRow(label = "Apertura", value = currencyFormat.format(summary.openingAmount))
            SummaryRow(label = "Saldo Actual", value = currencyFormat.format(summary.currentAmount))
            SummaryRow(label = "Ingresos", value = currencyFormat.format(summary.totalIncome))
            SummaryRow(label = "Egresos", value = currencyFormat.format(summary.totalExpenses))
            SummaryRow(label = "Movimientos", value = summary.movementCount.toString())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NoMovementsWarning() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(LcxSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Alerta",
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "No hay movimientos registrados hoy. Debes realizar la apertura de caja.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DenominationGrid(
    denominations: List<com.cleanx.lcx.feature.cash.data.Denomination>,
    counts: Map<Double, Int>,
    onCountChange: (Double, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
        // Two items per row
        denominations.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                rowItems.forEach { denomination ->
                    val count = counts[denomination.value] ?: 0
                    LcxTextField(
                        value = if (count == 0) "" else count.toString(),
                        onValueChange = { onCountChange(denomination.value, it) },
                        label = denomination.label,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill remaining space if odd number of items in last row
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
