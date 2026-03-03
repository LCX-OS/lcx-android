package com.cleanx.lcx.feature.cash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.cash.data.BILL_DENOMINATIONS
import com.cleanx.lcx.feature.cash.data.COIN_DENOMINATIONS
import com.cleanx.lcx.feature.cash.data.CashMovementInsert
import com.cleanx.lcx.feature.cash.data.CashMovementMetadata
import com.cleanx.lcx.feature.cash.data.CashMovementRow
import com.cleanx.lcx.feature.cash.data.CashRepository
import com.cleanx.lcx.feature.cash.data.CashSummary
import com.cleanx.lcx.feature.cash.data.DenominationBreakdown
import com.cleanx.lcx.feature.cash.data.MovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class CashUiState(
    // Summary
    val isLoadingSummary: Boolean = true,
    val summary: CashSummary = CashSummary(),
    val summaryError: String? = null,

    // Form state
    val selectedType: MovementType = MovementType.OPENING,
    val billCounts: Map<Double, Int> = BILL_DENOMINATIONS.associate { it.value to 0 },
    val coinCounts: Map<Double, Int> = COIN_DENOMINATIONS.associate { it.value to 0 },
    val notes: String = "",
    val totalSalesForDay: String = "",
    val expenseAmount: String = "",

    // Submission
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,

    // History
    val selectedTab: Int = 0,
    val isLoadingHistory: Boolean = false,
    val history: List<CashMovementRow> = emptyList(),
    val historyError: String? = null,
) {
    val billsTotal: Double
        get() = BILL_DENOMINATIONS.sumOf { d -> d.value * (billCounts[d.value] ?: 0) }

    val coinsTotal: Double
        get() = COIN_DENOMINATIONS.sumOf { d -> d.value * (coinCounts[d.value] ?: 0) }

    val grandTotal: Double
        get() = if (selectedType == MovementType.EXPENSE) {
            expenseAmount.toDoubleOrNull() ?: 0.0
        } else {
            billsTotal + coinsTotal
        }

    val parsedTotalSalesForDay: Double?
        get() = totalSalesForDay.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    val expectedClosingFromSales: Double?
        get() {
            if (selectedType != MovementType.CLOSING || parsedTotalSalesForDay == null) return null
            return summary.openingAmount + (parsedTotalSalesForDay ?: 0.0) +
                summary.totalIncome - summary.totalExpenses
        }

    val discrepancyPreview: Double?
        get() {
            val expected = expectedClosingFromSales ?: return null
            return grandTotal - expected
        }

    /** Build DenominationBreakdown from current counts. */
    fun buildBreakdown(): DenominationBreakdown = DenominationBreakdown(
        bills1000 = billCounts[1000.0] ?: 0,
        bills500 = billCounts[500.0] ?: 0,
        bills200 = billCounts[200.0] ?: 0,
        bills100 = billCounts[100.0] ?: 0,
        bills50 = billCounts[50.0] ?: 0,
        bills20 = billCounts[20.0] ?: 0,
        coins20 = coinCounts[20.0] ?: 0,
        coins10 = coinCounts[10.0] ?: 0,
        coins5 = coinCounts[5.0] ?: 0,
        coins2 = coinCounts[2.0] ?: 0,
        coins1 = coinCounts[1.0] ?: 0,
        coins50c = coinCounts[0.5] ?: 0,
    )
}

