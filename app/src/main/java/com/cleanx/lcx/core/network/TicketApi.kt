package com.cleanx.lcx.core.network

import com.cleanx.lcx.core.model.Ticket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CreateTicketsRequest(
    val source: String,
    val tickets: List<TicketDraft>,
)

@Serializable
data class TicketDraft(
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("service_type") val serviceType: String,
    val service: String,
    val weight: Double? = null,
    val status: String? = null,
    val notes: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("paid_amount") val paidAmount: Double? = null,
)

@Serializable
data class TicketsResponse(val data: List<Ticket>)

@Serializable
data class TicketResponse(val data: Ticket)

@Serializable
data class UpdateStatusRequest(val status: String)

@Serializable
data class UpdatePaymentRequest(
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("paid_amount") val paidAmount: Double? = null,
)

@Serializable
data class ApiError(
    val error: String,
    val code: String? = null,
    val details: String? = null,
)

interface TicketApi {
    @POST("api/tickets")
    suspend fun createTickets(@Body request: CreateTicketsRequest): Response<TicketsResponse>

    @PATCH("api/tickets/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body request: UpdateStatusRequest,
    ): Response<TicketResponse>

    @PATCH("api/tickets/{id}/payment")
    suspend fun updatePayment(
        @Path("id") id: String,
        @Body request: UpdatePaymentRequest,
    ): Response<TicketResponse>
}
