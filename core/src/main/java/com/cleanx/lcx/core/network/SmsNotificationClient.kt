package com.cleanx.lcx.core.network

import com.cleanx.lcx.core.config.BuildConfigProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class SmsNotificationUseCase {
    TICKET_READY_PICKUP,
    TICKET_PICKUP_REMINDER,
    ;

    fun wireValue(): String = when (this) {
        TICKET_READY_PICKUP -> "ticket_ready_pickup"
        TICKET_PICKUP_REMINDER -> "ticket_pickup_reminder"
    }
}

sealed interface SmsNotificationResult {
    data class Success(val data: SmsSendResponseData) : SmsNotificationResult

    data class Error(
        val code: String? = null,
        val message: String,
        val httpStatus: Int,
        val details: String? = null,
    ) : SmsNotificationResult

    data object Skipped : SmsNotificationResult
}

@Singleton
class SmsNotificationClient @Inject constructor(
    private val api: SmsNotificationApi,
    private val json: Json,
    private val config: BuildConfigProvider,
) {

    suspend fun sendTicketReadyPickup(
        ticketId: String,
        ticketNumber: String,
        customerPhone: String?,
        branchId: String? = null,
    ): SmsNotificationResult {
        val normalizedPhone = normalizePhoneToE164(customerPhone ?: return SmsNotificationResult.Skipped)
            ?: return SmsNotificationResult.Skipped

        val request = SmsSendRequest(
            idempotencyKey = buildIdempotencyKey(
                useCase = SmsNotificationUseCase.TICKET_READY_PICKUP,
                ticketId = ticketId,
            ),
            to = normalizedPhone,
            message = buildTicketReadyPickupMessage(ticketNumber),
            requestedBy = "android",
            metadata = mapOf(
                "use_case" to SmsNotificationUseCase.TICKET_READY_PICKUP.wireValue(),
                "ticket_id" to ticketId,
                "ticket_number" to ticketNumber,
                "branch_id" to branchId,
                "channel" to "android",
            ),
        )

        return try {
            val response = api.sendSms(
                url = smsSendUrl(),
                request = request,
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    SmsNotificationResult.Success(data)
                } else {
                    SmsNotificationResult.Error(
                        message = "Respuesta vacia del servidor de SMS.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            Timber.tag("SMS").w(e, "sendTicketReadyPickup request failed")
            SmsNotificationResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun sendTicketPickupReminder(
        ticketId: String,
        ticketNumber: String,
        customerPhone: String?,
        reminderDate: String? = null,
        branchId: String? = null,
    ): SmsNotificationResult {
        val normalizedPhone = normalizePhoneToE164(customerPhone ?: return SmsNotificationResult.Skipped)
            ?: return SmsNotificationResult.Skipped
        val datePart = reminderDate?.trim()?.takeIf { it.isNotEmpty() }
            ?: currentUtcDate()

        val request = SmsSendRequest(
            idempotencyKey = buildIdempotencyKey(
                useCase = SmsNotificationUseCase.TICKET_PICKUP_REMINDER,
                ticketId = ticketId,
                reminderDate = datePart,
            ),
            to = normalizedPhone,
            message = buildTicketPickupReminderMessage(ticketNumber),
            requestedBy = "android",
            metadata = mapOf(
                "use_case" to SmsNotificationUseCase.TICKET_PICKUP_REMINDER.wireValue(),
                "ticket_id" to ticketId,
                "ticket_number" to ticketNumber,
                "reminder_date" to datePart,
                "branch_id" to branchId,
                "channel" to "android",
            ),
        )

        return try {
            val response = api.sendSms(
                url = smsSendUrl(),
                request = request,
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    SmsNotificationResult.Success(data)
                } else {
                    SmsNotificationResult.Error(
                        message = "Respuesta vacia del servidor de SMS.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            Timber.tag("SMS").w(e, "sendTicketPickupReminder request failed")
            SmsNotificationResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    private fun smsSendUrl(): String {
        val base = config.notificationsBaseUrl.trim().trimEnd('/')
        return "$base/v1/notifications/sms/send"
    }

    fun buildIdempotencyKey(
        useCase: SmsNotificationUseCase,
        ticketId: String,
        reminderDate: String? = null,
    ): String {
        val cleanedTicketId = ticketId.trim()
        require(cleanedTicketId.isNotEmpty()) { "ticketId is required" }

        return when (useCase) {
            SmsNotificationUseCase.TICKET_READY_PICKUP ->
                "sms:${useCase.wireValue()}:$cleanedTicketId"

            SmsNotificationUseCase.TICKET_PICKUP_REMINDER -> {
                val datePart = reminderDate?.trim()?.takeIf { it.isNotEmpty() }
                    ?: currentUtcDate()
                "sms:${useCase.wireValue()}:$cleanedTicketId:$datePart"
            }
        }
    }

    private fun currentUtcDate(): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()

    private fun normalizePhoneToE164(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val compact = trimmed.replace(" ", "")
        if (compact.matches(Regex("^\\+\\d{8,15}$"))) {
            return compact
        }

        val digits = compact.filter { it.isDigit() }
        if (digits.length == 10) {
            return "+52$digits"
        }

        if (digits.length == 12 && digits.startsWith("52")) {
            return "+$digits"
        }

        if (digits.length in 8..15) {
            return "+$digits"
        }

        return null
    }

    private fun buildTicketReadyPickupMessage(ticketNumber: String): String {
        val cleaned = ticketNumber.trim().ifEmpty { "tu ticket" }
        return "Tu ticket $cleaned ya esta listo para recoger. Gracias por usar Clean X."
    }

    private fun buildTicketPickupReminderMessage(ticketNumber: String): String {
        val cleaned = ticketNumber.trim().ifEmpty { "tu ticket" }
        return "Recordatorio: tu ticket $cleaned sigue listo para recoger en Clean X."
    }

    private fun Response<*>.parseError(): SmsNotificationResult.Error {
        val raw = errorBody()?.string()
        val apiError = try {
            if (raw.isNullOrBlank()) null
            else json.decodeFromString(ApiError.serializer(), raw)
        } catch (_: SerializationException) {
            null
        }

        return SmsNotificationResult.Error(
            httpStatus = code(),
            message = apiError?.error ?: "No se pudo completar la operacion SMS.",
            code = apiError?.code,
            details = apiError?.details,
        )
    }
}
