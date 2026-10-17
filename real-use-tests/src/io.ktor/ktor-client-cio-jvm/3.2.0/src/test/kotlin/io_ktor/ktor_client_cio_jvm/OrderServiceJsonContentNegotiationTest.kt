/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * JSON content negotiation over the CIO client engine (the client `ContentNegotiation` plugin with kotlinx.serialization).
 *
 * A storefront places orders with the order service through typed Kotlin models: it posts an
 * `OrderRequest`, reads back the service's `OrderConfirmation`, fetches one order and a customer's
 * order list as `List<OrderConfirmation>`, and turns a rejected order into the service's typed
 * `ApiError`. The client never touches JSON text: the plugin serializes request bodies, advertises
 * `application/json` in `Accept`, and decodes every response body into the requested type.
 */
class OrderServiceJsonContentNegotiationTest {

    /** Delivery speed the customer chose; serialized under its wire names. */
    @Serializable
    enum class Priority {
        @SerialName("standard")
        STANDARD,

        @SerialName("express")
        EXPRESS,
    }

    @Serializable
    data class OrderLine(val sku: String, val quantity: Int, val unitPriceCents: Long)

    @Serializable
    data class OrderRequest(
        val customerId: String,
        val lines: List<OrderLine>,
        val priority: Priority = Priority.STANDARD,
        val note: String? = null,
    )

    @Serializable
    data class OrderConfirmation(
        val orderId: String,
        val customerId: String,
        val totalCents: Long,
        val priority: Priority,
        val lineCount: Int,
        val note: String? = null,
    )

    @Serializable
    data class ApiError(val code: String, val message: String, val fields: List<String> = emptyList())

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Orders the service has accepted, by order id. */
    private val orders: ConcurrentHashMap<String, OrderConfirmation> = ConcurrentHashMap()

    private val nextOrderNumber: AtomicInteger = AtomicInteger(1000)

    /** `Content-Type` of every order the service received. */
    private val receivedContentTypes: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** `Accept` header of every request the service received. */
    private val receivedAccepts: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(WIRE_JSON)
            }
            routing {
                post("/orders") {
                    receivedContentTypes += call.request.headers[HttpHeaders.ContentType].orEmpty()
                    receivedAccepts += call.request.headers[HttpHeaders.Accept].orEmpty()
                    val request: OrderRequest = call.receive()
                    val invalid: List<String> = request.lines
                        .filter { line -> line.quantity <= 0 }
                        .map { line -> "lines[${line.sku}].quantity" }
                    if (request.lines.isEmpty() || invalid.isNotEmpty()) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiError("invalid_order", "Order for ${request.customerId} was rejected", invalid),
                        )
                        return@post
                    }
                    val confirmation = OrderConfirmation(
                        orderId = "ORD-${nextOrderNumber.incrementAndGet()}",
                        customerId = request.customerId,
                        totalCents = request.lines.sumOf { line -> line.quantity * line.unitPriceCents },
                        priority = request.priority,
                        lineCount = request.lines.size,
                        note = request.note,
                    )
                    orders[confirmation.orderId] = confirmation
                    call.respond(HttpStatusCode.Created, confirmation)
                }
                get("/orders/{id}") {
                    receivedAccepts += call.request.headers[HttpHeaders.Accept].orEmpty()
                    val confirmation: OrderConfirmation? = orders[call.parameters["id"]!!]
                    if (confirmation == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("not_found", "No such order"))
                    } else {
                        call.respond(confirmation)
                    }
                }
                get("/orders") {
                    receivedAccepts += call.request.headers[HttpHeaders.Accept].orEmpty()
                    val customerId: String = call.request.queryParameters["customer"]!!
                    call.respond(orders.values.filter { order -> order.customerId == customerId }.sortedBy { it.orderId })
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun storefrontExchangesTypedOrdersAsJson() {
        val orderServiceUrl: String = "http://127.0.0.1:$port/"
        val client = HttpClient(ClientCIO) {
            expectSuccess = true
            install(ClientContentNegotiation) {
                json(WIRE_JSON)
            }
            defaultRequest {
                url(orderServiceUrl)
            }
        }

        client.use {
            runBlocking {
                val express: OrderConfirmation = placeOrder(
                    client,
                    OrderRequest(
                        customerId = "C-7",
                        lines = listOf(OrderLine("MUG-01", 2, 1250), OrderLine("TEA-12", 3, 499)),
                        priority = Priority.EXPRESS,
                        note = "Gift wrap, please",
                    ),
                )
                assertEquals(OrderConfirmation("ORD-1001", "C-7", 3997, Priority.EXPRESS, 2, "Gift wrap, please"), express)

                val standard: OrderConfirmation = placeOrder(
                    client,
                    OrderRequest(customerId = "C-7", lines = listOf(OrderLine("KETTLE-3", 1, 4900))),
                )
                assertEquals(OrderConfirmation("ORD-1002", "C-7", 4900, Priority.STANDARD, 1, null), standard)

                placeOrder(client, OrderRequest(customerId = "C-9", lines = listOf(OrderLine("MUG-01", 1, 1250))))

                val fetched: OrderConfirmation = client.get("orders/ORD-1001").body()
                assertEquals(express, fetched)

                val history: List<OrderConfirmation> = client.get("orders") {
                    parameter("customer", "C-7")
                }.body()
                assertEquals(listOf(express, standard), history)

                val rejected: ClientRequestException = assertThrows(ClientRequestException::class.java) {
                    runBlocking {
                        placeOrder(
                            client,
                            OrderRequest(
                                customerId = "C-7",
                                lines = listOf(OrderLine("MUG-01", 0, 1250), OrderLine("TEA-12", -1, 499)),
                            ),
                        )
                    }
                }
                assertEquals(HttpStatusCode.UnprocessableEntity, rejected.response.status)
                val error: ApiError = rejected.response.body()
                assertEquals(
                    ApiError(
                        "invalid_order",
                        "Order for C-7 was rejected",
                        listOf("lines[MUG-01].quantity", "lines[TEA-12].quantity"),
                    ),
                    error,
                )

                val missing: ClientRequestException = assertThrows(ClientRequestException::class.java) {
                    runBlocking { client.get("orders/ORD-9999").body<OrderConfirmation>() }
                }
                assertEquals(HttpStatusCode.NotFound, missing.response.status)
                assertEquals(ApiError("not_found", "No such order"), missing.response.body<ApiError>())
            }
        }

        assertEquals(setOf("ORD-1001", "ORD-1002", "ORD-1003"), orders.keys)
        assertEquals(4, receivedContentTypes.size)
        receivedContentTypes.forEach { contentType ->
            assertEquals(ContentType.Application.Json, ContentType.parse(contentType).withoutParameters())
        }
        assertEquals(7, receivedAccepts.size)
        receivedAccepts.forEach { accept -> assertEquals(ContentType.Application.Json.toString(), accept) }
    }

    private suspend fun placeOrder(client: HttpClient, order: OrderRequest): OrderConfirmation {
        val response: HttpResponse = client.post("orders") {
            contentType(ContentType.Application.Json)
            setBody(order)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private companion object {
        /** Wire format both sides agree on: defaults are written so the service sees every field. */
        val WIRE_JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
