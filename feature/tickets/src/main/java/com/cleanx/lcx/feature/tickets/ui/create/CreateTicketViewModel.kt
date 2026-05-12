package com.cleanx.lcx.feature.tickets.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.feature.tickets.data.CustomerCreateInput
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.domain.create.BuildEncargoDraftResult
import com.cleanx.lcx.feature.tickets.domain.create.BuildEncargoDraftUseCase
import com.cleanx.lcx.feature.tickets.domain.create.CreateCustomerUseCase
import com.cleanx.lcx.feature.tickets.domain.create.CreateEncargoResult
import com.cleanx.lcx.feature.tickets.domain.create.CreateEncargoUseCase
import com.cleanx.lcx.feature.tickets.domain.create.CreateTicketMutation
import com.cleanx.lcx.feature.tickets.domain.create.CreateTicketReducer
import com.cleanx.lcx.feature.tickets.domain.create.CreateTicketUiState
import com.cleanx.lcx.feature.tickets.domain.create.EncargoPaymentChoice
import com.cleanx.lcx.feature.tickets.domain.create.LoadEncargoCatalogsResult
import com.cleanx.lcx.feature.tickets.domain.create.LoadEncargoCatalogsUseCase
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val CUSTOMER_SEARCH_DEBOUNCE_MS = 350L
private val CatalogLoadedFormatter = DateTimeFormatter.ofPattern("HH:mm")

