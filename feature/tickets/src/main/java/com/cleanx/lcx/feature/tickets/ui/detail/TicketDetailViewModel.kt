package com.cleanx.lcx.feature.tickets.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
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

data class TicketDetailUiState(
    val ticket: Ticket? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val ticketUpdated: Boolean = false,
)

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    private val repository: TicketRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val ticketId: String = savedStateHandle["ticketId"] ?: ""

    private val _uiState = MutableStateFlow(TicketDetailUiState())
    val uiState: StateFlow<TicketDetailUiState> = _uiState.asStateFlow()

    fun setTicket(ticket: Ticket) {
        _uiState.update { it.copy(ticket = ticket) }
    }

    fun advanceStatus() {
        val current = _uiState.value.ticket ?: return
        val nextStatus = when (current.status) {
            TicketStatus.RECEIVED -> TicketStatus.PROCESSING
            TicketStatus.PROCESSING -> TicketStatus.READY
            TicketStatus.READY -> TicketStatus.DELIVERED
            TicketStatus.DELIVERED -> return // Already at final status
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.updateStatus(current.id, nextStatus)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            ticket = result.data,
                            isLoading = false,
                            ticketUpdated = true,
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = ErrorMessages.forCode(result.code, result.message),
                        )
                    }
                }
            }
        }
    }

    fun markAsPaid(method: PaymentMethod) {
        val current = _uiState.value.ticket ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.updatePayment(
                ticketId = current.id,
                paymentStatus = PaymentStatus.PAID,
                paymentMethod = method,
                paidAmount = current.totalAmount,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            ticket = result.data,
                            isLoading = false,
                            ticketUpdated = true,
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = ErrorMessages.forCode(result.code, result.message),
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeUpdated() {
        _uiState.update { it.copy(ticketUpdated = false) }
    }
}
