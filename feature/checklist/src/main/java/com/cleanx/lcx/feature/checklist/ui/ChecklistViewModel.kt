package com.cleanx.lcx.feature.checklist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.feature.checklist.data.Checklist
import com.cleanx.lcx.feature.checklist.data.ChecklistItemUi
import com.cleanx.lcx.feature.checklist.data.ChecklistRepository
import com.cleanx.lcx.feature.checklist.data.ChecklistStatus
import com.cleanx.lcx.feature.checklist.data.ChecklistType
import com.cleanx.lcx.feature.checklist.data.SYSTEM_VALIDATED_TEMPLATES
import com.cleanx.lcx.feature.checklist.data.TEMPLATE_CASH_REGISTER
import com.cleanx.lcx.feature.checklist.data.TEMPLATE_WATER_LEVEL
import com.cleanx.lcx.feature.checklist.data.canCompleteChecklist
import com.cleanx.lcx.feature.checklist.data.getChecklistProgress
import com.cleanx.lcx.feature.checklist.data.requiredItemCounts
import com.cleanx.lcx.feature.checklist.data.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

data class ChecklistUiState(
    val selectedTab: Int = 0,
    // Entry
    val isLoadingEntry: Boolean = false,
    val entryChecklist: Checklist? = null,
    val entryItems: List<ChecklistItemUi> = emptyList(),
    val entryProgress: Float = 0f,
    val entryRequiredDone: Int = 0,
    val entryRequiredTotal: Int = 0,
    val entryCanComplete: Boolean = false,
    val entryNotes: String = "",
    val entryError: String? = null,
    // Exit
    val isLoadingExit: Boolean = false,
    val exitChecklist: Checklist? = null,
    val exitItems: List<ChecklistItemUi> = emptyList(),
    val exitProgress: Float = 0f,
    val exitRequiredDone: Int = 0,
    val exitRequiredTotal: Int = 0,
    val exitCanComplete: Boolean = false,
    val exitNotes: String = "",
    val exitError: String? = null,
    // History
    val isLoadingHistory: Boolean = false,
    val history: List<Checklist> = emptyList(),
    val historyError: String? = null,
    // Actions
    val isSaving: Boolean = false,
    val isCompleting: Boolean = false,
    val completedMessage: String? = null,
    // Operational routine status (auto-validation)
    val waterLevelRecorded: Boolean = false,
    val cashRegisterOpened: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val repository: ChecklistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChecklistUiState())
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    init {
        loadEntryChecklist()
    }

    // -- TAB SELECTION --------------------------------------------------------

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        when (index) {
            0 -> if (_uiState.value.entryChecklist == null) loadEntryChecklist()
            1 -> if (_uiState.value.exitChecklist == null) loadExitChecklist()
            2 -> if (_uiState.value.history.isEmpty()) loadHistory()
        }
    }

    // -- ENTRY CHECKLIST ------------------------------------------------------

    fun loadEntryChecklist() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingEntry = true, entryError = null) }

            // Load operational routine status in parallel
            val waterDone = repository.hasWaterLevelToday()
            val cashDone = repository.hasCashRegisterToday()

            repository.getTodayChecklist(ChecklistType.ENTRADA)
                .onSuccess { (checklist, items) ->
                    val uiItems = items.map { item ->
                        val ui = item.toUi()
                        // Auto-validate system items
                        if (ui.isSystemValidated) {
                            val autoCompleted = when (ui.metadata.templateId) {
                                TEMPLATE_WATER_LEVEL -> waterDone
                                TEMPLATE_CASH_REGISTER -> cashDone
                                else -> ui.item.isCompleted
                            }
                            ui.copy(
                                item = ui.item.copy(isCompleted = autoCompleted)
                            )
                        } else {
                            ui
                        }
                    }
                    val (done, total) = requiredItemCounts(uiItems)

                    _uiState.update {
                        it.copy(
                            isLoadingEntry = false,
                            entryChecklist = checklist,
                            entryItems = uiItems,
                            entryProgress = getChecklistProgress(uiItems),
                            entryRequiredDone = done,
                            entryRequiredTotal = total,
                            entryCanComplete = canCompleteChecklist(uiItems)
                                && checklist.status != ChecklistStatus.COMPLETED,
                            waterLevelRecorded = waterDone,
                            cashRegisterOpened = cashDone,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load entry checklist")
                    _uiState.update {
                        it.copy(
                            isLoadingEntry = false,
                            entryError = error.message ?: "Error al cargar checklist de entrada",
                        )
                    }
                }
        }
    }

    // -- EXIT CHECKLIST -------------------------------------------------------

    fun loadExitChecklist() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingExit = true, exitError = null) }

            repository.getTodayChecklist(ChecklistType.SALIDA)
                .onSuccess { (checklist, items) ->
                    val uiItems = items.map { it.toUi() }
                    val (done, total) = requiredItemCounts(uiItems)

                    _uiState.update {
                        it.copy(
                            isLoadingExit = false,
                            exitChecklist = checklist,
                            exitItems = uiItems,
                            exitProgress = getChecklistProgress(uiItems),
                            exitRequiredDone = done,
                            exitRequiredTotal = total,
                            exitCanComplete = canCompleteChecklist(uiItems)
                                && checklist.status != ChecklistStatus.COMPLETED,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load exit checklist")
                    _uiState.update {
                        it.copy(
                            isLoadingExit = false,
                            exitError = error.message ?: "Error al cargar checklist de salida",
                        )
                    }
                }
        }
    }

    // -- TOGGLE ITEM ----------------------------------------------------------

    fun toggleItem(itemUi: ChecklistItemUi, type: ChecklistType) {
        // System-validated items cannot be manually toggled
        if (itemUi.isSystemValidated) return

        // Already completed checklists are read-only
        val checklist = when (type) {
            ChecklistType.ENTRADA -> _uiState.value.entryChecklist
            ChecklistType.SALIDA -> _uiState.value.exitChecklist
        }
        if (checklist?.status == ChecklistStatus.COMPLETED) return

        val itemId = itemUi.item.id ?: return
        val newCompleted = !itemUi.item.isCompleted

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }

            repository.updateChecklistItem(itemId, newCompleted)
                .onSuccess { updatedItem ->
                    updateItemInState(updatedItem.toUi(), type)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to toggle item %s", itemId)
                    // Optimistic update was not applied; no revert needed
                }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    // -- NOTES ----------------------------------------------------------------

    fun updateNotes(notes: String, type: ChecklistType) {
        when (type) {
            ChecklistType.ENTRADA -> _uiState.update { it.copy(entryNotes = notes) }
            ChecklistType.SALIDA -> _uiState.update { it.copy(exitNotes = notes) }
        }
    }

    // -- COMPLETE CHECKLIST ---------------------------------------------------

    fun completeChecklist(type: ChecklistType) {
        val state = _uiState.value
        val checklistId = when (type) {
            ChecklistType.ENTRADA -> state.entryChecklist?.id
            ChecklistType.SALIDA -> state.exitChecklist?.id
        } ?: return

        val notes = when (type) {
            ChecklistType.ENTRADA -> state.entryNotes
            ChecklistType.SALIDA -> state.exitNotes
        }.takeIf { it.isNotBlank() }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCompleting = true) }

            repository.completeChecklist(checklistId, notes)
                .onSuccess { updatedChecklist ->
                    val label = when (type) {
                        ChecklistType.ENTRADA -> "entrada"
                        ChecklistType.SALIDA -> "salida"
                    }
                    when (type) {
                        ChecklistType.ENTRADA -> _uiState.update {
                            it.copy(
                                isCompleting = false,
                                entryChecklist = updatedChecklist,
                                entryCanComplete = false,
                                completedMessage = "Checklist de $label completado",
                                selectedTab = 2, // Switch to history
                            )
                        }
                        ChecklistType.SALIDA -> _uiState.update {
                            it.copy(
                                isCompleting = false,
                                exitChecklist = updatedChecklist,
                                exitCanComplete = false,
                                completedMessage = "Checklist de $label completado",
                                selectedTab = 2,
                            )
                        }
                    }
                    // Refresh history
                    loadHistory()
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to complete checklist %s", checklistId)
                    _uiState.update {
                        it.copy(
                            isCompleting = false,
                            completedMessage = "Error al completar: ${error.message}",
                        )
                    }
                }
        }
    }

    // -- HISTORY --------------------------------------------------------------

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingHistory = true, historyError = null) }

            repository.getChecklistHistory()
                .onSuccess { checklists ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            history = checklists,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            historyError = error.message ?: "Error al cargar historial",
                        )
                    }
                }
        }
    }

    // -- CLEAR MESSAGE --------------------------------------------------------

    fun clearCompletedMessage() {
        _uiState.update { it.copy(completedMessage = null) }
    }

    // -- PRIVATE HELPERS ------------------------------------------------------

    private fun updateItemInState(updatedUi: ChecklistItemUi, type: ChecklistType) {
        _uiState.update { state ->
            when (type) {
                ChecklistType.ENTRADA -> {
                    val newItems = state.entryItems.map { existing ->
                        if (existing.item.id == updatedUi.item.id) updatedUi else existing
                    }
                    val (done, total) = requiredItemCounts(newItems)
                    state.copy(
                        entryItems = newItems,
                        entryProgress = getChecklistProgress(newItems),
                        entryRequiredDone = done,
                        entryRequiredTotal = total,
                        entryCanComplete = canCompleteChecklist(newItems)
                            && state.entryChecklist?.status != ChecklistStatus.COMPLETED,
                    )
                }
                ChecklistType.SALIDA -> {
                    val newItems = state.exitItems.map { existing ->
                        if (existing.item.id == updatedUi.item.id) updatedUi else existing
                    }
                    val (done, total) = requiredItemCounts(newItems)
                    state.copy(
                        exitItems = newItems,
                        exitProgress = getChecklistProgress(newItems),
                        exitRequiredDone = done,
                        exitRequiredTotal = total,
                        exitCanComplete = canCompleteChecklist(newItems)
                            && state.exitChecklist?.status != ChecklistStatus.COMPLETED,
                    )
                }
            }
        }
    }
}
