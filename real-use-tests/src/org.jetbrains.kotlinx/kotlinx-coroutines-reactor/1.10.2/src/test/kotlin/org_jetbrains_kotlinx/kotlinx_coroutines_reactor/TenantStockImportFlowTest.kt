/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import reactor.util.context.Context

/**
 * Exercises Reactor context injection into Publisher-to-Flow collection for tenant stock imports.
 * Coroutine import jobs select their own reactive warehouse feed and aggregate its stock adjustments.
 */
@Timeout(60)
class TenantStockImportFlowTest {
    @Test
    fun importsOnlyTheWarehouseAdjustmentsForEachCoroutineTenant(): Unit {
        val warehouse: WarehouseFeed = WarehouseFeed(
            mapOf(
                "north" to listOf(
                    StockAdjustment("coffee", 12),
                    StockAdjustment("tea", 7),
                    StockAdjustment("coffee", -3)
                ),
                "south" to listOf(
                    StockAdjustment("coffee", 4),
                    StockAdjustment("cocoa", 6),
                    StockAdjustment("coffee", 2)
                )
            )
        )
        val importer: StockImporter = StockImporter(warehouse)

        runBlocking {
            withTimeout(10000) {
                val jobs: List<Deferred<Map<String, Int>>> = listOf("north", "south", "north").map { tenant ->
                    async { importer.snapshot(tenant) }
                }
                val snapshots: List<Map<String, Int>> = jobs.awaitAll()

                assertEquals(mapOf("coffee" to 9, "tea" to 7), snapshots[0])
                assertEquals(mapOf("coffee" to 6, "cocoa" to 6), snapshots[1])
                assertEquals(snapshots[0], snapshots[2])
            }
        }
    }

    private class StockImporter(private val warehouse: WarehouseFeed) {
        suspend fun snapshot(tenant: String): Map<String, Int> =
            withContext(Context.of("tenant", tenant).asCoroutineContext()) {
                val stock: MutableMap<String, Int> = linkedMapOf()
                warehouse.adjustments().asFlow().buffer(1).collect { adjustment ->
                    stock[adjustment.sku] = stock.getOrDefault(adjustment.sku, 0) + adjustment.quantity
                }
                stock.toMap()
            }
    }

    private class WarehouseFeed(private val adjustmentsByTenant: Map<String, List<StockAdjustment>>) {
        fun adjustments(): Flux<StockAdjustment> = Flux.deferContextual { context ->
            val tenant: String = context.get("tenant")
            Flux.fromIterable(adjustmentsByTenant.getValue(tenant))
        }
    }

    private data class StockAdjustment(val sku: String, val quantity: Int)
}
