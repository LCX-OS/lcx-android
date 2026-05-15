package com.cleanx.lcx.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText as hasComposeText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.MainActivity
import com.cleanx.lcx.core.ui.LcxTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ZETTLE_PACKAGE = "com.izettle.android"
private const val DEFAULT_TIMEOUT_MS = 30_000L
private const val LONG_TIMEOUT_MS = 90_000L

data class HardwareE2eConfig(
    val branch: String,
    val operatorName: String,
    val pin: String,
    val allowRealCharge: Boolean,
    val runTicketHardwarePath: Boolean,
    val seededTicketNumber: String,
    val brotherPrinterName: String,
    val chargeTimeoutMs: Long,
) {
    companion object {
        fun fromInstrumentation(): HardwareE2eConfig {
            val args = InstrumentationRegistry.getArguments()
            return HardwareE2eConfig(
                branch = args.getString("lcxBranch") ?: "La Esperanza",
                operatorName = args.getString("lcxOperatorName") ?: "Operador E2E",
                pin = args.getString("lcxPin") ?: "1234",
                allowRealCharge = args.getString("lcxAllowRealCharge") == "true",
                runTicketHardwarePath = args.getString("lcxRunTicketHardwarePath") == "true",
                seededTicketNumber = args.getString("lcxSeededTicketNumber").orEmpty(),
                brotherPrinterName = args.getString("lcxBrotherPrinterName") ?: "QL-810W",
                chargeTimeoutMs = args.getString("lcxRealChargeTimeoutMs")?.toLongOrNull() ?: 300_000L,
            )
        }
    }
}

@LargeTest
abstract class HardwareE2eBase {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    protected lateinit var config: HardwareE2eConfig
    protected lateinit var robot: HardwareE2eRobot

    @Before
    fun setUpHardwareE2e() {
        config = HardwareE2eConfig.fromInstrumentation()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        robot = HardwareE2eRobot(
            compose = compose,
            device = UiDevice.getInstance(instrumentation),
            targetPackage = instrumentation.targetContext.packageName,
            config = config,
        )
        robot.wakeDevice()
    }
}

