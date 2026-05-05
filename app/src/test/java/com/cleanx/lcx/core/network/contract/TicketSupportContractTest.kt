package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.feature.tickets.data.TicketSupportApi
import com.cleanx.lcx.feature.tickets.data.TicketSupportCreateCustomerRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TicketSupportContractTest : ContractTestBase() {

    private lateinit var supportApi: TicketSupportApi

    @Before
    fun setUpSupportApi() {
        supportApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(okhttp3.OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TicketSupportApi::class.java)
    }

    @Test
    fun `catalog support endpoint decodes service add-on and inventory payloads`() = runTest {
        enqueue(
            """
            {
              "data": {
                "services": [
                  {
                    "id": "svc-1",
                    "name": "Lavado KG",
                    "description": null,
                    "category": "Lavado",
                    "price": 30.0,
                    "unit": "kg",
                    "active": true
                  }
                ],
                "add_ons": [
                  {
                    "id": "addon-1",
                    "name": "Suavizante",
                    "description": null,
                    "price": 10.0,
                    "active": true
                  }
                ],
                "inventory_items": [
                  {
                    "id": "item-1",
                    "item_name": "Bolsa",
                    "category": "Insumos",
                    "quantity": 4,
                    "unit": "pieza",
                    "min_quantity": 0,
                    "price": 5.0,
                    "is_for_sale": true,
                    "sku": null,
                    "barcode": null,
                    "product_code": null,
                    "branch": "La Esperanza"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val response = supportApi.getCatalogs()
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/api/ticket-support/catalogs", request.path)
        assertTrue(response.isSuccessful)
        assertEquals("svc-1", response.body()?.data?.services?.first()?.id)
        assertEquals("addon-1", response.body()?.data?.addOns?.first()?.id)
        assertEquals("item-1", response.body()?.data?.inventoryItems?.first()?.id)
    }

    @Test
    fun `customer create support endpoint sends duplicate override flag`() = runTest {
        enqueue(
            """
            {
              "data": {
                "id": "customer-1",
                "full_name": "Ana Lopez",
                "phone": "555-123-4567",
                "phone_normalized": "5551234567",
                "email": null,
                "notes": null,
                "created_by": "user-1",
                "created_at": "2026-03-02T10:00:00Z"
              }
            }
            """.trimIndent(),
        )

        val response = supportApi.createCustomer(
            TicketSupportCreateCustomerRequest(
                fullName = "Ana Lopez",
                phone = "555-123-4567",
                email = null,
                notes = null,
                allowDuplicatePhone = true,
            ),
        )
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/api/ticket-support/customers", request.path)
        assertTrue(request.body.readUtf8().contains("\"allow_duplicate_phone\":true"))
        assertTrue(response.isSuccessful)
        assertEquals("customer-1", response.body()?.data?.id)
    }
}
