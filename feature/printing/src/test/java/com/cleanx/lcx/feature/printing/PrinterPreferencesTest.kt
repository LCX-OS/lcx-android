package com.cleanx.lcx.feature.printing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.cleanx.lcx.feature.printing.data.ConnectionType
import com.cleanx.lcx.feature.printing.data.PrinterInfo
import com.cleanx.lcx.feature.printing.data.PrinterPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PrinterPreferences] DataStore persistence.
 *
 * Because [preferencesDataStore] delegates create a process-wide singleton,
 * the underlying DataStore persists across test methods. We clear all keys
 * at the start of each test so that "default" assertions are reliable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrinterPreferencesTest {

    private lateinit var printerPreferences: PrinterPreferences

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        printerPreferences = PrinterPreferences(context)
        // Clear all preferences so each test starts from scratch.
        // We call the public API methods that remove data.
        printerPreferences.clearPrinter()
        printerPreferences.setPrintCopies(1)
        printerPreferences.setAutoConnect(true)
    }

    // -- Saved printer --------------------------------------------------------

    @Test
    fun `savedPrinter is null by default`() = runTest {
        printerPreferences.savedPrinter.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `savePrinter persists printer info`() = runTest {
        val printer = PrinterInfo(
            name = "Brother QL-820NWB",
            address = "192.168.1.100",
            connectionType = ConnectionType.WIFI,
        )

        printerPreferences.savePrinter(printer)

        printerPreferences.savedPrinter.test {
            val saved = awaitItem()!!
            assertEquals("Brother QL-820NWB", saved.name)
            assertEquals("192.168.1.100", saved.address)
            assertEquals(ConnectionType.WIFI, saved.connectionType)
        }
    }

    @Test
    fun `savePrinter with BLUETOOTH connection type`() = runTest {
        val printer = PrinterInfo(
            name = "Brother BT Printer",
            address = "AA:BB:CC:DD:EE:FF",
            connectionType = ConnectionType.BLUETOOTH,
        )

        printerPreferences.savePrinter(printer)

        printerPreferences.savedPrinter.test {
            val saved = awaitItem()!!
            assertEquals("Brother BT Printer", saved.name)
            assertEquals("AA:BB:CC:DD:EE:FF", saved.address)
            assertEquals(ConnectionType.BLUETOOTH, saved.connectionType)
        }
    }

    @Test
    fun `clearPrinter removes saved printer`() = runTest {
        val printer = PrinterInfo(
            name = "Brother QL-820NWB",
            address = "192.168.1.100",
            connectionType = ConnectionType.WIFI,
        )
        printerPreferences.savePrinter(printer)
        printerPreferences.clearPrinter()

        printerPreferences.savedPrinter.test {
            assertNull(awaitItem())
        }
    }

    // -- Print copies ---------------------------------------------------------

    @Test
    fun `printCopies defaults to 1`() = runTest {
        // setUp already resets to 1
        val copies = printerPreferences.printCopies.first()
        assertEquals(1, copies)
    }

    @Test
    fun `setPrintCopies persists value`() = runTest {
        printerPreferences.setPrintCopies(3)

        printerPreferences.printCopies.test {
            assertEquals(3, awaitItem())
        }
    }

    @Test
    fun `setPrintCopies to 2`() = runTest {
        printerPreferences.setPrintCopies(2)

        printerPreferences.printCopies.test {
            assertEquals(2, awaitItem())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setPrintCopies rejects values below 1`() = runTest {
        printerPreferences.setPrintCopies(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setPrintCopies rejects values above 3`() = runTest {
        printerPreferences.setPrintCopies(4)
    }

    // -- Auto-connect ---------------------------------------------------------

    @Test
    fun `autoConnect defaults to true`() = runTest {
        // setUp already resets to true
        val auto = printerPreferences.autoConnect.first()
        assertTrue(auto)
    }

    @Test
    fun `setAutoConnect persists false`() = runTest {
        printerPreferences.setAutoConnect(false)

        printerPreferences.autoConnect.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `setAutoConnect can be toggled back to true`() = runTest {
        printerPreferences.setAutoConnect(false)
        printerPreferences.setAutoConnect(true)

        printerPreferences.autoConnect.test {
            assertTrue(awaitItem())
        }
    }
}
