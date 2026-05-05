package com.cleanx.lcx.feature.tickets.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class TicketSupportCatalogsPayload(
    val services: List<ServiceCatalogRecord>,
    @SerialName("add_ons") val addOns: List<AddOnCatalogRecord>,
    @SerialName("inventory_items") val inventoryItems: List<InventoryCatalogRecord>,
)

@Serializable
data class TicketSupportCatalogsResponse(
    val data: TicketSupportCatalogsPayload,
)

@Serializable
data class TicketSupportCustomersResponse(
    val data: List<CustomerRecord>,
)

@Serializable
data class TicketSupportCustomerResponse(
    val data: CustomerRecord,
)

@Serializable
data class TicketSupportCreateCustomerRequest(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val email: String? = null,
    val notes: String? = null,
    @SerialName("allow_duplicate_phone") val allowDuplicatePhone: Boolean = false,
)

@Serializable
data class TicketSupportCustomerValidationErrorsPayload(
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
) {
    fun toDomain(): CustomerValidationErrors {
        return CustomerValidationErrors(
            fullName = fullName,
            phone = phone,
            email = email,
        )
    }
}

@Serializable
data class TicketSupportErrorResponse(
    val error: String,
    val code: String? = null,
    val details: String? = null,
    @SerialName("duplicate_phone_matches")
    val duplicatePhoneMatches: List<CustomerRecord> = emptyList(),
    @SerialName("validation_errors")
    val validationErrors: TicketSupportCustomerValidationErrorsPayload? = null,
)

interface TicketSupportApi {
    @GET("api/ticket-support/catalogs")
    suspend fun getCatalogs(): Response<TicketSupportCatalogsResponse>

    @GET("api/ticket-support/customers")
    suspend fun searchCustomers(
        @Query("query") query: String,
        @Query("limit") limit: Int,
    ): Response<TicketSupportCustomersResponse>

    @POST("api/ticket-support/customers")
    suspend fun createCustomer(
        @Body request: TicketSupportCreateCustomerRequest,
    ): Response<TicketSupportCustomerResponse>
}
