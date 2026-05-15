package com.cleanx.lcx.ui.ops.problems

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PHOTO_PREFIX = "image/"
private const val AUDIO_PREFIX = "audio/"

data class ProblemOption(
    val value: String,
    val label: String,
)

data class CapturedEvidencePhoto(
    val id: String,
    val uri: String,
)

data class CapturedEvidenceAudio(
    val path: String,
    val fileName: String,
    val mimeType: String = "audio/mp4",
)

data class EvidenceUploadSummary(
    val totalPhotos: Int = 0,
    val uploadedPhotos: Int = 0,
    val audioRequested: Boolean = false,
    val audioUploaded: Boolean = false,
    val failedUploads: Int = 0,
    val firstErrorMessage: String? = null,
) {
    val hasFailures: Boolean = failedUploads > 0 || (audioRequested && !audioUploaded)
}

data class SubmitProblemResult(
    val recordId: String,
    val evidence: EvidenceUploadSummary,
)

data class CreateDamagedClothingInput(
    val ticketReference: String,
    val garmentType: String?,
    val damageType: String?,
    val description: String,
    val audioTranscript: String?,
    val photoUris: List<String>,
    val audioPath: String?,
    val audioMimeType: String?,
)

data class CreateIncidentInput(
    val incidentType: String,
    val severity: String,
    val description: String,
    val peopleInvolved: String?,
    val actionsTaken: String?,
    val photoUris: List<String>,
    val audioPath: String?,
    val audioMimeType: String?,
)

@Serializable
data class ProblemEvidenceRow(
    val id: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_type") val fileType: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ProblemTicketSummary(
    @SerialName("ticket_number") val ticketNumber: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("service_type") val serviceType: String,
)

@Serializable
data class ProblemProfileSummary(
    @SerialName("full_name") val fullName: String? = null,
    val branch: String? = null,
)

@Serializable
data class DamagedClothingRecord(
    val id: String,
    @SerialName("ticket_id") val ticketId: String? = null,
    @SerialName("damage_description") val damageDescription: String,
    @SerialName("reported_by") val reportedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val ticket: ProblemTicketSummary? = null,
    @SerialName("reported_by_profile") val reportedByProfile: ProblemProfileSummary? = null,
    val evidence: List<ProblemEvidenceRow>? = null,
)

@Serializable
data class IncidentRecord(
    val id: String,
    @SerialName("incident_number") val incidentNumber: String,
    @SerialName("incident_type") val incidentType: String,
    val description: String,
    @SerialName("people_involved") val peopleInvolved: String? = null,
    @SerialName("actions_taken") val actionsTaken: String? = null,
    @SerialName("reported_by") val reportedBy: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    val reporter: ProblemProfileSummary? = null,
    @SerialName("assigned_to_profile") val assignedToProfile: ProblemProfileSummary? = null,
    val evidence: List<ProblemEvidenceRow>? = null,
)

val incidentTypeOptions = listOf(
    ProblemOption("machine", "Falla de maquina"),
    ProblemOption("customer", "Problema con cliente"),
    ProblemOption("safety", "Seguridad"),
    ProblemOption("staff", "Personal"),
    ProblemOption("other", "Otro"),
)

val incidentSeverityOptions = listOf(
    ProblemOption("low", "Baja"),
    ProblemOption("medium", "Media"),
    ProblemOption("high", "Alta"),
    ProblemOption("critical", "Critica"),
)

val incidentStatusOptions = listOf(
    ProblemOption("all", "Todos"),
    ProblemOption("pending", "Pendiente"),
    ProblemOption("in_progress", "En proceso"),
    ProblemOption("resolved", "Resuelto"),
)

val garmentTypeOptions = listOf(
    ProblemOption("shirt", "Camisa"),
    ProblemOption("pants", "Pantalon"),
    ProblemOption("dress", "Vestido"),
    ProblemOption("jacket", "Chaqueta"),
    ProblemOption("skirt", "Falda"),
    ProblemOption("suit", "Traje"),
    ProblemOption("other", "Otro"),
)

val damageTypeOptions = listOf(
    ProblemOption("stain", "Mancha"),
    ProblemOption("tear", "Rasgadura"),
    ProblemOption("hole", "Agujero"),
    ProblemOption("burn", "Quemadura"),
    ProblemOption("discoloration", "Decoloracion"),
    ProblemOption("missing_button", "Boton faltante"),
    ProblemOption("broken_zipper", "Cierre roto"),
    ProblemOption("other", "Otro"),
)

fun optionLabel(options: List<ProblemOption>, value: String?): String {
    return options.firstOrNull { it.value == value }?.label ?: value.orEmpty()
}

fun evidencePhotoCount(evidence: List<ProblemEvidenceRow>?): Int {
    return evidence.orEmpty().count { item ->
        item.fileType.startsWith(PHOTO_PREFIX) || item.fileType == "photo"
    }
}

fun evidenceAudioCount(evidence: List<ProblemEvidenceRow>?): Int {
    return evidence.orEmpty().count { item ->
        item.fileType.startsWith(AUDIO_PREFIX) || item.fileType == "audio"
    }
}

fun formatProblemDate(value: String?): String {
    if (value.isNullOrBlank()) return "Sin fecha"
    return runCatching {
        val parsed = OffsetDateTime.parse(value)
        parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX")))
    }.getOrElse { value.take(16).replace('T', ' ') }
}
