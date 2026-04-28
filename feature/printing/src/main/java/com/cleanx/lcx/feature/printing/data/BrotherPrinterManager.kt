package com.cleanx.lcx.feature.printing.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [PrinterManager] backed by Brother SDK v4.
 *
 * The SDK is loaded via reflection so the app still compiles when the AAR
 * is absent. To enable physical printing, drop:
 * `feature/printing/libs/BrotherPrintLibrary.aar`
 */
@Singleton
class BrotherPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PrinterManager {

    private val bridge: BrotherSdkBridge? = BrotherSdkBridge.loadOrNull()
    private val lock = Any()

    private var connectedDriver: Any? = null
    private var connectedPrinter: PrinterInfo? = null

    fun isSdkAvailable(): Boolean = bridge != null

    override suspend fun discoverPrinters(): List<PrinterInfo> = withContext(Dispatchers.IO) {
        val sdk = bridge ?: run {
            Timber.tag(TAG).w("Brother SDK not available (missing BrotherPrintLibrary.aar)")
            return@withContext emptyList()
        }

        val channels = LinkedHashMap<String, Any>()

        runCatching {
            sdk.startNetworkSearch(context).forEach { channel ->
                val address = sdk.channelInfo(channel).orEmpty()
                if (address.isNotBlank()) {
                    channels["WIFI:$address"] = channel
                }
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Network printer search failed")
        }

        if (hasBluetoothConnectPermission()) {
            runCatching {
                sdk.startBluetoothSearch(context).forEach { channel ->
                    val address = sdk.channelInfo(channel).orEmpty()
                    if (address.isNotBlank()) {
                        channels["BT:$address"] = channel
                    }
                }
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Bluetooth printer search failed")
            }
        } else {
            Timber.tag(TAG).w("Skipping Bluetooth search (BLUETOOTH_CONNECT not granted)")
        }

        val printers = channels.values.mapNotNull { channel ->
            val address = sdk.channelInfo(channel).orEmpty()
            if (address.isBlank()) return@mapNotNull null

            val connectionType = when (sdk.channelType(channel)) {
                BrotherSdkBridge.ChannelKind.WIFI -> ConnectionType.WIFI
                BrotherSdkBridge.ChannelKind.BLUETOOTH -> ConnectionType.BLUETOOTH
                BrotherSdkBridge.ChannelKind.UNKNOWN -> return@mapNotNull null
            }

            val modelName = sdk.channelModelName(channel)
            val alias = sdk.channelAlias(channel)
            val name = when {
                !modelName.isNullOrBlank() -> modelName
                !alias.isNullOrBlank() -> alias
                else -> defaultPrinterName(connectionType, address)
            }

            PrinterInfo(
                name = name,
                address = address,
                connectionType = connectionType,
            )
        }

        Timber.tag("PRINT").d("Brother discovery completed: %d printer(s)", printers.size)
        printers
    }

    override suspend fun connect(printer: PrinterInfo): Boolean = withContext(Dispatchers.IO) {
        val sdk = bridge ?: run {
            Timber.tag(TAG).w("Cannot connect: Brother SDK not available")
            return@withContext false
        }

        if (printer.connectionType == ConnectionType.BLUETOOTH && !hasBluetoothConnectPermission()) {
            Timber.tag(TAG).w("Cannot connect Bluetooth printer: BLUETOOTH_CONNECT not granted")
            return@withContext false
        }

        synchronized(lock) {
            disconnectLocked()

            val channel = when (printer.connectionType) {
                ConnectionType.WIFI -> sdk.newWifiChannel(printer.address)
                ConnectionType.BLUETOOTH -> sdk.newBluetoothChannel(
                    address = printer.address,
                    adapter = BluetoothAdapter.getDefaultAdapter(),
                )
            } ?: return@synchronized false

            val openResult = sdk.openChannel(channel)
            if (!openResult.isSuccess || openResult.driver == null) {
                Timber.tag(TAG).w(
                    "openChannel failed: code=%s details=%s address=%s",
                    openResult.code,
                    openResult.details ?: "-",
                    printer.address,
                )
                return@synchronized false
            }

            connectedDriver = openResult.driver
            connectedPrinter = printer

            Timber.tag("PRINT").i(
                "Brother connected: type=%s address=%s name=%s",
                printer.connectionType,
                printer.address,
                printer.name,
            )
            true
        }
    }

    override suspend fun print(label: LabelData): PrintResult = withContext(Dispatchers.IO) {
        val sdk = bridge ?: return@withContext PrintResult.Error(
            code = "BROTHER_SDK_MISSING",
            message = "Brother SDK no integrado en esta build.",
        )

        val (driver, printer) = synchronized(lock) { connectedDriver to connectedPrinter }
        if (driver == null) {
            return@withContext PrintResult.Error(
                code = "PRINTER_NOT_CONNECTED",
                message = "No hay impresora conectada.",
            )
        }

        val settings = runCatching {
            sdk.newQlPrintSettings(inferPrinterModel(printer?.name))
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "Failed to create QLPrintSettings")
            return@withContext PrintResult.Error(
                code = "PRINT_SETTINGS_ERROR",
                message = "No se pudo configurar la impresion.",
            )
        }

        runCatching {
            // Etiqueta 209 => DK-1209 (62x29mm).
            sdk.setQlLabelSize(settings, LABEL_SIZE_209)
            sdk.setWorkPath(settings, context.cacheDir.absolutePath)
            sdk.setAutoCut(settings, enabled = true)
            sdk.setCutAtEnd(settings, enabled = true)
            sdk.setAutoCutForEachPageCount(settings, count = 1)
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Unable to apply one or more print settings")
        }

        val bitmap = LabelRenderer.render(label, LabelVariant.STANDARD)
        val outcome = runCatching {
            sdk.printImage(driver, bitmap, settings)
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "printImage failed unexpectedly")
            return@withContext PrintResult.Error(
                code = "PRINT_EXCEPTION",
                message = "Fallo inesperado al imprimir.",
            )
        }

