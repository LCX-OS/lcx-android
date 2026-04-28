package com.cleanx.lcx.feature.printing

import com.cleanx.lcx.feature.printing.data.ConnectionType
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.printing.data.PrinterInfo
import com.cleanx.lcx.feature.printing.data.PrinterManager
import com.cleanx.lcx.feature.printing.data.PrinterPreferences
import com.cleanx.lcx.feature.printing.data.SavedPrinter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrintRepositoryTest {

    private lateinit var printerManager: PrinterManager
    private lateinit var printerPreferences: PrinterPreferences
    private lateinit var repository: PrintRepository

    private val fakePrinter = PrinterInfo(
        name = "Test Printer",
        address = "192.168.1.100",
        connectionType = ConnectionType.WIFI,
    )

    private val sampleLabel = LabelData(
        ticketNumber = "T-20260302-0001",
        customerName = "Juan Perez",
        serviceType = "wash-fold",
        date = "2026-03-02",
        dailyFolio = 1,
    )

    @Before
    fun setUp() {
        printerManager = mockk(relaxed = true)
        printerPreferences = mockk(relaxed = true)

        // Default preferences flows
        every { printerPreferences.printCopies } returns flowOf(1)
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(null)

        // Default: printer is connected (so printWithRetry doesn't short-circuit)
        every { printerManager.isConnected() } returns true

        repository = PrintRepository(printerManager, printerPreferences)
    }

    // -- Discovery ------------------------------------------------------------

    @Test
    fun `discoverPrinters delegates to PrinterManager`() = runTest {
        coEvery { printerManager.discoverPrinters() } returns listOf(fakePrinter)

        val printers = repository.discoverPrinters()

        assertEquals(1, printers.size)
        assertEquals(fakePrinter, printers[0])
    }

    // -- Connection -----------------------------------------------------------

    @Test
    fun `connectToSelected returns false when no printer selected`() = runTest {
        val result = repository.connectToSelected()

        assertFalse(result)
    }

    @Test
    fun `connectToSelected delegates to PrinterManager when printer is selected`() = runTest {
        coEvery { printerManager.connect(fakePrinter) } returns true
        repository.selectPrinter(fakePrinter)

        val result = repository.connectToSelected()

        assertTrue(result)
        coVerify { printerManager.connect(fakePrinter) }
    }

    @Test
    fun `connectToSelected saves printer on success`() = runTest {
        coEvery { printerManager.connect(fakePrinter) } returns true
        repository.selectPrinter(fakePrinter)

        repository.connectToSelected()

        coVerify { printerPreferences.savePrinter(fakePrinter) }
    }

    @Test
    fun `connectToSelected does not save printer on failure`() = runTest {
        coEvery { printerManager.connect(fakePrinter) } returns false
        repository.selectPrinter(fakePrinter)

        repository.connectToSelected()

        coVerify(exactly = 0) { printerPreferences.savePrinter(any()) }
    }

    @Test
    fun `disconnect delegates to PrinterManager`() {
        repository.disconnect()

        verify { printerManager.disconnect() }
    }

    // -- Auto-connect ---------------------------------------------------------

    @Test
    fun `tryAutoConnect returns false when auto-connect disabled`() = runTest {
        every { printerPreferences.autoConnect } returns flowOf(false)

        val result = repository.tryAutoConnect()

        assertFalse(result)
    }

    @Test
    fun `tryAutoConnect returns false when no saved printer`() = runTest {
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(null)

        val result = repository.tryAutoConnect()

        assertFalse(result)
    }

    @Test
    fun `tryAutoConnect connects to saved printer`() = runTest {
        val saved = SavedPrinter(
            name = "Test Printer",
            address = "192.168.1.100",
            connectionType = ConnectionType.WIFI,
        )
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(saved)
        coEvery { printerManager.connect(any()) } returns true

        val result = repository.tryAutoConnect()

        assertTrue(result)
        coVerify { printerManager.connect(saved.toPrinterInfo()) }
    }

    @Test
    fun `tryAutoConnect returns false when connect fails`() = runTest {
        val saved = SavedPrinter(
            name = "Test Printer",
            address = "192.168.1.100",
            connectionType = ConnectionType.WIFI,
        )
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(saved)
        coEvery { printerManager.connect(any()) } returns false

        val result = repository.tryAutoConnect()

        assertFalse(result)
    }

    // -- Retry logic ----------------------------------------------------------

    @Test
    fun `printWithRetry succeeds on first attempt`() = runTest {
        coEvery { printerManager.print(sampleLabel) } returns PrintResult.Success

        val result = repository.printWithRetry(sampleLabel)

        assertTrue(result is PrintResult.Success)
        coVerify(exactly = 1) { printerManager.print(sampleLabel) }
    }

    @Test
    fun `printWithRetry retries on failure and succeeds on third attempt`() = runTest {
        coEvery { printerManager.print(sampleLabel) } returnsMany listOf(
            PrintResult.Error("ERR1", "fail 1"),
            PrintResult.Error("ERR2", "fail 2"),
            PrintResult.Success,
        )

        val result = repository.printWithRetry(sampleLabel, maxAttempts = 3)

        assertTrue(result is PrintResult.Success)
        coVerify(exactly = 3) { printerManager.print(sampleLabel) }
    }

    @Test
    fun `printWithRetry returns last error after all attempts exhausted`() = runTest {
        coEvery { printerManager.print(sampleLabel) } returns
            PrintResult.Error("ERR", "always fails")

        val result = repository.printWithRetry(sampleLabel, maxAttempts = 3)

        assertTrue(result is PrintResult.Error)
        assertEquals("always fails", (result as PrintResult.Error).message)
        coVerify(exactly = 3) { printerManager.print(sampleLabel) }
    }

    @Test
    fun `printWithRetry fails twice then succeeds`() = runTest {
        coEvery { printerManager.print(sampleLabel) } returnsMany listOf(
            PrintResult.Error("ERR1", "fail 1"),
            PrintResult.Error("ERR2", "fail 2"),
            PrintResult.Success,
        )

        val result = repository.printWithRetry(sampleLabel)

        assertTrue(result is PrintResult.Success)
    }

    @Test
    fun `printWithRetry prints multiple copies when configured`() = runTest {
        every { printerPreferences.printCopies } returns flowOf(2)
        every { printerManager.isConnected() } returns true
        // Re-create repo with updated preferences mock
        repository = PrintRepository(printerManager, printerPreferences)
        coEvery { printerManager.print(any()) } returns PrintResult.Success

        val result = repository.printWithRetry(sampleLabel)

        assertTrue(result is PrintResult.Success)
        coVerify { printerManager.print(sampleLabel.copy(copyNumber = 1)) }
        coVerify { printerManager.print(sampleLabel.copy(copyNumber = 2)) }
    }

    @Test
    fun `printWithRetry stops on error during multi-copy`() = runTest {
        every { printerPreferences.printCopies } returns flowOf(3)
        every { printerManager.isConnected() } returns true
        repository = PrintRepository(printerManager, printerPreferences)
        coEvery { printerManager.print(any()) } returnsMany listOf(
            PrintResult.Success,
            PrintResult.Error("ERR", "fail"),
            PrintResult.Error("ERR", "fail"),
            PrintResult.Error("ERR", "fail"),
        )

        val result = repository.printWithRetry(sampleLabel)

        assertTrue(result is PrintResult.Error)
    }

    // -- Skip behavior --------------------------------------------------------

    @Test
    fun `selectedPrinter is null by default`() {
        assertEquals(null, repository.getSelectedPrinter())
    }

    @Test
    fun `selectPrinter stores the printer`() {
        repository.selectPrinter(fakePrinter)

        assertEquals(fakePrinter, repository.getSelectedPrinter())
    }

    @Test
    fun `isConnected delegates to PrinterManager`() {
        every { printerManager.isConnected() } returns true

        assertTrue(repository.isConnected())
    }

    // -- ensureConnected ------------------------------------------------------

    @Test
    fun `ensureConnected returns true when already connected`() = runTest {
        every { printerManager.isConnected() } returns true

        assertTrue(repository.ensureConnected())
    }

    @Test
    fun `ensureConnected reconnects to selected printer`() = runTest {
        every { printerManager.isConnected() } returns false
        coEvery { printerManager.connect(fakePrinter) } returns true
        repository.selectPrinter(fakePrinter)

        assertTrue(repository.ensureConnected())
        coVerify { printerManager.connect(fakePrinter) }
    }

    @Test
    fun `ensureConnected falls back to auto-connect from saved prefs`() = runTest {
        val saved = SavedPrinter(
            name = "Saved Printer",
            address = "192.168.1.200",
            connectionType = ConnectionType.WIFI,
        )
        every { printerManager.isConnected() } returns false
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(saved)
        coEvery { printerManager.connect(any()) } returns true

        assertTrue(repository.ensureConnected())
        coVerify { printerManager.connect(saved.toPrinterInfo()) }
    }

    @Test
    fun `ensureConnected returns false when nothing is available`() = runTest {
        every { printerManager.isConnected() } returns false
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(null)

        assertFalse(repository.ensureConnected())
    }

    @Test
    fun `printWithRetry returns PRINTER_NOT_CONNECTED when ensureConnected fails`() = runTest {
        every { printerManager.isConnected() } returns false
        every { printerPreferences.autoConnect } returns flowOf(true)
        every { printerPreferences.savedPrinter } returns flowOf(null)

        val result = repository.printWithRetry(sampleLabel)

        assertTrue(result is PrintResult.Error)
        assertEquals("PRINTER_NOT_CONNECTED", (result as PrintResult.Error).code)
    }

    // -- Forget printer -------------------------------------------------------

    @Test
    fun `forgetPrinter disconnects and clears preferences`() = runTest {
        repository.selectPrinter(fakePrinter)
        repository.forgetPrinter()

        verify { printerManager.disconnect() }
        coVerify { printerPreferences.clearPrinter() }
        assertEquals(null, repository.getSelectedPrinter())
    }
}
