package com.cleanx.lcx.feature.printing.data

import kotlinx.coroutines.delay
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
            Timber.tag("PRINT").d("Auto-connect disabled")
            return false
        }

        val saved = printerPreferences.savedPrinter.first() ?: run {
            Timber.tag("PRINT").d("No saved printer for auto-connect")
            return false
        }

        Timber.tag("PRINT").d("Auto-connecting to saved printer %s (%s)", saved.name, saved.address)
        val printer = saved.toPrinterInfo()
        selectedPrinter = printer

        return try {
            val connected = printerManager.connect(printer)
            if (connected) {
                Timber.tag("PRINT").i("Auto-connect succeeded: %s", saved.name)
            } else {
                Timber.tag("PRINT").w("Auto-connect failed — printer unreachable: %s", saved.address)
            }
            connected
        } catch (e: Exception) {
            Timber.tag("PRINT").e(e, "Auto-connect threw for %s", saved.address)
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
                Timber.tag("PRINT").w("No printer selected for connect.")
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

    // -- Ensure connected -----------------------------------------------------

    /**
     * Ensures a printer is connected before printing.
     *
     * Strategy:
     * 1. If already connected, return true immediately.
     * 2. If a printer is selected (from current session), reconnect.
     * 3. Otherwise, try auto-connect from saved preferences.
     *
     * @return `true` if a printer is ready; `false` if no connection could
     *         be established (caller should fall back to manual discovery).
     */
    suspend fun ensureConnected(): Boolean {
        if (printerManager.isConnected()) {
            Timber.tag("PRINT").d("ensureConnected: already connected")
            return true
        }

        // Try reconnecting to the currently selected printer first
        val selected = selectedPrinter
        if (selected != null) {
            Timber.tag("PRINT").d("ensureConnected: reconnecting to selected printer %s", selected.name)
            val reconnected = runCatching { printerManager.connect(selected) }.getOrDefault(false)
            if (reconnected) {
                Timber.tag("PRINT").i("ensureConnected: reconnected to %s", selected.name)
                return true
            }
            Timber.tag("PRINT").w("ensureConnected: reconnect to %s failed", selected.name)
        }

        // Fall back to auto-connect from saved preferences
        return tryAutoConnect()
    }

    // -- Printing with retry --------------------------------------------------

    /**
     * Attempts to print [label] up to [maxAttempts] times, respecting the
     * configured number of copies.
     *
     * Automatically ensures a printer is connected before the first attempt.
     * Between failed attempts, applies exponential backoff (500ms, 1s, 2s).
     *
     * Returns [PrintResult.Success] as soon as one attempt succeeds, or the
     * last [PrintResult.Error] if all attempts fail.
     */
    suspend fun printWithRetry(
        label: LabelData,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
    ): PrintResult {
        // Ensure printer is connected before attempting to print
        if (!ensureConnected()) {
            Timber.tag("PRINT").w("printWithRetry: no printer connected and auto-connect failed")
            return PrintResult.Error(
                code = "PRINTER_NOT_CONNECTED",
                message = "No hay impresora conectada. Configure una impresora en ajustes.",
            )
        }

        val copies = printerPreferences.printCopies.first()

        repeat(copies) { copy ->
            Timber.tag("PRINT").d("Printing copy %d/%d", copy + 1, copies)
            val result = printSingleCopy(
                label.copy(copyNumber = label.copyNumber + copy),
                maxAttempts,
            )
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
            Timber.tag("PRINT").d(
                "Printing label: ticketNumber=%s, attempt=%d/%d",
                label.ticketNumber, attempt + 1, maxAttempts,
            )

            // On retry (not first attempt), apply backoff and reconnect if needed
            if (attempt > 0) {
                val backoffMs = RETRY_BASE_DELAY_MS * (1L shl attempt.coerceAtMost(3))
                Timber.tag("PRINT").d("Retry backoff: %dms before attempt %d", backoffMs, attempt + 1)
                delay(backoffMs)

                if (!printerManager.isConnected()) {
                    Timber.tag("PRINT").d("Connection lost, attempting reconnect before retry")
                    ensureConnected()
                }
            }

            when (val result = printerManager.print(label)) {
                is PrintResult.Success -> return result
                is PrintResult.Error -> {
                    Timber.tag("PRINT").w(
                        "Attempt %d/%d failed: code=%s message=%s",
                        attempt + 1, maxAttempts, result.code, result.message,
                    )
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
        private const val RETRY_BASE_DELAY_MS = 500L
    }
}
