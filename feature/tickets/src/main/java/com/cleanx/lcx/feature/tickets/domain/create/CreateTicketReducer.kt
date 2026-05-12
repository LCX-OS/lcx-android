package com.cleanx.lcx.feature.tickets.domain.create

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.feature.tickets.data.CustomerCreateInput
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.CustomerValidationErrors
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.calculateEncargoPricing
import com.cleanx.lcx.feature.tickets.data.findInventoryItemBySkuOrBarcode
import com.cleanx.lcx.feature.tickets.data.inventoryLookupNotFoundMessage
import com.cleanx.lcx.feature.tickets.data.inventoryStockLimitMessage
import com.cleanx.lcx.feature.tickets.data.normalizePhone
import com.cleanx.lcx.feature.tickets.data.toDraft
import javax.inject.Inject

sealed interface CreateTicketMutation {
    data object CatalogLoadingStarted : CreateTicketMutation
    data class CatalogsLoaded(
        val snapshot: EncargoCatalogSnapshot,
        val loadedAtLabel: String,
    ) : CreateTicketMutation
    data class CatalogLoadFailed(val message: String) : CreateTicketMutation
    data class CustomerSearchQueryChanged(val value: String) : CreateTicketMutation
    data class CustomerSearchResolved(
        val query: String,
        val results: List<CustomerRecord>,
    ) : CreateTicketMutation
    data class CustomerSearchFailed(
        val query: String,
        val message: String,
    ) : CreateTicketMutation
    data object CreateCustomerFormOpened : CreateTicketMutation
    data object CreateCustomerFormCancelled : CreateTicketMutation
    data object SelectedCustomerCleared : CreateTicketMutation
    data class CustomerSelected(val customer: CustomerRecord) : CreateTicketMutation
    data class CustomerFullNameChanged(val value: String) : CreateTicketMutation
    data class CustomerPhoneChanged(val value: String) : CreateTicketMutation
    data class CustomerEmailChanged(val value: String) : CreateTicketMutation
    data class CustomerNotesChanged(val value: String) : CreateTicketMutation
    data object CustomerCreateStarted : CreateTicketMutation
    data class CustomerCreateRequiresDuplicate(
        val input: CustomerCreateInput,
        val matches: List<CustomerRecord>,
        val validationErrors: CustomerValidationErrors,
    ) : CreateTicketMutation
    data class CustomerCreateFailed(
        val message: String,
        val validationErrors: CustomerValidationErrors,
    ) : CreateTicketMutation
    data object DuplicateCustomerDialogDismissed : CreateTicketMutation
    data class BaseServiceSelected(val serviceId: String) : CreateTicketMutation
    data class WeightChanged(val value: String) : CreateTicketMutation
    data class PickupEstimateChanged(val value: String) : CreateTicketMutation
    data class InventorySearchQueryChanged(val value: String) : CreateTicketMutation
    data class InventoryLookupSubmitted(val value: String) : CreateTicketMutation
    data class SharedMachinePoolChanged(val enabled: Boolean) : CreateTicketMutation
    data class SpecialItemToggled(val itemId: String) : CreateTicketMutation
    data class SpecialItemNotesChanged(val value: String) : CreateTicketMutation
    data class BeddingQuantityAdjusted(
        val itemId: String,
        val delta: Int,
    ) : CreateTicketMutation
    data class InventoryQuantityAdjusted(
        val itemId: String,
        val delta: Int,
    ) : CreateTicketMutation
    data class ExtraToggled(val itemId: String) : CreateTicketMutation
    data class PaymentChoiceChanged(val choice: EncargoPaymentChoice) : CreateTicketMutation
    data class PaymentMethodChanged(val method: PaymentMethod) : CreateTicketMutation
    data object SubmitStarted : CreateTicketMutation
    data class SubmitFailed(
        val message: String,
        val customerValidationErrors: CustomerValidationErrors? = null,
    ) : CreateTicketMutation
    data class SubmitSucceeded(val ticket: Ticket) : CreateTicketMutation
    data object CreatedTicketCleared : CreateTicketMutation
}

class CreateTicketReducer @Inject constructor() {

