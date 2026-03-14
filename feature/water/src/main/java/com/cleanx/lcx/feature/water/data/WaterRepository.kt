package com.cleanx.lcx.feature.water.data

import com.cleanx.lcx.core.model.WaterLevelStatus
import com.cleanx.lcx.core.network.SupabaseTableClient
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Tank capacity in liters, matching the PWA constant. */
const val TANK_CAPACITY_LITERS = 10_000
const val DEFAULT_WATER_LEVEL_PERCENTAGE = 75

private const val DEFAULT_WATER_ACTION = "Nivel inicial"
private const val RECORDED_WATER_ACTION = "Nivel registrado"
private const val AUDIT_LOG_TABLE = "audit_logs"
private const val WATER_ALERTS_TABLE = "water_alerts"
private const val PUSH_NOTIFICATION_ACTION = "push_notification"

/**
 * Extended model for water_levels rows joined with profiles.
 * The Supabase PostgREST join embeds the profile as a nested object.
 */
@Serializable
data class WaterLevelWithUser(
    val id: String? = null,
    @SerialName("level_percentage") val levelPercentage: Int,
    val liters: Int? = null,
    @SerialName("tank_capacity") val tankCapacity: Int? = null,
    val status: WaterLevelStatus? = null,
    val action: String? = null,
    val notes: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("recorded_by") val recordedBy: String? = null,
    val branch: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val user: UserProfile? = null,
) {
    @Serializable
    data class UserProfile(
        @SerialName("full_name") val fullName: String? = null,
    )
}

/**
 * Body for inserting a new water level record.
 * Omits id, created_at, updated_at (server-generated).
 */
@Serializable
data class WaterLevelInsert(
    @SerialName("level_percentage") val levelPercentage: Int,
    val liters: Int,
    @SerialName("tank_capacity") val tankCapacity: Int = TANK_CAPACITY_LITERS,
    val status: WaterLevelStatus,
    val action: String,
    val notes: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("recorded_by") val recordedBy: String? = null,
    val branch: String? = null,
)

@Serializable
internal data class WaterAlertAuditLogInsert(
    @SerialName("table_name") val tableName: String,
    @SerialName("record_id") val recordId: String,
    val action: String,
    @SerialName("changed_data") val changedData: JsonObject,
    @SerialName("performed_by") val performedBy: String? = null,
)

