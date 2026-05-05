package com.cleanx.lcx.feature.tickets.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_CUSTOMER_SEARCH_LIMIT = 8
private const val MAX_CUSTOMER_SEARCH_LIMIT = 25
private const val DUPLICATE_PHONE_CODE = "CUSTOMER_DUPLICATE_PHONE"

@Singleton
class TicketCreationRepository @Inject constructor(
    private val api: TicketSupportApi,
    private val json: Json,
) {

    suspend fun loadCatalogs(): ApiResult<TicketCreationCatalogs> {
        return try {
            val response = api.getCatalogs()

            if (!response.isSuccessful) {
                return response.parseApiError("No se pudieron cargar los datos para crear encargos.")
            }

            val data = response.body()?.data
                ?: return ApiResult.Error(
                    message = "Respuesta vacia del servidor.",
                    httpStatus = response.code(),
                )

            ApiResult.Success(
                TicketCreationCatalogs(
                    services = data.services.filter { it.active },
                    addOns = data.addOns.filter { it.active },
                    inventoryItems = filterSellableInventoryItems(data.inventoryItems),
                ),
            )
        } catch (error: Exception) {
            ApiResult.Error(
                message = error.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun searchCustomers(
        query: String,
        limit: Int = DEFAULT_CUSTOMER_SEARCH_LIMIT,
    ): ApiResult<List<CustomerRecord>> {
        val sanitizedQuery = sanitizeCustomerSearchToken(query)
        val normalizedQuery = normalizePhone(query)

        if (sanitizedQuery.isBlank() && normalizedQuery.isBlank()) {
            return ApiResult.Success(emptyList())
        }

        val safeLimit = limit.coerceIn(1, MAX_CUSTOMER_SEARCH_LIMIT)

        return try {
            val response = api.searchCustomers(query = query, limit = safeLimit)

            if (!response.isSuccessful) {
                return response.parseApiError("No se pudieron buscar clientes.")
            }

            ApiResult.Success(response.body()?.data ?: emptyList())
        } catch (error: Exception) {
            ApiResult.Error(
                message = error.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
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

        return try {
            val response = api.createCustomer(
                TicketSupportCreateCustomerRequest(
                    fullName = fullName,
                    phone = phone,
                    email = email,
                    notes = notes,
                    allowDuplicatePhone = allowDuplicatePhone,
                ),
            )

            if (response.isSuccessful) {
                val customer = response.body()?.data
                    ?: return CustomerCreateResult(
                        errorMessage = "Respuesta vacia del servidor.",
                        validationErrors = validationErrors,
                    )

                return CustomerCreateResult(
                    customer = customer,
                    validationErrors = validationErrors,
                )
            }

            val error = response.parseTicketSupportError("No se pudo crear el cliente.")
            val responseValidationErrors = error.validationErrors?.toDomain() ?: validationErrors

            if (error.code == DUPLICATE_PHONE_CODE) {
                return CustomerCreateResult(
                    duplicatePhoneMatches = error.duplicatePhoneMatches,
                    requiresDuplicateConfirmation = true,
                    validationErrors = responseValidationErrors,
                )
            }

            CustomerCreateResult(
                errorMessage = error.error.ifBlank { "No se pudo crear el cliente." },
                duplicatePhoneMatches = error.duplicatePhoneMatches,
                validationErrors = responseValidationErrors,
            )
        } catch (error: Exception) {
            CustomerCreateResult(
                errorMessage = error.message ?: "Error de conexion.",
                validationErrors = validationErrors,
            )
        }
    }

    private fun Response<*>.parseApiError(defaultMessage: String): ApiResult.Error {
        val apiError = parseTicketSupportError(defaultMessage)
        return ApiResult.Error(
            code = apiError.code,
            message = apiError.error.ifBlank { defaultMessage },
            httpStatus = code(),
            details = apiError.details,
        )
    }

    private fun Response<*>.parseTicketSupportError(defaultMessage: String): TicketSupportErrorResponse {
        val raw = errorBody()?.string()

        return try {
            if (raw.isNullOrBlank()) {
                null
            } else {
                json.decodeFromString(TicketSupportErrorResponse.serializer(), raw)
            }
        } catch (_: SerializationException) {
            null
        } ?: TicketSupportErrorResponse(
            error = defaultMessage,
            code = null,
            details = raw,
        )
    }
}
