package com.cleanx.lcx.core.operational

import com.cleanx.lcx.core.network.SupabaseTableClient
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperatorOperationalRepository @Inject constructor(
    private val supabase: SupabaseTableClient,
) {

    suspend fun loadSnapshot(branch: String?): OperatorOperationalSnapshot = coroutineScope {
        val today = LocalDate.now().toString()

        val waterDeferred = async { loadWaterSignal(branch = branch, today = today) }
        val cashDeferred = async { loadCashSignal(today = today) }
        val entryChecklistDeferred = async {
            loadChecklistSignal(type = "entrada", today = today)
        }
        val exitChecklistDeferred = async {
            loadChecklistSignal(type = "salida", today = today)
        }

        buildOperatorOperationalSnapshot(
            signals = OperatorOperationalSignals(
                water = waterDeferred.await(),
                cash = cashDeferred.await(),
                checklists = OperatorOperationalChecklistSignals(
                    entry = entryChecklistDeferred.await(),
                    exit = exitChecklistDeferred.await(),
                ),
            ),
        )
    }

    private suspend fun loadWaterSignal(
        branch: String?,
        today: String,
    ): OperatorOperationalWaterSignal {
        val latestRecord = supabase.selectWithRequest<TodayWaterLevelRow>("water_levels") {
            filter {
                gte("created_at", "${today}T00:00:00")
                lte("created_at", "${today}T23:59:59")
                if (branch != null) {
                    eq("branch", branch)
                }
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.getOrElse { error ->
            Timber.w(error, "loadWaterSignal failed")
            emptyList()
        }.firstOrNull()

        return OperatorOperationalWaterSignal(
            recordedToday = latestRecord != null,
            lastLevelPercentage = latestRecord?.levelPercentage,
            lastRecordedAt = latestRecord?.createdAt,
            lastStatusLabel = latestRecord?.status?.lowercase(),
        )
    }

    private suspend fun loadCashSignal(
        today: String,
    ): OperatorOperationalCashSignal {
        val movementTypes = supabase.selectWithRequest<TodayCashMovementRow>("cash_movements") {
            filter {
                gte("created_at", "${today}T00:00:00")
                lte("created_at", "${today}T23:59:59")
            }
        }.getOrElse { error ->
            Timber.w(error, "loadCashSignal failed")
            emptyList()
        }.map { it.type.lowercase() }
            .toSet()

        return OperatorOperationalCashSignal(
            openingRegisteredToday = movementTypes.contains("opening"),
            closingRegisteredToday = movementTypes.contains("closing"),
        )
    }

    private suspend fun loadChecklistSignal(
        type: String,
        today: String,
    ): OperatorOperationalChecklistSignal {
        val current = selectChecklistByField(
            field = "checklist_type",
            value = type,
            today = today,
        ).getOrElse { error ->
            Timber.w(error, "loadChecklistSignal current lookup failed for %s", type)
            null
        }
        val resolved = current ?: selectChecklistByField(
            field = "notes",
            value = type,
            today = today,
        ).getOrElse { error ->
            Timber.w(error, "loadChecklistSignal legacy lookup failed for %s", type)
            null
        }

        return OperatorOperationalChecklistSignal(
            existsToday = resolved != null,
            progress = OperatorOperationalProgress.fromPersisted(resolved?.status),
        )
    }

    private suspend fun selectChecklistByField(
        field: String,
        value: String,
        today: String,
    ): Result<OperationalChecklistRow?> {
        return supabase.selectWithRequest<OperationalChecklistRow>("maintenance_checklists") {
            filter {
                eq(field, value)
                eq("checklist_date", today)
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.map { rows -> rows.firstOrNull() }
    }
}

@Serializable
private data class TodayWaterLevelRow(
    @SerialName("level_percentage") val levelPercentage: Int? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class TodayCashMovementRow(
    val type: String,
)

@Serializable
private data class OperationalChecklistRow(
    val id: String? = null,
    val status: String? = null,
)
