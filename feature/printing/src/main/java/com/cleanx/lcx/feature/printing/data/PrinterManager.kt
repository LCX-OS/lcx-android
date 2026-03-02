package com.cleanx.lcx.feature.printing.data

/**
 * Abstraction layer for printer communication.
 *
 * Isolates Brother SDK specifics behind a clean interface so the rest of
 * the app never depends on the physical SDK.  Implementations:
 * - [StubPrinterManager] for development / testing without hardware
 * - (future) BrotherPrinterManager wrapping the real Brother Mobile SDK AAR
 */
interface PrinterManager {
    /** Scan for available printers on the network / nearby Bluetooth. */
    suspend fun discoverPrinters(): List<PrinterInfo>

    /** Open a communication session with the given printer. */
    suspend fun connect(printer: PrinterInfo): Boolean

    /** Render and send [label] to the currently connected printer. */
    suspend fun print(label: LabelData): PrintResult

    /** Close the communication session. */
    fun disconnect()

    /** Whether a session is currently open. */
    fun isConnected(): Boolean
}

data class PrinterInfo(
    val name: String,
    /** IP address (WiFi) or MAC address (Bluetooth). */
    val address: String,
    val connectionType: ConnectionType,
)

enum class ConnectionType { WIFI, BLUETOOTH }

/**
 * All the data that goes on a single ticket label.
 *
 * This is a pure value object -- no Android / SDK dependencies --
 * making it easy to test and serialise.
 */
data class LabelData(
    val ticketNumber: String,
    val customerName: String,
    val serviceType: String,
    val date: String,
    val dailyFolio: Int,
)

/**
 * Result of a print attempt.
 *
 * Print failures must NEVER corrupt ticket or payment state; callers
 * should treat [Error] as recoverable via retry or skip.
 */
sealed class PrintResult {
    data object Success : PrintResult()
    data class Error(val code: String, val message: String) : PrintResult()
}
