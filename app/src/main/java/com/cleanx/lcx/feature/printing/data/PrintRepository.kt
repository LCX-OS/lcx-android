package com.cleanx.lcx.feature.printing.data

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates printer discovery, connection management, and label printing
 * with automatic retry logic.
 *
 * Design invariant: printing is a **non-blocking** side-effect. A failure
 * here must NEVER corrupt ticket or payment state. The UI always offers a
 * "skip" option.
 */
@Singleton
class PrintRepository @Inject constructor(
    private val printerManager: PrinterManager,
) {

    /** Most recently selected printer, cached across prints. */
    private var selectedPrinter: PrinterInfo? = null

    // -- Discovery ------------------------------------------------------------

    suspend fun discoverPrinters(): List<PrinterInfo> {
        return printerManager.discoverPrinters()
    }

    // -- Selection / connection -----------------------------------------------

    fun selectPrinter(printer: PrinterInfo) {
        selectedPrinter = printer
    }

    fun getSelectedPrinter(): PrinterInfo? = selectedPrinter

    suspend fun connectToSelected(): Boolean {
        val printer = selectedPrinter
            ?: run {
                Timber.w("PrintRepository: no printer selected.")
                return false
            }
        return printerManager.connect(printer)
    }

    fun isConnected(): Boolean = printerManager.isConnected()

    fun disconnect() {
        printerManager.disconnect()
    }

    // -- Printing with retry --------------------------------------------------

    /**
     * Attempts to print [label] up to [maxAttempts] times.
     *
     * Returns [PrintResult.Success] as soon as one attempt succeeds, or the
     * last [PrintResult.Error] if all attempts fail.
     */
    suspend fun printWithRetry(
        label: LabelData,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
    ): PrintResult {
        var lastError: PrintResult.Error? = null

        repeat(maxAttempts) { attempt ->
            Timber.d("PrintRepository: attempt ${attempt + 1}/$maxAttempts")
            when (val result = printerManager.print(label)) {
                is PrintResult.Success -> return result
                is PrintResult.Error -> {
                    Timber.w("PrintRepository: attempt ${attempt + 1} failed – ${result.message}")
                    lastError = result
                }
            }
        }

        return lastError ?: PrintResult.Error(
            code = "UNKNOWN",
            message = "Impresion fallida despues de $maxAttempts intentos.",
        )
    }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
