/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.flux
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

/**
 * Exercises concurrent coroutine Flux producers for a warehouse fulfillment fan-in stream.
 * Ready warehouses publish options without waiting for slower lookups, respecting client demand.
 */
@Timeout(60)
class WarehouseFulfillmentFanInTest {
    @Test
    fun streamsReadyWarehouseOptionsBeforeSlowerLookupsFinish(): Unit {
        val north: CompletableDeferred<Map<String, Int>> = CompletableDeferred()
        val south: CompletableDeferred<Map<String, Int>> = CompletableDeferred()
        val east: CompletableDeferred<Map<String, Int>> = CompletableDeferred()
        val fulfillment: FulfillmentSearch = FulfillmentSearch(
            listOf(Warehouse("north", north), Warehouse("south", south), Warehouse("east", east))
        )
        val options: Flux<Option> = fulfillment.options(linkedMapOf("coffee" to 3, "tea" to 2))

        val received: MutableList<Option> = mutableListOf()

        StepVerifier.create(options.doOnNext { received.add(it) }, 0)
            .then { south.complete(mapOf("coffee" to 2, "tea" to 0)) }
            .thenRequest(1)
            .expectNext(Option("south", "coffee", 2))
            .then {
                // A ready result must reach the client while both other lookups are still pending.
                assertFalse(north.isCompleted)
                assertFalse(east.isCompleted)
                north.complete(mapOf("coffee" to 10, "tea" to 1))
                east.complete(mapOf("coffee" to 1, "tea" to 8))
            }
            .thenRequest(5)
            .expectNextCount(4)
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        // Different warehouses may finish in any order, but none may lose or duplicate an option.
        assertEquals(
            listOf(
                Option("east", "coffee", 1),
                Option("east", "tea", 2),
                Option("north", "coffee", 3),
                Option("north", "tea", 1),
                Option("south", "coffee", 2)
            ),
            received.sortedWith(compareBy<Option> { it.warehouse }.thenBy { it.product })
        )
    }

    private class FulfillmentSearch(private val warehouses: List<Warehouse>) {
        fun options(requested: Map<String, Int>): Flux<Option> = flux {
            coroutineScope {
                for (warehouse: Warehouse in warehouses) {
                    launch {
                        val stock: Map<String, Int> = warehouse.stock.await()
                        for ((product: String, quantity: Int) in requested) {
                            val available: Int = minOf(quantity, stock.getOrDefault(product, 0))
                            if (available > 0) {
                                send(Option(warehouse.name, product, available))
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Warehouse(val name: String, val stock: Deferred<Map<String, Int>>)

    private data class Option(val warehouse: String, val product: String, val quantity: Int)
}
