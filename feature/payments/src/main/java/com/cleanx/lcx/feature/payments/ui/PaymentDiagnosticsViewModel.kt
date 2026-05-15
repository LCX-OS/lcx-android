package com.cleanx.lcx.feature.payments.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.PaymentBackendType
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
    val backendLabel: String = "",
    val backendStatusMessage: String = "",
    val backendType: PaymentBackendType = PaymentBackendType.STUB,
    val canAcceptPayments: Boolean = false,
    val isInitialized: Boolean = false,
    val currentScenario: String? = null,
    val isProcessing: Boolean = false,
    val lastResult: String? = null,
)

@HiltViewModel
class PaymentDiagnosticsViewModel @Inject constructor(
    private val paymentManager: PaymentManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        paymentManager.capability().toUiState(),
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
        refreshCapability()
    }

    fun triggerTestPayment() {
        if (_uiState.value.isProcessing) return
        val capability = paymentManager.capability()
        if (!capability.canAcceptPayments) {
            refreshCapability(lastResult = "No disponible: ${capability.statusMessage}")
            return
        }
        if (!capability.isInitialized) {
            refreshCapability(
                lastResult = "No inicializado: ${capability.backendLabel}. Reabre la app e intenta de nuevo.",
            )
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, lastResult = null) }
            try {
                val result = paymentManager.requestPayment(
                    amount = 1.00,
                    reference = "diag-test",
                )
                val display = when (result) {
                    is PaymentResult.Success -> {
                        Timber.tag("PAYMENT").i(
                            "Zettle diagnostic charge success transactionId=%s amount=%s",
                            result.transactionId,
                            result.amount,
                        )
                        "Exito: txn=${result.transactionId.take(8)}... monto=${result.amount}"
                    }
                    is PaymentResult.Cancelled -> {
                        Timber.tag("PAYMENT").w("Zettle diagnostic charge cancelled")
                        "Cancelado"
                    }
                    is PaymentResult.Failed -> {
                        Timber.tag("PAYMENT").e(
                            "Zettle diagnostic charge failed code=%s message=%s",
                            result.errorCode,
                            result.message,
                        )
                        "Error: ${result.errorCode} — ${result.message}"
                    }
                }
                refreshCapability(lastResult = display)
            } catch (e: Exception) {
                Timber.e(e, "[DIAG] Test payment threw exception")
                refreshCapability(lastResult = "Excepcion: ${e.message}")
            }
        }
    }

    private fun refreshCapability(lastResult: String? = _uiState.value.lastResult) {
        _uiState.update {
            paymentManager.capability().toUiState(
                isProcessing = false,
                lastResult = lastResult,
            )
        }
    }
}

private fun com.cleanx.lcx.feature.payments.data.PaymentCapability.toUiState(
    isProcessing: Boolean = false,
    lastResult: String? = null,
): DiagnosticsUiState = DiagnosticsUiState(
    backendLabel = backendLabel,
    backendStatusMessage = statusMessage,
    backendType = backendType,
    canAcceptPayments = canAcceptPayments,
    isInitialized = isInitialized,
    currentScenario = currentScenario,
    isProcessing = isProcessing,
    lastResult = lastResult,
)
