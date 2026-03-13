package com.cleanx.lcx.feature.tickets.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.CustomerCreateInput
import com.cleanx.lcx.feature.tickets.data.CustomerCreateResult
import com.cleanx.lcx.feature.tickets.data.CustomerDraft
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.CustomerValidationErrors
import com.cleanx.lcx.feature.tickets.data.DefaultEncargoSpecialItems
import com.cleanx.lcx.feature.tickets.data.EncargoSpecialItemOption
import com.cleanx.lcx.feature.tickets.data.ErrorMessages
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import com.cleanx.lcx.feature.tickets.data.TicketCreationRepository
import com.cleanx.lcx.feature.tickets.data.TicketPricingSummary
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import com.cleanx.lcx.feature.tickets.data.buildCustomerValidationErrors
import com.cleanx.lcx.feature.tickets.data.buildEncargoNotes
import com.cleanx.lcx.feature.tickets.data.buildEncargoSpecialInstructions
import com.cleanx.lcx.feature.tickets.data.buildTicketCustomerFields
import com.cleanx.lcx.feature.tickets.data.calculateEncargoPricing
import com.cleanx.lcx.feature.tickets.data.createEmptyCustomerDraft
import com.cleanx.lcx.feature.tickets.data.expandIdsByQuantity
import com.cleanx.lcx.feature.tickets.data.getPickupTargetHours
import com.cleanx.lcx.feature.tickets.data.isBaseWashByKilo
import com.cleanx.lcx.feature.tickets.data.isBeddingService
import com.cleanx.lcx.feature.tickets.data.isCustomerDraftValid
import com.cleanx.lcx.feature.tickets.data.isExtraAddOn
import com.cleanx.lcx.feature.tickets.data.normalizePhone
import com.cleanx.lcx.feature.tickets.data.parsePickupEstimateToIso
import com.cleanx.lcx.feature.tickets.data.recommendedPickupEstimate
import com.cleanx.lcx.feature.tickets.data.toDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val CUSTOMER_SEARCH_DEBOUNCE_MS = 350L

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

