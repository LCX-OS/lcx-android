package com.cleanx.lcx.ui.ops.problems

import android.content.Context
import android.net.Uri
import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.network.SupabaseStorageClient
import com.cleanx.lcx.core.network.SupabaseTableClient
import com.cleanx.lcx.core.session.SessionProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val DAMAGED_CLOTHING_TABLE = "damaged_clothing"
private const val DAMAGED_CLOTHING_EVIDENCE_TABLE = "damaged_clothing_evidence"
private const val INCIDENTS_TABLE = "incidents"
private const val INCIDENT_EVIDENCE_TABLE = "incident_evidence"
private const val AUDIT_LOG_TABLE = "audit_logs"
private val UUID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
)

@Serializable
private data class IdOnly(
    val id: String,
)

@Serializable
private data class TicketLookupRow(
    val id: String,
    @SerialName("ticket_number") val ticketNumber: String,
)

@Serializable
private data class DamagedClothingInsert(
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("damage_description") val damageDescription: String,
    @SerialName("reported_by") val reportedBy: String,
)

@Serializable
private data class DamagedClothingEvidenceInsert(
    @SerialName("damaged_clothing_id") val damagedClothingId: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_type") val fileType: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

@Serializable
private data class IncidentInsert(
    @SerialName("incident_number") val incidentNumber: String,
    @SerialName("incident_type") val incidentType: String,
    val description: String,
    @SerialName("people_involved") val peopleInvolved: String? = null,
    @SerialName("actions_taken") val actionsTaken: String? = null,
    @SerialName("reported_by") val reportedBy: String,
    val status: String = "pending",
)

@Serializable
private data class IncidentEvidenceInsert(
    @SerialName("incident_id") val incidentId: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_type") val fileType: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

@Serializable
private data class AuditLogInsert(
    @SerialName("table_name") val tableName: String,
    @SerialName("record_id") val recordId: String,
    val action: String,
    @SerialName("changed_data") val changedData: JsonObject? = null,
    @SerialName("performed_by") val performedBy: String? = null,
)

@Singleton
class ProblemDocumentationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseTableClient,
    private val storage: SupabaseStorageClient,
    private val profileRepository: SessionProfileRepository,
    private val config: BuildConfigProvider,
) {
    suspend fun createDamagedClothing(input: CreateDamagedClothingInput): Result<SubmitProblemResult> {
        return runCatching {
            val profile = profileRepository.getCurrentProfile()
            val ticketId = resolveTicketId(input.ticketReference).getOrThrow()
            val description = buildDamageDescription(input)
            val insert = DamagedClothingInsert(
                ticketId = ticketId,
                damageDescription = description,
                reportedBy = profile.userId,
            )
            val record = supabase.insertReturning<DamagedClothingInsert, DamagedClothingRecord>(
                table = DAMAGED_CLOTHING_TABLE,
                value = insert,
            ).getOrThrow()

            logAuditAction(
                tableName = DAMAGED_CLOTHING_TABLE,
                recordId = record.id,
                action = "create",
                performedBy = profile.userId,
                changedData = buildJsonObject {
                    put("ticket_id", JsonPrimitive(ticketId))
                    put("damage_description", JsonPrimitive(description))
                },
            )

            val evidence = uploadDamagedEvidence(
                recordId = record.id,
                userId = profile.userId,
                photoUris = input.photoUris,
                audioPath = input.audioPath,
                audioMimeType = input.audioMimeType,
            )
            SubmitProblemResult(recordId = record.id, evidence = evidence)
        }.onFailure { error ->
            Timber.w(error, "createDamagedClothing failed")
        }
    }

    suspend fun getDamagedClothingRecords(limit: Long = 50): Result<List<DamagedClothingRecord>> {
        val profile = profileRepository.getCurrentProfile()
        val branch = profile.branch
        val columns = if (branch != null) {
            Columns.raw(
                """
                *,
                ticket:tickets!damaged_clothing_ticket_id_fkey(ticket_number,customer_name,service_type),
                reported_by_profile:profiles!damaged_clothing_reported_by_fkey!inner(full_name,branch),
                evidence:damaged_clothing_evidence(*)
                """.trimIndent(),
            )
        } else {
            Columns.raw(
                """
                *,
                ticket:tickets!damaged_clothing_ticket_id_fkey(ticket_number,customer_name,service_type),
                reported_by_profile:profiles!damaged_clothing_reported_by_fkey(full_name,branch),
                evidence:damaged_clothing_evidence(*)
                """.trimIndent(),
            )
        }

        return supabase.selectWithRequest<DamagedClothingRecord>(
            table = DAMAGED_CLOTHING_TABLE,
            columns = columns,
        ) {
            if (branch != null) {
                filter { eq("reported_by_profile.branch", branch) }
            }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.onFailure { error ->
            Timber.w(error, "getDamagedClothingRecords failed")
        }
    }

    suspend fun createIncident(input: CreateIncidentInput): Result<SubmitProblemResult> {
        return runCatching {
            val profile = profileRepository.getCurrentProfile()
            val description = buildIncidentDescription(input)
            val insert = IncidentInsert(
                incidentNumber = generateIncidentNumber(),
                incidentType = input.incidentType,
                description = description,
                peopleInvolved = input.peopleInvolved?.trim()?.takeIf { it.isNotEmpty() },
                actionsTaken = input.actionsTaken?.trim()?.takeIf { it.isNotEmpty() },
                reportedBy = profile.userId,
            )
            val incident = supabase.insertReturning<IncidentInsert, IncidentRecord>(
                table = INCIDENTS_TABLE,
                value = insert,
            ).getOrThrow()

            logAuditAction(
                tableName = INCIDENTS_TABLE,
                recordId = incident.id,
                action = "create",
                performedBy = profile.userId,
                changedData = buildJsonObject {
                    put("incident_number", JsonPrimitive(insert.incidentNumber))
                    put("incident_type", JsonPrimitive(insert.incidentType))
                    put("description", JsonPrimitive(description))
                    put("status", JsonPrimitive(insert.status))
                },
            )

            val evidence = uploadIncidentEvidence(
                incidentId = incident.id,
                userId = profile.userId,
                photoUris = input.photoUris,
                audioPath = input.audioPath,
                audioMimeType = input.audioMimeType,
            )
            SubmitProblemResult(recordId = incident.id, evidence = evidence)
        }.onFailure { error ->
            Timber.w(error, "createIncident failed")
        }
    }

    suspend fun getIncidents(
        status: String? = null,
        type: String? = null,
        limit: Long = 50,
    ): Result<List<IncidentRecord>> {
        val profile = profileRepository.getCurrentProfile()
        val branch = profile.branch
        val columns = if (branch != null) {
            Columns.raw(
                """
                *,
                reporter:profiles!incidents_reported_by_fkey!inner(full_name,branch),
                assigned_to_profile:profiles!incidents_assigned_to_fkey(full_name,branch),
                evidence:incident_evidence(*)
                """.trimIndent(),
            )
        } else {
            Columns.raw(
                """
                *,
                reporter:profiles!incidents_reported_by_fkey(full_name,branch),
                assigned_to_profile:profiles!incidents_assigned_to_fkey(full_name,branch),
                evidence:incident_evidence(*)
                """.trimIndent(),
            )
        }

        return supabase.selectWithRequest<IncidentRecord>(
            table = INCIDENTS_TABLE,
            columns = columns,
        ) {
            filter {
                if (branch != null) {
                    eq("reporter.branch", branch)
                }
                if (status != null) {
                    eq("status", status)
                }
                if (type != null) {
                    eq("incident_type", type)
                }
            }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.onFailure { error ->
            Timber.w(error, "getIncidents failed")
        }
    }

    private suspend fun resolveTicketId(ticketReference: String): Result<String> {
        val normalized = ticketReference.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresa el numero de ticket."))
        }

        return supabase.selectSingle<TicketLookupRow>("tickets") {
            if (UUID_PATTERN.matches(normalized)) {
                eq("id", normalized)
            } else {
                eq("ticket_number", normalized)
            }
        }.map { row ->
            row?.id ?: throw IllegalArgumentException("Ticket no encontrado.")
        }
    }

    private suspend fun uploadDamagedEvidence(
        recordId: String,
        userId: String,
        photoUris: List<String>,
        audioPath: String?,
        audioMimeType: String?,
    ): EvidenceUploadSummary {
        return uploadEvidence(
            ownerId = recordId,
            userId = userId,
            photoUris = photoUris,
            audioPath = audioPath,
            audioMimeType = audioMimeType,
            photoFolder = "damaged-clothes/photos",
            audioFolder = "damaged-clothes/audio",
            insertEvidence = { path, contentType ->
                val row = DamagedClothingEvidenceInsert(
                    damagedClothingId = recordId,
                    fileUrl = path,
                    fileType = contentType,
                    uploadedBy = userId,
                )
                supabase.insert(DAMAGED_CLOTHING_EVIDENCE_TABLE, row).map { Unit }
            },
        )
    }

    private suspend fun uploadIncidentEvidence(
        incidentId: String,
        userId: String,
        photoUris: List<String>,
        audioPath: String?,
        audioMimeType: String?,
    ): EvidenceUploadSummary {
        return uploadEvidence(
            ownerId = incidentId,
            userId = userId,
            photoUris = photoUris,
            audioPath = audioPath,
            audioMimeType = audioMimeType,
            photoFolder = "incident-photos",
            audioFolder = "incident-audio",
            insertEvidence = { path, contentType ->
                val row = IncidentEvidenceInsert(
                    incidentId = incidentId,
                    fileUrl = path,
                    fileType = contentType,
                    uploadedBy = userId,
                )
                supabase.insert(INCIDENT_EVIDENCE_TABLE, row).map { Unit }
            },
        )
    }

    private suspend fun uploadEvidence(
        ownerId: String,
        userId: String,
        photoUris: List<String>,
        audioPath: String?,
        audioMimeType: String?,
        photoFolder: String,
        audioFolder: String,
        insertEvidence: suspend (path: String, contentType: String) -> Result<Unit>,
    ): EvidenceUploadSummary {
        var uploadedPhotos = 0
        var failedUploads = 0
        var firstErrorMessage: String? = null

        for (uri in photoUris) {
            val bytesResult = readEvidenceBytes { readUriBytes(uri) }
            val bytes = bytesResult.getOrNull()
            if (bytes == null) {
                failedUploads++
                firstErrorMessage = firstErrorMessage ?: bytesResult.exceptionOrNull()?.localizedMessage
                continue
            }

            val uploadResult = uploadSingleEvidence(
                ownerId = ownerId,
                userId = userId,
                folder = photoFolder,
                bytes = bytes,
                contentType = "image/jpeg",
                extension = "jpg",
                insertEvidence = insertEvidence,
            )
            uploadResult
                .onSuccess { uploadedPhotos++ }
                .onFailure { error ->
                    failedUploads++
                    firstErrorMessage = firstErrorMessage ?: error.localizedMessage
                }
        }

        var audioUploaded = false
        if (audioPath != null) {
            val contentType = audioMimeType?.takeIf { it.isNotBlank() } ?: "audio/mp4"
            val audioBytesResult = readEvidenceBytes { readFileBytes(audioPath) }
            val audioBytes = audioBytesResult.getOrNull()
            if (audioBytes == null) {
                firstErrorMessage = firstErrorMessage ?: audioBytesResult.exceptionOrNull()?.localizedMessage
            } else {
                val audioResult = uploadSingleEvidence(
                    ownerId = ownerId,
                    userId = userId,
                    folder = audioFolder,
                    bytes = audioBytes,
                    contentType = contentType,
                    extension = extensionForMimeType(contentType, default = "m4a"),
                    insertEvidence = insertEvidence,
                )
                audioResult
                    .onSuccess { audioUploaded = true }
                    .onFailure { error ->
                        firstErrorMessage = firstErrorMessage ?: error.localizedMessage
                    }
            }
        }

        return EvidenceUploadSummary(
            totalPhotos = photoUris.size,
            uploadedPhotos = uploadedPhotos,
            audioRequested = audioPath != null,
            audioUploaded = audioUploaded,
            failedUploads = failedUploads,
            firstErrorMessage = firstErrorMessage,
        )
    }

    private suspend fun readEvidenceBytes(read: suspend () -> ByteArray): Result<ByteArray> {
        return try {
            Result.success(read())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun uploadSingleEvidence(
        ownerId: String,
        userId: String,
        folder: String,
        bytes: ByteArray,
        contentType: String,
        extension: String,
        insertEvidence: suspend (path: String, contentType: String) -> Result<Unit>,
    ): Result<Unit> {
        val path = "$folder/$ownerId/${uniqueFileName(userId, extension)}"
        return storage.uploadObject(
            bucket = config.supabaseEvidenceBucket,
            path = path,
            bytes = bytes,
            contentType = contentType,
        ).fold(
            onSuccess = {
                val insertResult = insertEvidence(path, contentType)
                if (insertResult.isFailure) {
                    storage.deleteObject(config.supabaseEvidenceBucket, path)
                }
                insertResult
            },
            onFailure = { Result.failure(it) },
        )
    }

    private suspend fun readUriBytes(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        context.contentResolver.openInputStream(parsed)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("No se pudo leer la foto capturada.")
    }

    private suspend fun readFileBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) {
            throw IllegalArgumentException("No se pudo leer el audio grabado.")
        }
        file.readBytes()
    }

    private suspend fun logAuditAction(
        tableName: String,
        recordId: String,
        action: String,
        performedBy: String,
        changedData: JsonObject,
    ) {
        val auditLog = AuditLogInsert(
            tableName = tableName,
            recordId = recordId,
            action = action,
            changedData = changedData,
            performedBy = performedBy,
        )
        supabase.insert(AUDIT_LOG_TABLE, auditLog)
            .onFailure { error -> Timber.w(error, "Audit log failed for %s/%s", tableName, recordId) }
    }

    private fun buildDamageDescription(input: CreateDamagedClothingInput): String {
        val metadata = buildList {
            input.garmentType
                ?.takeIf { it.isNotBlank() }
                ?.let { add("Prenda: ${optionLabel(garmentTypeOptions, it)}") }
            input.damageType
                ?.takeIf { it.isNotBlank() }
                ?.let { add("Tipo de dano: ${optionLabel(damageTypeOptions, it)}") }
            input.audioTranscript
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add("Transcripcion audio: $it") }
        }
        return (metadata + input.description.trim()).joinToString(separator = "\n")
    }

    private fun buildIncidentDescription(input: CreateIncidentInput): String {
        val severityLabel = optionLabel(incidentSeverityOptions, input.severity)
        return buildList {
            add("Severidad: $severityLabel")
            add(input.description.trim())
        }.joinToString(separator = "\n")
    }

    private fun generateIncidentNumber(): String {
        val timestamp = Instant.now().toEpochMilli().toString(36).uppercase(Locale.US)
        val random = Random.nextInt(36 * 36 * 36 * 36)
            .toString(36)
            .padStart(4, '0')
            .uppercase(Locale.US)
        return "INC-$timestamp-$random"
    }

    private fun uniqueFileName(seed: String, extension: String): String {
        val safeExtension = extension.trim().trimStart('.').ifBlank { "bin" }
        val suffix = UUID.randomUUID().toString().take(8)
        return "${System.currentTimeMillis()}-${seed.take(8)}-$suffix.$safeExtension"
    }

    private fun extensionForMimeType(contentType: String, default: String): String {
        return when {
            contentType.contains("jpeg", ignoreCase = true) -> "jpg"
            contentType.contains("png", ignoreCase = true) -> "png"
            contentType.contains("mp4", ignoreCase = true) -> "m4a"
            contentType.contains("mpeg", ignoreCase = true) -> "mp3"
            contentType.contains("ogg", ignoreCase = true) -> "ogg"
            contentType.contains("webm", ignoreCase = true) -> "webm"
            else -> default
        }
    }
}
