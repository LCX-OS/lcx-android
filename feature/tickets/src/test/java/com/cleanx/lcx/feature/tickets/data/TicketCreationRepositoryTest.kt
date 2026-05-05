package com.cleanx.lcx.feature.tickets.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class TicketCreationRepositoryTest {

    private lateinit var api: TicketSupportApi
    private lateinit var repository: TicketCreationRepository
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        api = mockk()
        repository = TicketCreationRepository(api, json)
    }

    @Test
    fun `loadCatalogs returns support API catalogs with defensive filtering`() = runTest {
        coEvery { api.getCatalogs() } returns Response.success(
            TicketSupportCatalogsResponse(
                data = TicketSupportCatalogsPayload(
                    services = listOf(
                        sampleService(id = "service-active", active = true),
                        sampleService(id = "service-inactive", active = false),
                    ),
                    addOns = listOf(
                        sampleAddOn(id = "addon-active", active = true),
                        sampleAddOn(id = "addon-inactive", active = false),
                    ),
                    inventoryItems = listOf(
                        sampleInventoryItem(id = "item-valid", quantity = 3, isForSale = true, price = 20.0),
                        sampleInventoryItem(id = "item-hidden", quantity = 3, isForSale = false, price = 20.0),
                    ),
                ),
            ),
        )

        val result = repository.loadCatalogs()

        assertTrue(result is ApiResult.Success)
        val catalogs = (result as ApiResult.Success).data
        assertEquals(listOf("service-active"), catalogs.services.map { it.id })
        assertEquals(listOf("addon-active"), catalogs.addOns.map { it.id })
        assertEquals(listOf("item-valid"), catalogs.inventoryItems.map { it.id })
    }

    @Test
    fun `searchCustomers returns empty result without network call for blank query`() = runTest {
        val result = repository.searchCustomers(" %, _ ")

        assertTrue(result is ApiResult.Success)
        assertEquals(emptyList<CustomerRecord>(), (result as ApiResult.Success).data)
        coVerify(exactly = 0) { api.searchCustomers(any(), any()) }
    }

    @Test
    fun `createCustomer sends trimmed payload and duplicate override flag`() = runTest {
        val requestSlot = slot<TicketSupportCreateCustomerRequest>()
        coEvery { api.createCustomer(capture(requestSlot)) } returns Response.success(
            TicketSupportCustomerResponse(sampleCustomer(id = "created")),
        )

        val result = repository.createCustomer(
            input = CustomerCreateInput(
                fullName = "  Ana Lopez  ",
                phone = "  555-123-4567  ",
                email = " ana@example.com ",
                notes = "  VIP  ",
            ),
            allowDuplicatePhone = true,
        )

        assertFalse(result.requiresDuplicateConfirmation)
        assertEquals("created", result.customer?.id)
        assertEquals("Ana Lopez", requestSlot.captured.fullName)
        assertEquals("555-123-4567", requestSlot.captured.phone)
        assertEquals("ana@example.com", requestSlot.captured.email)
        assertEquals("VIP", requestSlot.captured.notes)
        assertTrue(requestSlot.captured.allowDuplicatePhone)
    }

    @Test
    fun `createCustomer maps duplicate phone response to confirmation state`() = runTest {
        val duplicate = sampleCustomer(id = "duplicate")
        val errorJson = """
            {
              "error": "Ya existe un cliente con este telefono.",
              "code": "CUSTOMER_DUPLICATE_PHONE",
              "duplicate_phone_matches": [
                {
                  "id": "${duplicate.id}",
                  "full_name": "${duplicate.fullName}",
                  "phone": "${duplicate.phone}",
                  "phone_normalized": "${duplicate.phoneNormalized}",
                  "email": null,
                  "notes": null,
                  "created_by": null,
                  "created_at": "2026-03-02T10:00:00Z"
                }
              ]
            }
        """.trimIndent()

        coEvery { api.createCustomer(any()) } returns Response.error(
            409,
            errorJson.toResponseBody("application/json".toMediaType()),
        )

        val result = repository.createCustomer(
            CustomerCreateInput(
                fullName = "Ana Lopez",
                phone = "555-123-4567",
            ),
        )

        assertTrue(result.requiresDuplicateConfirmation)
        assertEquals(null, result.errorMessage)
        assertEquals(listOf("duplicate"), result.duplicatePhoneMatches.map { it.id })
    }

    private fun sampleCustomer(id: String): CustomerRecord {
        return CustomerRecord(
            id = id,
            fullName = "Ana Lopez",
            phone = "555-123-4567",
            phoneNormalized = "5551234567",
            email = null,
            notes = null,
            createdBy = null,
            createdAt = "2026-03-02T10:00:00Z",
        )
    }

    private fun sampleService(id: String, active: Boolean): ServiceCatalogRecord {
        return ServiceCatalogRecord(
            id = id,
            name = "Lavado",
            description = null,
            category = "Lavado",
            price = 30.0,
            unit = "kg",
            active = active,
        )
    }

    private fun sampleAddOn(id: String, active: Boolean): AddOnCatalogRecord {
        return AddOnCatalogRecord(
            id = id,
            name = "Suavizante",
            description = null,
            price = 10.0,
            active = active,
        )
    }

    private fun sampleInventoryItem(
        id: String,
        quantity: Int,
        isForSale: Boolean,
        price: Double,
    ): InventoryCatalogRecord {
        return InventoryCatalogRecord(
            id = id,
            itemName = "Bolsa",
            category = "Insumos",
            quantity = quantity,
            unit = "pieza",
            minQuantity = 0,
            price = price,
            isForSale = isForSale,
            sku = null,
            barcode = null,
            productCode = null,
            branch = "La Esperanza",
        )
    }
}
