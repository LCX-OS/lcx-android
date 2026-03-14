package com.cleanx.lcx.core.network

import com.cleanx.lcx.core.model.Ticket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
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
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("service_type") val serviceType: String,
    val service: String,
    val weight: Double? = null,
    val status: String? = null,
    val notes: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    val subtotal: Double? = null,
    @SerialName("add_ons_total") val addOnsTotal: Double? = null,
    @SerialName("add_ons") val addOns: List<String>? = null,
    @SerialName("promised_pickup_date") val promisedPickupDate: String? = null,
    @SerialName("actual_pickup_date") val actualPickupDate: String? = null,
    @SerialName("special_instructions") val specialInstructions: String? = null,
    val photos: List<String>? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("paid_amount") val paidAmount: Double? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("prepaid_amount") val prepaidAmount: Double? = null,
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
    suspend fun createTickets(
        @Body request: CreateTicketsRequest,
        @Header(SessionExpiredInterceptor.SUPPRESS_SESSION_EXPIRED_HEADER)
        suppressSessionExpired: String? = null,
    ): Response<TicketsResponse>

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