    operator fun invoke(
        current: CreateTicketUiState,
        mutation: CreateTicketMutation,
    ): CreateTicketUiState {
        val next = when (mutation) {
            CreateTicketMutation.CatalogLoadingStarted -> current.copy(
                isLoadingCatalogs = true,
                catalogError = null,
            )

            is CreateTicketMutation.CatalogsLoaded -> {
                val snapshot = mutation.snapshot
                val validServiceIds = snapshot.services.map { it.id }.toSet()
                val validBeddingIds = snapshot.beddingItems.map { it.id }.toSet()
                val validExtraIds = snapshot.extraItems.map { it.id }.toSet()
                val validInventoryIds = snapshot.inventoryItems.map { it.id }.toSet()

                current.copy(
                    isLoadingCatalogs = false,
                    catalogError = null,
                    catalogLoadedAtLabel = mutation.loadedAtLabel,
                    services = snapshot.services,
                    beddingItems = snapshot.beddingItems,
                    extraItems = snapshot.extraItems,
                    inventoryItems = snapshot.inventoryItems,
                    selectedBaseServiceId = current.selectedBaseServiceId
                        ?.takeIf { it in validServiceIds }
                        ?: snapshot.defaultBaseServiceId,
                    beddingQuantities = current.beddingQuantities.filterKeys { it in validBeddingIds },
                    inventoryQuantities = current.inventoryQuantities.filterKeys { it in validInventoryIds },
                    selectedExtraIds = current.selectedExtraIds.filter { it in validExtraIds },
                )
            }

            is CreateTicketMutation.CatalogLoadFailed -> current.copy(
                isLoadingCatalogs = false,
                catalogError = mutation.message,
            )

            is CreateTicketMutation.CustomerSearchQueryChanged -> {
                val trimmed = mutation.value.trim()
                current.copy(
                    customer = current.customer.copy(
                        searchQuery = mutation.value,
                        searchResults = if (trimmed.length >= 2) {
                            current.customer.searchResults
                        } else {
                            emptyList()
                        },
                        isSearching = trimmed.length >= 2,
                        searchError = null,
                    ),
                    error = null,
                )
            }

            is CreateTicketMutation.CustomerSearchResolved -> {
                if (current.customer.searchQuery.trim() != mutation.query) {
                    current
                } else {
                    current.copy(
                        customer = current.customer.copy(
                            isSearching = false,
                            searchResults = mutation.results,
                            searchError = null,
                        ),
                    )
                }
            }

            is CreateTicketMutation.CustomerSearchFailed -> {
                if (current.customer.searchQuery.trim() != mutation.query) {
                    current
                } else {
                    current.copy(
                        customer = current.customer.copy(
                            isSearching = false,
                            searchResults = emptyList(),
                            searchError = mutation.message,
                        ),
                    )
                }
            }

            CreateTicketMutation.CreateCustomerFormOpened -> {
                val searchQuery = current.customer.searchQuery.trim()
                val normalizedSearchPhone = normalizePhone(searchQuery)
                val nextCustomer = current.customer.selectedCustomer.copy(
                    fullName = current.customer.selectedCustomer.fullName.ifBlank {
                        if (searchQuery.isNotBlank() && normalizedSearchPhone.length < 7) {
                            searchQuery
                        } else {
                            ""
                        }
                    },
                    phone = current.customer.selectedCustomer.phone.ifBlank {
                        if (normalizedSearchPhone.length >= 7) {
                            searchQuery
                        } else {
                            ""
                        }
                    },
                )

                current.copy(
                    customer = current.customer.copy(
                        selectedCustomer = nextCustomer,
                        showCreateForm = true,
                        validationErrors = CustomerValidationErrors(),
                        submitError = null,
                        duplicatePhoneMatches = emptyList(),
                        pendingCreateInput = null,
                    ),
                    error = null,
                )
            }

            CreateTicketMutation.CreateCustomerFormCancelled -> current.copy(
                customer = current.customer.copy(
                    showCreateForm = false,
                    selectedCustomer = if (current.customer.selectedCustomer.customerId == null) {
                        current.customer.selectedCustomer.copy(
                            fullName = "",
                            phone = "",
                            email = "",
                        )
                    } else {
                        current.customer.selectedCustomer
                    },
                    createNotes = "",
                    validationErrors = CustomerValidationErrors(),
                    submitError = null,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                    isSaving = false,
                ),
            )

            CreateTicketMutation.SelectedCustomerCleared -> current.copy(
                customer = CustomerPickerUiState(),
                error = null,
            )

            is CreateTicketMutation.CustomerSelected -> current.copy(
                customer = current.customer.copy(
                    selectedCustomer = mutation.customer.toDraft(),
                    searchQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = null,
                    showCreateForm = false,
                    createNotes = "",
                    validationErrors = CustomerValidationErrors(),
                    submitError = null,
                    isSaving = false,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                ),
                error = null,
            )

            is CreateTicketMutation.CustomerFullNameChanged -> current.copy(
                customer = current.customer.copy(
                    selectedCustomer = current.customer.selectedCustomer.copy(fullName = mutation.value),
                    validationErrors = current.customer.validationErrors.copy(fullName = null),
                    submitError = null,
                ),
                error = null,
            )

            is CreateTicketMutation.CustomerPhoneChanged -> current.copy(
                customer = current.customer.copy(
                    selectedCustomer = current.customer.selectedCustomer.copy(phone = mutation.value),
                    validationErrors = current.customer.validationErrors.copy(phone = null),
                    submitError = null,
                ),
                error = null,
            )

            is CreateTicketMutation.CustomerEmailChanged -> current.copy(
                customer = current.customer.copy(
                    selectedCustomer = current.customer.selectedCustomer.copy(email = mutation.value),
                    validationErrors = current.customer.validationErrors.copy(email = null),
                    submitError = null,
                ),
                error = null,
            )

            is CreateTicketMutation.CustomerNotesChanged -> current.copy(
                customer = current.customer.copy(
                    createNotes = mutation.value,
                    submitError = null,
                ),
                error = null,
            )

            CreateTicketMutation.CustomerCreateStarted -> current.copy(
                customer = current.customer.copy(
                    isSaving = true,
                    submitError = null,
                ),
                error = null,
            )

            is CreateTicketMutation.CustomerCreateRequiresDuplicate -> current.copy(
                customer = current.customer.copy(
                    isSaving = false,
                    validationErrors = mutation.validationErrors,
                    duplicatePhoneMatches = mutation.matches,
                    pendingCreateInput = mutation.input,
                ),
            )

            is CreateTicketMutation.CustomerCreateFailed -> current.copy(
                customer = current.customer.copy(
                    isSaving = false,
                    validationErrors = mutation.validationErrors,
                    submitError = mutation.message,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                ),
            )

            CreateTicketMutation.DuplicateCustomerDialogDismissed -> current.copy(
                customer = current.customer.copy(
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                    isSaving = false,
                ),
            )

            is CreateTicketMutation.BaseServiceSelected -> current.copy(
                selectedBaseServiceId = mutation.serviceId,
                error = null,
            )

            is CreateTicketMutation.WeightChanged -> current.copy(
                weight = mutation.value,
                error = null,
            )

            is CreateTicketMutation.PickupEstimateChanged -> current.copy(
                pickupEstimate = mutation.value,
                error = null,
            )

            is CreateTicketMutation.InventorySearchQueryChanged -> current.copy(
                inventorySearchQuery = mutation.value,
                error = null,
            )

            is CreateTicketMutation.InventoryLookupSubmitted -> current.addInventoryBySkuOrBarcode(
                rawValue = mutation.value,
            )

            is CreateTicketMutation.SharedMachinePoolChanged -> current.copy(
                useSharedMachinePool = mutation.enabled,
                error = null,
            )

            is CreateTicketMutation.SpecialItemToggled -> current.copy(
                selectedSpecialItemIds = if (current.selectedSpecialItemIds.contains(mutation.itemId)) {
                    current.selectedSpecialItemIds - mutation.itemId
                } else {
                    current.selectedSpecialItemIds + mutation.itemId
                },
                error = null,
            )

            is CreateTicketMutation.SpecialItemNotesChanged -> current.copy(
                specialItemNotes = mutation.value,
                error = null,
            )

            is CreateTicketMutation.BeddingQuantityAdjusted -> current.copy(
                beddingQuantities = current.beddingQuantities.adjustQuantity(
                    itemId = mutation.itemId,
                    delta = mutation.delta,
                ),
                error = null,
            )

            is CreateTicketMutation.InventoryQuantityAdjusted -> current.copy(
                inventoryQuantities = current.inventoryQuantities.adjustQuantity(
                    itemId = mutation.itemId,
                    delta = mutation.delta,
                    maxValue = current.inventoryItems.firstOrNull { it.id == mutation.itemId }?.quantity,
                ),
                error = inventoryLimitError(
                    itemId = mutation.itemId,
                    delta = mutation.delta,
                    quantities = current.inventoryQuantities,
                    items = current.inventoryItems,
                ),
            )

            is CreateTicketMutation.ExtraToggled -> current.copy(
                selectedExtraIds = if (current.selectedExtraIds.contains(mutation.itemId)) {
                    current.selectedExtraIds - mutation.itemId
                } else {
                    current.selectedExtraIds + mutation.itemId
                },
                error = null,
            )

            is CreateTicketMutation.PaymentChoiceChanged -> current.copy(
                paymentChoice = mutation.choice,
                error = null,
            )

            is CreateTicketMutation.PaymentMethodChanged -> current.copy(
                paymentMethod = mutation.method,
                error = null,
            )

            CreateTicketMutation.SubmitStarted -> current.copy(
                isSubmitting = true,
                error = null,
            )

            is CreateTicketMutation.SubmitFailed -> current.copy(
                isSubmitting = false,
                error = mutation.message,
                customer = if (mutation.customerValidationErrors != null) {
                    current.customer.copy(validationErrors = mutation.customerValidationErrors)
                } else {
                    current.customer
                },
            )

            is CreateTicketMutation.SubmitSucceeded -> current.copy(
                isSubmitting = false,
                error = null,
                createdTicket = mutation.ticket,
            )

            CreateTicketMutation.CreatedTicketCleared -> current.copy(
                createdTicket = null,
            )
        }

        return next.derive()
    }

