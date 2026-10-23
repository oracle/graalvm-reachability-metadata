 /*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/** Exercises typed JSON request and response bodies by obtaining checkout quotes through content negotiation. */
class CheckoutJsonBodiesTest {
    @Test
    @Timeout(60)
    fun obtainsQuotesForACartWithAndWithoutADiscount(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                install(ServerContentNegotiation) { json() }
                routing {
                    post("/checkout/quotes") {
                        val cart: Cart = call.receive<Cart>()
                        val acceptedItems: List<CartItem> = cart.items.filter { it.sku == "field-notebook" }
                        val subtotal: Int = acceptedItems.sumOf { it.quantity * 850 }
                        val discount: Int = if (cart.coupon == "NOTEBOOK10") subtotal / 10 else 0
                        call.respond(
                            HttpStatusCode.Created,
                            Quote(cart.customer, acceptedItems, subtotal - discount, discount)
                        )
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(ContentNegotiation) { json() }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val items: List<CartItem> = listOf(
                        CartItem("field-notebook", 2),
                        CartItem("discontinued-pen", 1)
                    )
                    val discountedResponse: HttpResponse = client.post("http://127.0.0.1:$port/checkout/quotes") {
                        contentType(ContentType.Application.Json)
                        setBody(Cart("Zo\u00eb & Co", items, "NOTEBOOK10"))
                    }
                    assertEquals(HttpStatusCode.Created, discountedResponse.status)
                    assertEquals(ContentType.Application.Json, discountedResponse.contentType()?.withoutParameters())
                    val discounted: Quote = discountedResponse.body<Quote>()
                    assertEquals(
                        Quote("Zo\u00eb & Co", listOf(CartItem("field-notebook", 2)), 1530, 170),
                        discounted
                    )

                    // Omitting the optional coupon uses the regular price for the same cart.
                    val regularResponse: HttpResponse = client.post("http://127.0.0.1:$port/checkout/quotes") {
                        contentType(ContentType.Application.Json)
                        setBody(Cart("Zo\u00eb & Co", items))
                    }
                    assertEquals(HttpStatusCode.Created, regularResponse.status)
                    val regular: Quote = regularResponse.body<Quote>()
                    assertEquals(
                        Quote("Zo\u00eb & Co", listOf(CartItem("field-notebook", 2)), 1700, 0),
                        regular
                    )
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }

    @Serializable
    data class Cart(val customer: String, val items: List<CartItem>, val coupon: String? = null)

    @Serializable
    data class CartItem(val sku: String, val quantity: Int)

    @Serializable
    data class Quote(
        val customer: String,
        val items: List<CartItem>,
        val totalCents: Int,
        val discountCents: Int
    )
}
