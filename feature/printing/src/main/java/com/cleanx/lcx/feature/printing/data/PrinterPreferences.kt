package com.cleanx.lcx.feature.printing.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printerDataStore by preferencesDataStore(name = "printer_preferences")

/**
 * Persists printer-related preferences using [DataStore].
 *
 * Values survive app restarts so the operator only has to pick a printer once.
 * When [autoConnect] is `true`, the app will try to reconnect on startup.
 */
@Singleton
class PrinterPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.printerDataStore

    // -- Saved printer --------------------------------------------------------

    val savedPrinter: Flow<SavedPrinter?> = dataStore.data
        .catchIo()
        .map { prefs ->
            val name = prefs[KEY_PRINTER_NAME] ?: return@map null
            val address = prefs[KEY_PRINTER_ADDRESS] ?: return@map null
            val typeString = prefs[KEY_PRINTER_CONNECTION_TYPE] ?: return@map null
            SavedPrinter(
                name = name,
                address = address,
                connectionType = ConnectionType.valueOf(typeString),
            )
        }

    suspend fun savePrinter(printer: PrinterInfo) {
        dataStore.edit { prefs ->
            prefs[KEY_PRINTER_NAME] = printer.name
            prefs[KEY_PRINTER_ADDRESS] = printer.address
            prefs[KEY_PRINTER_CONNECTION_TYPE] = printer.connectionType.name
        }
        Timber.d("PrinterPreferences: saved printer %s (%s)", printer.name, printer.address)
    }

    suspend fun clearPrinter() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PRINTER_NAME)
            prefs.remove(KEY_PRINTER_ADDRESS)
            prefs.remove(KEY_PRINTER_CONNECTION_TYPE)
        }
        Timber.d("PrinterPreferences: cleared saved printer")
    }

    // -- Print copies ---------------------------------------------------------

    val printCopies: Flow<Int> = dataStore.data
        .catchIo()
        .map { prefs -> prefs[KEY_PRINT_COPIES] ?: DEFAULT_COPIES }

    suspend fun setPrintCopies(copies: Int) {
        require(copies in 1..3) { "Print copies must be between 1 and 3" }
        dataStore.edit { prefs -> prefs[KEY_PRINT_COPIES] = copies }
        Timber.d("PrinterPreferences: print copies set to %d", copies)
    }

    // -- Auto-connect ---------------------------------------------------------

    val autoConnect: Flow<Boolean> = dataStore.data
        .catchIo()
        .map { prefs -> prefs[KEY_AUTO_CONNECT] ?: DEFAULT_AUTO_CONNECT }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_CONNECT] = enabled }
        Timber.d("PrinterPreferences: auto-connect set to %b", enabled)
    }

    // -- Helpers --------------------------------------------------------------

    private fun <T> Flow<T>.catchIo(): Flow<T> = catch { exception ->
        if (exception is IOException) {
            Timber.e(exception, "PrinterPreferences: I/O error reading DataStore")
            @Suppress("UNCHECKED_CAST")
            emit(emptyPreferences() as T)
        } else {
            throw exception
        }
    }

    companion object {
        private val KEY_PRINTER_NAME = stringPreferencesKey("printer_name")
        private val KEY_PRINTER_ADDRESS = stringPreferencesKey("printer_address")
        private val KEY_PRINTER_CONNECTION_TYPE = stringPreferencesKey("printer_connection_type")
        private val KEY_PRINT_COPIES = intPreferencesKey("print_copies")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")

        const val DEFAULT_COPIES = 1
        const val DEFAULT_AUTO_CONNECT = true
    }
}

/**
 * Serialisable snapshot of a previously connected printer.
 * Used to skip discovery on subsequent launches.
 */
data class SavedPrinter(
    val name: String,
    val address: String,
    val connectionType: ConnectionType,
) {
    /** Convert back to [PrinterInfo] for reconnection. */
    fun toPrinterInfo(): PrinterInfo = PrinterInfo(
        name = name,
        address = address,
        connectionType = connectionType,
    )
}
