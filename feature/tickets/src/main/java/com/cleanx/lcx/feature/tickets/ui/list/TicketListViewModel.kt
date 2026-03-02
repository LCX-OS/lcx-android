package com.cleanx.lcx.feature.tickets.ui.list

import androidx.lifecycle.ViewModel
import com.cleanx.lcx.core.model.Ticket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TicketListUiState(
    val isLoading: Boolean = false,
    val tickets: List<Ticket> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TicketListViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TicketListUiState())
    val uiState: StateFlow<TicketListUiState> = _uiState.asStateFlow()

    fun addCreatedTicket(ticket: Ticket) {
        _uiState.update { state ->
            state.copy(
                tickets = listOf(ticket) + state.tickets,
                error = null,
            )
        }
    }

    fun updateTicketInList(updated: Ticket) {
        _uiState.update { state ->
            state.copy(
                tickets = state.tickets.map { if (it.id == updated.id) updated else it },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