@RunWith(AndroidJUnit4::class)
class PreflightHardwareE2eTest : HardwareE2eBase() {
    @Test
    fun preflight_asserts_prodLike_debug_build_and_payment_diagnostics() {
        assertEquals("com.cleanx.app", robot.targetPackage)
        assertEquals("com.cleanx.app", BuildConfig.APPLICATION_ID)
        assertTrue("USE_REAL_ZETTLE must be true", BuildConfig.USE_REAL_ZETTLE)
        assertTrue("USE_REAL_BROTHER must be true", BuildConfig.USE_REAL_BROTHER)
        assertEquals("http://127.0.0.1:3000", BuildConfig.API_BASE_URL)
        assertEquals("http://127.0.0.1:54321", BuildConfig.SUPABASE_URL)

        robot.ensureLoggedIn()
        robot.openPaymentDiagnostics()
        robot.waitForText("Zettle SDK real", timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForText("Inicializado: Si", timeoutMs = LONG_TIMEOUT_MS)
    }
}

@RunWith(AndroidJUnit4::class)
class DeviceLoginE2eTest : HardwareE2eBase() {
    @Test
    fun login_with_seeded_branch_operator_and_pin_lands_on_dashboard() {
        robot.ensureLoggedIn()
        robot.waitForTag(LcxTestTags.DASHBOARD_ROOT, timeoutMs = LONG_TIMEOUT_MS)
    }
}

@RunWith(AndroidJUnit4::class)
class CriticalOperatorSmokeE2eTest : HardwareE2eBase() {
    @Test
    fun operator_can_open_critical_modules_without_runtime_errors() {
        robot.ensureLoggedIn()

        robot.navigateBottom("Inicio", LcxTestTags.DASHBOARD_ROOT)
        robot.openWaterFromDrawer()
        robot.navigateBottom("Caja", LcxTestTags.CASH_ROOT)
        robot.navigateBottom("Checklist", LcxTestTags.CHECKLIST_ROOT)
        robot.navigateBottom("Encargos", LcxTestTags.TICKET_LIST_ROOT)
        robot.openPaymentDiagnostics()
    }
}

@RunWith(AndroidJUnit4::class)
class RealZettleChargeE2eTest : HardwareE2eBase() {
    @Test
    fun diagnostics_runs_real_one_peso_zettle_charge() {
        assumeTrue("Real Zettle charge requires --allow-real-charge", config.allowRealCharge)

        robot.ensureLoggedIn()
        robot.openPaymentDiagnostics()
        robot.waitForText("Zettle SDK real", timeoutMs = LONG_TIMEOUT_MS)
        robot.clickTag(LcxTestTags.PAYMENT_DIAGNOSTICS_CHARGE_BUTTON, timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForDiagnosticChargeSuccess(timeoutMs = config.chargeTimeoutMs)
    }
}

@RunWith(AndroidJUnit4::class)
class RealBrotherPrintE2eTest : HardwareE2eBase() {
    @Test
    fun brother_settings_discovers_connects_and_prints_test_label() {
        robot.ensureLoggedIn()
        robot.openPrinterSettings()
        robot.clickDiscoverPrinters(timeoutMs = LONG_TIMEOUT_MS)
        robot.clickPrinterCard(config.brotherPrinterName, timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForText("Impresora conectada", substring = true, timeoutMs = LONG_TIMEOUT_MS)
        robot.clickPrinterTestPrint(timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForText("Prueba de impresion exitosa", timeoutMs = LONG_TIMEOUT_MS)
    }
}

@RunWith(AndroidJUnit4::class)
class TicketHardwarePathE2eTest : HardwareE2eBase() {
    @Test
    fun seeded_ticket_can_be_charged_and_printed_through_app_ui() {
        assumeTrue(
            "Ticket hardware path requires --allow-real-charge and --run-ticket-hardware-path",
            config.allowRealCharge && config.runTicketHardwarePath,
        )
        assumeTrue("Seeded ticket number is required", config.seededTicketNumber.isNotBlank())

        robot.ensureLoggedIn()
        robot.navigateBottom("Encargos", LcxTestTags.TICKET_LIST_ROOT)
        robot.clickTicketListItem(
            LcxTestTags.ticketListItem(config.seededTicketNumber),
            timeoutMs = LONG_TIMEOUT_MS,
        )
        robot.waitForTag(LcxTestTags.TICKET_DETAIL_ROOT, timeoutMs = LONG_TIMEOUT_MS)
        robot.clickTag(LcxTestTags.TICKET_DETAIL_CHARGE_BUTTON, timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForTag(LcxTestTags.CHARGE_ROOT, timeoutMs = LONG_TIMEOUT_MS)
        robot.clickTag(LcxTestTags.CHARGE_START_BUTTON, timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForChargeScreenSuccess(timeoutMs = config.chargeTimeoutMs)
        robot.clickTag(LcxTestTags.CHARGE_PRINT_BUTTON, timeoutMs = LONG_TIMEOUT_MS)
        robot.waitForTag(LcxTestTags.PRINT_ROOT, timeoutMs = LONG_TIMEOUT_MS)
        robot.clickTag(LcxTestTags.PRINT_START_BUTTON, timeoutMs = LONG_TIMEOUT_MS)
        robot.completeTicketPrint(timeoutMs = LONG_TIMEOUT_MS)
    }
}

class HardwareE2eRobot(
    private val compose: ComposeTestRule,
    private val device: UiDevice,
    val targetPackage: String,
    private val config: HardwareE2eConfig,
) {
    fun wakeDevice() {
        device.wakeUp()
        device.executeShellCommand("cmd statusbar collapse")
        device.waitForIdle()
    }

    fun ensureLoggedIn() {
        waitForLoginOrAuthenticated(timeoutMs = LONG_TIMEOUT_MS)
        if (hasTag(LcxTestTags.DASHBOARD_ROOT) || hasDeviceText("Inicio")) return
        if (isAuthenticatedScreenVisible()) {
            navigateBottom("Inicio", LcxTestTags.DASHBOARD_ROOT)
            return
        }

        if (waitForOptionalTag(LcxTestTags.loginBranch(config.branch), timeoutMs = LONG_TIMEOUT_MS)) {
            clickTag(LcxTestTags.loginBranch(config.branch), timeoutMs = DEFAULT_TIMEOUT_MS)
        }
        clickTag(LcxTestTags.loginOperator(config.operatorName), timeoutMs = LONG_TIMEOUT_MS)
        waitForContentDescription("PIN", timeoutMs = DEFAULT_TIMEOUT_MS)
        compose.onAllNodesWithContentDescription("PIN", useUnmergedTree = true)
            .onFirst()
            .performTextClearance()
        compose.onAllNodesWithContentDescription("PIN", useUnmergedTree = true)
            .onFirst()
            .performTextInput(config.pin)
        clickTag(LcxTestTags.LOGIN_SUBMIT_PIN, timeoutMs = DEFAULT_TIMEOUT_MS)
        waitForTag(LcxTestTags.DASHBOARD_ROOT, timeoutMs = LONG_TIMEOUT_MS)
    }

    fun navigateBottom(label: String, targetRootTag: String) {
        if (hasTag(targetRootTag)) return
        clickTag(LcxTestTags.bottomNav(label), timeoutMs = DEFAULT_TIMEOUT_MS)
        waitForTag(targetRootTag, timeoutMs = LONG_TIMEOUT_MS)
    }

    fun openWaterFromDrawer() {
        navigateBottom("Inicio", LcxTestTags.DASHBOARD_ROOT)
        openDrawer()
        clickTag(LcxTestTags.drawerItem("Agua"), timeoutMs = DEFAULT_TIMEOUT_MS)
        waitForTag(LcxTestTags.WATER_ROOT, timeoutMs = LONG_TIMEOUT_MS)
    }

    fun openPaymentDiagnostics() {
        openMore()
        clickMoreItem("Diagnosticos de pagos", timeoutMs = LONG_TIMEOUT_MS)
        waitForTag(LcxTestTags.PAYMENT_DIAGNOSTICS_ROOT, timeoutMs = LONG_TIMEOUT_MS)
    }

    fun openPrinterSettings() {
        val openedWithCompose = runCatching {
            openMore()
            clickMoreItem("Debug Brother", timeoutMs = 10_000L)
            waitForTagOrText(
                tag = LcxTestTags.PRINTER_SETTINGS_ROOT,
                text = "Configuracion de impresora",
                timeoutMs = 10_000L,
            )
        }.isSuccess
        if (!openedWithCompose) {
            openMoreWithDevice()
            scrollAndClickDeviceText("Debug Brother", timeoutMs = LONG_TIMEOUT_MS)
            waitForTagOrText(
                tag = LcxTestTags.PRINTER_SETTINGS_ROOT,
                text = "Configuracion de impresora",
                timeoutMs = LONG_TIMEOUT_MS,
            )
        }
    }

    fun waitForDiagnosticChargeSuccess(timeoutMs: Long) {
        device.wait(Until.hasObject(By.pkg(ZETTLE_PACKAGE)), 30_000L)
        waitForText("Exito", substring = true, timeoutMs = timeoutMs)
    }

    fun waitForChargeScreenSuccess(timeoutMs: Long) {
        device.wait(Until.hasObject(By.pkg(ZETTLE_PACKAGE)), 30_000L)
        waitForTag(LcxTestTags.CHARGE_SUCCESS_ROOT, timeoutMs = timeoutMs)
    }

    fun completeTicketPrint(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when {
                hasTag(LcxTestTags.PRINT_SUCCESS_ROOT) -> return
                hasTag(LcxTestTags.PRINTER_CARD) -> clickTag(LcxTestTags.PRINTER_CARD, timeoutMs = 5_000L)
            }
            Thread.sleep(500L)
        }
        fail("Timed out waiting for ticket print success")
    }

    fun clickTag(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        waitForTag(tag, timeoutMs)
        val node = compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst()
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed().performClick()
    }

    fun clickTagIfPresent(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        if (!waitForOptionalTag(tag, timeoutMs)) return false
        clickTag(tag, timeoutMs = 5_000L)
        return true
    }

    fun clickTicketListItem(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        scrollToTagInContainer(LcxTestTags.TICKET_LIST_SCROLL, tag, timeoutMs)
        clickTag(tag, timeoutMs = 5_000L)
    }

    fun clickPrinterCard(nameOrAddress: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        if (runCatching { clickTag(LcxTestTags.printerCard(nameOrAddress), timeoutMs = 5_000L) }.isSuccess) {
            return
        }
        val matcher = hasClickAction() and hasAnyDescendant(
            hasComposeText(nameOrAddress, substring = true),
        )
        val foundWithCompose = runCatching {
            waitForNode(matcher, timeoutMs)
            compose.onNodeWithTag(LcxTestTags.PRINTER_SETTINGS_ROOT, useUnmergedTree = true)
                .performScrollToNode(matcher)
            compose.onAllNodes(matcher, useUnmergedTree = true)
                .onFirst()
                .assertIsDisplayed()
                .performClick()
        }.isSuccess
        if (!foundWithCompose) {
            scrollAndClickDeviceText(nameOrAddress, timeoutMs = timeoutMs, substring = true)
        }
    }

    fun clickDiscoverPrinters(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        if (runCatching { clickTag(LcxTestTags.PRINTER_DISCOVER_BUTTON, timeoutMs = 5_000L) }.isSuccess) {
            return
        }
        clickDeviceText("Buscar impresoras", timeoutMs = timeoutMs)
    }

    fun clickPrinterTestPrint(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        if (runCatching { clickTag(LcxTestTags.PRINTER_TEST_PRINT_BUTTON, timeoutMs = 5_000L) }.isSuccess) {
            return
        }
        clickDeviceText("Imprimir prueba", timeoutMs = timeoutMs)
    }

    fun waitForTag(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        compose.waitUntil(timeoutMillis = timeoutMs) { hasTag(tag) }
    }

    private fun waitForNode(
        matcher: androidx.compose.ui.test.SemanticsMatcher,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ) {
        compose.waitUntil(timeoutMillis = timeoutMs) {
            runCatching {
                compose.onAllNodes(matcher, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ) {
        val selector = if (substring) By.textContains(text) else By.text(text)
        compose.waitUntil(timeoutMillis = timeoutMs) {
            hasText(text, substring = substring) || hasDeviceObject(selector)
        }
        if (hasText(text, substring = substring)) {
            val node = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst()
            runCatching { node.performScrollTo() }
            node.assertIsDisplayed()
        } else if (!hasDeviceObject(selector)) {
            fail("Timed out waiting for text: $text")
        }
    }

    private fun waitForTagOrText(tag: String, text: String, timeoutMs: Long) {
        val selector = By.textContains(text)
        compose.waitUntil(timeoutMillis = timeoutMs) {
            hasTag(tag) || hasDeviceObject(selector)
        }
    }

    private fun openMore() {
        openDrawer()
        clickTag(LcxTestTags.DRAWER_MORE, timeoutMs = DEFAULT_TIMEOUT_MS)
        waitForTag(LcxTestTags.MORE_ROOT, timeoutMs = LONG_TIMEOUT_MS)
    }

    private fun openMoreWithDevice() {
        clickDeviceDescription("Menú", timeoutMs = DEFAULT_TIMEOUT_MS)
        if (!clickDeviceText("Más", timeoutMs = 10_000L)) {
            clickDeviceText("Mas", timeoutMs = 10_000L)
        }
    }

    private fun openDrawer() {
        compose.onAllNodesWithContentDescription("Menú", useUnmergedTree = true)
            .onFirst()
            .performClick()
        waitForTag(LcxTestTags.DRAWER_MORE, timeoutMs = DEFAULT_TIMEOUT_MS)
    }

    private fun clickText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        waitForText(text, timeoutMs = timeoutMs)
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun clickMoreItem(label: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        val tag = LcxTestTags.moreItem(label)
        scrollToTagInContainer(LcxTestTags.MORE_LIST, tag, timeoutMs)
        clickTag(tag, timeoutMs = 5_000L)
    }

    private fun scrollToTagInContainer(containerTag: String, childTag: String, timeoutMs: Long) {
        waitForTag(containerTag, timeoutMs)
        compose.onNodeWithTag(containerTag, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(childTag))
        waitForTag(childTag, timeoutMs = 5_000L)
    }

    private fun waitForContentDescription(description: String, timeoutMs: Long) {
        compose.waitUntil(timeoutMillis = timeoutMs) {
            runCatching {
                compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitForOptionalTag(tag: String, timeoutMs: Long): Boolean {
        return runCatching {
            waitForTag(tag, timeoutMs)
            true
        }.getOrDefault(false)
    }

    private fun waitForLoginOrAuthenticated(timeoutMs: Long) {
        compose.waitUntil(timeoutMillis = timeoutMs) {
            hasTag(LcxTestTags.LOGIN_ROOT) ||
                isAuthenticatedScreenVisible() ||
                isAuthenticatedDeviceScreenVisible()
        }
    }

    private fun isAuthenticatedScreenVisible(): Boolean {
        return listOf(
            LcxTestTags.WATER_ROOT,
            LcxTestTags.CHECKLIST_ROOT,
            LcxTestTags.CASH_ROOT,
            LcxTestTags.TICKET_LIST_ROOT,
            LcxTestTags.MORE_ROOT,
            LcxTestTags.PAYMENT_DIAGNOSTICS_ROOT,
            LcxTestTags.PRINTER_SETTINGS_ROOT,
            LcxTestTags.TICKET_DETAIL_ROOT,
            LcxTestTags.CHARGE_ROOT,
            LcxTestTags.PRINT_ROOT,
        ).any(::hasTag)
    }

    private fun hasTag(tag: String): Boolean {
        return runCatching {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }.getOrDefault(false)
    }

    private fun hasText(text: String, substring: Boolean): Boolean {
        return runCatching {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }.getOrDefault(false)
    }

    private fun isAuthenticatedDeviceScreenVisible(): Boolean {
        return listOf("Inicio", "Ventas", "Encargos", "Checklist", "Caja", "Registrar agua")
            .any(::hasDeviceText)
    }

    private fun hasDeviceText(text: String): Boolean = hasDeviceObject(By.text(text))

    private fun hasDeviceObject(selector: androidx.test.uiautomator.BySelector): Boolean {
        return runCatching { device.hasObject(selector) }.getOrDefault(false)
    }

    private fun clickDeviceText(
        text: String,
        timeoutMs: Long,
        substring: Boolean = false,
    ): Boolean {
        val selector = if (substring) By.textContains(text) else By.text(text)
        val node = device.wait(Until.findObject(selector), timeoutMs) ?: return false
        node.click()
        device.waitForIdle()
        return true
    }

    private fun clickDeviceDescription(description: String, timeoutMs: Long): Boolean {
        val node = device.wait(Until.findObject(By.desc(description)), timeoutMs) ?: return false
        node.click()
        device.waitForIdle()
        return true
    }

    private fun scrollAndClickDeviceText(
        text: String,
        timeoutMs: Long,
        substring: Boolean = false,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (clickDeviceText(text, timeoutMs = 1_000L, substring = substring)) return
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.82f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.32f).toInt(),
                18,
            )
            device.waitForIdle()
        }
        fail("Timed out waiting for device text: $text")
    }
}
