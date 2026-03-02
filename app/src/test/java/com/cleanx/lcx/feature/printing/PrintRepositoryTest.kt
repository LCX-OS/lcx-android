package com.cleanx.lcx.feature.printing

import com.cleanx.lcx.feature.printing.data.ConnectionType
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.printing.data.PrinterInfo
import com.cleanx.lcx.feature.printing.data.PrinterManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrintRepositoryTest {

    private lateinit var printerManager: PrinterManager
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
        repository = PrintRepository(printerManager)
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
    fun `disconnect delegates to PrinterManager`() {
        repository.disconnect()

        verify { printerManager.disconnect() }
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
}
