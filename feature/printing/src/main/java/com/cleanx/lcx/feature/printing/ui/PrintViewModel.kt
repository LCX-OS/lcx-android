package com.cleanx.lcx.feature.printing.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.printing.data.PrinterInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// -- UI State -----------------------------------------------------------------

data class PrintUiState(
    val phase: PrintPhase = PrintPhase.IDLE,
    val printers: List<PrinterInfo> = emptyList(),
    val selectedPrinter: PrinterInfo? = null,
    val errorMessage: String? = null,
    val labelCount: Int = 1,
    /** Set to true once the user decides to skip or printing succeeds. */
    val finished: Boolean = false,
)

enum class PrintPhase {
    IDLE,
    DISCOVERING,
    SELECTING,
    CONNECTING,
    PRINTING,
    SUCCESS,
    ERROR,
}

// -- ViewModel ----------------------------------------------------------------

@HiltViewModel
class PrintViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val printRepository: PrintRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrintUiState())
    val uiState: StateFlow<PrintUiState> = _uiState.asStateFlow()

    // Navigation argument — the ticketId is embedded in the route by LcxNavHost.
    val ticketId: String = savedStateHandle["ticketId"] ?: ""

    // Label data is set externally before printing (from the ticket detail).
    private var labelData: LabelData? = null
    private var labelsToPrint: List<LabelData> = emptyList()

    /** Provide the label content that should be printed. */
    fun setLabelData(data: LabelData) {
        labelData = data
        labelsToPrint = emptyList()
        _uiState.update { it.copy(labelCount = LabelData.DEFAULT_TOTAL_BAGS, errorMessage = null) }
    }

    fun prepareLabels(totalBags: Int) {
        val base = labelData
        if (base == null) {
            _uiState.update {
                it.copy(
                    phase = PrintPhase.ERROR,
                    errorMessage = "No hay datos de etiqueta.",
                )
            }
            return
        }

        val safeTotal = totalBags.coerceIn(LabelData.MIN_BAG_COUNT, LabelData.MAX_BAG_COUNT)
        labelsToPrint = (1..safeTotal).map { bagNumber ->
            base.forBag(bagNumber = bagNumber, totalBags = safeTotal)
        }
        _uiState.update { it.copy(labelCount = labelsToPrint.size, errorMessage = null) }
    }

    // -- Actions --------------------------------------------------------------

    /**
     * Try auto-connect first; if successful, skip discovery and print directly.
     * Falls back to full discovery if auto-connect fails.
     */
    fun autoConnectOrDiscover() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = PrintPhase.CONNECTING, errorMessage = null) }
            try {
                val autoConnected = printRepository.tryAutoConnect()
                if (autoConnected) {
                    Timber.tag("PRINT").i("Auto-connect succeeded, proceeding to print")
                    val saved = printRepository.getSelectedPrinter()
                    _uiState.update { it.copy(selectedPrinter = saved) }
                    printLabel()
                    return@launch
                }
            } catch (e: Exception) {
                Timber.tag("PRINT").w(e, "Auto-connect failed, falling back to discovery")
            }
            // Fall back to normal discovery
            discoverPrinters()
        }
    }

    fun discoverPrinters() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = PrintPhase.DISCOVERING, errorMessage = null) }
            try {
                val printers = printRepository.discoverPrinters()
                if (printers.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            phase = PrintPhase.ERROR,
                            errorMessage = "No se encontraron impresoras.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(phase = PrintPhase.SELECTING, printers = printers)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Printer discovery failed")
                _uiState.update {
                    it.copy(
                        phase = PrintPhase.ERROR,
                        errorMessage = "Error buscando impresoras: ${e.message}",
                    )
                }
            }
        }
    }

    fun selectPrinter(printer: PrinterInfo) {
        printRepository.selectPrinter(printer)
        _uiState.update { it.copy(selectedPrinter = printer) }
        connectAndPrint()
    }

    fun retry() {
        val printer = _uiState.value.selectedPrinter
        if (printer != null) {
            connectAndPrint()
        } else {
            discoverPrinters()
        }
    }

    fun skip() {
        printRepository.disconnect()
        _uiState.update { it.copy(phase = PrintPhase.IDLE, finished = true) }
    }

    fun onBluetoothPermissionDenied() {
        _uiState.update {
            it.copy(
                phase = PrintPhase.ERROR,
                errorMessage = "Permiso de dispositivos cercanos requerido para impresoras Bluetooth.",
            )
        }
    }

    // -- Private helpers ------------------------------------------------------

    private fun connectAndPrint() {
        viewModelScope.launch {
            // 1. Connect
            _uiState.update { it.copy(phase = PrintPhase.CONNECTING, errorMessage = null) }
            try {
                val connected = if (printRepository.isConnected()) {
                    true
                } else {
                    printRepository.connectToSelected()
                }
                if (!connected) {
                    _uiState.update {
                        it.copy(
                            phase = PrintPhase.ERROR,
                            errorMessage = "No se pudo conectar a la impresora.",
                        )
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection failed")
                _uiState.update {
                    it.copy(
                        phase = PrintPhase.ERROR,
                        errorMessage = "Error de conexion: ${e.message}",
                    )
                }
                return@launch
            }

            // 2. Print
            printLabel()
        }
    }

    private suspend fun printLabel() {
        val labels = labelsToPrint.ifEmpty {
            labelData?.let { listOf(it.forBag(LabelData.DEFAULT_BAG_NUMBER, LabelData.DEFAULT_TOTAL_BAGS)) }
                ?: emptyList()
        }
        if (labels.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = PrintPhase.ERROR,
                    errorMessage = "No hay datos de etiqueta.",
                )
            }
            return
        }

        _uiState.update { it.copy(phase = PrintPhase.PRINTING) }
        labels.forEach { label ->
            when (val result = printRepository.printWithRetry(label)) {
                is PrintResult.Success -> Unit
                is PrintResult.Error -> {
                    _uiState.update {
                        it.copy(
                            phase = PrintPhase.ERROR,
                            errorMessage = "Bolsa ${label.bagNumber}/${label.totalBags}: ${result.message}",
                        )
                    }
                    return
                }
            }
        }
        _uiState.update { it.copy(phase = PrintPhase.SUCCESS, labelCount = labels.size) }
    }

    override fun onCleared() {
        super.onCleared()
        printRepository.disconnect()
    }
}
