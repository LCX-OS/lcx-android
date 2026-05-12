package com.cleanx.lcx.feature.sales.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.feature.sales.domain.CardCapturedCreateFailure
import com.cleanx.lcx.feature.sales.domain.LoadSalesCatalogsResult
import com.cleanx.lcx.feature.sales.domain.LoadSalesCatalogsUseCase
import com.cleanx.lcx.feature.sales.domain.SalesCatalogSnapshot
import com.cleanx.lcx.feature.sales.domain.SalesCheckoutRequest
import com.cleanx.lcx.feature.sales.domain.SalesCheckoutResult
import com.cleanx.lcx.feature.sales.domain.SalesCheckoutUseCase
import com.cleanx.lcx.feature.sales.domain.anonymousSalesCustomer
import com.cleanx.lcx.feature.sales.domain.calculateSalesCartTotal
import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.CustomerCreateInput
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.CustomerValidationErrors
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import com.cleanx.lcx.feature.tickets.data.findInventoryItemBySkuOrBarcode
import com.cleanx.lcx.feature.tickets.data.inventoryLookupNotFoundMessage
import com.cleanx.lcx.feature.tickets.data.inventoryStockLimitMessage
import com.cleanx.lcx.feature.tickets.data.normalizePhone
import com.cleanx.lcx.feature.tickets.data.toDraft
import com.cleanx.lcx.feature.tickets.domain.create.CreateCustomerUseCase
import com.cleanx.lcx.feature.tickets.domain.create.CustomerPickerUiState
import com.cleanx.lcx.feature.tickets.domain.create.SaveCustomerResult
import com.cleanx.lcx.feature.tickets.domain.create.SearchCustomersResult
import com.cleanx.lcx.feature.tickets.domain.create.SearchCustomersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CUSTOMER_SEARCH_DEBOUNCE_MS = 350L

