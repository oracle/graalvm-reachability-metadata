/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO JSON request and response handling through an order checkout API.
 */
class JsonOrderCheckoutTest {
    @Test
    @Timeout(60)
    fun `creates an order from a JSON cart and retrieves its complete receipt`(): Unit = runBlocking {
        val prices: Map<String, Int> = mapOf("notebook" to 450, "pen" to 125)
        val orders: ConcurrentHashMap<String, OrderReceipt> = ConcurrentHashMap()
        val sequence: AtomicInteger = AtomicInteger()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/orders") {
                    val cart: CheckoutRequest = call.receive()
                    val lines: List<ReceiptLine> = cart.items.map { item: CartItem ->
                        ReceiptLine(item.sku, item.quantity, prices.getValue(item.sku) * item.quantity)
                    }
                    val receipt: OrderReceipt = OrderReceipt(
                        "order-${sequence.incrementAndGet()}",
                        cart.customerNote,
                        lines,
                        lines.sumOf { it.totalCents },
                    )
                    orders[receipt.id] = receipt
                    call.response.headers.append(HttpHeaders.Location, "/orders/${receipt.id}")
                    call.respond(HttpStatusCode.Created, receipt)
                }
                get("/orders/{id}") {
                    val receipt: OrderReceipt? = orders[call.parameters["id"]]
                    if (receipt == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(receipt)
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    install(ClientContentNegotiation) {
                        json()
                    }
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val baseUrl: String = "http://127.0.0.1:$port"
                    val note: String = "Caf\u00e9 \"North\"\nReceiving desk"
                    val created: HttpResponse = client.post("$baseUrl/orders") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(CheckoutRequest(note, listOf(CartItem("notebook", 2), CartItem("pen", 3))))
                    }
                    assertEquals(HttpStatusCode.Created, created.status)
                    assertEquals(ContentType.Application.Json, created.contentType()?.withoutParameters())
                    assertEquals("/orders/order-1", created.headers[HttpHeaders.Location])
                    val expected: OrderReceipt = OrderReceipt(
                        "order-1",
                        note,
                        listOf(ReceiptLine("notebook", 2, 900), ReceiptLine("pen", 3, 375)),
                        1275,
                    )
                    assertEquals(expected, created.body<OrderReceipt>())

                    val fetched: HttpResponse = client.get(baseUrl + created.headers[HttpHeaders.Location]!!) {
                        accept(ContentType.Application.Json)
                    }
                    assertEquals(HttpStatusCode.OK, fetched.status)
                    assertEquals(ContentType.Application.Json, fetched.contentType()?.withoutParameters())
                    assertEquals(expected, fetched.body<OrderReceipt>())
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    @Serializable
    data class CheckoutRequest(val customerNote: String, val items: List<CartItem>)

    @Serializable
    data class CartItem(val sku: String, val quantity: Int)

    @Serializable
    data class ReceiptLine(val sku: String, val quantity: Int, val totalCents: Int)

    @Serializable
    data class OrderReceipt(
        val id: String,
        val customerNote: String,
        val lines: List<ReceiptLine>,
        val totalCents: Int,
    )
}
