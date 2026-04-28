package com.cleanx.lcx.feature.printing.data

import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Fake [PrinterManager] for development and testing without physical hardware.
 *
 * - [discoverPrinters] returns two simulated printers after a short delay.
 * - [connect] succeeds after 1 s and remembers the connected state.
 * - [print] succeeds 90 % of the time after 2 s; 10 % returns a random error.
 *
 * All operations are logged with the `[STUB-BROTHER]` Timber tag for easy
 * filtering in Logcat.
 *
 * Connected state is remembered across calls so that callers relying on
 * [isConnected] get consistent behaviour during a session.
 */
@Singleton
class StubPrinterManager @Inject constructor() : PrinterManager {

    @Volatile
    private var connected = false

    @Volatile
    private var connectedPrinter: PrinterInfo? = null

    /**
     * When `true`, [print] always succeeds (useful for demos).
     * When `false`, the normal random distribution applies.
     */
    @Volatile
    var alwaysSucceed: Boolean = false

    /**
     * When `true`, [connect] always fails (useful for testing error handling).
     */
    @Volatile
    var alwaysFail: Boolean = false

    override suspend fun discoverPrinters(): List<PrinterInfo> {
        Timber.tag(TAG).d("discovering printers...")
        delay(DISCOVERY_DELAY_MS)
        val printers = listOf(
            PrinterInfo(
                name = "Brother QL-820NWB (stub)",
                address = "192.168.1.100",
                connectionType = ConnectionType.WIFI,
            ),
            PrinterInfo(
                name = "Brother QL-820NWB-BT (stub)",
                address = "AA:BB:CC:DD:EE:FF",
                connectionType = ConnectionType.BLUETOOTH,
            ),
        )
        Timber.tag(TAG).d("discovered %d printers", printers.size)
        return printers
    }

    override suspend fun connect(printer: PrinterInfo): Boolean {
        Timber.tag(TAG).d("connecting to %s (%s)...", printer.name, printer.address)
        delay(CONNECT_DELAY_MS)

        if (alwaysFail) {
            Timber.tag(TAG).w("connection forced to fail (alwaysFail=true)")
            connected = false
            connectedPrinter = null
            return false
        }

        connected = true
        connectedPrinter = printer
        Timber.tag(TAG).d("connected to %s", printer.name)
        return true
    }

    override suspend fun print(label: LabelData): PrintResult {
        Timber.tag(TAG).d(
            "printing label ticket=%s bag=%d/%d copy=%d customer=%s",
            label.ticketNumber,
            label.bagNumber,
            label.totalBags,
            label.copyNumber,
            label.customerName,
        )
        delay(PRINT_DELAY_MS)

        val shouldSucceed = alwaysSucceed || Random.nextInt(100) < SUCCESS_RATE_PERCENT
        return if (shouldSucceed) {
            Timber.tag(TAG).d("print success for ticket=%s", label.ticketNumber)
            PrintResult.Success
        } else {
            val error = SIMULATED_ERRORS.random()
            Timber.tag(TAG).w(
                "simulated print error: code=%s message=%s",
                error.code,
                error.message,
            )
            error
        }
    }

    override fun disconnect() {
        Timber.tag(TAG).d("disconnected from %s", connectedPrinter?.name ?: "none")
        connected = false
        connectedPrinter = null
    }

    override fun isConnected(): Boolean = connected

    companion object {
        private const val TAG = "STUB-BROTHER"
        private const val DISCOVERY_DELAY_MS = 500L
        private const val CONNECT_DELAY_MS = 1_000L
        private const val PRINT_DELAY_MS = 2_000L
        private const val SUCCESS_RATE_PERCENT = 90

        private val SIMULATED_ERRORS = listOf(
            PrintResult.Error("STUB_ERROR", "Error simulado de impresion"),
            PrintResult.Error("COMMUNICATION_ERROR", "Error de comunicacion simulado"),
            PrintResult.Error("NO_PAPER", "Sin papel/etiquetas (simulado)"),
        )
    }
}