data class SalesUiState(
    val isLoadingCatalogs: Boolean = true,
    val catalogError: String? = null,
    val equipmentServices: List<ServiceCatalogRecord> = emptyList(),
    val productAddOns: List<AddOnCatalogRecord> = emptyList(),
    val inventoryItems: List<InventoryCatalogRecord> = emptyList(),
    val customer: CustomerPickerUiState = CustomerPickerUiState(),
    val cart: Map<String, Int> = emptyMap(),
    val inventorySearchQuery: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val totalPrice: Double = 0.0,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val criticalFailure: CardCapturedCreateFailure? = null,
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val loadSalesCatalogsUseCase: LoadSalesCatalogsUseCase,
    private val searchCustomersUseCase: SearchCustomersUseCase,
    private val createCustomerUseCase: CreateCustomerUseCase,
    private val salesCheckoutUseCase: SalesCheckoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private var customerSearchJob: Job? = null

    init {
        loadCatalogs()
    }

    fun retryCatalogs() {
        loadCatalogs()
    }

    fun onCustomerSearchQueryChanged(value: String) {
        customerSearchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    searchQuery = value,
                    searchResults = if (value.trim().length >= 2) {
                        state.customer.searchResults
                    } else {
                        emptyList()
                    },
                    isSearching = value.trim().length >= 2,
                    searchError = null,
                ),
                error = null,
                successMessage = null,
            )
        }

        val trimmed = value.trim()
        if (trimmed.length < 2) {
            return
        }

        customerSearchJob = viewModelScope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            if (_uiState.value.customer.searchQuery.trim() != trimmed) return@launch

            when (val result = searchCustomersUseCase(trimmed)) {
                is SearchCustomersResult.Success -> {
                    _uiState.update { state ->
                        if (state.customer.searchQuery.trim() != result.query) {
                            state
                        } else {
                            state.copy(
                                customer = state.customer.copy(
                                    isSearching = false,
                                    searchResults = result.customers,
                                    searchError = null,
                                ),
                            )
                        }
                    }
                }

                is SearchCustomersResult.Failure -> {
                    _uiState.update { state ->
                        if (state.customer.searchQuery.trim() != result.query) {
                            state
                        } else {
                            state.copy(
                                customer = state.customer.copy(
                                    isSearching = false,
                                    searchResults = emptyList(),
                                    searchError = result.message,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun openCreateCustomerForm() {
        _uiState.update { state ->
            val searchQuery = state.customer.searchQuery.trim()
            val normalizedSearchPhone = normalizePhone(searchQuery)
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = state.customer.selectedCustomer.copy(
                        fullName = state.customer.selectedCustomer.fullName.ifBlank {
                            if (searchQuery.isNotBlank() && normalizedSearchPhone.length < 7) {
                                searchQuery
                            } else {
                                ""
                            }
                        },
                        phone = state.customer.selectedCustomer.phone.ifBlank {
                            if (normalizedSearchPhone.length >= 7) searchQuery else ""
                        },
                    ),
                    showCreateForm = true,
                    validationErrors = CustomerValidationErrors(),
                    submitError = null,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                ),
                error = null,
                successMessage = null,
            )
        }
    }

    fun cancelCreateCustomerForm() {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    showCreateForm = false,
                    selectedCustomer = if (state.customer.selectedCustomer.customerId == null) {
                        state.customer.selectedCustomer.copy(
                            fullName = "",
                            phone = "",
                            email = "",
                        )
                    } else {
                        state.customer.selectedCustomer
                    },
                    createNotes = "",
                    validationErrors = CustomerValidationErrors(),
                    submitError = null,
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                    isSaving = false,
                ),
            )
        }
    }

    fun clearSelectedCustomer() {
        customerSearchJob?.cancel()
        _uiState.update {
            it.copy(
                customer = CustomerPickerUiState(),
                error = null,
                successMessage = null,
            )
        }
    }

    fun useAnonymousCustomer() {
        customerSearchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = anonymousSalesCustomer(),
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
                successMessage = null,
            )
        }
    }

    fun selectCustomer(customer: CustomerRecord) {
        customerSearchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = customer.toDraft(),
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
                successMessage = null,
            )
        }
    }

    fun onCustomerFullNameChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = state.customer.selectedCustomer.copy(fullName = value),
                    validationErrors = state.customer.validationErrors.copy(fullName = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerPhoneChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = state.customer.selectedCustomer.copy(phone = value),
                    validationErrors = state.customer.validationErrors.copy(phone = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerEmailChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    selectedCustomer = state.customer.selectedCustomer.copy(email = value),
                    validationErrors = state.customer.validationErrors.copy(email = null),
                    submitError = null,
                ),
                error = null,
            )
        }
    }

    fun onCustomerNotesChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
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

        val input = CustomerCreateInput(
            fullName = current.customer.selectedCustomer.fullName,
            phone = current.customer.selectedCustomer.phone,
            email = current.customer.selectedCustomer.email,
            notes = current.customer.createNotes,
        )

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    customer = state.customer.copy(
                        isSaving = true,
                        submitError = null,
                    ),
                    error = null,
                )
            }

            when (val result = createCustomerUseCase(input, allowDuplicatePhone = false)) {
                is SaveCustomerResult.Success -> selectCustomer(result.customer)

                is SaveCustomerResult.RequiresDuplicateConfirmation -> {
                    _uiState.update { state ->
                        state.copy(
                            customer = state.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                duplicatePhoneMatches = result.matches,
                                pendingCreateInput = result.input,
                            ),
                        )
                    }
                }

                is SaveCustomerResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            customer = state.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                submitError = result.message,
                                duplicatePhoneMatches = emptyList(),
                                pendingCreateInput = null,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun confirmCreateCustomerDespiteDuplicate() {
        val input = _uiState.value.customer.pendingCreateInput ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    customer = state.customer.copy(
                        isSaving = true,
                        submitError = null,
                    ),
                )
            }

            when (val result = createCustomerUseCase(input, allowDuplicatePhone = true)) {
                is SaveCustomerResult.Success -> selectCustomer(result.customer)

                is SaveCustomerResult.RequiresDuplicateConfirmation -> {
                    _uiState.update { state ->
                        state.copy(
                            customer = state.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                duplicatePhoneMatches = result.matches,
                                pendingCreateInput = result.input,
                            ),
                        )
                    }
                }

                is SaveCustomerResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            customer = state.customer.copy(
                                isSaving = false,
                                validationErrors = result.validationErrors,
                                submitError = result.message,
                                duplicatePhoneMatches = emptyList(),
                                pendingCreateInput = null,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun dismissDuplicateCustomerDialog() {
        _uiState.update { state ->
            state.copy(
                customer = state.customer.copy(
                    duplicatePhoneMatches = emptyList(),
                    pendingCreateInput = null,
                    isSaving = false,
                ),
            )
        }
    }

    fun onInventorySearchQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                inventorySearchQuery = value,
                error = null,
                successMessage = null,
            )
        }
    }

    fun submitInventorySearch() {
        addInventoryBySkuOrBarcode(_uiState.value.inventorySearchQuery)
    }

    fun scanInventoryBarcode(value: String) {
        addInventoryBySkuOrBarcode(value)
    }

    fun adjustQuantity(itemId: String, delta: Int) {
        _uiState.update { state ->
            val currentQuantity = state.cart[itemId] ?: 0
            val inventoryItem = state.inventoryItems.firstOrNull { it.id == itemId }
            if (delta > 0 && inventoryItem != null && currentQuantity >= inventoryItem.quantity) {
                return@update state.copy(
                    error = inventoryStockLimitMessage(inventoryItem),
                    successMessage = null,
                )
            }

            val nextQuantity = if (inventoryItem != null) {
                (currentQuantity + delta).coerceIn(0, inventoryItem.quantity)
            } else {
                (currentQuantity + delta).coerceAtLeast(0)
            }
            val nextCart = state.cart.toMutableMap().apply {
                if (nextQuantity == 0) {
                    remove(itemId)
                } else {
                    put(itemId, nextQuantity)
                }
            }
            state.withCatalogSnapshot(
                snapshot = SalesCatalogSnapshot(
                    equipmentServices = state.equipmentServices,
                    productAddOns = state.productAddOns,
                    inventoryItems = state.inventoryItems,
                ),
                cart = nextCart,
            ).copy(
                error = null,
                successMessage = null,
            )
        }
    }

    private fun addInventoryBySkuOrBarcode(rawValue: String) {
        val lookup = rawValue.trim()
        if (lookup.isBlank()) return

        _uiState.update { state ->
            val item = findInventoryItemBySkuOrBarcode(state.inventoryItems, lookup)
            if (item == null) {
                state.copy(
                    inventorySearchQuery = rawValue,
                    error = inventoryLookupNotFoundMessage(lookup),
                    successMessage = null,
                )
            } else {
                val currentQuantity = state.cart[item.id] ?: 0
                if (currentQuantity >= item.quantity) {
                    state.copy(
                        inventorySearchQuery = "",
                        error = inventoryStockLimitMessage(item),
                        successMessage = null,
                    )
                } else {
                    val nextCart = state.cart.toMutableMap().apply {
                        put(item.id, currentQuantity + 1)
                    }
                    state.withCatalogSnapshot(
                        snapshot = SalesCatalogSnapshot(
                            equipmentServices = state.equipmentServices,
                            productAddOns = state.productAddOns,
                            inventoryItems = state.inventoryItems,
                        ),
                        cart = nextCart,
                    ).copy(
                        inventorySearchQuery = "",
                        error = null,
                        successMessage = "Agregado: ${item.itemName}",
                    )
                }
            }
        }
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        _uiState.update {
            it.copy(
                paymentMethod = method,
                error = null,
                successMessage = null,
            )
        }
    }

    fun submit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || snapshot.isLoadingCatalogs || snapshot.customer.isSaving || snapshot.criticalFailure != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    error = null,
                    successMessage = null,
                )
            }

            when (
                val result = salesCheckoutUseCase(
                    request = SalesCheckoutRequest(
                        customer = _uiState.value.customer.selectedCustomer,
                        cart = _uiState.value.cart,
                        catalogs = SalesCatalogSnapshot(
                            equipmentServices = _uiState.value.equipmentServices,
                            productAddOns = _uiState.value.productAddOns,
                            inventoryItems = _uiState.value.inventoryItems,
                        ),
                        paymentMethod = _uiState.value.paymentMethod,
                    ),
                )
            ) {
                is SalesCheckoutResult.Success -> {
                    _uiState.update { state ->
                        state.withCatalogSnapshot(
                            snapshot = SalesCatalogSnapshot(
                                equipmentServices = state.equipmentServices,
                                productAddOns = state.productAddOns,
                                inventoryItems = state.inventoryItems,
                            ),
                            cart = emptyMap(),
                        ).copy(
                            customer = CustomerPickerUiState(),
                            inventorySearchQuery = "",
                            isSubmitting = false,
                            error = null,
                            successMessage = buildSuccessMessage(result),
                            criticalFailure = null,
                        )
                    }
                }

                is SalesCheckoutResult.ValidationFailure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.message,
                        )
                    }
                }

                is SalesCheckoutResult.PaymentCancelled -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.message,
                        )
                    }
                }

                is SalesCheckoutResult.PaymentFailed -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.message,
                        )
                    }
                }

                is SalesCheckoutResult.CreateFailed -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.message,
                        )
                    }
                }

                is SalesCheckoutResult.CardCapturedCreateFailed -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = null,
                            criticalFailure = result.failure,
                        )
                    }
                }
            }
        }
    }

    fun resetAfterCriticalFailure() {
        _uiState.update { state ->
            state.withCatalogSnapshot(
                snapshot = SalesCatalogSnapshot(
                    equipmentServices = state.equipmentServices,
                    productAddOns = state.productAddOns,
                    inventoryItems = state.inventoryItems,
                ),
                cart = emptyMap(),
            ).copy(
                customer = CustomerPickerUiState(),
                inventorySearchQuery = "",
                error = null,
                successMessage = null,
                criticalFailure = null,
                isSubmitting = false,
            )
        }
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCatalogs = true,
                    catalogError = null,
                )
            }

            when (val result = loadSalesCatalogsUseCase()) {
                is LoadSalesCatalogsResult.Success -> {
                    _uiState.update { state ->
                        state.withCatalogSnapshot(
                            snapshot = result.snapshot,
                            cart = state.cart.filterKeys { result.snapshot.containsCatalogItem(it) },
                        ).copy(
                            isLoadingCatalogs = false,
                            catalogError = null,
                        )
                    }
                }

                is LoadSalesCatalogsResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalogs = false,
                            catalogError = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun SalesUiState.withCatalogSnapshot(
        snapshot: SalesCatalogSnapshot,
        cart: Map<String, Int>,
    ): SalesUiState {
        return copy(
            equipmentServices = snapshot.equipmentServices,
            productAddOns = snapshot.productAddOns,
            inventoryItems = snapshot.inventoryItems,
            cart = cart,
            totalPrice = calculateSalesCartTotal(
                cart = cart,
                equipmentServices = snapshot.equipmentServices,
                productAddOns = snapshot.productAddOns,
                inventoryItems = snapshot.inventoryItems,
            ),
        )
    }

    private fun SalesCatalogSnapshot.containsCatalogItem(id: String): Boolean {
        return equipmentServices.any { it.id == id } ||
            productAddOns.any { it.id == id } ||
            inventoryItems.any { it.id == id }
    }

    private fun buildSuccessMessage(result: SalesCheckoutResult.Success): String {
        val ticketsCount = result.tickets.size
        val paymentDetail = if (result.transactionId != null) {
            " Cobro terminal OK."
        } else {
            ""
        }
        return "Venta completada. Se generaron $ticketsCount ticket(s).$paymentDetail"
    }
}
