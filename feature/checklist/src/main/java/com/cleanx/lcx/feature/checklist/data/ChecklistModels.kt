package com.cleanx.lcx.feature.checklist.data

import com.cleanx.lcx.core.operational.ChecklistRoutineRequirements
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
    ;

    companion object {
        fun fromPersisted(value: String?): ChecklistType? = when (value?.lowercase()) {
            "entrada" -> ENTRADA
            "salida" -> SALIDA
            else -> null
        }
    }
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
 * Represents a row in the `maintenance_checklists` table.
 */
@Serializable
data class Checklist(
    val id: String? = null,
    @SerialName("checklist_type") val type: ChecklistType? = null,
    val status: ChecklistStatus = ChecklistStatus.PENDING,
    @SerialName("checklist_date") val date: String,
    val notes: String? = null,
    @SerialName("completion_notes") val completionNotes: String? = null,
    @SerialName("completed_by") val completedBy: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
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

/** entry-2 = opening cash movement must be recorded today */
const val TEMPLATE_OPENING_CASH = "entry-2"

/** exit-1 = closing cash movement must be recorded today */
const val TEMPLATE_CLOSING_CASH = "exit-1"

val SYSTEM_VALIDATED_TEMPLATES = setOf(
    TEMPLATE_WATER_LEVEL,
    TEMPLATE_OPENING_CASH,
    TEMPLATE_CLOSING_CASH,
)

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

data class ChecklistCompletionGate(
    val canComplete: Boolean,
    val reason: String? = null,
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

fun ChecklistType.systemRequirementExpectations(
    requirements: ChecklistRoutineRequirements,
): Map<String, Boolean> = when (this) {
    ChecklistType.ENTRADA -> mapOf(
        TEMPLATE_WATER_LEVEL to requirements.waterReviewedToday,
        TEMPLATE_OPENING_CASH to requirements.openingCashRegisteredToday,
    )

    ChecklistType.SALIDA -> mapOf(
        TEMPLATE_CLOSING_CASH to requirements.closingCashRegisteredToday,
    )
}

fun Checklist.resolvedType(fallback: ChecklistType = ChecklistType.ENTRADA): ChecklistType {
    return type ?: ChecklistType.fromPersisted(notes) ?: fallback
}

private val templateDisplayOrder = listOf(
    "entry-1",
    "entry-2",
    "entry-3",
    "entry-4",
    "entry-5",
    "entry-6",
    "entry-7",
    "entry-8",
    "exit-1",
    "exit-2",
    "exit-3",
    "exit-4",
    "exit-5",
    "exit-6",
    "exit-9",
    "exit-7",
    "exit-8",
).withIndex().associate { (index, templateId) -> templateId to index }

fun sortChecklistItems(items: List<ChecklistItem>): List<ChecklistItem> {
    return items.sortedBy { item ->
        templateDisplayOrder[item.parseMetadata().templateId] ?: Int.MAX_VALUE
    }
}

fun sortChecklistUiItems(items: List<ChecklistItemUi>): List<ChecklistItemUi> {
    return items.sortedBy { item ->
        templateDisplayOrder[item.metadata.templateId] ?: Int.MAX_VALUE
    }
}

/**
 * Calculate completion progress (0.0 .. 1.0).
 */
fun getChecklistProgress(items: List<ChecklistItemUi>): Float {
    if (items.isEmpty()) return 0f
    val completed = items.count { it.item.isCompleted }
    return completed.toFloat() / items.size.toFloat()
}

fun evaluateChecklistCompletion(items: List<ChecklistItem>): ChecklistCompletionGate {
    if (items.isEmpty()) {
        return ChecklistCompletionGate(
            canComplete = false,
            reason = "No hay tareas en el checklist",
        )
    }

    val requiredItems = items.filter { it.parseMetadata().required }
    val incompleteRequired = requiredItems.filterNot { it.isCompleted }
    return if (incompleteRequired.isNotEmpty()) {
        ChecklistCompletionGate(
            canComplete = false,
            reason = "Faltan ${incompleteRequired.size} tareas requeridas por completar",
        )
    } else {
        ChecklistCompletionGate(canComplete = true)
    }
}

/**
 * Check if all required items are completed.
 */
fun canCompleteChecklist(items: List<ChecklistItemUi>): Boolean {
    return evaluateChecklistCompletion(items.map { it.item }).canComplete
}

/**
 * Count required items that are completed vs total required.
 */
fun requiredItemCounts(items: List<ChecklistItemUi>): Pair<Int, Int> {
    val required = items.filter { it.metadata.required }
    val done = required.count { it.item.isCompleted }
    return done to required.size
}
