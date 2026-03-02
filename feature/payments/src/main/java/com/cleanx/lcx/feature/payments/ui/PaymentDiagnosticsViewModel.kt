package com.cleanx.lcx.feature.payments.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.config.FeatureFlags
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.PaymentResult
import com.cleanx.lcx.feature.payments.data.SimulatedScenario
import com.cleanx.lcx.feature.payments.data.StubPaymentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DiagnosticsUiState(
    val useRealZettle: Boolean = false,
    val isInitialized: Boolean = false,
    val currentScenario: String = "Random",
    val isProcessing: Boolean = false,
    val lastResult: String? = null,
)

@HiltViewModel
class PaymentDiagnosticsViewModel @Inject constructor(
    private val paymentManager: PaymentManager,
    private val featureFlags: FeatureFlags,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DiagnosticsUiState(
            useRealZettle = featureFlags.useRealZettle,
            isInitialized = paymentManager.isInitialized(),
            currentScenario = currentScenarioName(),
        ),
    )
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    fun setScenario(name: String) {
        val stub = paymentManager as? StubPaymentManager ?: return
        val scenario = when (name) {
            "AlwaysSuccess" -> SimulatedScenario.AlwaysSuccess
            "AlwaysCancelled" -> SimulatedScenario.AlwaysCancelled
            "AlwaysFailed" -> SimulatedScenario.AlwaysFailed
            else -> SimulatedScenario.Random
        }
        stub.scenario = scenario
        Timber.d("[DIAG] Scenario changed to %s", scenario)
        _uiState.update { it.copy(currentScenario = scenario.name) }
    }

    fun triggerTestPayment() {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, lastResult = null) }
            try {
                val result = paymentManager.requestPayment(
                    amount = 1.00,
                    reference = "diag-test",
                )
                val display = when (result) {
                    is PaymentResult.Success ->
                        "Exito: txn=${result.transactionId.take(8)}... monto=${result.amount}"
                    is PaymentResult.Cancelled ->
                        "Cancelado"
                    is PaymentResult.Failed ->
                        "Error: ${result.errorCode} — ${result.message}"
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        lastResult = display,
                        isInitialized = paymentManager.isInitialized(),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[DIAG] Test payment threw exception")
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        lastResult = "Excepcion: ${e.message}",
                    )
                }
            }
        }
    }

    private fun currentScenarioName(): String {
        val stub = paymentManager as? StubPaymentManager
        return stub?.scenario?.name ?: "N/A (SDK real)"
    }
}
