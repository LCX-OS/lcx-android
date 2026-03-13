package com.cleanx.lcx.feature.tickets.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketCreationPricingTest {

    private val baseService = ServiceCatalogRecord(
        id = "service-base",
        name = "Lavado por KG",
        description = "Servicio base por kilo",
        category = "Lavado",
        price = 50.0,
        unit = "kg",
        active = true,
    )

    @Test
    fun `pricing enforces minimum 3kg subtotal`() {
        val pricing = calculateEncargoPricing(
            baseServiceId = baseService.id,
            weight = 2.0,
            addOnIds = emptyList(),
            services = listOf(baseService),
            addOns = emptyList(),
            inventoryItems = emptyList(),
        )

        assertEquals(150.0, pricing.subtotal, 0.001)
        assertEquals(0.0, pricing.addOnsTotal, 0.001)
        assertEquals(150.0, pricing.total, 0.001)
    }

    @Test
    fun `pricing counts repeated ids for fixed-price catalog and inventory extras`() {
        val beddingService = ServiceCatalogRecord(
            id = "bedding-1",
            name = "Edredon King",
            description = "Edredon grande",
            category = "Edredones",
            price = 90.0,
            unit = "pieza",
            active = true,
        )
        val inventoryItem = InventoryCatalogRecord(
            id = "inventory-1",
            itemName = "Bolsa para edredon",
            category = "Accesorios",
            quantity = 10,
            unit = "pieza",
            price = 12.0,
            isForSale = true,
        )

        val pricing = calculateEncargoPricing(
            baseServiceId = baseService.id,
            weight = 5.0,
            addOnIds = listOf(beddingService.id, beddingService.id, inventoryItem.id),
            services = listOf(baseService, beddingService),
            addOns = emptyList(),
            inventoryItems = listOf(inventoryItem),
        )

        assertEquals(250.0, pricing.subtotal, 0.001)
        assertEquals(192.0, pricing.addOnsTotal, 0.001)
        assertEquals(442.0, pricing.total, 0.001)
    }

    @Test
    fun `pricing applies 15 percent surcharge items over subtotal plus fixed extras`() {
        val fixedExtra = AddOnCatalogRecord(
            id = "addon-softener",
            name = "Suavizante",
            description = null,
            price = 18.0,
            active = true,
        )
        val percentageExtra = AddOnCatalogRecord(
            id = "addon-premium",
            name = "Fragancia premium",
            description = null,
            price = 999.0,
            active = true,
        )

        val pricing = calculateEncargoPricing(
            baseServiceId = baseService.id,
            weight = 4.0,
            addOnIds = listOf(fixedExtra.id, percentageExtra.id, percentageExtra.id),
            services = listOf(baseService),
            addOns = listOf(fixedExtra, percentageExtra),
            inventoryItems = emptyList(),
        )

        assertEquals(200.0, pricing.subtotal, 0.001)
        assertEquals(83.4, pricing.addOnsTotal, 0.001)
        assertEquals(283.4, pricing.total, 0.001)
    }
}
