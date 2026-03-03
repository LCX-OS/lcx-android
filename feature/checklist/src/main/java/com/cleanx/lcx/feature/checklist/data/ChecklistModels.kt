package com.cleanx.lcx.feature.checklist.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

@Serializable
enum class ChecklistType {
    @SerialName("entrada") ENTRADA,
    @SerialName("salida") SALIDA,
}

@Serializable
enum class ChecklistStatus {
    @SerialName("pending") PENDING,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
}

// ---------------------------------------------------------------------------
// Row models – mirror the Supabase tables
// ---------------------------------------------------------------------------

/**
 * Represents a row in the `checklists` table.
 */
@Serializable
data class Checklist(
    val id: String? = null,
    val type: ChecklistType,
    val status: ChecklistStatus = ChecklistStatus.PENDING,
    val date: String,
    val notes: String? = null,
    @SerialName("completed_by") val completedBy: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val branch: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Represents a row in the `checklist_items` table.
 *
 * The `notes` column stores JSON metadata in the database:
 * `{"templateId":"entry-1","category":"maintenance","required":true}`
 */
@Serializable
data class ChecklistItem(
    val id: String? = null,
    @SerialName("checklist_id") val checklistId: String,
    @SerialName("item_description") val itemDescription: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_by") val completedBy: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---------------------------------------------------------------------------
// Parsed metadata extracted from ChecklistItem.notes JSON
// ---------------------------------------------------------------------------

data class ItemMetadata(
    val templateId: String? = null,
    val category: ItemCategory = ItemCategory.ADMIN,
    val required: Boolean = false,
)

enum class ItemCategory {
    CLEANING,
    MAINTENANCE,
    SAFETY,
    ADMIN;

    companion object {
        fun fromString(value: String): ItemCategory = when (value.lowercase()) {
            "cleaning" -> CLEANING
            "maintenance" -> MAINTENANCE
            "safety" -> SAFETY
            "admin" -> ADMIN
            else -> ADMIN
        }
    }
}

// ---------------------------------------------------------------------------
// System-validated template IDs
// ---------------------------------------------------------------------------

/** entry-1 = water level must be recorded today */
const val TEMPLATE_WATER_LEVEL = "entry-1"

/** entry-2 = cash register must be opened today */
const val TEMPLATE_CASH_REGISTER = "entry-2"

val SYSTEM_VALIDATED_TEMPLATES = setOf(TEMPLATE_WATER_LEVEL, TEMPLATE_CASH_REGISTER)

// ---------------------------------------------------------------------------
// UI-oriented model (item + parsed metadata)
// ---------------------------------------------------------------------------

data class ChecklistItemUi(
    val item: ChecklistItem,
    val metadata: ItemMetadata,
    val title: String,
    val description: String,
    val isSystemValidated: Boolean,
)

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private val metadataJson = Json { ignoreUnknownKeys = true }

fun ChecklistItem.parseMetadata(): ItemMetadata {
    if (notes.isNullOrBlank()) return ItemMetadata()
    return try {
        val obj = metadataJson.decodeFromString<JsonObject>(notes)
        ItemMetadata(
            templateId = obj["templateId"]?.jsonPrimitive?.content,
            category = obj["category"]?.jsonPrimitive?.content
                ?.let { ItemCategory.fromString(it) } ?: ItemCategory.ADMIN,
            required = obj["required"]?.jsonPrimitive?.boolean ?: false,
        )
    } catch (_: Exception) {
        ItemMetadata()
    }
}

/**
 * Split `item_description` into title + description.
 * The PWA uses ": " as delimiter: "Revisar nivel de agua: Verificar el nivel..."
 */
fun ChecklistItem.splitDescription(): Pair<String, String> {
    val parts = itemDescription.split(": ", limit = 2)
    return if (parts.size == 2) {
        parts[0] to parts[1]
    } else {
        itemDescription to ""
    }
}

fun ChecklistItem.toUi(): ChecklistItemUi {
    val meta = parseMetadata()
    val (title, description) = splitDescription()
    return ChecklistItemUi(
        item = this,
        metadata = meta,
        title = title,
        description = description,
        isSystemValidated = meta.templateId in SYSTEM_VALIDATED_TEMPLATES,
    )
}

/**
 * Calculate completion progress (0.0 .. 1.0).
 */
fun getChecklistProgress(items: List<ChecklistItemUi>): Float {
    if (items.isEmpty()) return 0f
    val completed = items.count { it.item.isCompleted }
    return completed.toFloat() / items.size.toFloat()
}

/**
 * Check if all required items are completed.
 */
fun canCompleteChecklist(items: List<ChecklistItemUi>): Boolean {
    return items.filter { it.metadata.required }.all { it.item.isCompleted }
}

/**
 * Count required items that are completed vs total required.
 */
fun requiredItemCounts(items: List<ChecklistItemUi>): Pair<Int, Int> {
    val required = items.filter { it.metadata.required }
    val done = required.count { it.item.isCompleted }
    return done to required.size
}
