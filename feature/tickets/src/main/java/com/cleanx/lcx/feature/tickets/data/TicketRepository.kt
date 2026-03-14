package com.cleanx.lcx.feature.tickets.data

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.ApiError
import com.cleanx.lcx.core.network.CreateTicketsRequest
import com.cleanx.lcx.core.network.SessionExpiredInterceptor
import com.cleanx.lcx.core.network.SmsNotificationClient
import com.cleanx.lcx.core.network.SmsNotificationResult
import com.cleanx.lcx.core.network.SupabaseError
import com.cleanx.lcx.core.network.SupabaseTableClient
import com.cleanx.lcx.core.network.TicketApi
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.network.UpdatePaymentRequest
import com.cleanx.lcx.core.network.UpdateStatusRequest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(
        val code: String? = null,
        val message: String,
        val httpStatus: Int,
        val details: String? = null,
    ) : ApiResult<Nothing>
}

@Singleton
class TicketRepository @Inject constructor(
    private val api: TicketApi,
    private val json: Json,
    private val smsNotificationClient: SmsNotificationClient,
    private val supabase: SupabaseTableClient,
) {

    suspend fun getTickets(limit: Long = 100): ApiResult<List<Ticket>> {
        Timber.tag("TICKET").d("Loading tickets limit=%d", limit)

        return supabase.selectWithRequest<Ticket>("tickets") {
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.fold(
            onSuccess = { tickets -> ApiResult.Success(tickets) },
            onFailure = { error ->
                error.toApiError(defaultMessage = "No se pudieron cargar los encargos.")
            },
        )
    }

    suspend fun getTicket(ticketId: String): ApiResult<Ticket?> {
        Timber.tag("TICKET").d("Loading ticket %s", ticketId)

        return supabase.selectSingle<Ticket>("tickets") {
            eq("id", ticketId)
        }.fold(
            onSuccess = { ticket -> ApiResult.Success(ticket) },
            onFailure = { error ->
                error.toApiError(defaultMessage = "No se pudo cargar el encargo.")
            },
        )
    }

    suspend fun createTickets(
        source: String,
        tickets: List<TicketDraft>,
        suppressSessionExpiredOnUnauthorized: Boolean = false,
    ): ApiResult<List<Ticket>> {
        Timber.tag("TICKET").d("Creating %d tickets (source=%s)", tickets.size, source)
        return try {
            val response = api.createTickets(
                request = CreateTicketsRequest(
                    source = source,
                    tickets = tickets,
                ),
                suppressSessionExpired = if (suppressSessionExpiredOnUnauthorized) {
                    SessionExpiredInterceptor.SUPPRESS_SESSION_EXPIRED_VALUE
                } else {
                    null
                },
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    ApiResult.Success(data)
                } else {
                    ApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun updateStatus(
        ticketId: String,
        status: TicketStatus,
    ): ApiResult<Ticket> {
        Timber.tag("TICKET").d("Updating status: ticketId=%s, status=%s", ticketId, status)
        if (status == TicketStatus.PAID) {
            return ApiResult.Error(
                code = "LEGACY_STATUS_READ_ONLY",
                message = "El estado pagado es legado y solo lectura.",
                httpStatus = 422,
            )
        }
        val statusValue = when (status) {
            TicketStatus.RECEIVED -> "received"
            TicketStatus.PROCESSING -> "processing"
            TicketStatus.READY -> "ready"
            TicketStatus.DELIVERED -> "delivered"
            TicketStatus.PAID -> error("Legacy paid status must not be written")
        }
        return try {
            val response = api.updateStatus(
                id = ticketId,
                request = UpdateStatusRequest(status = statusValue),
            )
            if (response.isSuccessful) {
                val ticket = response.body()?.data
                if (ticket != null) {
                    maybeSendReadySms(ticket)
                    ApiResult.Success(ticket)
                } else {
                    ApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun updatePayment(
        ticketId: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod? = null,
        paidAmount: Double? = null,
    ): ApiResult<Ticket> {
        Timber.tag("TICKET").d("Updating payment: ticketId=%s, status=%s, method=%s", ticketId, paymentStatus, paymentMethod)
        val statusValue = when (paymentStatus) {
            PaymentStatus.PENDING -> "pending"
            PaymentStatus.PREPAID -> "prepaid"
            PaymentStatus.PAID -> "paid"
        }
        val methodValue = paymentMethod?.let {
            when (it) {
                PaymentMethod.CASH -> "cash"
                PaymentMethod.CARD -> "card"
                PaymentMethod.TRANSFER -> "transfer"
            }
        }
        return try {
            val response = api.updatePayment(
                id = ticketId,
                request = UpdatePaymentRequest(
                    paymentStatus = statusValue,
                    paymentMethod = methodValue,
                    paidAmount = paidAmount,
                ),
            )
            if (response.isSuccessful) {
                val ticket = response.body()?.data
                if (ticket != null) {
                    ApiResult.Success(ticket)
                } else {
                    ApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun sendPickupReminder(ticket: Ticket): SmsNotificationResult {
        if (ticket.status != TicketStatus.READY) {
            return SmsNotificationResult.Skipped
        }

        val result = smsNotificationClient.sendTicketPickupReminder(
            ticketId = ticket.id,
            ticketNumber = ticket.ticketNumber,
            customerPhone = ticket.customerPhone,
        )

        when (result) {
            is SmsNotificationResult.Success -> {
                Timber.tag("SMS").d(
                    "Reminder SMS accepted notificationId=%s provider=%s idempotent=%s",
                    result.data.notificationId,
                    result.data.provider,
                    result.data.idempotent,
                )
            }

            is SmsNotificationResult.Error -> {
                Timber.tag("SMS").w(
                    "Reminder SMS failed ticketId=%s code=%s status=%d message=%s",
                    ticket.id,
                    result.code,
                    result.httpStatus,
                    result.message,
                )
            }

            SmsNotificationResult.Skipped -> {
                Timber.tag("SMS").d(
                    "Reminder SMS skipped ticketId=%s (status=%s or invalid phone)",
                    ticket.id,
                    ticket.status,
                )
            }
        }

        return result
    }

    private fun Response<*>.parseError(): ApiResult.Error {
        val raw = errorBody()?.string()
        val apiError = try {
            if (raw.isNullOrBlank()) null
            else json.decodeFromString(ApiError.serializer(), raw)
        } catch (_: SerializationException) {
            null
        }

        return ApiResult.Error(
            httpStatus = code(),
            message = apiError?.error ?: "No se pudo completar la operacion.",
            code = apiError?.code,
            details = apiError?.details,
        )
    }

    private fun Throwable.toApiError(defaultMessage: String): ApiResult.Error {
        return when (this) {
            is SupabaseError.Unauthorized -> ApiResult.Error(
                code = "NOT_AUTHENTICATED",
                message = message,
                httpStatus = 401,
            )

            is SupabaseError.NotFound -> ApiResult.Error(
                code = "NOT_FOUND",
                message = message,
                httpStatus = 404,
            )

            is SupabaseError.BadRequest -> ApiResult.Error(
                code = "BAD_REQUEST",
                message = message,
                httpStatus = statusCode ?: 400,
            )

            is SupabaseError.ServerError -> ApiResult.Error(
                code = "INTERNAL_SERVER_ERROR",
                message = message,
                httpStatus = statusCode ?: 500,
            )

            is SupabaseError.NetworkError -> ApiResult.Error(
                message = message,
                httpStatus = 0,
            )

            else -> ApiResult.Error(
                message = message ?: defaultMessage,
                httpStatus = 0,
            )
        }
    }

    private suspend fun maybeSendReadySms(ticket: Ticket) {
        if (ticket.status != TicketStatus.READY) {
            return
        }

        when (
            val result = smsNotificationClient.sendTicketReadyPickup(
                ticketId = ticket.id,
                ticketNumber = ticket.ticketNumber,
                customerPhone = ticket.customerPhone,
            )
        ) {
            is SmsNotificationResult.Success -> {
                Timber.tag("SMS").d(
                    "Ready SMS accepted notificationId=%s provider=%s idempotent=%s",
                    result.data.notificationId,
                    result.data.provider,
                    result.data.idempotent,
                )
            }

            is SmsNotificationResult.Error -> {
                Timber.tag("SMS").w(
                    "Ready SMS failed ticketId=%s code=%s status=%d message=%s",
                    ticket.id,
                    result.code,
                    result.httpStatus,
                    result.message,
                )
            }

            SmsNotificationResult.Skipped -> {
                Timber.tag("SMS").d(
                    "Ready SMS skipped ticketId=%s (missing/invalid phone)",
                    ticket.id,
                )
            }
        }
    }
}
