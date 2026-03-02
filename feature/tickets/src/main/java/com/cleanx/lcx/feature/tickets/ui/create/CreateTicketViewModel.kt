package com.cleanx.lcx.feature.tickets.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.ErrorMessages
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateTicketUiState(
    val customerName: String = "",
    val customerPhone: String = "",
    val serviceType: String = "wash-fold",
    val service: String = "",
    val weight: String = "",
    val notes: String = "",
    val totalAmount: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val createdTicket: Ticket? = null,
)

@HiltViewModel
class CreateTicketViewModel @Inject constructor(
    private val repository: TicketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTicketUiState())
    val uiState: StateFlow<CreateTicketUiState> = _uiState.asStateFlow()

    fun onCustomerNameChanged(value: String) {
        _uiState.update { it.copy(customerName = value, error = null) }
    }

    fun onCustomerPhoneChanged(value: String) {
        _uiState.update { it.copy(customerPhone = value, error = null) }
    }

    fun onServiceTypeChanged(value: String) {
        _uiState.update { it.copy(serviceType = value, error = null) }
    }

    fun onServiceChanged(value: String) {
        _uiState.update { it.copy(service = value, error = null) }
    }

    fun onWeightChanged(value: String) {
        _uiState.update { it.copy(weight = value, error = null) }
    }

    fun onNotesChanged(value: String) {
        _uiState.update { it.copy(notes = value, error = null) }
    }

    fun onTotalAmountChanged(value: String) {
        _uiState.update { it.copy(totalAmount = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value

        // Validation
        if (state.customerName.isBlank()) {
            _uiState.update { it.copy(error = "El nombre del cliente es obligatorio.") }
            return
        }
        if (state.customerPhone.isBlank()) {
            _uiState.update { it.copy(error = "El telefono del cliente es obligatorio.") }
            return
        }
        if (state.service.isBlank()) {
            _uiState.update { it.copy(error = "El servicio es obligatorio.") }
            return
        }

        val parsedWeight = if (state.weight.isNotBlank()) {
            state.weight.toDoubleOrNull()
                ?: run {
                    _uiState.update { it.copy(error = "El peso debe ser un numero valido.") }
                    return
                }
        } else {
            null
        }

        val parsedAmount = if (state.totalAmount.isNotBlank()) {
            state.totalAmount.toDoubleOrNull()
                ?: run {
                    _uiState.update { it.copy(error = "El monto debe ser un numero valido.") }
                    return
                }
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val draft = TicketDraft(
                customerName = state.customerName.trim(),
                customerPhone = state.customerPhone.trim(),
                serviceType = state.serviceType,
                service = state.service.trim(),
                weight = parsedWeight,
                notes = state.notes.trim().ifBlank { null },
                totalAmount = parsedAmount,
            )

            when (val result = repository.createTickets(source = "encargo", tickets = listOf(draft))) {
                is ApiResult.Success -> {
                    val ticket = result.data.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            createdTicket = ticket,
                        )
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
}
