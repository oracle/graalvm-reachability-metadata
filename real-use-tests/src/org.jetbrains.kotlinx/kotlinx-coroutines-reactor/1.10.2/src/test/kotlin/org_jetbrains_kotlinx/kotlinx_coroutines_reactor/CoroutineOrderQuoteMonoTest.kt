/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises the coroutine-backed Mono builder for a reactive order-pricing endpoint.
 * A suspending pricing service waits for its catalog and runs afresh for each subscriber.
 */
@Timeout(60)
class CoroutineOrderQuoteMonoTest {
    @Test
    fun pricesAnOrderLazilyForEachSubscriber(): Unit {
        val catalog: CompletableDeferred<Map<String, Int>> = CompletableDeferred()
        val service: PricingService = PricingService(catalog)
        val response: Mono<Quote> = mono {
            service.quote("coffee-beans", 3)
        }

        assertEquals(0, service.requests.get())

        StepVerifier.create(response)
            .then { catalog.complete(mapOf("coffee-beans" to 1250)) }
            .expectNext(Quote("coffee-beans", 3, 3750, 1))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        assertEquals(1, service.requests.get())

        StepVerifier.create(response)
            .expectNext(Quote("coffee-beans", 3, 3750, 2))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        assertEquals(2, service.requests.get())
    }

    private class PricingService(
        private val catalog: CompletableDeferred<Map<String, Int>>
    ) {
        val requests: AtomicInteger = AtomicInteger()

        suspend fun quote(product: String, quantity: Int): Quote {
            val requestNumber: Int = requests.incrementAndGet()
            val prices: Map<String, Int> = catalog.await()
            return Quote(product, quantity, prices.getValue(product) * quantity, requestNumber)
        }
    }

    private data class Quote(
        val product: String,
        val quantity: Int,
        val totalCents: Int,
        val requestNumber: Int
    )
}
