package com.cleanx.lcx.feature.printing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.printing.data.PrinterInfo
import com.cleanx.lcx.feature.printing.data.PrinterPreferences
import com.cleanx.lcx.feature.printing.data.SavedPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PrinterSettingsUiState(
    val savedPrinter: SavedPrinter? = null,
    val discoveredPrinters: List<PrinterInfo> = emptyList(),
    val printCopies: Int = 1,
    val autoConnect: Boolean = true,
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PrinterSettingsViewModel @Inject constructor(
    private val printRepository: PrintRepository,
    private val printerPreferences: PrinterPreferences,
) : ViewModel() {

    private val _transient = MutableStateFlow(TransientState())
    private val transient: StateFlow<TransientState> = _transient.asStateFlow()

    val uiState: StateFlow<PrinterSettingsUiState> = combine(
        printerPreferences.savedPrinter,
        printerPreferences.printCopies,
        printerPreferences.autoConnect,
        transient,
    ) { saved, copies, auto, t ->
        PrinterSettingsUiState(
            savedPrinter = saved,
            discoveredPrinters = t.discoveredPrinters,
            printCopies = copies,
            autoConnect = auto,
            isDiscovering = t.isDiscovering,
            isConnecting = t.isConnecting,
            isPrinting = t.isPrinting,
            message = t.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrinterSettingsUiState(),
    )

    fun discoverPrinters() {
        viewModelScope.launch {
            _transient.update { it.copy(isDiscovering = true, message = null, discoveredPrinters = emptyList()) }
            try {
                val printers = printRepository.discoverPrinters()
                _transient.update {
                    it.copy(
                        isDiscovering = false,
                        discoveredPrinters = printers,
                        message = if (printers.isEmpty()) "No se encontraron impresoras." else null,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Printer discovery failed")
                _transient.update {
                    it.copy(
                        isDiscovering = false,
                        message = "Error buscando impresoras: ${e.message}",
                    )
                }
            }
        }
    }

    fun selectPrinter(printer: PrinterInfo) {
        viewModelScope.launch {
            _transient.update { it.copy(isConnecting = true, message = null) }
            printRepository.selectPrinter(printer)
            try {
                val connected = printRepository.connectToSelected()
                if (connected) {
                    _transient.update {
                        it.copy(
                            isConnecting = false,
                            discoveredPrinters = emptyList(),
                            message = "Impresora conectada: ${printer.name}",
                        )
                    }
                } else {
                    _transient.update {
                        it.copy(
                            isConnecting = false,
                            message = "No se pudo conectar a ${printer.name}",
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection to printer failed")
                _transient.update {
                    it.copy(
                        isConnecting = false,
                        message = "Error de conexion: ${e.message}",
                    )
                }
            }
        }
    }

    fun setPrintCopies(copies: Int) {
        viewModelScope.launch {
            printerPreferences.setPrintCopies(copies)
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            printerPreferences.setAutoConnect(enabled)
        }
    }

    fun forgetPrinter() {
        viewModelScope.launch {
            printRepository.forgetPrinter()
            _transient.update {
                it.copy(message = "Impresora olvidada")
            }
        }
    }

    fun testPrint() {
        viewModelScope.launch {
            _transient.update { it.copy(isPrinting = true, message = null) }

            // Try to auto-connect if not already connected
            if (!printRepository.isConnected()) {
                val autoConnected = printRepository.tryAutoConnect()
                if (!autoConnected) {
                    _transient.update {
                        it.copy(
                            isPrinting = false,
                            message = "No hay impresora conectada. Seleccione una primero.",
                        )
                    }
                    return@launch
                }
            }

            val testLabel = LabelData(
                ticketNumber = "T-TEST-0000",
                customerName = "Prueba de impresion",
                serviceType = "test-print",
                date = "2026-03-02",
                dailyFolio = 0,
                ticketId = "test-print",
                promisedPickupDate = "2026-03-02",
                paymentLabel = "PAGO: PENDIENTE",
            )
            when (val result = printRepository.printWithRetry(testLabel)) {
                is PrintResult.Success -> {
                    _transient.update {
                        it.copy(isPrinting = false, message = "Prueba de impresion exitosa")
                    }
                }
                is PrintResult.Error -> {
                    _transient.update {
                        it.copy(isPrinting = false, message = "Error: ${result.message}")
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _transient.update { it.copy(message = null) }
    }

    fun onBluetoothPermissionDenied() {
        _transient.update {
            it.copy(
                message = "Permiso de dispositivos cercanos requerido para impresoras Bluetooth.",
            )
        }
    }

    private data class TransientState(
        val discoveredPrinters: List<PrinterInfo> = emptyList(),
        val isDiscovering: Boolean = false,
        val isConnecting: Boolean = false,
        val isPrinting: Boolean = false,
        val message: String? = null,
    )
}
