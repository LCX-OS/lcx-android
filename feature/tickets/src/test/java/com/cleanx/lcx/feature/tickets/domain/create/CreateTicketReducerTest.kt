package com.cleanx.lcx.feature.tickets.domain.create

import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTicketReducerTest {

    private val reducer = CreateTicketReducer()

    private val baseService = ServiceCatalogRecord(
        id = "service-base",
        name = "Lavado por KG",
        description = "Servicio base",
        category = "Lavado",
        price = 50.0,
        unit = "kg",
        active = true,
    )

    private val beddingService = ServiceCatalogRecord(
        id = "service-bedding",
        name = "Edredon King",
        description = "Edredon grande",
        category = "Edredones",
        price = 90.0,
        unit = "pieza",
        active = true,
    )

    private val extraItem = AddOnCatalogRecord(
        id = "addon-softener",
        name = "Suavizante",
        description = null,
        price = 18.0,
        active = true,
    )

    @Test
    fun `catalog load derives pricing and filters stale selections`() {
        val current = CreateTicketUiState(
            isLoadingCatalogs = true,
            weight = "2",
            selectedBaseServiceId = "missing-service",
            beddingQuantities = mapOf("service-bedding" to 1, "stale-bedding" to 2),
            selectedExtraIds = listOf("addon-softener", "stale-extra"),
        )

        val result = reducer(
            current,
            CreateTicketMutation.CatalogsLoaded(
                snapshot = EncargoCatalogSnapshot(
                    services = listOf(baseService, beddingService),
                    beddingItems = listOf(beddingService),
                    extraItems = listOf(extraItem),
                    inventoryItems = emptyList(),
                    defaultBaseServiceId = baseService.id,
                ),
                loadedAtLabel = "10:30",
            ),
        )

        assertFalse(result.isLoadingCatalogs)
        assertEquals("10:30", result.catalogLoadedAtLabel)
        assertEquals(baseService.id, result.selectedBaseServiceId)
        assertEquals(mapOf("service-bedding" to 1), result.beddingQuantities)
        assertEquals(listOf("addon-softener"), result.selectedExtraIds)
        assertEquals(150.0, result.pricing.subtotal, 0.001)
        assertEquals(108.0, result.pricing.addOnsTotal, 0.001)
        assertEquals(258.0, result.pricing.total, 0.001)
    }

    @Test
    fun `open create customer form prefills phone from numeric search query`() {
        val current = CreateTicketUiState(
            customer = CustomerPickerUiState(
                searchQuery = "5512345678",
            ),
        )

        val result = reducer(current, CreateTicketMutation.CreateCustomerFormOpened)

        assertTrue(result.customer.showCreateForm)
        assertEquals("5512345678", result.customer.selectedCustomer.phone)
        assertEquals("", result.customer.selectedCustomer.fullName)
    }

    @Test
    fun `short customer search query clears results and searching state`() {
        val current = CreateTicketUiState(
            customer = CustomerPickerUiState(
                searchQuery = "juan",
                searchResults = listOf(
                    com.cleanx.lcx.feature.tickets.data.CustomerRecord(
                        id = "customer-1",
                        fullName = "Juan Perez",
                        phone = "5512345678",
                        phoneNormalized = "5512345678",
                    ),
                ),
                isSearching = true,
            ),
        )

        val result = reducer(
            current,
            CreateTicketMutation.CustomerSearchQueryChanged("j"),
        )

        assertFalse(result.customer.isSearching)
        assertTrue(result.customer.searchResults.isEmpty())
        assertEquals("j", result.customer.searchQuery)
    }

    @Test
    fun `inventory lookup submission adds exact barcode match and clears query`() {
        val inventory = inventoryItem(
            id = "bag-small",
            itemName = "Bolsa chica",
            quantity = 3,
            sku = "BOLSA-CH",
            barcode = "7501234567890",
        )
        val current = CreateTicketUiState(
            inventoryItems = listOf(inventory),
            inventorySearchQuery = "7501234567890",
        )

        val result = reducer(
            current,
            CreateTicketMutation.InventoryLookupSubmitted("7501234567890"),
        )

        assertEquals("", result.inventorySearchQuery)
        assertEquals(mapOf("bag-small" to 1), result.inventoryQuantities)
        assertNull(result.error)
    }

    @Test
    fun `inventory quantity cannot exceed available stock`() {
        val inventory = inventoryItem(
            id = "bag-small",
            itemName = "Bolsa chica",
            quantity = 1,
            sku = "BOLSA-CH",
            barcode = "7501234567890",
        )
        val current = CreateTicketUiState(
            inventoryItems = listOf(inventory),
            inventoryQuantities = mapOf("bag-small" to 1),
        )

        val result = reducer(
            current,
            CreateTicketMutation.InventoryQuantityAdjusted("bag-small", 1),
        )

        assertEquals(mapOf("bag-small" to 1), result.inventoryQuantities)
        assertEquals("No hay stock suficiente para Bolsa chica. Disponible: 1 pieza.", result.error)
    }

    @Test
    fun `inventory lookup not found keeps scanned value for visible search`() {
        val result = reducer(
            CreateTicketUiState(inventoryItems = emptyList()),
            CreateTicketMutation.InventoryLookupSubmitted("missing-code"),
        )

        assertEquals("missing-code", result.inventorySearchQuery)
        assertEquals(
            "No se encontro un producto vendible con SKU o codigo \"missing-code\".",
            result.error,
        )
    }

    private fun inventoryItem(
        id: String,
        itemName: String,
        quantity: Int,
        sku: String?,
        barcode: String?,
    ): InventoryCatalogRecord {
        return InventoryCatalogRecord(
            id = id,
            itemName = itemName,
            category = "Productos",
            quantity = quantity,
            unit = "pieza",
            minQuantity = 0,
            price = 10.0,
            isForSale = true,
            sku = sku,
            barcode = barcode,
            productCode = null,
            branch = "Sucursal Centro",
        )
    }
}
