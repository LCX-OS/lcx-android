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

data class DamagedClothingFormState(
    val ticketReference: String = "",
    val garmentType: String = "",
    val damageType: String = "",
    val description: String = "",
    val audioTranscript: String = "",
    val photos: List<CapturedEvidencePhoto> = emptyList(),
    val audio: CapturedEvidenceAudio? = null,
    val isRecording: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = ticketReference.isNotBlank() &&
            description.isNotBlank() &&
            !isSubmitting &&
            !isRecording
}

data class DamagedClothingHistoryState(
    val isLoading: Boolean = true,
    val records: List<DamagedClothingRecord> = emptyList(),
    val query: String = "",
    val error: String? = null,
) {
    val filteredRecords: List<DamagedClothingRecord>
        get() {
            val normalized = query.trim().lowercase()
            if (normalized.isEmpty()) return records
            return records.filter { record ->
                listOfNotNull(
                    record.id,
                    record.ticket?.ticketNumber,
                    record.ticket?.customerName,
                    record.damageDescription,
                    record.reportedByProfile?.fullName,
                ).any { it.lowercase().contains(normalized) }
            }
        }
}

@HiltViewModel
class DamagedClothingNewViewModel @Inject constructor(
    private val repository: ProblemDocumentationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DamagedClothingFormState())
    val state: StateFlow<DamagedClothingFormState> = _state.asStateFlow()

    fun updateTicketReference(value: String) = updateState { copy(ticketReference = value, error = null) }
    fun updateGarmentType(value: String) = updateState { copy(garmentType = value, error = null) }
    fun updateDamageType(value: String) = updateState { copy(damageType = value, error = null) }
    fun updateDescription(value: String) = updateState { copy(description = value, error = null) }
    fun updateAudioTranscript(value: String) = updateState { copy(audioTranscript = value, error = null) }
    fun setRecording(recording: Boolean) = updateState { copy(isRecording = recording, error = null) }

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
    fun setAudio(audio: CapturedEvidenceAudio) = updateState { copy(audio = audio, error = null) }
    fun removeAudio() = updateState { copy(audio = null) }
    fun clearMessage() = updateState { copy(message = null, error = null) }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) {
            _state.update {
                it.copy(error = "Completa ticket y descripcion antes de guardar.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, message = null) }
            val input = CreateDamagedClothingInput(
                ticketReference = current.ticketReference,
                garmentType = current.garmentType.takeIf { it.isNotBlank() },
                damageType = current.damageType.takeIf { it.isNotBlank() },
                description = current.description,
                audioTranscript = current.audioTranscript.takeIf { it.isNotBlank() },
                photoUris = current.photos.map { it.uri },
                audioPath = current.audio?.path,
                audioMimeType = current.audio?.mimeType,
            )
            repository.createDamagedClothing(input)
                .onSuccess { result ->
                    _state.value = DamagedClothingFormState(
                        message = buildSuccessMessage("Reporte guardado", result.evidence),
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = error.localizedMessage ?: "No se pudo guardar el reporte.",
                        )
                    }
                }
        }
    }

    private fun updateState(block: DamagedClothingFormState.() -> DamagedClothingFormState) {
        _state.update(block)
    }
}

@HiltViewModel
class DamagedClothingHistoryViewModel @Inject constructor(
    private val repository: ProblemDocumentationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DamagedClothingHistoryState())
    val state: StateFlow<DamagedClothingHistoryState> = _state.asStateFlow()

    init {
        load()
    }

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getDamagedClothingRecords()
                .onSuccess { records ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            records = records,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
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

internal fun buildSuccessMessage(prefix: String, evidence: EvidenceUploadSummary): String {
    val audio = when {
        evidence.audioRequested && evidence.audioUploaded -> " - Audio: ok"
        evidence.audioRequested -> " - Audio: fallo"
        else -> ""
    }
    val base = "$prefix. Fotos: ${evidence.uploadedPhotos}/${evidence.totalPhotos}$audio"
    return if (evidence.hasFailures && evidence.firstErrorMessage != null) {
        "$base. Evidencia parcial: ${evidence.firstErrorMessage}"
    } else {
        base
    }
}
