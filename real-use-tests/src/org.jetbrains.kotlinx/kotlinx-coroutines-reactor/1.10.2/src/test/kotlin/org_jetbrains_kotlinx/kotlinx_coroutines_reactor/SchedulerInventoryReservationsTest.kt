/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

/**
 * Exercises Reactor Scheduler-to-coroutine dispatcher adaptation for serialized inventory reservations.
 * Concurrent checkout requests share a single-threaded inventory ledger without overselling stock.
 */
@Timeout(60)
class SchedulerInventoryReservationsTest {
    @Test
    fun serializesConcurrentCheckoutsOnTheInventoryScheduler(): Unit {
        val scheduler: Scheduler = Schedulers.newSingle("inventory-ledger")
        try {
            val dispatcher: CoroutineDispatcher = scheduler.asCoroutineDispatcher()
            val inventory: Inventory = Inventory(dispatcher, mapOf("coffee" to 10, "tea" to 4))

            runBlocking {
                withTimeout(10000) {
                    val checkouts: List<Deferred<Boolean>> = (1..5).map {
                        async(Dispatchers.Default) { inventory.reserve("coffee", 3) }
                    }
                    val accepted: List<Boolean> = checkouts.awaitAll()

                    assertEquals(3, accepted.count { it })
                    assertEquals(2, accepted.count { !it })
                    assertEquals(mapOf("coffee" to 1, "tea" to 4), inventory.snapshot())

                    assertTrue(inventory.reserve("tea", 4))
                    assertFalse(inventory.reserve("tea", 1))
                    assertFalse(inventory.reserve("coffee", 2))
                    assertEquals(mapOf("coffee" to 1, "tea" to 0), inventory.snapshot())
                }
            }
        } finally {
            scheduler.dispose()
        }
    }

    private class Inventory(
        private val dispatcher: CoroutineDispatcher,
        initialStock: Map<String, Int>
    ) {
        private val stock: MutableMap<String, Int> = initialStock.toMutableMap()

        suspend fun reserve(product: String, quantity: Int): Boolean = withContext(dispatcher) {
            require(quantity > 0)
            val available: Int = stock.getValue(product)
            if (available < quantity) {
                false
            } else {
                stock[product] = available - quantity
                true
            }
        }

        suspend fun snapshot(): Map<String, Int> = withContext(dispatcher) {
            stock.toMap()
        }
    }
}
