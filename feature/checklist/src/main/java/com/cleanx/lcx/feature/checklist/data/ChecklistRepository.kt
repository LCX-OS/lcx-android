package com.cleanx.lcx.feature.checklist.data

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.network.TokenProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for checklist operations via Supabase PostgREST API.
 *
 * Uses OkHttp directly (the DI-provided client already includes
 * [AuthInterceptor] which adds the Bearer token).
 */
@Singleton
class ChecklistRepository @Inject constructor(
    private val client: OkHttpClient,
    private val config: BuildConfigProvider,
    private val tokenProvider: TokenProvider,
    private val json: Json,
) {

    private val restBase: String
        get() = "${config.supabaseUrl}/rest/v1"

    private val contentType = "application/json".toMediaType()

    // -- GET TODAY'S CHECKLIST ------------------------------------------------

    /**
     * Get today's checklist for the given type, creating it with template
     * items if one doesn't exist yet.
     *
     * Mirrors PWA's `getTodayChecklist(type)`.
     */
    suspend fun getTodayChecklist(type: ChecklistType): Result<Pair<Checklist, List<ChecklistItem>>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val typeStr = when (type) {
            ChecklistType.ENTRADA -> "entrada"
            ChecklistType.SALIDA -> "salida"
        }

        return try {
            // 1. Try to find existing checklist for today
            val existing = selectChecklists(typeStr, today)
            if (existing.isNotEmpty()) {
                val checklist = existing.first()
                val items = selectChecklistItems(checklist.id!!)
                return Result.success(checklist to items)
            }

            // 2. None found – create a new one with template items
            val newChecklist = insertChecklist(
                Checklist(type = type, date = today, status = ChecklistStatus.PENDING)
            )
            val templateItems = createTemplateItems(type, newChecklist.id!!)
            val insertedItems = insertChecklistItems(templateItems)

            Result.success(newChecklist to insertedItems)
        } catch (e: Exception) {
            Timber.e(e, "getTodayChecklist(%s) failed", typeStr)
            Result.failure(e)
        }
    }

    // -- TOGGLE ITEM ----------------------------------------------------------

    /**
     * Toggle a checklist item's completion status.
     * Mirrors PWA's `updateChecklistItem(id, completed)`.
     */
    suspend fun updateChecklistItem(
        itemId: String,
        completed: Boolean,
        userId: String? = null,
    ): Result<ChecklistItem> {
        return try {
            val now = java.time.OffsetDateTime.now().toString()
            val body = buildString {
                append("{")
                append("\"is_completed\":$completed")
                if (completed && userId != null) {
                    append(",\"completed_by\":\"$userId\"")
                    append(",\"completed_at\":\"$now\"")
                } else if (!completed) {
                    append(",\"completed_by\":null")
                    append(",\"completed_at\":null")
                }
                append("}")
            }

            val request = Request.Builder()
                .url("$restBase/checklist_items?id=eq.$itemId")
                .addHeader("apikey", config.supabaseAnonKey)
                .addHeader("Prefer", "return=representation")
                .patch(body.toRequestBody(contentType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw PostgRestException(response.code, response.body?.string())
            }
            val items = json.decodeFromString<List<ChecklistItem>>(
                response.body!!.string()
            )
            Result.success(items.first())
        } catch (e: Exception) {
            Timber.e(e, "updateChecklistItem(%s, %s) failed", itemId, completed)
            Result.failure(e)
        }
    }

    // -- COMPLETE CHECKLIST ---------------------------------------------------

    /**
     * Mark a checklist as completed.
     * Mirrors PWA's `completeChecklist(id, notes)`.
     */
    suspend fun completeChecklist(
        checklistId: String,
        notes: String? = null,
        userId: String? = null,
    ): Result<Checklist> {
        return try {
            val now = java.time.OffsetDateTime.now().toString()
            val body = buildString {
                append("{")
                append("\"status\":\"completed\"")
                if (notes != null) append(",\"notes\":${json.encodeToString(notes)}")
                if (userId != null) append(",\"completed_by\":\"$userId\"")
                append(",\"completed_at\":\"$now\"")
                append("}")
            }

            val request = Request.Builder()
                .url("$restBase/checklists?id=eq.$checklistId")
                .addHeader("apikey", config.supabaseAnonKey)
                .addHeader("Prefer", "return=representation")
                .patch(body.toRequestBody(contentType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw PostgRestException(response.code, response.body?.string())
            }
            val checklists = json.decodeFromString<List<Checklist>>(
                response.body!!.string()
            )
            Result.success(checklists.first())
        } catch (e: Exception) {
            Timber.e(e, "completeChecklist(%s) failed", checklistId)
            Result.failure(e)
        }
    }

    // -- CHECKLIST HISTORY ----------------------------------------------------

    /**
     * Get completed checklists (both types), ordered by date descending.
     */
    suspend fun getChecklistHistory(limit: Int = 30): Result<List<Checklist>> {
        return try {
            val url = "$restBase/checklists" +
                "?status=eq.completed" +
                "&order=date.desc" +
                "&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", config.supabaseAnonKey)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw PostgRestException(response.code, response.body?.string())
            }
            val checklists = json.decodeFromString<List<Checklist>>(
                response.body!!.string()
            )
            Result.success(checklists)
        } catch (e: Exception) {
            Timber.e(e, "getChecklistHistory failed")
            Result.failure(e)
        }
    }

    // -- OPERATIONAL ROUTINE STATUS -------------------------------------------

    /**
     * Check if water level has been recorded today.
     * Used for auto-validation of entry-1.
     */
    suspend fun hasWaterLevelToday(): Boolean {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val url = "$restBase/water_levels" +
                "?created_at=gte.${today}T00:00:00" +
                "&limit=1"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", config.supabaseAnonKey)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            // If array is non-empty, water level was recorded today
            body.contains("\"id\"")
        } catch (e: Exception) {
            Timber.w(e, "hasWaterLevelToday check failed")
            false
        }
    }

    /**
     * Check if cash register has been opened today.
     * Used for auto-validation of entry-2.
     */
    suspend fun hasCashRegisterToday(): Boolean {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val url = "$restBase/cash_registers" +
                "?created_at=gte.${today}T00:00:00" +
                "&limit=1"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", config.supabaseAnonKey)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            body.contains("\"id\"")
        } catch (e: Exception) {
            Timber.w(e, "hasCashRegisterToday check failed")
            false
        }
    }

    // -- PRIVATE HELPERS ------------------------------------------------------

    private fun selectChecklists(type: String, date: String): List<Checklist> {
        val url = "$restBase/checklists" +
            "?type=eq.$type" +
            "&date=eq.$date" +
            "&order=created_at.desc" +
            "&limit=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", config.supabaseAnonKey)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw PostgRestException(response.code, response.body?.string())
        }
        return json.decodeFromString<List<Checklist>>(response.body!!.string())
    }

    private fun selectChecklistItems(checklistId: String): List<ChecklistItem> {
        val url = "$restBase/checklist_items" +
            "?checklist_id=eq.$checklistId" +
            "&order=sort_order.asc"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", config.supabaseAnonKey)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw PostgRestException(response.code, response.body?.string())
        }
        return json.decodeFromString<List<ChecklistItem>>(response.body!!.string())
    }

    private fun insertChecklist(checklist: Checklist): Checklist {
        val body = json.encodeToString(checklist)
        val request = Request.Builder()
            .url("$restBase/checklists")
            .addHeader("apikey", config.supabaseAnonKey)
            .addHeader("Prefer", "return=representation")
            .post(body.toRequestBody(contentType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw PostgRestException(response.code, response.body?.string())
        }
        return json.decodeFromString<List<Checklist>>(response.body!!.string()).first()
    }

    private fun insertChecklistItems(items: List<ChecklistItem>): List<ChecklistItem> {
        val body = json.encodeToString(items)
        val request = Request.Builder()
            .url("$restBase/checklist_items")
            .addHeader("apikey", config.supabaseAnonKey)
            .addHeader("Prefer", "return=representation")
            .post(body.toRequestBody(contentType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw PostgRestException(response.code, response.body?.string())
        }
        return json.decodeFromString<List<ChecklistItem>>(response.body!!.string())
    }

    // -- TEMPLATE ITEMS -------------------------------------------------------

    /**
     * Template items matching the PWA's checklist templates.
     */
    private fun createTemplateItems(
        type: ChecklistType,
        checklistId: String,
    ): List<ChecklistItem> {
        return when (type) {
            ChecklistType.ENTRADA -> entryTemplateItems(checklistId)
            ChecklistType.SALIDA -> exitTemplateItems(checklistId)
        }
    }

    private fun entryTemplateItems(checklistId: String): List<ChecklistItem> = listOf(
        templateItem(
            checklistId = checklistId,
            description = "Revisar nivel de agua: Verificar el nivel del tanque de agua y registrarlo en el sistema",
            templateId = "entry-1",
            category = "maintenance",
            required = true,
            sortOrder = 1,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Abrir caja registradora: Verificar el fondo de caja y registrar apertura en el sistema",
            templateId = "entry-2",
            category = "admin",
            required = true,
            sortOrder = 2,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Limpieza de area de recepcion: Barrer y trapear el area de atencion al cliente",
            templateId = "entry-3",
            category = "cleaning",
            required = true,
            sortOrder = 3,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Verificar equipos de lavado: Revisar que todas las lavadoras y secadoras esten operativas",
            templateId = "entry-4",
            category = "maintenance",
            required = true,
            sortOrder = 4,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Revisar suministros: Verificar existencias de detergente, suavizante y bolsas",
            templateId = "entry-5",
            category = "admin",
            required = false,
            sortOrder = 5,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Verificar extintores: Comprobar que los extintores esten en su lugar y vigentes",
            templateId = "entry-6",
            category = "safety",
            required = false,
            sortOrder = 6,
        ),
    )

    private fun exitTemplateItems(checklistId: String): List<ChecklistItem> = listOf(
        templateItem(
            checklistId = checklistId,
            description = "Cerrar caja registradora: Realizar el corte de caja y registrar cierre",
            templateId = "exit-1",
            category = "admin",
            required = true,
            sortOrder = 1,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Limpieza general: Realizar limpieza completa del local",
            templateId = "exit-2",
            category = "cleaning",
            required = true,
            sortOrder = 2,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Apagar equipos: Apagar todas las maquinas y equipos no esenciales",
            templateId = "exit-3",
            category = "maintenance",
            required = true,
            sortOrder = 3,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Verificar cerraduras: Asegurar puertas y ventanas del local",
            templateId = "exit-4",
            category = "safety",
            required = true,
            sortOrder = 4,
        ),
        templateItem(
            checklistId = checklistId,
            description = "Reporte de incidencias: Registrar cualquier novedad del turno",
            templateId = "exit-5",
            category = "admin",
            required = false,
            sortOrder = 5,
        ),
    )

    private fun templateItem(
        checklistId: String,
        description: String,
        templateId: String,
        category: String,
        required: Boolean,
        sortOrder: Int,
    ): ChecklistItem {
        val meta = """{"templateId":"$templateId","category":"$category","required":$required}"""
        return ChecklistItem(
            checklistId = checklistId,
            itemDescription = description,
            notes = meta,
            sortOrder = sortOrder,
        )
    }
}

/**
 * Exception for PostgREST HTTP errors.
 */
class PostgRestException(
    val statusCode: Int,
    val responseBody: String?,
) : Exception("PostgREST error $statusCode: ${responseBody?.take(200)}")
