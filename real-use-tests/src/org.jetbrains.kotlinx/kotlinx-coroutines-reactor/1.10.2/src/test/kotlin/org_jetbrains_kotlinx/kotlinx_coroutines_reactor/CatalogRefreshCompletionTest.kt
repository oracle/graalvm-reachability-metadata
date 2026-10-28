/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.asMono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises Job-to-Mono adaptation as a completion barrier for a background catalog refresh.
 * A reactive search waits for a coroutine to publish the replacement catalog before reading it.
 */
@Timeout(60)
class CatalogRefreshCompletionTest {
    @Test
    fun servesTheRefreshedCatalogOnlyAfterTheImportJobCompletes(): Unit {
        runBlocking {
            val incoming: CompletableDeferred<List<Product>> = CompletableDeferred()
            val catalog: SearchCatalog = SearchCatalog()
            val refresh: Job = launch(Dispatchers.Default) {
                catalog.replaceFrom(incoming)
            }
            val search: Mono<List<String>> = refresh.asMono(Dispatchers.Default)
                .then(Mono.fromSupplier { catalog.search("coffee") })

            StepVerifier.create(search)
                .then {
                    assertEquals(emptyList<String>(), catalog.search("coffee"))
                    incoming.complete(
                        listOf(
                            Product("coffee-beans", "Roasted coffee beans", true),
                            Product("tea-leaves", "Green tea leaves", true),
                            Product("coffee-ground", "Ground coffee", true),
                            Product("coffee-retired", "Discontinued coffee", false)
                        )
                    )
                }
                .expectNext(listOf("coffee-beans", "coffee-ground"))
                .expectComplete()
                .verify(Duration.ofSeconds(10))

            // Late subscribers can use the same completion barrier without importing again.
            StepVerifier.create(search)
                .expectNext(listOf("coffee-beans", "coffee-ground"))
                .expectComplete()
                .verify(Duration.ofSeconds(10))

            assertEquals(listOf("tea-leaves"), catalog.search("tea"))
        }
    }

    private class SearchCatalog {
        private val products: AtomicReference<List<Product>> = AtomicReference(emptyList())

        suspend fun replaceFrom(incoming: Deferred<List<Product>>): Unit {
            val replacement: List<Product> = incoming.await()
            products.set(replacement.filter { it.active }.sortedBy { it.sku })
        }

        fun search(term: String): List<String> = products.get()
            .filter { it.description.contains(term, ignoreCase = true) }
            .map { it.sku }
    }

    private data class Product(val sku: String, val description: String, val active: Boolean)
}