@Singleton
class WaterRepository @Inject constructor(
    private val supabase: SupabaseTableClient,
) {
    private val table = "water_levels"

    /**
     * Fetch the most recent water level for the given branch.
     * Mirrors PWA's `getCurrentWaterLevel(branch)`.
     */
    suspend fun getCurrentWaterLevel(branch: String? = null): Result<WaterLevelWithUser?> {
        Timber.d("Fetching current water level (branch=%s)", branch)
        val columns = Columns.raw(
            "*, user:profiles!water_levels_recorded_by_fkey(full_name)"
        )
        return supabase.selectWithRequest<WaterLevelWithUser>(
            table = table,
            columns = columns,
        ) {
            if (branch != null) {
                filter { eq("branch", branch) }
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(1)
        }.map { rows ->
            rows.firstOrNull() ?: defaultWaterLevel(branch = branch)
        }
    }

    /**
     * Fetch water level history with user names.
     * Mirrors PWA's `getWaterLevelHistoryWithUser(limit, branch)`.
     */
    suspend fun getWaterLevelHistory(
        limit: Long = 50,
        branch: String? = null,
    ): Result<List<WaterLevelWithUser>> {
        Timber.d("Fetching water level history (limit=%d, branch=%s)", limit, branch)
        val columns = Columns.raw(
            "*, user:profiles!water_levels_recorded_by_fkey(full_name)"
        )
        return supabase.selectWithRequest<WaterLevelWithUser>(
            table = table,
            columns = columns,
        ) {
            if (branch != null) {
                filter { eq("branch", branch) }
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(limit)
        }
    }

    /**
     * Record a new water level.
     * Mirrors PWA's `recordWaterLevel(percentage, options)`.
     */
    suspend fun recordWaterLevel(
        percentage: Int,
        recordedBy: String? = null,
        branch: String? = null,
        notes: String? = null,
    ): Result<WaterLevelInsert> {
        val liters = (percentage * TANK_CAPACITY_LITERS) / 100
        Timber.d(
            "Recording water level: %d%% (%d L), status=%s",
            percentage, liters, percentageToStatus(percentage),
        )
        val insert = buildWaterLevelInsert(
            percentage = percentage,
            recordedBy = recordedBy,
            branch = branch,
            notes = notes,
        )
        val result = supabase.insert(table, insert)
        if (result.isSuccess) {
            logWaterAlertNotification(
                levelPercentage = insert.levelPercentage,
                liters = insert.liters,
                status = insert.status,
                branch = insert.branch,
                performedBy = insert.recordedBy,
            )
        }
        return result
    }

    /**
     * Record a water order for a specific provider.
     * Mirrors PWA's `recordWaterOrder(providerId, currentLevel, branch)`.
     */
    suspend fun recordWaterOrder(
        provider: WaterProvider,
        currentPercentage: Int,
        recordedBy: String? = null,
        branch: String? = null,
    ): Result<WaterLevelInsert> {
        Timber.d(
            "Recording water order: provider=%s, level=%d%%",
            provider.name, currentPercentage,
        )
        val insert = buildWaterOrderInsert(
            provider = provider,
            currentPercentage = currentPercentage,
            recordedBy = recordedBy,
            branch = branch,
        )
        val result = supabase.insert(table, insert)
        if (result.isSuccess) {
            logWaterAlertNotification(
                levelPercentage = insert.levelPercentage,
                liters = insert.liters,
                status = insert.status,
                branch = insert.branch,
                performedBy = insert.recordedBy,
            )
        }
        return result
    }

    private suspend fun logWaterAlertNotification(
        levelPercentage: Int,
        liters: Int,
        status: WaterLevelStatus,
        branch: String?,
        performedBy: String?,
    ) {
        val auditLog = buildWaterAlertAuditLog(
            levelPercentage = levelPercentage,
            liters = liters,
            status = status,
            branch = branch,
            performedBy = performedBy,
            recordId = System.currentTimeMillis().toString(),
        ) ?: return

        supabase.insert(AUDIT_LOG_TABLE, auditLog)
            .onSuccess {
                Timber.d(
                    "Recorded water alert audit log: status=%s, level=%d%%",
                    status,
                    levelPercentage,
                )
            }
            .onFailure { error ->
                Timber.w(error, "Failed to record water alert audit log")
            }
    }

    companion object {
        fun percentageToStatus(percentage: Int): WaterLevelStatus = when {
            percentage < 20 -> WaterLevelStatus.CRITICAL
            percentage < 40 -> WaterLevelStatus.LOW
            percentage < 70 -> WaterLevelStatus.NORMAL
            else -> WaterLevelStatus.OPTIMAL
        }
    }
}

internal fun defaultWaterLevel(branch: String?): WaterLevelWithUser {
    val liters = (DEFAULT_WATER_LEVEL_PERCENTAGE * TANK_CAPACITY_LITERS) / 100
    return WaterLevelWithUser(
        id = "default",
        levelPercentage = DEFAULT_WATER_LEVEL_PERCENTAGE,
        liters = liters,
        tankCapacity = TANK_CAPACITY_LITERS,
        status = WaterRepository.percentageToStatus(DEFAULT_WATER_LEVEL_PERCENTAGE),
        action = DEFAULT_WATER_ACTION,
        branch = branch,
    )
}

internal fun buildWaterLevelInsert(
    percentage: Int,
    recordedBy: String?,
    branch: String?,
    notes: String? = null,
): WaterLevelInsert {
    val liters = (percentage * TANK_CAPACITY_LITERS) / 100
    return WaterLevelInsert(
        levelPercentage = percentage,
        liters = liters,
        status = WaterRepository.percentageToStatus(percentage),
        action = RECORDED_WATER_ACTION,
        recordedBy = recordedBy,
        branch = branch,
        notes = notes,
    )
}

internal fun buildWaterOrderInsert(
    provider: WaterProvider,
    currentPercentage: Int,
    recordedBy: String?,
    branch: String?,
): WaterLevelInsert {
    val liters = (currentPercentage * TANK_CAPACITY_LITERS) / 100
    return WaterLevelInsert(
        levelPercentage = currentPercentage,
        liters = liters,
        status = WaterRepository.percentageToStatus(currentPercentage),
        action = "Agua pedida - ${provider.name}",
        providerId = provider.id,
        providerName = provider.name,
        recordedBy = recordedBy,
        branch = branch,
    )
}

internal fun buildWaterAlertAuditLog(
    levelPercentage: Int,
    liters: Int,
    status: WaterLevelStatus,
    branch: String?,
    performedBy: String?,
    recordId: String,
): WaterAlertAuditLogInsert? {
    if (status != WaterLevelStatus.CRITICAL && status != WaterLevelStatus.LOW) {
        return null
    }

    return WaterAlertAuditLogInsert(
        tableName = WATER_ALERTS_TABLE,
        recordId = recordId,
        action = PUSH_NOTIFICATION_ACTION,
        changedData = buildJsonObject {
            put("status", JsonPrimitive(status.name.lowercase()))
            put("level_percentage", JsonPrimitive(levelPercentage))
            put("liters", JsonPrimitive(liters))
            put("branch", branch?.let(::JsonPrimitive) ?: JsonNull)
        },
        performedBy = performedBy,
    )
}
