/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises nullable coroutine Mono completion for an idempotent saved-cart deletion endpoint.
 * A deleted cart produces a receipt, while an absent cart completes empty and becomes a no-content response.
 */
@Timeout(60)
class NullableCartDeletionTest {
    @Test
    fun returnsADeletionReceiptOnceAndNoContentForRepeatedRequests(): Unit {
        val carts: CartStore = CartStore(
            listOf(Cart("cart-101", 3), Cart("cart-202", 5))
        )
        val endpoint: CartEndpoint = CartEndpoint(carts)
        val delete: Mono<DeleteResponse> = endpoint.delete("cart-101")

        StepVerifier.create(delete)
            .expectNext(DeleteResponse(200, "Removed cart-101 with 3 items"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(delete)
            .expectNext(DeleteResponse(204, null))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(endpoint.delete("unknown-cart"))
            .expectNext(DeleteResponse(204, null))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(endpoint.delete("cart-202"))
            .expectNext(DeleteResponse(200, "Removed cart-202 with 5 items"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        assertEquals(
            listOf("cart-101", "cart-202"),
            carts.deletedCartIds.toList(),
            "Repeated or missing-cart requests must not record another deletion"
        )
    }

    private class CartEndpoint(private val carts: CartStore) {
        fun delete(cartId: String): Mono<DeleteResponse> = mono {
            carts.remove(cartId)?.let { cart ->
                DeleteResponse(200, "Removed ${cart.id} with ${cart.itemCount} items")
            }
        }.switchIfEmpty(Mono.just(DeleteResponse(204, null)))
    }

    private class CartStore(initialCarts: List<Cart>) {
        private val lock: Mutex = Mutex()
        private val saved: MutableMap<String, Cart> = initialCarts.associateBy { it.id }.toMutableMap()
        val deletedCartIds: MutableList<String> = mutableListOf()

        suspend fun remove(cartId: String): Cart? = lock.withLock {
            saved.remove(cartId)?.also { deletedCartIds.add(it.id) }
        }
    }

    private data class Cart(val id: String, val itemCount: Int)

    private data class DeleteResponse(val status: Int, val body: String?)
}