@HiltViewModel
class CashViewModel @Inject constructor(
    private val repository: CashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
        loadHistory()
    }

    // -- Tab selection ---------------------------------------------------------

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    // -- Type selection --------------------------------------------------------

    fun selectType(type: MovementType) {
        _uiState.update { it.copy(selectedType = type, submitSuccess = false, submitError = null) }
    }

    // -- Form input handlers --------------------------------------------------

    fun onBillCountChange(denomination: Double, value: String) {
        val cleaned = value.filter { it.isDigit() }
        val count = cleaned.toIntOrNull() ?: 0
        _uiState.update { state ->
            state.copy(
                billCounts = state.billCounts.toMutableMap().apply { put(denomination, count) },
                submitSuccess = false,
            )
        }
    }

    fun onCoinCountChange(denomination: Double, value: String) {
        val cleaned = value.filter { it.isDigit() }
        val count = cleaned.toIntOrNull() ?: 0
        _uiState.update { state ->
            state.copy(
                coinCounts = state.coinCounts.toMutableMap().apply { put(denomination, count) },
                submitSuccess = false,
            )
        }
    }

    fun onNotesChange(text: String) {
        _uiState.update { it.copy(notes = text, submitSuccess = false) }
    }

    fun onTotalSalesForDayChange(text: String) {
        _uiState.update { it.copy(totalSalesForDay = text, submitSuccess = false) }
    }

    fun onExpenseAmountChange(text: String) {
        _uiState.update { it.copy(expenseAmount = text, submitSuccess = false) }
    }

    // -- Load summary ---------------------------------------------------------

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSummary = true, summaryError = null) }
            repository.getTodaySummary()
                .onSuccess { summary ->
                    val autoType = if (summary.movementCount == 0) {
                        MovementType.OPENING
                    } else {
                        _uiState.value.selectedType
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingSummary = false,
                            summary = summary,
                            selectedType = autoType,
                        )
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load cash summary")
                    _uiState.update {
                        it.copy(
                            isLoadingSummary = false,
                            summaryError = e.message ?: "Error al cargar resumen de caja",
                        )
                    }
                }
        }
    }

    // -- Load history ---------------------------------------------------------

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, historyError = null) }
            repository.getMovementHistory()
                .onSuccess { records ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            history = records,
                        )
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load cash movement history")
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            historyError = e.message ?: "Error al cargar historial",
                        )
                    }
                }
        }
    }

    // -- Submit movement ------------------------------------------------------

    fun submit() {
        val state = _uiState.value

        // Validation
        if (state.selectedType == MovementType.EXPENSE && state.notes.isBlank()) {
            _uiState.update {
                it.copy(submitError = "Por favor describe el gasto realizado")
            }
            return
        }

        if (state.selectedType == MovementType.CLOSING) {
            if (!state.summary.canClose) {
                _uiState.update {
                    it.copy(
                        submitError = state.summary.canCloseReason
                            ?: "No se puede cerrar la caja en este momento",
                    )
                }
                return
            }
            val salesForDay = state.parsedTotalSalesForDay
            if (salesForDay != null && salesForDay < 0) {
                _uiState.update {
                    it.copy(submitError = "Las ventas del dia no pueden ser negativas")
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null, submitSuccess = false) }

            val amount = state.grandTotal
            val previousBalance = state.summary.currentAmount
            val difference = when (state.selectedType) {
                MovementType.OPENING -> amount
                MovementType.INCOME -> amount
                MovementType.EXPENSE -> -amount
                MovementType.CLOSING -> amount - previousBalance
            }

            val metadata = when (state.selectedType) {
                MovementType.OPENING, MovementType.CLOSING -> {
                    val discrepancyType = if (state.selectedType == MovementType.CLOSING) {
                        state.discrepancyPreview?.let { disc ->
                            when {
                                disc > 0 -> "sobrante"
                                disc < 0 -> "faltante"
                                else -> "exacto"
                            }
                        }
                    } else {
                        null
                    }
                    CashMovementMetadata(
                        denominations = state.buildBreakdown(),
                        totalSalesForDay = state.parsedTotalSalesForDay,
                        expectedClosingAmount = state.expectedClosingFromSales,
                        discrepancyType = discrepancyType,
                    )
                }
                else -> null
            }

            val insert = CashMovementInsert(
                type = state.selectedType,
                amount = amount,
                previousBalance = previousBalance,
                difference = difference,
                notes = state.notes.takeIf { it.isNotBlank() },
                userId = null, // Repository handles current user
                metadata = metadata,
            )

            repository.recordMovement(insert)
                .onSuccess {
                    Timber.d("Cash movement recorded: %s $%.2f", state.selectedType.name, amount)
                    val nextType = if (state.selectedType == MovementType.OPENING) {
                        MovementType.EXPENSE
                    } else {
                        state.selectedType
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitSuccess = true,
                            selectedType = nextType,
                        )
                    }
                    clearForm()
                    loadSummary()
                    loadHistory()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to record cash movement")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = e.message ?: "Error al guardar movimiento",
                        )
                    }
                }
        }
    }

    // -- Clear form -----------------------------------------------------------

    fun clearForm() {
        _uiState.update { state ->
            state.copy(
                billCounts = BILL_DENOMINATIONS.associate { it.value to 0 },
                coinCounts = COIN_DENOMINATIONS.associate { it.value to 0 },
                notes = "",
                totalSalesForDay = "",
                expenseAmount = "",
            )
        }
    }

    // -- Error / success clearing ---------------------------------------------

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun clearSubmitError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun clearSummaryError() {
        _uiState.update { it.copy(summaryError = null) }
    }

    fun clearHistoryError() {
        _uiState.update { it.copy(historyError = null) }
    }
}
