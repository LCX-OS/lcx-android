package com.cleanx.lcx.feature.tickets.data

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.ApiError
import com.cleanx.lcx.core.network.CreateTicketsRequest
import com.cleanx.lcx.core.network.TicketApi
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.network.UpdatePaymentRequest
import com.cleanx.lcx.core.network.UpdateStatusRequest
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
) {

    suspend fun createTickets(
        source: String,
        tickets: List<TicketDraft>,
    ): ApiResult<List<Ticket>> {
        Timber.tag("TICKET").d("Creating %d tickets (source=%s)", tickets.size, source)
        return try {
            val response = api.createTickets(
                request = CreateTicketsRequest(
                    source = source,
                    tickets = tickets,
                ),
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
        val statusValue = when (status) {
            TicketStatus.RECEIVED -> "received"
            TicketStatus.PROCESSING -> "processing"
            TicketStatus.READY -> "ready"
            TicketStatus.DELIVERED -> "delivered"
        }
        return try {
            val response = api.updateStatus(
                id = ticketId,
                request = UpdateStatusRequest(status = statusValue),
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
}
