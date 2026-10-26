/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

/**
 * Exercises optional Mono awaiting for a suspending checkout promotion lookup.
 * Checkout applies eligible promotions, keeps the full price for missing offers, and propagates invalid input.
 */
@Timeout(60)
class OptionalPromotionLookupTest {
    @Test
    fun quotesCheckoutWithPresentAbsentAndInvalidPromotionCodes(): Unit {
        val scheduler: Scheduler = Schedulers.newSingle("promotion-catalog")
        try {
            val catalog: PromotionCatalog = PromotionCatalog(
                mapOf("WELCOME" to Promotion(3000, 750)),
                scheduler
            )
            val checkout: Checkout = Checkout(catalog)

            runBlocking {
                withTimeout(10000) {
                    assertEquals(Quote(5000, 750, 4250), checkout.quote(5000, "WELCOME"))
                    assertEquals(Quote(2000, 0, 2000), checkout.quote(2000, "WELCOME"))
                    assertEquals(Quote(5000, 0, 5000), checkout.quote(5000, "EXPIRED"))

                    val failure: Throwable? = runCatching { checkout.quote(5000, " ") }.exceptionOrNull()
                    assertInstanceOf(IllegalArgumentException::class.java, failure)
                    assertEquals("Promotion code must not be blank", failure?.message)
                }
            }
        } finally {
            scheduler.dispose()
        }
    }

    private class Checkout(private val catalog: PromotionCatalog) {
        suspend fun quote(subtotalCents: Int, code: String): Quote {
            require(subtotalCents >= 0)
            val promotion: Promotion? = catalog.find(code).awaitSingleOrNull()
            val discountCents: Int = if (promotion != null && subtotalCents >= promotion.minimumCents) {
                minOf(subtotalCents, promotion.discountCents)
            } else {
                0
            }
            return Quote(subtotalCents, discountCents, subtotalCents - discountCents)
        }
    }

    private class PromotionCatalog(
        private val promotions: Map<String, Promotion>,
        private val scheduler: Scheduler
    ) {
        fun find(code: String): Mono<Promotion> = Mono.defer {
            require(code.isNotBlank()) { "Promotion code must not be blank" }
            Mono.justOrEmpty<Promotion>(promotions[code])
        }.subscribeOn(scheduler)
    }

    private data class Promotion(val minimumCents: Int, val discountCents: Int)

    private data class Quote(val subtotalCents: Int, val discountCents: Int, val totalCents: Int)
}