        if (outcome.isSuccess) {
            Timber.tag("PRINT").i(
                "Brother print success: ticket=%s bag=%d/%d copy=%d printer=%s",
                label.ticketNumber,
                label.bagNumber,
                label.totalBags,
                label.copyNumber,
                printer?.name ?: "unknown",
            )
            return@withContext PrintResult.Success
        }

        Timber.tag("PRINT").w(
            "Brother print error: code=%s description=%s",
            outcome.code ?: "UnknownError",
            outcome.description ?: "-",
        )
        BrotherErrorMapper.mapSdkV4Error(
            errorCode = outcome.code ?: "UnknownError",
            description = outcome.description,
        )
    }

    override fun disconnect() {
        synchronized(lock) {
            disconnectLocked()
        }
    }

    override fun isConnected(): Boolean = synchronized(lock) {
        connectedDriver != null
    }

    private fun disconnectLocked() {
        val driver = connectedDriver
        if (driver != null) {
            runCatching { bridge?.closeChannel(driver) }
                .onFailure { error -> Timber.tag(TAG).w(error, "closeChannel failed") }
        }
        connectedDriver = null
        connectedPrinter = null
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun defaultPrinterName(type: ConnectionType, address: String): String {
        return when (type) {
            ConnectionType.WIFI -> "Brother (WiFi) $address"
            ConnectionType.BLUETOOTH -> "Brother (Bluetooth) $address"
        }
    }

    private fun inferPrinterModel(printerName: String?): String {
        val normalized = printerName?.uppercase().orEmpty()
        return when {
            "1115" in normalized -> "QL_1115NWB"
            "1110" in normalized -> "QL_1110NWB"
            "1100" in normalized -> "QL_1100"
            "810" in normalized -> "QL_810W"
            "820" in normalized -> "QL_820NWB"
            else -> DEFAULT_PRINTER_MODEL
        }
    }

    companion object {
        private const val TAG = "BROTHER"
        private const val DEFAULT_PRINTER_MODEL = "QL_820NWB"
        private const val LABEL_SIZE_209 = "DieCutW62H29"
    }
}

/**
 * Reflection bridge over Brother SDK classes so the project compiles even
 * before the vendor AAR is dropped in `feature/printing/libs`.
 */
