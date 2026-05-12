package com.cleanx.lcx.feature.tickets.domain.create

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.CustomerCreateInput
import com.cleanx.lcx.feature.tickets.data.CustomerDraft
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.CustomerValidationErrors
import com.cleanx.lcx.feature.tickets.data.DefaultEncargoSpecialItems
import com.cleanx.lcx.feature.tickets.data.EncargoSpecialItemOption
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import com.cleanx.lcx.feature.tickets.data.TicketPricingSummary
import com.cleanx.lcx.feature.tickets.data.createEmptyCustomerDraft
import com.cleanx.lcx.feature.tickets.data.expandIdsByQuantity
import com.cleanx.lcx.feature.tickets.data.getPickupTargetHours
import com.cleanx.lcx.feature.tickets.data.recommendedPickupEstimate

enum class EncargoPaymentChoice {
    PENDING,
    PAID,
}

data class CustomerPickerUiState(
    val selectedCustomer: CustomerDraft = createEmptyCustomerDraft(),
    val searchQuery: String = "",
    val searchResults: List<CustomerRecord> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val showCreateForm: Boolean = false,
    val createNotes: String = "",
    val validationErrors: CustomerValidationErrors = CustomerValidationErrors(),
    val submitError: String? = null,
    val isSaving: Boolean = false,
    val duplicatePhoneMatches: List<CustomerRecord> = emptyList(),
    val pendingCreateInput: CustomerCreateInput? = null,
)

data class CreateTicketUiState(
    val isLoadingCatalogs: Boolean = true,
    val catalogError: String? = null,
    val catalogLoadedAtLabel: String? = null,
    val services: List<ServiceCatalogRecord> = emptyList(),
    val beddingItems: List<ServiceCatalogRecord> = emptyList(),
    val extraItems: List<AddOnCatalogRecord> = emptyList(),
    val inventoryItems: List<InventoryCatalogRecord> = emptyList(),
    val customer: CustomerPickerUiState = CustomerPickerUiState(),
    val selectedBaseServiceId: String? = null,
    val weight: String = "0",
    val inventorySearchQuery: String = "",
    val pickupEstimate: String = recommendedPickupEstimate(),
    val pickupTargetHours: Long = getPickupTargetHours(),
    val useSharedMachinePool: Boolean = true,
    val specialItemOptions: List<EncargoSpecialItemOption> = DefaultEncargoSpecialItems,
    val selectedSpecialItemIds: List<String> = emptyList(),
    val specialItemNotes: String = "",
    val beddingQuantities: Map<String, Int> = emptyMap(),
    val inventoryQuantities: Map<String, Int> = emptyMap(),
    val selectedExtraIds: List<String> = emptyList(),
    val pricing: TicketPricingSummary = TicketPricingSummary(),
    val paymentChoice: EncargoPaymentChoice = EncargoPaymentChoice.PENDING,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val createdTicket: Ticket? = null,
    val hasStartedForm: Boolean = false,
)

fun CreateTicketUiState.selectedAddOnIds(): List<String> {
    return expandIdsByQuantity(beddingQuantities) +
        expandIdsByQuantity(inventoryQuantities) +
        selectedExtraIds
}

fun CreateTicketUiState.parsedWeightOrZero(): Double {
    return weight.toDecimalOrNull() ?: 0.0
}

fun CreateTicketUiState.hasSelectedCustomer(): Boolean {
    return customer.selectedCustomer.customerId != null &&
        customer.selectedCustomer.fullName.isNotBlank() &&
        customer.selectedCustomer.phone.isNotBlank()
}

fun String.toDecimalOrNull(): Double? {
    return trim()
        .replace(',', '.')
        .toDoubleOrNull()
}
