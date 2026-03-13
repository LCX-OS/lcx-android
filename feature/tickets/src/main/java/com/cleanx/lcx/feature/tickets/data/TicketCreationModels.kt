package com.cleanx.lcx.feature.tickets.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Serializable
data class CustomerRecord(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val phone: String,
    @SerialName("phone_normalized") val phoneNormalized: String,
    val email: String? = null,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CustomerInsertPayload(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    @SerialName("phone_normalized") val phoneNormalized: String,
    val email: String? = null,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
)

data class CustomerDraft(
    val customerId: String? = null,
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
)

data class CustomerValidationErrors(
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

data class CustomerCreateInput(
    val fullName: String,
    val phone: String,
    val email: String? = null,
    val notes: String? = null,
)

data class CustomerCreateResult(
    val customer: CustomerRecord? = null,
    val errorMessage: String? = null,
    val duplicatePhoneMatches: List<CustomerRecord> = emptyList(),
    val requiresDuplicateConfirmation: Boolean = false,
    val validationErrors: CustomerValidationErrors = CustomerValidationErrors(),
)

data class TicketCustomerFields(
    val customerName: String,
    val customerPhone: String?,
    val customerEmail: String?,
    val customerId: String?,
)

@Serializable
data class ServiceCatalogRecord(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String = "",
    val price: Double,
    val unit: String = "",
    val active: Boolean = true,
)

@Serializable
data class AddOnCatalogRecord(
    val id: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val active: Boolean = true,
)

@Serializable
data class InventoryCatalogRecord(
    val id: String,
    @SerialName("item_name") val itemName: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    @SerialName("min_quantity") val minQuantity: Int = 0,
    val price: Double = 0.0,
    @SerialName("is_for_sale") val isForSale: Boolean = false,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("product_code") val productCode: String? = null,
    val branch: String? = null,
)

data class TicketCreationCatalogs(
    val services: List<ServiceCatalogRecord>,
    val addOns: List<AddOnCatalogRecord>,
    val inventoryItems: List<InventoryCatalogRecord>,
)

data class TicketPricingSummary(
    val subtotal: Double = 0.0,
    val addOnsTotal: Double = 0.0,
    val total: Double = 0.0,
)

data class EncargoCatalogItem(
    val id: String,
    val name: String,
    val price: Double,
    val subtitle: String? = null,
)

data class EncargoSpecialItemOption(
    val id: String,
    val label: String,
)

val DefaultEncargoSpecialItems = listOf(
    EncargoSpecialItemOption(id = "delicadas", label = "Prendas delicadas"),
    EncargoSpecialItemOption(id = "oscuras", label = "Ropa oscura"),
    EncargoSpecialItemOption(id = "blancas", label = "Ropa blanca"),
    EncargoSpecialItemOption(id = "manchas", label = "Prendas con manchas"),
    EncargoSpecialItemOption(id = "mascotas", label = "Prendas con pelo de mascota"),
)

private const val MIN_FULL_NAME_LENGTH = 2
private const val MIN_PHONE_DIGITS = 7
private const val WEEKDAY_PICKUP_HOURS = 24L
private const val WEEKEND_PICKUP_HOURS = 48L
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PickupInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun createEmptyCustomerDraft(): CustomerDraft = CustomerDraft()

fun CustomerRecord.toDraft(): CustomerDraft {
    return CustomerDraft(
        customerId = id,
        fullName = fullName,
        phone = phone,
        email = email.orEmpty(),
    )
}

fun normalizePhone(phone: String): String {
    val withoutPrefix = phone
        .trim()
        .replace(
            Regex(
                "^(tel|telefono|teléfono|phone)\\s*[:.\\-]?\\s*",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .removePrefix("+")

    return withoutPrefix.filter(Char::isDigit)
}

fun sanitizeCustomerSearchToken(value: String): String {
    return value.replace(Regex("[%_,]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun buildCustomerValidationErrors(
    fullName: String,
    normalizedPhone: String,
    email: String?,
): CustomerValidationErrors {
    return CustomerValidationErrors(
        fullName = if (fullName.length < MIN_FULL_NAME_LENGTH) {
            "Ingresa un nombre completo valido (minimo 2 caracteres)."
        } else {
            null
        },
        phone = if (normalizedPhone.length < MIN_PHONE_DIGITS) {
            "Ingresa un telefono valido (minimo 7 digitos)."
        } else {
            null
        },
        email = if (email != null && !EMAIL_REGEX.matches(email)) {
            "Ingresa un correo valido o dejalo en blanco."
        } else {
            null
        },
    )
}

fun firstCustomerValidationErrorMessage(errors: CustomerValidationErrors): String {
    return errors.fullName
        ?: errors.phone
        ?: errors.email
        ?: "Revisa los datos del cliente."
}

fun isCustomerDraftValid(customer: CustomerDraft): Boolean {
    return customer.fullName.trim().length >= MIN_FULL_NAME_LENGTH &&
        normalizePhone(customer.phone).length >= MIN_PHONE_DIGITS
}

fun buildTicketCustomerFields(customer: CustomerDraft): TicketCustomerFields {
    return TicketCustomerFields(
        customerName = customer.fullName.trim(),
        customerPhone = customer.phone.trim().ifBlank { null },
        customerEmail = customer.email.trim().ifBlank { null },
        customerId = customer.customerId?.takeIf { it.isNotBlank() },
    )
}

fun isBaseWashByKilo(service: ServiceCatalogRecord): Boolean {
    return service.name.uppercase().contains("KG") ||
        service.description?.lowercase()?.contains("kilo") == true
}

fun isBeddingService(service: ServiceCatalogRecord): Boolean {
    val normalizedName = service.name.lowercase()
    return service.category == "Edredones" ||
        normalizedName.contains("edredón") ||
        normalizedName.contains("edredon") ||
        normalizedName.contains("cobertor") ||
        normalizedName.contains("colcha") ||
        normalizedName.contains("matrimonial") ||
        normalizedName.contains("king")
}

fun isExtraAddOn(addOn: AddOnCatalogRecord): Boolean {
    val normalizedName = addOn.name.lowercase()
    return normalizedName.contains("jabón") ||
        normalizedName.contains("jabon") ||
        normalizedName.contains("perfume") ||
        normalizedName.contains("fragancia") ||
        normalizedName.contains("suavizante") ||
        normalizedName.contains("hipo") ||
        normalizedName.contains("manchas")
}

fun isSellableInventoryItem(item: InventoryCatalogRecord): Boolean {
    return item.quantity > 0 && item.isForSale && item.price > 0.0
}

fun filterSellableInventoryItems(items: List<InventoryCatalogRecord>): List<InventoryCatalogRecord> {
    return items.filter(::isSellableInventoryItem)
}

fun expandIdsByQuantity(quantities: Map<String, Int>): List<String> {
    val ids = mutableListOf<String>()
    quantities.forEach { (id, quantity) ->
        repeat(quantity.coerceAtLeast(0)) {
            ids += id
        }
    }
    return ids
}

fun calculateEncargoPricing(
    baseServiceId: String?,
    weight: Double,
    addOnIds: List<String>,
    services: List<ServiceCatalogRecord>,
    addOns: List<AddOnCatalogRecord>,
    inventoryItems: List<InventoryCatalogRecord>,
): TicketPricingSummary {
    val service = services.firstOrNull { it.id == baseServiceId } ?: return TicketPricingSummary()

    val normalizedWeight = weight.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val chargedWeight = if (normalizedWeight > 0.0) {
        normalizedWeight.coerceAtLeast(3.0)
    } else {
        0.0
    }

    val subtotal = service.price * chargedWeight
    if (addOnIds.isEmpty()) {
        return TicketPricingSummary(
            subtotal = subtotal,
            addOnsTotal = 0.0,
            total = subtotal,
        )
    }

    val addOnMap = addOns.associateBy { it.id }
    val serviceMap = services.associateBy { it.id }
    val inventoryMap = inventoryItems.associateBy { it.id }

    var fixedPriceTotal = 0.0
    var percentageItemsCount = 0

    addOnIds.forEach { id ->
        val pricedItemName: String
        val pricedItemPrice: Double

        when {
            addOnMap[id] != null -> {
                val item = addOnMap.getValue(id)
                pricedItemName = item.name
                pricedItemPrice = item.price
            }

            serviceMap[id] != null -> {
                val item = serviceMap.getValue(id)
                pricedItemName = item.name
                pricedItemPrice = item.price
            }

            inventoryMap[id] != null -> {
                val item = inventoryMap.getValue(id)
                pricedItemName = item.itemName
                pricedItemPrice = item.price
            }

            else -> return@forEach
        }

        val normalizedName = pricedItemName.lowercase()
        val isPercentageSurcharge = normalizedName.contains("fragancia premium") ||
            normalizedName.contains("hipoalergénico") ||
            normalizedName.contains("hipoalergenico") ||
            normalizedName.contains("quitamanchas") ||
            normalizedName.contains("tratamiento de manchas")

        if (isPercentageSurcharge) {
            percentageItemsCount += 1
        } else {
            fixedPriceTotal += pricedItemPrice
        }
    }

    val surchargeBase = subtotal + fixedPriceTotal
    val surchargeTotal = percentageItemsCount * (surchargeBase * 0.15)
    val addOnsTotal = fixedPriceTotal + surchargeTotal

    return TicketPricingSummary(
        subtotal = subtotal,
        addOnsTotal = addOnsTotal,
        total = subtotal + addOnsTotal,
    )
}

fun buildEncargoSpecialInstructions(
    selectedSpecialItemIds: List<String>,
    specialItemNotes: String,
    useSharedMachinePool: Boolean,
    specialItemOptions: List<EncargoSpecialItemOption> = DefaultEncargoSpecialItems,
): String? {
    val selectedSpecialItemLabels = specialItemOptions
        .filter { selectedSpecialItemIds.contains(it.id) }
        .map { it.label }

    val parts = mutableListOf<String>()

    if (selectedSpecialItemLabels.isNotEmpty()) {
        parts += "Separar prendas especiales: ${selectedSpecialItemLabels.joinToString(", ")}."
    }

    val cleanedNotes = specialItemNotes.trim()
    if (cleanedNotes.isNotEmpty()) {
        parts += "Notas especiales: $cleanedNotes."
    }

    if (useSharedMachinePool) {
        parts += "Operacion en pool de maquinas compartidas para autoservicio y encargo."
    }

    return parts.joinToString(" ").ifBlank { null }
}

fun buildEncargoNotes(
    pickupTargetHours: Long,
    useSharedMachinePool: Boolean,
    selectedSpecialItemIds: List<String>,
): String? {
    return listOf(
        "pickup_target_hours=$pickupTargetHours",
        "shared_machine_pool=${if (useSharedMachinePool) "yes" else "no"}",
        selectedSpecialItemIds.takeIf { it.isNotEmpty() }?.joinToString("|")
            ?.let { "special_items=$it" },
    ).filterNotNull()
        .joinToString(" ; ")
        .ifBlank { null }
}

fun getPickupTargetHours(referenceDate: LocalDateTime = LocalDateTime.now()): Long {
    return when (referenceDate.dayOfWeek) {
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
        -> WEEKEND_PICKUP_HOURS

        else -> WEEKDAY_PICKUP_HOURS
    }
}

fun recommendedPickupEstimate(referenceDate: LocalDateTime = LocalDateTime.now()): String {
    return referenceDate.plusHours(getPickupTargetHours(referenceDate))
        .format(PickupInputFormatter)
}

fun parsePickupEstimateToIso(value: String): String? {
    return try {
        LocalDateTime.parse(value, PickupInputFormatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()
    } catch (_: DateTimeParseException) {
        null
    }
}
