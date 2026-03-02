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
    private val ticketId: String = savedStateHandle["ticketId"] ?: ""

    // Label data is set externally before printing (from the ticket detail).
    private var labelData: LabelData? = null

    /** Provide the label content that should be printed. */
    fun setLabelData(data: LabelData) {
        labelData = data
    }

    // -- Actions --------------------------------------------------------------

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
            val label = labelData
            if (label == null) {
                _uiState.update {
                    it.copy(
                        phase = PrintPhase.ERROR,
                        errorMessage = "No hay datos de etiqueta.",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(phase = PrintPhase.PRINTING) }
            when (val result = printRepository.printWithRetry(label)) {
                is PrintResult.Success -> {
                    _uiState.update { it.copy(phase = PrintPhase.SUCCESS) }
                }
                is PrintResult.Error -> {
                    _uiState.update {
                        it.copy(
                            phase = PrintPhase.ERROR,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        printRepository.disconnect()
    }
}
