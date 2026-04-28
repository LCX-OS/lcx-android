package com.cleanx.lcx.feature.printing.data

/**
 * Abstraction layer for printer communication.
 *
 * Isolates Brother SDK specifics behind a clean interface so the rest of
 * the app never depends on the physical SDK.  Implementations:
 * - [StubPrinterManager] for development / testing without hardware
 * - [BrotherPrinterManager] wrapping the real Brother Mobile SDK AAR
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
    val ticketId: String? = null,
    val promisedPickupDate: String? = null,
    val paymentLabel: String = DEFAULT_PAYMENT_LABEL,
    val bagNumber: Int = DEFAULT_BAG_NUMBER,
    val totalBags: Int = DEFAULT_TOTAL_BAGS,
    val copyNumber: Int = DEFAULT_COPY_NUMBER,
) {
    fun forBag(bagNumber: Int, totalBags: Int): LabelData {
        val safeTotal = totalBags.coerceIn(MIN_BAG_COUNT, MAX_BAG_COUNT)
        return copy(
            bagNumber = bagNumber.coerceIn(MIN_BAG_COUNT, safeTotal),
            totalBags = safeTotal,
            copyNumber = DEFAULT_COPY_NUMBER,
        )
    }

    companion object {
        const val DEFAULT_PAYMENT_LABEL = "PAGO: PENDIENTE"
        const val DEFAULT_BAG_NUMBER = 1
        const val DEFAULT_TOTAL_BAGS = 1
        const val DEFAULT_COPY_NUMBER = 1
        const val MIN_BAG_COUNT = 1
        const val MAX_BAG_COUNT = 25
    }
}

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