private class BrotherSdkBridge private constructor(
    private val channelClass: Class<*>,
    private val printerSearcherClass: Class<*>,
    private val networkSearchOptionClass: Class<*>,
    private val printerDriverGeneratorClass: Class<*>,
    private val printerModelClass: Class<*>,
    private val printSettingsClass: Class<*>,
    private val qlPrintSettingsClass: Class<*>,
    private val qlLabelSizeClass: Class<*>,
) {

    private val startBluetoothSearchMethod: Method =
        printerSearcherClass.getMethod("startBluetoothSearch", Context::class.java)
    private val startNetworkSearchMethod: Method =
        printerSearcherClass.getMethod(
            "startNetworkSearch",
            Context::class.java,
            networkSearchOptionClass,
            Consumer::class.java,
        )
    private val networkSearchOptionCtor: Constructor<*> =
        networkSearchOptionClass.getConstructor(Double::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)

    private val openChannelMethod: Method =
        printerDriverGeneratorClass.getMethod("openChannel", channelClass)

    private val newWifiChannelMethod: Method =
        channelClass.getMethod("newWifiChannel", String::class.java)
    private val newBluetoothChannelMethod: Method =
        channelClass.getMethod("newBluetoothChannel", String::class.java, BluetoothAdapter::class.java)

    private val channelInfoMethod: Method = channelClass.getMethod("getChannelInfo")
    private val channelTypeMethod: Method = channelClass.getMethod("getChannelType")
    private val channelExtraInfoMethod: Method? = channelClass.methodOrNull("getExtraInfo")

    private val qlPrintSettingsCtor: Constructor<*> =
        qlPrintSettingsClass.getConstructor(printerModelClass)
    private val setLabelSizeMethod: Method =
        qlPrintSettingsClass.getMethod("setLabelSize", qlLabelSizeClass)
    private val setWorkPathMethod: Method? =
        qlPrintSettingsClass.methodOrNull("setWorkPath", String::class.java)
    private val setAutoCutMethod: Method? =
        qlPrintSettingsClass.methodOrNull("setAutoCut", Boolean::class.javaPrimitiveType)
    private val setCutAtEndMethod: Method? =
        qlPrintSettingsClass.methodOrNull("setCutAtEnd", Boolean::class.javaPrimitiveType)
    private val setAutoCutPerPageMethod: Method? =
        qlPrintSettingsClass.methodOrNull("setAutoCutForEachPageCount", Int::class.javaPrimitiveType)

    fun startBluetoothSearch(context: Context): List<Any> {
        val result = startBluetoothSearchMethod.invoke(null, context)
        return extractChannels(result, source = "bluetooth")
    }

    fun startNetworkSearch(context: Context): List<Any> {
        val collected = mutableListOf<Any>()
        val callback = Consumer<Any?> { channel ->
            if (channel != null) {
                collected += channel
            }
        }

        val option = networkSearchOptionCtor.newInstance(DEFAULT_SEARCH_DURATION_SEC, false)
        val result = startNetworkSearchMethod.invoke(null, context, option, callback)
        val channels = extractChannels(result, source = "network")
        return (collected + channels).distinctBy { channelInfo(it).orEmpty() }
    }

    fun newWifiChannel(address: String): Any? =
        runCatching { newWifiChannelMethod.invoke(null, address) }.getOrNull()

    fun newBluetoothChannel(address: String, adapter: BluetoothAdapter?): Any? {
        if (adapter == null) return null
        return runCatching { newBluetoothChannelMethod.invoke(null, address, adapter) }.getOrNull()
    }

    fun openChannel(channel: Any): OpenChannelOutcome {
        val result = runCatching { openChannelMethod.invoke(null, channel) }.getOrElse { error ->
            return OpenChannelOutcome(
                isSuccess = false,
                driver = null,
                code = "OPEN_CHANNEL_EXCEPTION",
                details = error.message,
            )
        }

        val error = result?.invokeNoArgs("getError")
        val code = error?.invokeNoArgs("getCode")?.toString().orEmpty()
        val details = error?.toString()
        val driver = result?.invokeNoArgs("getDriver")
        val isSuccess = code == NO_ERROR_CODE && driver != null
        return OpenChannelOutcome(
            isSuccess = isSuccess,
            driver = driver,
            code = code.ifBlank { "UnknownError" },
            details = details,
        )
    }

    fun closeChannel(driver: Any) {
        runCatching { driver.invokeNoArgs("closeChannel") }
    }

    fun newQlPrintSettings(modelName: String): Any {
        val model = enumValue(printerModelClass, modelName)
            ?: enumValue(printerModelClass, DEFAULT_MODEL)
            ?: throw IllegalStateException("Brother PrinterModel enum missing $DEFAULT_MODEL")
        return qlPrintSettingsCtor.newInstance(model)
    }

    fun setQlLabelSize(settings: Any, labelSizeName: String) {
        val labelSize = enumValue(qlLabelSizeClass, labelSizeName)
            ?: throw IllegalStateException("Brother LabelSize enum missing $labelSizeName")
        setLabelSizeMethod.invoke(settings, labelSize)
    }

    fun setWorkPath(settings: Any, path: String) {
        setWorkPathMethod?.invoke(settings, path)
    }

    fun setAutoCut(settings: Any, enabled: Boolean) {
        setAutoCutMethod?.invoke(settings, enabled)
    }

    fun setCutAtEnd(settings: Any, enabled: Boolean) {
        setCutAtEndMethod?.invoke(settings, enabled)
    }

    fun setAutoCutForEachPageCount(settings: Any, count: Int) {
        setAutoCutPerPageMethod?.invoke(settings, count)
    }

    fun printImage(driver: Any, bitmap: Bitmap, settings: Any): PrintOutcome {
        val method = driver.javaClass.getMethod("printImage", Bitmap::class.java, printSettingsClass)
        val printError = method.invoke(driver, bitmap, settings)
        val code = printError?.invokeNoArgs("getCode")?.toString().orEmpty()
        val description = printError?.invokeNoArgs("getErrorDescription")?.toString()
        return PrintOutcome(
            isSuccess = code == NO_ERROR_CODE,
            code = code.ifBlank { "UnknownError" },
            description = description,
        )
    }

    fun channelInfo(channel: Any): String? =
        runCatching { channelInfoMethod.invoke(channel)?.toString() }.getOrNull()

    fun channelType(channel: Any): ChannelKind {
        val raw = runCatching { channelTypeMethod.invoke(channel)?.toString() }
            .getOrNull()
            ?.uppercase()
            .orEmpty()
        return when {
            "WIFI" in raw || "NETWORK" in raw -> ChannelKind.WIFI
            "BLUETOOTH" in raw -> ChannelKind.BLUETOOTH
            else -> ChannelKind.UNKNOWN
        }
    }

    fun channelModelName(channel: Any): String? =
        extractExtraInfo(channel, "ModelName")

    fun channelAlias(channel: Any): String? =
        extractExtraInfo(channel, "BluetoothAlias")

    private fun extractChannels(result: Any?, source: String): List<Any> {
        if (result == null) return emptyList()

        val error = result.invokeNoArgs("getError")
        val code = error?.invokeNoArgs("getCode")?.toString().orEmpty()
        if (code.isNotBlank() && code != NO_ERROR_CODE) {
            Timber.tag("BROTHER").w("Printer search error (%s): %s", source, code)
        }

        val channels = result.invokeNoArgs("getChannels")
        return (channels as? Iterable<*>)?.filterNotNull().orEmpty()
    }

    private fun extractExtraInfo(channel: Any, expectedKeySuffix: String): String? {
        val map = runCatching {
            channelExtraInfoMethod?.invoke(channel) as? Map<*, *>
        }.getOrNull() ?: return null

        return map.entries
            .firstOrNull { entry ->
                entry.key?.toString()?.endsWith(expectedKeySuffix) == true
            }
            ?.value
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    private fun enumValue(enumClass: Class<*>, enumName: String): Any? {
        return enumClass.enumConstants
            ?.firstOrNull { constant ->
                (constant as? Enum<*>)?.name == enumName
            }
    }

    data class OpenChannelOutcome(
        val isSuccess: Boolean,
        val driver: Any?,
        val code: String,
        val details: String?,
    )

    data class PrintOutcome(
        val isSuccess: Boolean,
        val code: String?,
        val description: String?,
    )

    enum class ChannelKind { WIFI, BLUETOOTH, UNKNOWN }

    companion object {
        private const val NO_ERROR_CODE = "NoError"
        private const val DEFAULT_MODEL = "QL_820NWB"
        private const val DEFAULT_SEARCH_DURATION_SEC = 5.0

        fun loadOrNull(): BrotherSdkBridge? {
            return runCatching {
                val printSettingsClass = loadClass(
                    "com.brother.sdk.lmprinter.PrintSettings",
                    "com.brother.sdk.lmprinter.setting.PrintSettings",
                )
                val qlPrintSettingsClass = loadClass(
                    "com.brother.sdk.lmprinter.QLPrintSettings",
                    "com.brother.sdk.lmprinter.setting.QLPrintSettings",
                )
                val qlLabelSizeClass = loadClass(
                    "com.brother.sdk.lmprinter.QLPrintSettings\$LabelSize",
                    "com.brother.sdk.lmprinter.setting.QLPrintSettings\$LabelSize",
                )
                BrotherSdkBridge(
                    channelClass = Class.forName("com.brother.sdk.lmprinter.Channel"),
                    printerSearcherClass = Class.forName("com.brother.sdk.lmprinter.PrinterSearcher"),
                    networkSearchOptionClass = Class.forName("com.brother.sdk.lmprinter.NetworkSearchOption"),
                    printerDriverGeneratorClass = Class.forName("com.brother.sdk.lmprinter.PrinterDriverGenerator"),
                    printerModelClass = Class.forName("com.brother.sdk.lmprinter.PrinterModel"),
                    printSettingsClass = printSettingsClass,
                    qlPrintSettingsClass = qlPrintSettingsClass,
                    qlLabelSizeClass = qlLabelSizeClass,
                )
            }.onFailure { error ->
                Timber.tag("BROTHER").w(
                    error,
                    "Brother SDK reflection bridge unavailable. Add BrotherPrintLibrary.aar to feature/printing/libs",
                )
            }.getOrNull()
        }

        private fun loadClass(vararg candidates: String): Class<*> {
            var lastError: Throwable? = null
            for (name in candidates) {
                try {
                    return Class.forName(name)
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            throw lastError ?: ClassNotFoundException(candidates.joinToString())
        }
    }
}

private fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>?): Method? {
    return runCatching {
        @Suppress("SpreadOperator")
        getMethod(name, *parameterTypes)
    }.getOrNull()
}

private fun Any.invokeNoArgs(name: String): Any? {
    val method = javaClass.methodOrNull(name) ?: return null
    return method.invoke(this)
}
