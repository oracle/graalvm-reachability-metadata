/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin Sequence JSON serialization for a lazy storefront catalog export.
 * A catalog service filters inventory without materializing an export list and publishes
 * a JSON array that another storefront consumes as typed catalog entries.
 */
public class SequenceCatalogExportTest {

    public data class InventoryItem(
        val sku: String,
        val title: String,
        val priceCents: Long,
        val stock: Int,
        val discontinued: Boolean = false,
    )

    public data class CatalogEntry(
        val sku: String,
        val title: String,
        val priceCents: Long,
    )

    @Test
    @Timeout(60)
    public fun exportsAvailableProductsForAStorefrontBasket(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val inventory: List<InventoryItem> = listOf(
            InventoryItem("NOTEBOOK", "Pocket notebook", 750L, 12),
            InventoryItem("PEN", "Fountain pen", 2_400L, 0),
            InventoryItem("OLD-DIARY", "Last season's diary", 900L, 5, discontinued = true),
            InventoryItem("PENCIL", "Drawing pencil", 125L, 40),
        )
        val export: Sequence<CatalogEntry> = inventory.asSequence()
            .filter { item: InventoryItem -> item.stock > 0 && !item.discontinued }
            .map { item: InventoryItem -> CatalogEntry(item.sku, item.title, item.priceCents) }
            .constrainOnce()

        val publishedJson: String = mapper.writeValueAsString(export)
        val published: JsonNode = mapper.readTree(publishedJson)
        val expected: JsonNode = mapper.readTree(
            """
                [
                  {"sku": "NOTEBOOK", "title": "Pocket notebook", "priceCents": 750},
                  {"sku": "PENCIL", "title": "Drawing pencil", "priceCents": 125}
                ]
            """.trimIndent(),
        )
        assertEquals(expected, published)

        val imported: List<CatalogEntry> = mapper.readValue(publishedJson)
        val storefront: Map<String, CatalogEntry> = imported.associateBy { entry: CatalogEntry ->
            entry.sku
        }
        assertEquals(
            mapOf(
                "NOTEBOOK" to CatalogEntry("NOTEBOOK", "Pocket notebook", 750L),
                "PENCIL" to CatalogEntry("PENCIL", "Drawing pencil", 125L),
            ),
            storefront,
        )
        val basketTotalCents: Long = storefront.getValue("NOTEBOOK").priceCents +
            4 * storefront.getValue("PENCIL").priceCents
        assertEquals(1_250L, basketTotalCents)

        val emptyExportJson: String = mapper.writeValueAsString(emptySequence<CatalogEntry>())
        assertEquals(mapper.readTree("[]"), mapper.readTree(emptyExportJson))
        val emptyImport: List<CatalogEntry> = mapper.readValue(emptyExportJson)
        assertEquals(emptyList<CatalogEntry>(), emptyImport)
    }
}
