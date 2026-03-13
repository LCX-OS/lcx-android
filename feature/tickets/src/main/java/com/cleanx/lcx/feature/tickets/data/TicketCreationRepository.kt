package com.cleanx.lcx.feature.tickets.data

import com.cleanx.lcx.core.network.SupabaseError
import com.cleanx.lcx.core.network.SupabaseTableClient
import com.cleanx.lcx.core.session.SessionProfileRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_CUSTOMER_SEARCH_LIMIT = 8
private const val MAX_CUSTOMER_SEARCH_LIMIT = 25
private const val DUPLICATE_PHONE_LIMIT = 5L

@Singleton
class TicketCreationRepository @Inject constructor(
    private val supabase: SupabaseTableClient,
    private val sessionProfileRepository: SessionProfileRepository,
) {

    suspend fun loadCatalogs(): ApiResult<TicketCreationCatalogs> = coroutineScope {
        val servicesDeferred = async {
            supabase.selectWithRequest<ServiceCatalogRecord>("services_catalog") {
                filter { eq("active", true) }
                order("name", Order.ASCENDING)
            }
        }
        val addOnsDeferred = async {
            supabase.selectWithRequest<AddOnCatalogRecord>("add_ons_catalog") {
                filter { eq("active", true) }
                order("name", Order.ASCENDING)
            }
        }
        val inventoryDeferred = async {
            supabase.selectWithRequest<InventoryCatalogRecord>("inventory") {
                filter {
                    eq("is_for_sale", true)
                    gt("quantity", 0)
                    gt("price", 0)
                }
                order("item_name", Order.ASCENDING)
            }
        }

        val services = servicesDeferred.await().getOrElse { error ->
            return@coroutineScope error.toApiError("No se pudo cargar el catalogo de servicios.")
        }
        val addOns = addOnsDeferred.await().getOrElse { error ->
            return@coroutineScope error.toApiError("No se pudo cargar el catalogo de extras.")
        }
        val inventoryItems = inventoryDeferred.await().getOrElse { error ->
            return@coroutineScope error.toApiError("No se pudo cargar el inventario vendible.")
        }

        ApiResult.Success(
            TicketCreationCatalogs(
                services = services.filter { it.active },
                addOns = addOns.filter { it.active },
                inventoryItems = filterSellableInventoryItems(inventoryItems),
            ),
        )
    }

    suspend fun searchCustomers(
        query: String,
        limit: Int = DEFAULT_CUSTOMER_SEARCH_LIMIT,
    ): ApiResult<List<CustomerRecord>> = coroutineScope {
        val sanitizedQuery = sanitizeCustomerSearchToken(query)
        val normalizedQuery = normalizePhone(query)

        if (sanitizedQuery.isBlank() && normalizedQuery.isBlank()) {
            return@coroutineScope ApiResult.Success(emptyList())
        }

        val safeLimit = limit.coerceIn(1, MAX_CUSTOMER_SEARCH_LIMIT).toLong()
        val requests = buildList {
            if (sanitizedQuery.isNotBlank()) {
                add(async { searchCustomersByColumn("full_name", sanitizedQuery, safeLimit) })
                add(async { searchCustomersByColumn("phone", sanitizedQuery, safeLimit) })
                add(async { searchCustomersByColumn("email", sanitizedQuery, safeLimit) })
            }
            if (normalizedQuery.isNotBlank()) {
                add(async { searchCustomersByColumn("phone_normalized", normalizedQuery, safeLimit) })
            }
        }

        val merged = linkedMapOf<String, CustomerRecord>()
        requests.awaitAll().forEach { result ->
            val rows = result.getOrElse { error ->
                return@coroutineScope error.toApiError("No se pudieron buscar clientes.")
            }
            rows.forEach { customer ->
                merged.putIfAbsent(customer.id, customer)
            }
        }

        ApiResult.Success(merged.values.take(safeLimit.toInt()))
    }

    suspend fun createCustomer(
        input: CustomerCreateInput,
        allowDuplicatePhone: Boolean = false,
    ): CustomerCreateResult {
        val fullName = input.fullName.trim()
        val phone = input.phone.trim()
        val normalizedPhone = normalizePhone(phone)
        val email = input.email?.trim()?.ifBlank { null }
        val notes = input.notes?.trim()?.ifBlank { null }

        val validationErrors = buildCustomerValidationErrors(
            fullName = fullName,
            normalizedPhone = normalizedPhone,
            email = email,
        )
        if (validationErrors != CustomerValidationErrors()) {
            return CustomerCreateResult(
                errorMessage = firstCustomerValidationErrorMessage(validationErrors),
                validationErrors = validationErrors,
            )
        }

        val duplicatePhoneMatches = supabase.selectWithRequest<CustomerRecord>("customers") {
            filter { eq("phone_normalized", normalizedPhone) }
            order("created_at", Order.DESCENDING)
            limit(DUPLICATE_PHONE_LIMIT)
        }.getOrElse { error ->
            return CustomerCreateResult(
                errorMessage = error.toApiError("No se pudieron revisar telefonos duplicados.").message,
                validationErrors = validationErrors,
            )
        }

        if (duplicatePhoneMatches.isNotEmpty() && !allowDuplicatePhone) {
            return CustomerCreateResult(
                duplicatePhoneMatches = duplicatePhoneMatches,
                requiresDuplicateConfirmation = true,
                validationErrors = validationErrors,
            )
        }

        val createdBy = runCatching {
            sessionProfileRepository.getCurrentProfile().userId
        }.getOrNull()

        val payload = CustomerInsertPayload(
            fullName = fullName,
            phone = phone,
            phoneNormalized = normalizedPhone,
            email = email,
            notes = notes,
            createdBy = createdBy,
        )

        val customer = supabase.insertReturning<CustomerInsertPayload, CustomerRecord>(
            table = "customers",
            value = payload,
        ).getOrElse { error ->
            return CustomerCreateResult(
                errorMessage = error.toApiError("No se pudo crear el cliente.").message,
                duplicatePhoneMatches = duplicatePhoneMatches,
                validationErrors = validationErrors,
            )
        }

        return CustomerCreateResult(
            customer = customer,
            duplicatePhoneMatches = duplicatePhoneMatches,
            validationErrors = validationErrors,
        )
    }

    private suspend fun searchCustomersByColumn(
        column: String,
        token: String,
        limit: Long,
    ): Result<List<CustomerRecord>> {
        return supabase.selectWithRequest("customers") {
            filter { ilike(column, "%$token%") }
            order("full_name", Order.ASCENDING)
            limit(limit)
        }
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
                code = "SERVER_ERROR",
                message = message,
                httpStatus = statusCode ?: 500,
            )

            is SupabaseError.NetworkError -> ApiResult.Error(
                code = "NETWORK_ERROR",
                message = message,
                httpStatus = 0,
            )

            is SupabaseError.Unknown -> ApiResult.Error(
                code = "UNKNOWN",
                message = message,
                httpStatus = 0,
            )

            else -> ApiResult.Error(
                message = message ?: defaultMessage,
                httpStatus = 0,
            )
        }.let { error ->
            if (error.message.isBlank()) {
                error.copy(message = defaultMessage)
            } else {
                error
            }
        }
    }
}
