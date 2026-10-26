/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises structured coroutine failure propagation into Reactor error recovery for checkout quotes.
 * Parallel product lookups either produce a complete quote or return a typed rejection without a partial total.
 */
@Timeout(60)
class CheckoutFailureRecoveryTest {
    @Test
    fun returnsCompleteQuotesAndRecoversUnknownProductsAsRejections(): Unit {
        val checkout: CheckoutEndpoint = CheckoutEndpoint(mapOf("tea" to 650, "coffee" to 1200))

        StepVerifier.create(checkout.quote(listOf(Line("tea", 2), Line("coffee", 1))))
            .expectNext(QuoteResponse("QUOTED", 2500, null))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(checkout.quote(listOf(Line("tea", 2), Line("cocoa", 1))))
            .expectNext(QuoteResponse("REJECTED", null, "Unknown product: cocoa"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(checkout.quote(listOf(Line("coffee", 2))))
            .expectNext(QuoteResponse("QUOTED", 2400, null))
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    private class CheckoutEndpoint(private val prices: Map<String, Int>) {
        fun quote(lines: List<Line>): Mono<QuoteResponse> = mono {
            val totalCents: Int = coroutineScope {
                lines.map { line -> async { price(line) } }.awaitAll().sum()
            }
            QuoteResponse("QUOTED", totalCents, null)
        }.onErrorResume(UnknownProductException::class.java) { failure ->
            Mono.just(QuoteResponse("REJECTED", null, failure.message))
        }

        private suspend fun price(line: Line): Int {
            yield()
            val unitPrice: Int = prices[line.product] ?: throw UnknownProductException(line.product)
            return unitPrice * line.quantity
        }
    }

    private class UnknownProductException(product: String) :
        IllegalArgumentException("Unknown product: $product")

    private data class Line(val product: String, val quantity: Int)

    private data class QuoteResponse(val status: String, val totalCents: Int?, val reason: String?)
}
