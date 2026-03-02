package com.cleanx.lcx.feature.printing.data

import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

/**
 * Fake [PrinterManager] for development and testing without physical hardware.
 *
 * - [discoverPrinters] returns one simulated WiFi printer after 500 ms.
 * - [connect] succeeds after 1 s.
 * - [print] succeeds 90 % of the time after 2 s; 10 % returns a random error.
 */
class StubPrinterManager @Inject constructor() : PrinterManager {

    private var connected = false

    override suspend fun discoverPrinters(): List<PrinterInfo> {
        Timber.d("StubPrinterManager: discovering printers...")
        delay(DISCOVERY_DELAY_MS)
        return listOf(
            PrinterInfo(
                name = "Brother QL-820NWB (stub)",
                address = "192.168.1.100",
                connectionType = ConnectionType.WIFI,
            ),
        )
    }

    override suspend fun connect(printer: PrinterInfo): Boolean {
        Timber.d("StubPrinterManager: connecting to ${printer.name}...")
        delay(CONNECT_DELAY_MS)
        connected = true
        Timber.d("StubPrinterManager: connected.")
        return true
    }

    override suspend fun print(label: LabelData): PrintResult {
        Timber.d("StubPrinterManager: printing label ${label.ticketNumber}...")
        delay(PRINT_DELAY_MS)

        return if (Random.nextInt(100) < SUCCESS_RATE_PERCENT) {
            Timber.d("StubPrinterManager: print success.")
            PrintResult.Success
        } else {
            Timber.w("StubPrinterManager: simulated print error.")
            PrintResult.Error(
                code = "STUB_ERROR",
                message = "Error simulado de impresion",
            )
        }
    }

    override fun disconnect() {
        Timber.d("StubPrinterManager: disconnected.")
        connected = false
    }

    override fun isConnected(): Boolean = connected

    companion object {
        private const val DISCOVERY_DELAY_MS = 500L
        private const val CONNECT_DELAY_MS = 1_000L
        private const val PRINT_DELAY_MS = 2_000L
        private const val SUCCESS_RATE_PERCENT = 90
    }
}