    private fun CreateTicketUiState.derive(): CreateTicketUiState {
        return copy(
            pricing = calculateEncargoPricing(
                baseServiceId = selectedBaseServiceId,
                weight = parsedWeightOrZero(),
                addOnIds = selectedAddOnIds(),
                services = services,
                addOns = extraItems,
                inventoryItems = inventoryItems,
            ),
            hasStartedForm = hasStartedForm(),
        )
    }

    private fun CreateTicketUiState.hasStartedForm(): Boolean {
        return customer.selectedCustomer.fullName.isNotBlank() ||
            customer.selectedCustomer.phone.isNotBlank() ||
            customer.selectedCustomer.email.isNotBlank() ||
            customer.searchQuery.isNotBlank() ||
            customer.showCreateForm ||
            customer.createNotes.isNotBlank() ||
            (weight != "0" && weight.isNotBlank()) ||
            inventorySearchQuery.isNotBlank() ||
            selectedSpecialItemIds.isNotEmpty() ||
            specialItemNotes.isNotBlank() ||
            beddingQuantities.values.any { it > 0 } ||
            inventoryQuantities.values.any { it > 0 } ||
            selectedExtraIds.isNotEmpty() ||
            paymentChoice != EncargoPaymentChoice.PENDING
    }

    private fun CreateTicketUiState.addInventoryBySkuOrBarcode(rawValue: String): CreateTicketUiState {
        val lookup = rawValue.trim()
        if (lookup.isBlank()) return copy(error = null)

        val item = findInventoryItemBySkuOrBarcode(inventoryItems, lookup)
        if (item == null) {
            return copy(
                inventorySearchQuery = rawValue,
                error = inventoryLookupNotFoundMessage(lookup),
            )
        }

        val currentQuantity = inventoryQuantities[item.id] ?: 0
        if (currentQuantity >= item.quantity) {
            return copy(
                inventorySearchQuery = "",
                error = inventoryStockLimitMessage(item),
            )
        }

        return copy(
            inventorySearchQuery = "",
            inventoryQuantities = inventoryQuantities.adjustQuantity(
                itemId = item.id,
                delta = 1,
                maxValue = item.quantity,
            ),
            error = null,
        )
    }

    private fun inventoryLimitError(
        itemId: String,
        delta: Int,
        quantities: Map<String, Int>,
        items: List<InventoryCatalogRecord>,
    ): String? {
        if (delta <= 0) return null

        val item = items.firstOrNull { it.id == itemId } ?: return null
        val currentQuantity = quantities[itemId] ?: 0
        return if (currentQuantity >= item.quantity) {
            inventoryStockLimitMessage(item)
        } else {
            null
        }
    }

    private fun Map<String, Int>.adjustQuantity(
        itemId: String,
        delta: Int,
        maxValue: Int? = null,
    ): Map<String, Int> {
        val rawNextValue = ((this[itemId] ?: 0) + delta).coerceAtLeast(0)
        val nextValue = maxValue?.let { rawNextValue.coerceAtMost(it) } ?: rawNextValue
        return toMutableMap().apply {
            if (nextValue == 0) remove(itemId) else put(itemId, nextValue)
        }
    }
}
