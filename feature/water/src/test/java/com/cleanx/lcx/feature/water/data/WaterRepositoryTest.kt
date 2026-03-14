package com.cleanx.lcx.feature.water.data

import com.cleanx.lcx.core.model.WaterLevelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class WaterRepositoryTest {

    @Test
    fun `defaultWaterLevel matches pwa initial snapshot`() {
        val level = defaultWaterLevel(branch = "sucursal-centro")

        assertEquals(DEFAULT_WATER_LEVEL_PERCENTAGE, level.levelPercentage)
        assertEquals(7_500, level.liters)
        assertEquals(TANK_CAPACITY_LITERS, level.tankCapacity)
        assertEquals(WaterLevelStatus.OPTIMAL, level.status)
        assertEquals("Nivel inicial", level.action)
        assertEquals("sucursal-centro", level.branch)
    }

    @Test
    fun `buildWaterLevelInsert persists recorded_by branch and pwa action label`() {
        val insert = buildWaterLevelInsert(
            percentage = 25,
            recordedBy = "user-1",
            branch = "sucursal-centro",
        )

        assertEquals(25, insert.levelPercentage)
        assertEquals(2_500, insert.liters)
        assertEquals(WaterLevelStatus.LOW, insert.status)
        assertEquals("Nivel registrado", insert.action)
        assertEquals("user-1", insert.recordedBy)
        assertEquals("sucursal-centro", insert.branch)
    }

    @Test
    fun `buildWaterOrderInsert persists provider and branch context`() {
        val provider = WATER_PROVIDERS.first { it.id == "cristal" }

        val insert = buildWaterOrderInsert(
            provider = provider,
            currentPercentage = 18,
            recordedBy = "user-2",
            branch = "sucursal-norte",
        )

        assertEquals(18, insert.levelPercentage)
        assertEquals(1_800, insert.liters)
        assertEquals(WaterLevelStatus.CRITICAL, insert.status)
        assertEquals("Agua pedida - ${provider.name}", insert.action)
        assertEquals(provider.id, insert.providerId)
        assertEquals(provider.name, insert.providerName)
        assertEquals("user-2", insert.recordedBy)
        assertEquals("sucursal-norte", insert.branch)
    }

    @Test
    fun `buildWaterAlertAuditLog mirrors pwa payload for critical levels`() {
        val auditLog = buildWaterAlertAuditLog(
            levelPercentage = 17,
            liters = 1_700,
            status = WaterLevelStatus.CRITICAL,
            branch = "La Esperanza",
            performedBy = "user-3",
            recordId = "1741838400000",
        )

        assertNotNull(auditLog)
        requireNotNull(auditLog)
        assertEquals("water_alerts", auditLog.tableName)
        assertEquals("push_notification", auditLog.action)
        assertEquals("1741838400000", auditLog.recordId)
        assertEquals("user-3", auditLog.performedBy)
        assertEquals("critical", auditLog.changedData["status"]?.jsonPrimitive?.content)
        assertEquals(17, auditLog.changedData["level_percentage"]?.jsonPrimitive?.int)
        assertEquals(1_700, auditLog.changedData["liters"]?.jsonPrimitive?.int)
        assertEquals("La Esperanza", auditLog.changedData["branch"]?.jsonPrimitive?.content)
    }

    @Test
    fun `buildWaterAlertAuditLog also logs low levels`() {
        val auditLog = buildWaterAlertAuditLog(
            levelPercentage = 25,
            liters = 2_500,
            status = WaterLevelStatus.LOW,
            branch = null,
            performedBy = "user-4",
            recordId = "1741838460000",
        )

        assertNotNull(auditLog)
        requireNotNull(auditLog)
        assertEquals("low", auditLog.changedData["status"]?.jsonPrimitive?.content)
        assertEquals("null", auditLog.changedData["branch"]?.toString())
    }

    @Test
    fun `buildWaterAlertAuditLog skips non alert levels`() {
        assertNull(
            buildWaterAlertAuditLog(
                levelPercentage = 55,
                liters = 5_500,
                status = WaterLevelStatus.NORMAL,
                branch = "La Esperanza",
                performedBy = "user-5",
                recordId = "1741838520000",
            ),
        )
        assertNull(
            buildWaterAlertAuditLog(
                levelPercentage = 85,
                liters = 8_500,
                status = WaterLevelStatus.OPTIMAL,
                branch = "La Esperanza",
                performedBy = "user-5",
                recordId = "1741838580000",
            ),
        )
    }
}
