package com.cleanx.lcx.feature.checklist.data

import com.cleanx.lcx.core.network.SupabaseTableClient
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for checklist operations against the same Supabase tables used by
 * the PWA (`maintenance_checklists`, `checklist_items`, `cash_movements`).
 */
@Singleton
class ChecklistRepository @Inject constructor(
    private val supabase: SupabaseTableClient,
) {
    private val checklistTable = "maintenance_checklists"
    private val itemTable = "checklist_items"

    suspend fun getTodayChecklist(type: ChecklistType): Result<Pair<Checklist, List<ChecklistItem>>> {
        val today = LocalDate.now().toString()
        Timber.d("Loading today's checklist type=%s date=%s", type, today)

        return runCatching {
            var checklist = selectTodayChecklist(type, today).getOrThrow()
            if (checklist == null) {
                checklist = createChecklist(type, today).getOrElse { createError ->
                    selectTodayChecklist(type, today).getOrThrow() ?: throw createError
                }
            }

            val checklistId = checklist.id
                ?: throw IllegalStateException("Checklist sin id despues de cargar/crear.")

            val items = selectChecklistItems(checklistId).getOrThrow().ifEmpty {
                insertChecklistItems(createTemplateItems(type, checklistId)).getOrThrow()
            }

            checklist to sortChecklistItems(items)
        }
    }

    suspend fun updateChecklistItem(
        itemId: String,
        completed: Boolean,
        userId: String? = null,
    ): Result<ChecklistItem> {
        val now = OffsetDateTime.now().toString()
        val payload = ChecklistItemUpdate(
            isCompleted = completed,
            completedBy = if (completed) userId else null,
            completedAt = if (completed) now else null,
        )

        return supabase.updateReturning<ChecklistItemUpdate, ChecklistItem>(itemTable, payload) {
            eq("id", itemId)
        }.map { rows ->
            rows.firstOrNull()
                ?: throw IllegalStateException("No se actualizo el item $itemId.")
        }.onFailure { error ->
            Timber.e(error, "updateChecklistItem(%s, %s) failed", itemId, completed)
        }
    }

    suspend fun completeChecklist(
        checklistId: String,
        notes: String? = null,
        userId: String? = null,
    ): Result<Checklist> {
        validateChecklistCompletion(checklistId).getOrElse { error ->
            return Result.failure(error)
        }

        val payload = ChecklistCompletionUpdate(
            status = ChecklistStatus.COMPLETED,
            completedBy = userId,
            completedAt = OffsetDateTime.now().toString(),
            completionNotes = notes,
        )

        return supabase.updateReturning<ChecklistCompletionUpdate, Checklist>(checklistTable, payload) {
            eq("id", checklistId)
        }.map { rows ->
            rows.firstOrNull()
                ?: throw IllegalStateException("No se actualizo el checklist $checklistId.")
        }.onFailure { error ->
            Timber.e(error, "completeChecklist(%s) failed", checklistId)
        }
    }

    suspend fun getChecklistHistory(limit: Long = 30): Result<List<Checklist>> {
        return supabase.selectWithRequest<Checklist>(checklistTable) {
            filter { eq("status", ChecklistStatus.COMPLETED.serializedValue()) }
            order("checklist_date", Order.DESCENDING)
            limit(limit)
        }.onFailure { error ->
            Timber.e(error, "getChecklistHistory failed")
        }
    }

    suspend fun hasWaterLevelToday(branch: String? = null): Boolean {
        val today = LocalDate.now().toString()
        return supabase.selectWithRequest<IdOnly>("water_levels") {
            filter {
                gte("created_at", "${today}T00:00:00")
                lte("created_at", "${today}T23:59:59")
                if (branch != null) {
                    eq("branch", branch)
                }
            }
            limit(1)
        }.map { it.isNotEmpty() }
            .getOrElse { error ->
                Timber.w(error, "hasWaterLevelToday check failed")
                false
            }
    }

    suspend fun hasCashMovementToday(type: String): Boolean {
        val today = LocalDate.now().toString()
        return supabase.selectWithRequest<IdOnly>("cash_movements") {
            filter {
                eq("type", type)
                gte("created_at", "${today}T00:00:00")
                lte("created_at", "${today}T23:59:59")
            }
            limit(1)
        }.map { it.isNotEmpty() }
            .getOrElse { error ->
                Timber.w(error, "hasCashMovementToday(%s) check failed", type)
                false
            }
    }

    private suspend fun selectTodayChecklist(
        type: ChecklistType,
        date: String,
    ): Result<Checklist?> {
        return runCatching {
            val current = selectChecklistByField("checklist_type", type.serializedValue(), date).getOrThrow()
            if (current != null) {
                current
            } else {
                val legacy = selectChecklistByField("notes", type.serializedValue(), date).getOrThrow()
                if (legacy?.id != null && legacy.type == null) {
                    backfillLegacyType(legacy.id, type).getOrElse { legacy }
                } else {
                    legacy
                }
            }
        }
    }

    private suspend fun selectChecklistByField(
        field: String,
        value: String,
        date: String,
    ): Result<Checklist?> {
        return supabase.selectWithRequest<Checklist>(checklistTable) {
            filter {
                eq(field, value)
                eq("checklist_date", date)
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.map { it.firstOrNull() }
    }

    private suspend fun createChecklist(
        type: ChecklistType,
        date: String,
    ): Result<Checklist> {
        return supabase.insertReturning<ChecklistInsert, Checklist>(
            table = checklistTable,
            value = ChecklistInsert(
                type = type,
                date = date,
            ),
        ).onFailure { error ->
            Timber.e(error, "createChecklist(%s, %s) failed", type, date)
        }
    }

    private suspend fun backfillLegacyType(
        checklistId: String,
        type: ChecklistType,
    ): Result<Checklist> {
        return supabase.updateReturning<ChecklistTypeBackfill, Checklist>(
            checklistTable,
            ChecklistTypeBackfill(type = type),
        ) {
            eq("id", checklistId)
        }.map { rows ->
            rows.firstOrNull()
                ?: throw IllegalStateException("No se pudo backfillear el checklist $checklistId.")
        }.onFailure { error ->
            Timber.w(error, "backfillLegacyType(%s, %s) failed", checklistId, type)
        }
    }

    private suspend fun selectChecklistItems(checklistId: String): Result<List<ChecklistItem>> {
        return supabase.selectWithRequest<ChecklistItem>(itemTable) {
            filter { eq("checklist_id", checklistId) }
            order("created_at", Order.ASCENDING)
        }
    }

    private suspend fun validateChecklistCompletion(checklistId: String): Result<Unit> {
        return selectChecklistItems(checklistId).map { items ->
            val gate = evaluateChecklistCompletion(items)
            if (!gate.canComplete) {
                throw IllegalStateException(gate.reason ?: "No se puede completar el checklist.")
            }
        }.onFailure { error ->
            Timber.w(error, "validateChecklistCompletion(%s) failed", checklistId)
        }
    }

    private suspend fun insertChecklistItems(
        items: List<ChecklistItemInsert>,
    ): Result<List<ChecklistItem>> {
        return supabase.insertManyReturning<ChecklistItemInsert, ChecklistItem>(itemTable, items)
            .onFailure { error ->
            Timber.e(error, "insertChecklistItems failed")
        }
    }

    private fun createTemplateItems(
        type: ChecklistType,
        checklistId: String,
    ): List<ChecklistItemInsert> {
        return CHECKLIST_TEMPLATES
            .filter { it.type == type }
            .map { template ->
                ChecklistItemInsert(
                    checklistId = checklistId,
                    itemDescription = "${template.title}: ${template.description}",
                    notes = """{"templateId":"${template.id}","category":"${template.category}","required":${template.required}}""",
                )
            }
    }
}

@Serializable
private data class ChecklistInsert(
    @SerialName("checklist_type") val type: ChecklistType,
    @SerialName("checklist_date") val date: String,
    val status: ChecklistStatus = ChecklistStatus.PENDING,
)

@Serializable
private data class ChecklistTypeBackfill(
    @SerialName("checklist_type") val type: ChecklistType,
)

@Serializable
private data class ChecklistCompletionUpdate(
    val status: ChecklistStatus,
    @SerialName("completed_by") val completedBy: String? = null,
    @SerialName("completed_at") val completedAt: String,
    @SerialName("completion_notes") val completionNotes: String? = null,
)

@Serializable
private data class ChecklistItemInsert(
    @SerialName("checklist_id") val checklistId: String,
    @SerialName("item_description") val itemDescription: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    val notes: String? = null,
)

@Serializable
private data class ChecklistItemUpdate(
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("completed_by") val completedBy: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
private data class IdOnly(
    val id: String? = null,
)

private data class ChecklistTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val required: Boolean,
    val type: ChecklistType,
)

private fun ChecklistType.serializedValue(): String = when (this) {
    ChecklistType.ENTRADA -> "entrada"
    ChecklistType.SALIDA -> "salida"
}

private fun ChecklistStatus.serializedValue(): String = when (this) {
    ChecklistStatus.PENDING -> "pending"
    ChecklistStatus.IN_PROGRESS -> "in_progress"
    ChecklistStatus.COMPLETED -> "completed"
}

private val CHECKLIST_TEMPLATES = listOf(
    ChecklistTemplate(
        id = "entry-1",
        title = "Revisar nivel de agua",
        description = "Verificar y registrar el nivel actual del tanque de agua",
        category = "maintenance",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-2",
        title = "Registrar caja inicial",
        description = "Contar y registrar el efectivo inicial del turno",
        category = "admin",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-3",
        title = "Verificar suministros",
        description = "Revisar niveles de jabon, suavizante y otros quimicos",
        category = "maintenance",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-4",
        title = "Inspeccionar maquinas",
        description = "Verificar que todas las lavadoras y secadoras esten funcionando",
        category = "maintenance",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-5",
        title = "Limpiar estacion de trabajo",
        description = "Limpiar y organizar el area de trabajo principal",
        category = "cleaning",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-6",
        title = "Revisar tickets pendientes",
        description = "Verificar tickets del dia anterior y prioridades",
        category = "admin",
        required = true,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-7",
        title = "Verificar sistema de seguridad",
        description = "Comprobar alarmas, camaras y sistemas de emergencia",
        category = "safety",
        required = false,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "entry-8",
        title = "Revisar temperatura ambiente",
        description = "Verificar que la temperatura este en rango optimo",
        category = "maintenance",
        required = false,
        type = ChecklistType.ENTRADA,
    ),
    ChecklistTemplate(
        id = "exit-1",
        title = "Cerrar caja",
        description = "Contar efectivo y realizar cierre de caja del turno",
        category = "admin",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-2",
        title = "Apagar maquinas",
        description = "Apagar todas las lavadoras y secadoras que no esten en uso",
        category = "maintenance",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-3",
        title = "Limpiar area de trabajo",
        description = "Limpiar y organizar toda el area antes de cerrar",
        category = "cleaning",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-4",
        title = "Dejar notas de tickets pendientes",
        description = "Documentar para el siguiente turno los tickets pendientes y observaciones clave",
        category = "admin",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-5",
        title = "Asegurar local",
        description = "Cerrar puertas, ventanas y activar alarma",
        category = "safety",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-6",
        title = "Apagar computadora y luces",
        description = "Apagar computadora/POS y luces (excepto iluminacion de seguridad)",
        category = "safety",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-9",
        title = "Verificar inventario de agua y caja",
        description = "Confirmar nivel de agua y corte final de caja antes de retirarse",
        category = "admin",
        required = true,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-7",
        title = "Revisar banos",
        description = "Asegurarse de que los banos esten limpios y cerrados",
        category = "cleaning",
        required = false,
        type = ChecklistType.SALIDA,
    ),
    ChecklistTemplate(
        id = "exit-8",
        title = "Backup de datos",
        description = "Verificar que los datos del dia esten respaldados",
        category = "admin",
        required = false,
        type = ChecklistType.SALIDA,
    ),
)
