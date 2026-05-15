package com.cleanx.lcx.ui.ops.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentFormState(
    val incidentType: String = "",
    val severity: String = "medium",
    val description: String = "",
    val peopleInvolved: String = "",
    val actionsTaken: String = "",
    val photos: List<CapturedEvidencePhoto> = emptyList(),
    val audio: CapturedEvidenceAudio? = null,
    val isRecording: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = incidentType.isNotBlank() &&
            description.isNotBlank() &&
            !isSubmitting &&
            !isRecording
}

data class IncidentHistoryState(
    val isLoading: Boolean = true,
    val records: List<IncidentRecord> = emptyList(),
    val query: String = "",
    val statusFilter: String = "all",
    val typeFilter: String = "all",
    val error: String? = null,
) {
    val filteredRecords: List<IncidentRecord>
        get() {
            val normalized = query.trim().lowercase()
            if (normalized.isEmpty()) return records
            return records.filter { record ->
                listOfNotNull(
                    record.id,
                    record.incidentNumber,
                    record.description,
                    record.peopleInvolved,
                    record.actionsTaken,
                    record.reporter?.fullName,
                ).any { it.lowercase().contains(normalized) }
            }
        }
}

@HiltViewModel
class IncidentNewViewModel @Inject constructor(
    private val repository: ProblemDocumentationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IncidentFormState())
    val state: StateFlow<IncidentFormState> = _state.asStateFlow()

    fun updateIncidentType(value: String) = updateState { copy(incidentType = value, error = null) }
    fun updateSeverity(value: String) = updateState { copy(severity = value, error = null) }
    fun updateDescription(value: String) = updateState { copy(description = value, error = null) }
    fun updatePeopleInvolved(value: String) = updateState { copy(peopleInvolved = value, error = null) }
    fun updateActionsTaken(value: String) = updateState { copy(actionsTaken = value, error = null) }
    fun setRecording(recording: Boolean) = updateState { copy(isRecording = recording, error = null) }
    fun setAudio(audio: CapturedEvidenceAudio) = updateState { copy(audio = audio, error = null) }
    fun removeAudio() = updateState { copy(audio = null) }
    fun clearMessage() = updateState { copy(message = null, error = null) }

    fun addPhoto(photo: CapturedEvidencePhoto) {
        updateState {
            if (photos.any { it.uri == photo.uri }) {
                this
            } else {
                copy(photos = photos + photo, error = null)
            }
        }
    }

    fun removePhoto(id: String) = updateState { copy(photos = photos.filterNot { it.id == id }) }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) {
            _state.update {
                it.copy(error = "Completa tipo y descripcion antes de guardar.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, message = null) }
            val input = CreateIncidentInput(
                incidentType = current.incidentType,
                severity = current.severity,
                description = current.description,
                peopleInvolved = current.peopleInvolved.takeIf { it.isNotBlank() },
                actionsTaken = current.actionsTaken.takeIf { it.isNotBlank() },
                photoUris = current.photos.map { it.uri },
                audioPath = current.audio?.path,
                audioMimeType = current.audio?.mimeType,
            )
            repository.createIncident(input)
                .onSuccess { result ->
                    _state.value = IncidentFormState(
                        severity = "medium",
                        message = buildSuccessMessage("Incidente guardado", result.evidence),
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = error.localizedMessage ?: "No se pudo guardar el incidente.",
                        )
                    }
                }
        }
    }

    private fun updateState(block: IncidentFormState.() -> IncidentFormState) {
        _state.update(block)
    }
}

@HiltViewModel
class IncidentHistoryViewModel @Inject constructor(
    private val repository: ProblemDocumentationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IncidentHistoryState())
    val state: StateFlow<IncidentHistoryState> = _state.asStateFlow()

    init {
        load()
    }

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun updateStatusFilter(value: String) {
        _state.update { it.copy(statusFilter = value) }
        load()
    }

    fun updateTypeFilter(value: String) {
        _state.update { it.copy(typeFilter = value) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val current = state.value
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getIncidents(
                status = current.statusFilter.takeIf { it != "all" },
                type = current.typeFilter.takeIf { it != "all" },
            ).onSuccess { records ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        records = records,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.localizedMessage ?: "No se pudo cargar el historial.",
                    )
                }
            }
        }
    }
}
