package com.cleanx.lcx.feature.printing.data

import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates printer discovery, connection management, and label printing
 * with automatic retry logic and persistence of the selected printer.
 *
 * Design invariant: printing is a **non-blocking** side-effect. A failure
 * here must NEVER corrupt ticket or payment state. The UI always offers a
 * "skip" option.
 */
@Singleton
class PrintRepository @Inject constructor(
    private val printerManager: PrinterManager,
    private val printerPreferences: PrinterPreferences,
) {

    /** Most recently selected printer, cached across prints within a session. */
    private var selectedPrinter: PrinterInfo? = null

    // -- Discovery ------------------------------------------------------------

    suspend fun discoverPrinters(): List<PrinterInfo> {
        Timber.tag("PRINT").d("Discovering printers...")
        return printerManager.discoverPrinters()
    }

    // -- Auto-connect ---------------------------------------------------------

    /**
     * Attempts to reconnect to the previously saved printer if [autoConnect] is
     * enabled and a saved printer exists.
     *
     * @return `true` if auto-connect succeeded (printer is ready to print);
     *         `false` otherwise (caller should fall through to manual discovery).
     */
    suspend fun tryAutoConnect(): Boolean {
        val autoConnect = printerPreferences.autoConnect.first()
        if (!autoConnect) {
            Timber.d("PrintRepository: auto-connect disabled")
            return false
        }

        val saved = printerPreferences.savedPrinter.first() ?: run {
            Timber.d("PrintRepository: no saved printer")
            return false
        }

        Timber.d("PrintRepository: auto-connecting to saved printer %s", saved.name)
        val printer = saved.toPrinterInfo()
        selectedPrinter = printer

        return try {
            val connected = printerManager.connect(printer)
            if (connected) {
                Timber.d("PrintRepository: auto-connect succeeded")
            } else {
                Timber.w("PrintRepository: auto-connect failed — printer unreachable")
            }
            connected
        } catch (e: Exception) {
            Timber.e(e, "PrintRepository: auto-connect threw")
            false
        }
    }

    // -- Selection / connection -----------------------------------------------

    fun selectPrinter(printer: PrinterInfo) {
        selectedPrinter = printer
    }

    fun getSelectedPrinter(): PrinterInfo? = selectedPrinter

    /**
     * Connects to the currently selected printer and persists it on success.
     */
    suspend fun connectToSelected(): Boolean {
        val printer = selectedPrinter
            ?: run {
                Timber.w("PrintRepository: no printer selected.")
                return false
            }
        Timber.tag("PRINT").d("Connecting to: %s (%s)", printer.name, printer.address)
        val connected = printerManager.connect(printer)
        if (connected) {
            printerPreferences.savePrinter(printer)
        }
        return connected
    }

    fun isConnected(): Boolean = printerManager.isConnected()

    fun disconnect() {
        printerManager.disconnect()
    }

    // -- Printer preferences shortcuts ----------------------------------------

    suspend fun forgetPrinter() {
        disconnect()
        selectedPrinter = null
        printerPreferences.clearPrinter()
    }

    // -- Printing with retry --------------------------------------------------

    /**
     * Attempts to print [label] up to [maxAttempts] times, respecting the
     * configured number of copies.
     *
     * Returns [PrintResult.Success] as soon as one attempt succeeds, or the
     * last [PrintResult.Error] if all attempts fail.
     */
    suspend fun printWithRetry(
        label: LabelData,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
    ): PrintResult {
        val copies = printerPreferences.printCopies.first()

        repeat(copies) { copy ->
            Timber.d("PrintRepository: printing copy ${copy + 1}/$copies")
            val result = printSingleCopy(label, maxAttempts)
            if (result is PrintResult.Error) {
                return result
            }
        }

        return PrintResult.Success
    }

    private suspend fun printSingleCopy(
        label: LabelData,
        maxAttempts: Int,
    ): PrintResult {
        var lastError: PrintResult.Error? = null

        repeat(maxAttempts) { attempt ->
            Timber.tag("PRINT").d("Printing label: ticketNumber=%s, attempt=%d/%d", label.ticketNumber, attempt + 1, maxAttempts)
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
