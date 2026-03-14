package com.cleanx.lcx.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ServiceType {
    @SerialName("in-store") IN_STORE,
    @SerialName("wash-fold") WASH_FOLD,
}

@Serializable
enum class TicketStatus {
    @SerialName("received") RECEIVED,
    @SerialName("processing") PROCESSING,
    @SerialName("ready") READY,
    @SerialName("delivered") DELIVERED,
    @SerialName("paid") PAID,
}

@Serializable
enum class PaymentStatus {
    @SerialName("pending") PENDING,
    @SerialName("prepaid") PREPAID,
    @SerialName("paid") PAID,
}

@Serializable
enum class PaymentMethod {
    @SerialName("cash") CASH,
    @SerialName("card") CARD,
    @SerialName("transfer") TRANSFER,
}

@Serializable
enum class UserRole {
    @SerialName("employee") EMPLOYEE,
    @SerialName("manager") MANAGER,
    @SerialName("superadmin") SUPERADMIN,
}
