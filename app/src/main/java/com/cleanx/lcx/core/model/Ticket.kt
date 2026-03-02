package com.cleanx.lcx.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val id: String,
    @SerialName("ticket_number") val ticketNumber: String,
    @SerialName("ticket_date") val ticketDate: String,
    @SerialName("daily_folio") val dailyFolio: Int,
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("service_type") val serviceType: ServiceType,
    val service: String? = null,
    val weight: Double? = null,
    val status: TicketStatus,
    val notes: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    val subtotal: Double? = null,
    @SerialName("add_ons_total") val addOnsTotal: Double? = null,
    @SerialName("add_ons") val addOns: List<String>? = null,
    @SerialName("promised_pickup_date") val promisedPickupDate: String? = null,
    @SerialName("actual_pickup_date") val actualPickupDate: String? = null,
    @SerialName("special_instructions") val specialInstructions: String? = null,
    val photos: List<String>? = null,
    @SerialName("payment_method") val paymentMethod: PaymentMethod? = null,
    @SerialName("payment_status") val paymentStatus: PaymentStatus? = null,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("prepaid_amount") val prepaidAmount: Double? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
