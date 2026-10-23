/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.test.scheduler.VirtualTimeScheduler

/**
 * Exercises Reactor scheduler-backed coroutine delays and timer cancellation for debounced product searches.
 * Typing replaces a pending search, and closing the search box cancels its remaining delayed work.
 */
@Timeout(60)
class DebouncedProductSearchTest {
    @Test
    fun publishesOnlyTheSettledQueryAndCancelsPendingWorkOnClose(): Unit {
        val scheduler: VirtualTimeScheduler = VirtualTimeScheduler.create()
        val results: MutableList<SearchResult> = mutableListOf()
        try {
            val dispatcher: CoroutineDispatcher = scheduler.asCoroutineDispatcher()
            ProductSearch(dispatcher, listOf("coffee-beans", "cocoa", "coffee-ground", "tea"), results).use { search ->
                search.type("co")
                scheduler.advanceTimeBy(Duration.ZERO)
                scheduler.advanceTimeBy(Duration.ofMillis(100))
                assertTrue(results.isEmpty())

                search.type("cof")
                scheduler.advanceTimeBy(Duration.ZERO)
                scheduler.advanceTimeBy(Duration.ofMillis(299))
                assertTrue(results.isEmpty(), "Neither the replaced query nor the unsettled query should publish")

                scheduler.advanceTimeBy(Duration.ofMillis(1))
                assertEquals(
                    listOf(SearchResult("cof", listOf("coffee-beans", "coffee-ground"))),
                    results
                )

                search.type("tea")
                scheduler.advanceTimeBy(Duration.ZERO)
                scheduler.advanceTimeBy(Duration.ofMillis(100))
            }

            scheduler.advanceTimeBy(Duration.ofSeconds(1))
            assertEquals(
                listOf(SearchResult("cof", listOf("coffee-beans", "coffee-ground"))),
                results,
                "Closing the search box must prevent pending suggestions from publishing"
            )
        } finally {
            scheduler.dispose()
        }
    }

    private class ProductSearch(
        dispatcher: CoroutineDispatcher,
        private val products: List<String>,
        private val results: MutableList<SearchResult>
    ) : AutoCloseable {
        private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
        private var pending: Job? = null

        fun type(query: String): Unit {
            pending?.cancel()
            pending = scope.launch {
                delay(300)
                val matches: List<String> = products.filter { it.startsWith(query) }.sorted()
                results.add(SearchResult(query, matches))
            }
        }

        override fun close(): Unit {
            scope.cancel()
        }
    }

    private data class SearchResult(val query: String, val products: List<String>)
}