@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val creationRepository: TicketCreationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTicketUiState())
    val uiState: StateFlow<CreateTicketUiState> = _uiState.asStateFlow()

    private var customerSearchJob: Job? = null

    init {
        loadCatalogs()
    }

    fun retryCatalogs() {
        loadCatalogs()
    }

    fun onCustomerSearchQueryChanged(value: String) {
        customerSearchJob?.cancel()
        val trimmed = value.trim()

        updateUiState {
            copy(
                customer = customer.copy(
                    searchQuery = value,
                    searchResults = if (trimmed.length >= 2) customer.searchResults else emptyList(),
                    isSearching = trimmed.length >= 2,
                    searchError = null,
                ),
                error = null,
            )
        }

        if (trimmed.length < 2) {
            updateUiState {
                copy(
                    customer = customer.copy(
                        isSearching = false,
                        searchResults = emptyList(),
                        searchError = null,
                    ),
                )
            }
            return
        }

        customerSearchJob = viewModelScope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            val latestQuery = _uiState.value.customer.searchQuery.trim()
            if (latestQuery != trimmed) return@launch

            when (val result = creationRepository.searchCustomers(trimmed)) {
                is ApiResult.Success -> {
                    updateUiState {
                        if (customer.searchQuery.trim() != trimmed) {
                            this
                        } else {
                            copy(
                                customer = customer.copy(
                                    isSearching = false,
                                    searchResults = result.data,
                                    searchError = null,
                                ),
                            )
                        }
                    }
                }

                is ApiResult.Error -> {
                    updateUiState {
                        if (customer.searchQuery.trim() != trimmed) {
                            this
                        } else {
                            copy(
                                customer = customer.copy(
                                    isSearching = false,
                                    searchResults = emptyList(),
                                    searchError = ErrorMessages.forCode(
                                        result.code,
                                        result.message,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun openCreateCustomerForm() {
        val searchQuery = _uiState.value.customer.searchQuery.trim()
        val normalizedSearchPhone = normalizePhone(searchQuery)

        updateUiState {
            val nextCustomer = customer.selectedCustomer.copy(
                fullName = customer.selectedCustomer.fullName.ifBlank {
                    if (searchQuery.isNotBlank() && normalizedSearchPhone.length < 7) {
                        searchQuery
                    } else {
                        ""
                    }
                },
                phone = customer.selectedCustomer.phone.ifBlank {
                    if (normalizedSearchPhone.length >= 7) {
                        searchQuery
                    } else {
                        ""
                    }
                },
            )

            copy(
                customer = customer.copy(
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
    }

    fun cancelCreateCustomerForm() {
        updateUiState {
            copy(
                customer = customer.copy(
                    showCreateForm = false,
                    selectedCustomer = if (customer.selectedCustomer.customerId == null) {
                        createEmptyCustomerDraft()
                    } else {
                        customer.selectedCustomer
                    },
                    createNotes = "",
                    validationErrors = CustomerValidationErrors(),
                    submitError = null,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                ),
            )
        }
    }

    fun clearSelectedCustomer() {
        customerSearchJob?.cancel()
        updateUiState {
            copy(
                customer = CustomerPickerUiState(),
                error = null,
            )
        }
    }

    fun selectCustomer(customerRecord: CustomerRecord) {
        customerSearchJob?.cancel()
        updateUiState {
            copy(
                customer = customer.copy(
                    selectedCustomer = customerRecord.toDraft(),
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
        }
    }

    fun onCustomerFullNameChanged(value: String) {
        updateUiState {
            copy(
                customer = customer.copy(
                    selectedCustomer = customer.selectedCustomer.copy(fullName = value),
                    validationErrors = customer.validationErrors.copy(fullName = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerPhoneChanged(value: String) {
        updateUiState {
            copy(
                customer = customer.copy(
                    selectedCustomer = customer.selectedCustomer.copy(phone = value),
                    validationErrors = customer.validationErrors.copy(phone = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerEmailChanged(value: String) {
        updateUiState {
            copy(
                customer = customer.copy(
                    selectedCustomer = customer.selectedCustomer.copy(email = value),
                    validationErrors = customer.validationErrors.copy(email = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerNotesChanged(value: String) {
        updateUiState {
            copy(
                customer = customer.copy(
                    createNotes = value,
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun createCustomer() {
        val current = _uiState.value
        if (current.customer.isSaving) return

        submitCustomerCreate(
            input = CustomerCreateInput(
                fullName = current.customer.selectedCustomer.fullName,
                phone = current.customer.selectedCustomer.phone,
                email = current.customer.selectedCustomer.email,
                notes = current.customer.createNotes,
            ),
            allowDuplicatePhone = false,
        )
    }

    fun confirmCreateCustomerDespiteDuplicate() {
        val input = _uiState.value.customer.pendingCreateInput ?: return
        submitCustomerCreate(input = input, allowDuplicatePhone = true)
    }

    fun dismissDuplicateCustomerDialog() {
        updateUiState {
            copy(
                customer = customer.copy(
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                ),
            )
        }
    }

    fun onBaseServiceSelected(serviceId: String) {
        updateUiState {
            copy(
                selectedBaseServiceId = serviceId,
                error = null,
            )
        }
    }

    fun onWeightChanged(value: String) {
        updateUiState {
            copy(weight = value, error = null)
        }
    }

    fun onPickupEstimateChanged(value: String) {
        updateUiState {
            copy(pickupEstimate = value, error = null)
        }
    }

    fun onInventorySearchQueryChanged(value: String) {
        updateUiState {
            copy(inventorySearchQuery = value, error = null)
        }
    }

    fun setSharedMachinePool(enabled: Boolean) {
        updateUiState {
            copy(useSharedMachinePool = enabled, error = null)
        }
    }

    fun toggleSpecialItem(itemId: String) {
        updateUiState {
            val next = if (selectedSpecialItemIds.contains(itemId)) {
                selectedSpecialItemIds - itemId
            } else {
                selectedSpecialItemIds + itemId
            }

            copy(
                selectedSpecialItemIds = next,
                error = null,
            )
        }
    }

    fun onSpecialItemNotesChanged(value: String) {
        updateUiState {
            copy(specialItemNotes = value, error = null)
        }
    }

    fun adjustBeddingQuantity(itemId: String, delta: Int) {
        updateUiState {
            copy(
                beddingQuantities = beddingQuantities.adjustQuantity(itemId, delta),
                error = null,
            )
        }
    }

    fun adjustInventoryQuantity(itemId: String, delta: Int) {
        updateUiState {
            copy(
                inventoryQuantities = inventoryQuantities.adjustQuantity(itemId, delta),
                error = null,
            )
        }
    }

    fun toggleExtra(itemId: String) {
        updateUiState {
            copy(
                selectedExtraIds = if (selectedExtraIds.contains(itemId)) {
                    selectedExtraIds - itemId
                } else {
                    selectedExtraIds + itemId
                },
                error = null,
            )
        }
    }

    fun onPaymentChoiceChanged(choice: EncargoPaymentChoice) {
        updateUiState {
            copy(
                paymentChoice = choice,
                error = null,
            )
        }
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        updateUiState {
            copy(
                paymentMethod = method,
                error = null,
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.isLoadingCatalogs || state.customer.isSaving) return

        val service = state.services.firstOrNull { it.id == state.selectedBaseServiceId }
        if (service == null) {
            _uiState.update { it.copy(error = "Selecciona un servicio base valido.") }
            return
        }

        val customerDraft = state.customer.selectedCustomer
        if (!isCustomerDraftValid(customerDraft)) {
            val validationErrors = buildCustomerValidationErrors(
                fullName = customerDraft.fullName.trim(),
                normalizedPhone = normalizePhone(customerDraft.phone),
                email = customerDraft.email.trim().ifBlank { null },
            )
            _uiState.update {
                deriveState(
                    it.copy(
                        error = "Selecciona o crea un cliente valido con nombre y telefono.",
                        customer = it.customer.copy(validationErrors = validationErrors),
                    ),
                )
            }
            return
        }

        val parsedWeight = state.weight.toDecimalOrNull()
        if (parsedWeight == null || parsedWeight <= 0.0) {
            _uiState.update { it.copy(error = "Ingresa un peso valido mayor a 0 kg.") }
            return
        }

        val promisedPickupDate = parsePickupEstimateToIso(state.pickupEstimate.trim())
        if (promisedPickupDate == null) {
            _uiState.update {
                it.copy(error = "La fecha promesa debe usar formato AAAA-MM-DDTHH:MM.")
            }
            return
        }

        val addOnIds = buildSelectedAddOnIds(state)
        val customerFields = buildTicketCustomerFields(customerDraft)
        val paymentStatus = when (state.paymentChoice) {
            EncargoPaymentChoice.PENDING -> "pending"
            EncargoPaymentChoice.PAID -> "paid"
        }
        val paymentMethod = if (state.paymentChoice == EncargoPaymentChoice.PAID) {
            state.paymentMethod.toWireValue()
        } else {
            null
        }
        val paidAmount = if (state.paymentChoice == EncargoPaymentChoice.PAID) {
            state.pricing.total
        } else {
            0.0
        }
        val paidAt = if (state.paymentChoice == EncargoPaymentChoice.PAID) {
            Instant.now().toString()
        } else {
            null
        }

        val draft = TicketDraft(
            customerName = customerFields.customerName,
            customerPhone = customerFields.customerPhone,
            customerEmail = customerFields.customerEmail,
            customerId = customerFields.customerId,
            serviceType = "wash-fold",
            service = service.name,
            weight = parsedWeight,
            status = "received",
            notes = buildEncargoNotes(
                pickupTargetHours = state.pickupTargetHours,
                useSharedMachinePool = state.useSharedMachinePool,
                selectedSpecialItemIds = state.selectedSpecialItemIds,
            ),
            totalAmount = state.pricing.total,
            subtotal = state.pricing.subtotal,
            addOnsTotal = state.pricing.addOnsTotal,
            addOns = addOnIds,
            promisedPickupDate = promisedPickupDate,
            specialInstructions = buildEncargoSpecialInstructions(
                selectedSpecialItemIds = state.selectedSpecialItemIds,
                specialItemNotes = state.specialItemNotes,
                useSharedMachinePool = state.useSharedMachinePool,
                specialItemOptions = state.specialItemOptions,
            ),
            photos = emptyList(),
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            paidAmount = paidAmount,
            paidAt = paidAt,
            prepaidAmount = null,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            when (val result = ticketRepository.createTickets(source = "encargo", tickets = listOf(draft))) {
                is ApiResult.Success -> {
                    val ticket = result.data.firstOrNull()
                    if (ticket == null) {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                error = "El servidor no devolvio el ticket creado.",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                error = null,
                                createdTicket = ticket,
                            )
                        }
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = ErrorMessages.forCode(result.code, result.message),
                        )
                    }
                }
            }
        }
    }

    fun clearCreated() {
        _uiState.update { it.copy(createdTicket = null) }
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCatalogs = true,
                    catalogError = null,
                )
            }

            when (val result = creationRepository.loadCatalogs()) {
                is ApiResult.Success -> {
                    val services = result.data.services
                    val beddingItems = services.filter(::isBeddingService)
                    val extraItems = result.data.addOns.filter(::isExtraAddOn)
                    val inventoryItems = result.data.inventoryItems

                    val validServiceIds = services.map { it.id }.toSet()
                    val validBeddingIds = beddingItems.map { it.id }.toSet()
                    val validExtraIds = extraItems.map { it.id }.toSet()
                    val validInventoryIds = inventoryItems.map { it.id }.toSet()

                    _uiState.update { current ->
                        deriveState(
                            current.copy(
                                isLoadingCatalogs = false,
                                catalogError = null,
                                services = services,
                                beddingItems = beddingItems,
                                extraItems = extraItems,
                                inventoryItems = inventoryItems,
                                selectedBaseServiceId = current.selectedBaseServiceId
                                    ?.takeIf { it in validServiceIds }
                                    ?: services.firstOrNull(::isBaseWashByKilo)?.id
                                    ?: services.firstOrNull()?.id,
                                beddingQuantities = current.beddingQuantities.filterKeys { it in validBeddingIds },
                                inventoryQuantities = current.inventoryQuantities.filterKeys { it in validInventoryIds },
                                selectedExtraIds = current.selectedExtraIds.filter { it in validExtraIds },
                            ),
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalogs = false,
                            catalogError = ErrorMessages.forCode(result.code, result.message),
                        )
                    }
                }
            }
        }
    }

    private fun submitCustomerCreate(
        input: CustomerCreateInput,
        allowDuplicatePhone: Boolean,
    ) {
        viewModelScope.launch {
            _uiState.update {
                deriveState(
                    it.copy(
                        customer = it.customer.copy(
                            isSaving = true,
                            submitError = null,
                        ),
                        error = null,
                    ),
                )
            }

            val result = creationRepository.createCustomer(
                input = input,
                allowDuplicatePhone = allowDuplicatePhone,
            )

            handleCreateCustomerResult(result, input)
        }
    }

    private fun handleCreateCustomerResult(
        result: CustomerCreateResult,
        input: CustomerCreateInput,
    ) {
        when {
            result.requiresDuplicateConfirmation -> {
                _uiState.update {
                    deriveState(
                        it.copy(
                            customer = it.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                duplicatePhoneMatches = result.duplicatePhoneMatches,
                                pendingCreateInput = input,
                            ),
                        ),
                    )
                }
            }

            result.errorMessage != null -> {
                _uiState.update {
                    deriveState(
                        it.copy(
                            customer = it.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                submitError = result.errorMessage,
                                duplicatePhoneMatches = emptyList(),
                                pendingCreateInput = null,
                            ),
                        ),
                    )
                }
            }

            result.customer != null -> {
                selectCustomer(result.customer)
            }

            else -> {
                _uiState.update {
                    deriveState(
                        it.copy(
                            customer = it.customer.copy(
                                isSaving = false,
                                submitError = "No se pudo crear el cliente.",
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun updateUiState(transform: CreateTicketUiState.() -> CreateTicketUiState) {
        _uiState.update { current -> deriveState(current.transform()) }
    }

    private fun deriveState(state: CreateTicketUiState): CreateTicketUiState {
        return state.copy(
            pricing = calculateEncargoPricing(
                baseServiceId = state.selectedBaseServiceId,
                weight = state.weight.toDecimalOrNull() ?: 0.0,
                addOnIds = buildSelectedAddOnIds(state),
                services = state.services,
                addOns = state.extraItems,
                inventoryItems = state.inventoryItems,
            ),
            hasStartedForm = state.hasStartedForm(),
        )
    }

    private fun buildSelectedAddOnIds(state: CreateTicketUiState): List<String> {
        return expandIdsByQuantity(state.beddingQuantities) +
            expandIdsByQuantity(state.inventoryQuantities) +
            state.selectedExtraIds
    }

    private fun CreateTicketUiState.hasStartedForm(): Boolean {
        return customer.selectedCustomer.fullName.isNotBlank() ||
            customer.selectedCustomer.phone.isNotBlank() ||
            customer.selectedCustomer.email.isNotBlank() ||
            customer.searchQuery.isNotBlank() ||
            customer.showCreateForm ||
            customer.createNotes.isNotBlank() ||
            weight != "0" && weight.isNotBlank() ||
            inventorySearchQuery.isNotBlank() ||
            selectedSpecialItemIds.isNotEmpty() ||
            specialItemNotes.isNotBlank() ||
            beddingQuantities.values.any { it > 0 } ||
            inventoryQuantities.values.any { it > 0 } ||
            selectedExtraIds.isNotEmpty() ||
            paymentChoice != EncargoPaymentChoice.PENDING
    }

    private fun Map<String, Int>.adjustQuantity(itemId: String, delta: Int): Map<String, Int> {
        val nextValue = ((this[itemId] ?: 0) + delta).coerceAtLeast(0)
        return toMutableMap().apply {
            if (nextValue == 0) remove(itemId) else put(itemId, nextValue)
        }
    }

    private fun String.toDecimalOrNull(): Double? {
        return trim()
            .replace(',', '.')
            .toDoubleOrNull()
    }

    private fun PaymentMethod.toWireValue(): String {
        return when (this) {
            PaymentMethod.CASH -> "cash"
            PaymentMethod.CARD -> "card"
            PaymentMethod.TRANSFER -> "transfer"
        }
    }
}
