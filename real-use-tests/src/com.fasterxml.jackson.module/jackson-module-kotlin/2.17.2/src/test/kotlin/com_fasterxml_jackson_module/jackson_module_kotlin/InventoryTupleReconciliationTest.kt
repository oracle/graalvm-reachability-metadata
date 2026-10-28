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
 * Exercises Kotlin Pair JSON binding for before-and-after inventory reconciliation snapshots.
 * An inventory audit reads a typed pair of stock snapshots, derives signed adjustments,
 * and archives the comparison so another worker can reproduce the same reconciliation.
 */
public class InventoryTupleReconciliationTest {

    public data class StockSnapshot(val warehouse: String, val quantities: Map<String, Int>)

    public fun adjustments(comparison: Pair<StockSnapshot, StockSnapshot>): Map<String, Int> {
        val (before: StockSnapshot, after: StockSnapshot) = comparison
        require(before.warehouse == after.warehouse) { "Snapshots must describe the same warehouse" }
        val products: Set<String> = before.quantities.keys + after.quantities.keys
        return products.associateWith { sku: String ->
            after.quantities.getOrDefault(sku, 0) - before.quantities.getOrDefault(sku, 0)
        }.filterValues { delta: Int -> delta != 0 }
    }

    @Test
    @Timeout(60)
    public fun reconcilesTypedSnapshotPairsAndArchivesTheAuditEvidence(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val comparisonJson: String = """
            {
              "first": {
                "warehouse": "north",
                "quantities": {"NOTEBOOK": 12, "PENCIL": 20, "PEN": 7, "OLD-DIARY": 3}
              },
              "second": {
                "warehouse": "north",
                "quantities": {"NOTEBOOK": 9, "PENCIL": 25, "PEN": 7, "ERASER": 8}
              }
            }
        """.trimIndent()

        val comparison: Pair<StockSnapshot, StockSnapshot> = mapper.readValue(comparisonJson)
        assertEquals(
            StockSnapshot("north", mapOf("NOTEBOOK" to 12, "PENCIL" to 20, "PEN" to 7, "OLD-DIARY" to 3)),
            comparison.first,
        )
        assertEquals(
            StockSnapshot("north", mapOf("NOTEBOOK" to 9, "PENCIL" to 25, "PEN" to 7, "ERASER" to 8)),
            comparison.second,
        )
        val expectedAdjustments: Map<String, Int> = mapOf(
            "NOTEBOOK" to -3,
            "PENCIL" to 5,
            "OLD-DIARY" to -3,
            "ERASER" to 8,
        )
        assertEquals(expectedAdjustments, adjustments(comparison))

        val archivedJson: String = mapper.writeValueAsString(comparison)
        val archived: JsonNode = mapper.readTree(archivedJson)
        assertEquals(mapper.readTree(comparisonJson), archived)
        val replayed: Pair<StockSnapshot, StockSnapshot> = mapper.readValue(archivedJson)
        assertEquals(comparison, replayed)
        assertEquals(expectedAdjustments, adjustments(replayed))
    }
}