@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val reducer: CreateTicketReducer,
    private val loadEncargoCatalogsUseCase: LoadEncargoCatalogsUseCase,
    private val searchCustomersUseCase: SearchCustomersUseCase,
    private val createCustomerUseCase: CreateCustomerUseCase,
    private val buildEncargoDraftUseCase: BuildEncargoDraftUseCase,
    private val createEncargoUseCase: CreateEncargoUseCase,
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
        mutate(CreateTicketMutation.CustomerSearchQueryChanged(value))

        val trimmed = value.trim()
        if (trimmed.length < 2) {
            return
        }

        customerSearchJob = viewModelScope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            val latestQuery = _uiState.value.customer.searchQuery.trim()
            if (latestQuery != trimmed) return@launch

            when (val result = searchCustomersUseCase(trimmed)) {
                is SearchCustomersResult.Success -> {
                    mutate(
                        CreateTicketMutation.CustomerSearchResolved(
                            query = result.query,
                            results = result.customers,
                        ),
                    )
                }

                is SearchCustomersResult.Failure -> {
                    mutate(
                        CreateTicketMutation.CustomerSearchFailed(
                            query = result.query,
                            message = result.message,
                        ),
                    )
                }
            }
        }
    }

    fun openCreateCustomerForm() {
        mutate(CreateTicketMutation.CreateCustomerFormOpened)
    }

    fun cancelCreateCustomerForm() {
        mutate(CreateTicketMutation.CreateCustomerFormCancelled)
    }

    fun clearSelectedCustomer() {
        customerSearchJob?.cancel()
        mutate(CreateTicketMutation.SelectedCustomerCleared)
    }

    fun selectCustomer(customerRecord: CustomerRecord) {
        customerSearchJob?.cancel()
        mutate(CreateTicketMutation.CustomerSelected(customerRecord))
    }

    fun onCustomerFullNameChanged(value: String) {
        mutate(CreateTicketMutation.CustomerFullNameChanged(value))
    }

    fun onCustomerPhoneChanged(value: String) {
        mutate(CreateTicketMutation.CustomerPhoneChanged(value))
    }

    fun onCustomerEmailChanged(value: String) {
        mutate(CreateTicketMutation.CustomerEmailChanged(value))
    }

    fun onCustomerNotesChanged(value: String) {
        mutate(CreateTicketMutation.CustomerNotesChanged(value))
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
        mutate(CreateTicketMutation.DuplicateCustomerDialogDismissed)
    }

    fun onBaseServiceSelected(serviceId: String) {
        mutate(CreateTicketMutation.BaseServiceSelected(serviceId))
    }

    fun onWeightChanged(value: String) {
        mutate(CreateTicketMutation.WeightChanged(value))
    }

    fun onPickupEstimateChanged(value: String) {
        mutate(CreateTicketMutation.PickupEstimateChanged(value))
    }

    fun onInventorySearchQueryChanged(value: String) {
        mutate(CreateTicketMutation.InventorySearchQueryChanged(value))
    }

    fun submitInventorySearch() {
        mutate(CreateTicketMutation.InventoryLookupSubmitted(_uiState.value.inventorySearchQuery))
    }

    fun scanInventoryBarcode(value: String) {
        mutate(CreateTicketMutation.InventoryLookupSubmitted(value))
    }

    fun setSharedMachinePool(enabled: Boolean) {
        mutate(CreateTicketMutation.SharedMachinePoolChanged(enabled))
    }

    fun toggleSpecialItem(itemId: String) {
        mutate(CreateTicketMutation.SpecialItemToggled(itemId))
    }

    fun onSpecialItemNotesChanged(value: String) {
        mutate(CreateTicketMutation.SpecialItemNotesChanged(value))
    }

    fun adjustBeddingQuantity(itemId: String, delta: Int) {
        mutate(CreateTicketMutation.BeddingQuantityAdjusted(itemId, delta))
    }

    fun adjustInventoryQuantity(itemId: String, delta: Int) {
        mutate(CreateTicketMutation.InventoryQuantityAdjusted(itemId, delta))
    }

    fun toggleExtra(itemId: String) {
        mutate(CreateTicketMutation.ExtraToggled(itemId))
    }

    fun onPaymentChoiceChanged(choice: EncargoPaymentChoice) {
        mutate(CreateTicketMutation.PaymentChoiceChanged(choice))
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        mutate(CreateTicketMutation.PaymentMethodChanged(method))
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.isLoadingCatalogs || state.customer.isSaving) return

        when (val result = buildEncargoDraftUseCase(state)) {
            is BuildEncargoDraftResult.ValidationFailure -> {
                mutate(
                    CreateTicketMutation.SubmitFailed(
                        message = result.message,
                        customerValidationErrors = result.customerValidationErrors,
                    ),
                )
            }

            is BuildEncargoDraftResult.Success -> {
                viewModelScope.launch {
                    mutate(CreateTicketMutation.SubmitStarted)
                    when (val createResult = createEncargoUseCase(result.draft)) {
                        is CreateEncargoResult.Success -> {
                            mutate(CreateTicketMutation.SubmitSucceeded(createResult.ticket))
                        }

                        is CreateEncargoResult.Failure -> {
                            mutate(CreateTicketMutation.SubmitFailed(createResult.message))
                        }
                    }
                }
            }
        }
    }

    fun clearCreated() {
        mutate(CreateTicketMutation.CreatedTicketCleared)
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            mutate(CreateTicketMutation.CatalogLoadingStarted)

            when (val result = loadEncargoCatalogsUseCase()) {
                is LoadEncargoCatalogsResult.Success -> {
                    mutate(
                        CreateTicketMutation.CatalogsLoaded(
                            snapshot = result.snapshot,
                            loadedAtLabel = LocalDateTime.now().format(CatalogLoadedFormatter),
                        ),
                    )
                }

                is LoadEncargoCatalogsResult.Failure -> {
                    mutate(CreateTicketMutation.CatalogLoadFailed(result.message))
                }
            }
        }
    }

    private fun submitCustomerCreate(
        input: CustomerCreateInput,
        allowDuplicatePhone: Boolean,
    ) {
        viewModelScope.launch {
            mutate(CreateTicketMutation.CustomerCreateStarted)

            when (val result = createCustomerUseCase(input, allowDuplicatePhone)) {
                is SaveCustomerResult.Success -> {
                    customerSearchJob?.cancel()
                    mutate(CreateTicketMutation.CustomerSelected(result.customer))
                }

                is SaveCustomerResult.RequiresDuplicateConfirmation -> {
                    mutate(
                        CreateTicketMutation.CustomerCreateRequiresDuplicate(
                            input = result.input,
                            matches = result.matches,
                            validationErrors = result.validationErrors,
                        ),
                    )
                }

                is SaveCustomerResult.Failure -> {
                    mutate(
                        CreateTicketMutation.CustomerCreateFailed(
                            message = result.message,
                            validationErrors = result.validationErrors,
                        ),
                    )
                }
            }
        }
    }

    private fun mutate(mutation: CreateTicketMutation) {
        _uiState.update { current -> reducer(current, mutation) }
    }
}
