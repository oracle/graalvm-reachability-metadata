/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts REST request and response bodies through the kotlinx JSON converter that `json()`
 * registers with server and client content negotiation.
 *
 * A bookstore order API accepts orders as JSON, prices them and answers with a receipt; a
 * typed storefront client talks to it through the same converter, while a partner integration
 * posts hand-written JSON carrying fields the API does not know and reads the raw receipts.
 */
public class BookstoreOrderApiJsonTest {

    /** One ordered title. */
    @Serializable
    private data class OrderLine(
        val isbn: String,
        val quantity: Int,
        @SerialName("unit_price_cents") val unitPriceCents: Long,
    )

    /** An order as the storefront submits it. */
    @Serializable
    private data class OrderRequest(
        @SerialName("customer_id") val customerId: String,
        val lines: List<OrderLine>,
        @SerialName("gift_note") val giftNote: String? = null,
    )

    /** The bookstore's answer to an accepted order. */
    @Serializable
    private data class OrderReceipt(
        @SerialName("order_id") val orderId: Int,
        @SerialName("customer_id") val customerId: String,
        @SerialName("total_cents") val totalCents: Long,
        @SerialName("item_count") val itemCount: Int,
        @SerialName("gift_note") val giftNote: String? = null,
        val currency: String = "EUR",
    )

    /** A rejected order. */
    @Serializable
    private data class ApiError(val code: String, val message: String)

    /** The wire format both sides agree on. */
    private val apiJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val receipts: MutableList<OrderReceipt> = CopyOnWriteArrayList()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var storefront: HttpClient
    private lateinit var partner: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(apiJson)
            }
            routing {
                post("/orders") {
                    val order: OrderRequest = call.receive()
                    if (order.lines.isEmpty()) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiError(code = "empty_order", message = "An order needs at least one line"),
                        )
                        return@post
                    }
                    val receipt = OrderReceipt(
                        orderId = receipts.size + 1,
                        customerId = order.customerId,
                        totalCents = order.lines.sumOf { it.unitPriceCents * it.quantity },
                        itemCount = order.lines.sumOf { it.quantity },
                        giftNote = order.giftNote,
                    )
                    receipts.add(receipt)
                    call.respond(HttpStatusCode.Created, receipt)
                }
                get("/orders") {
                    call.respond(receipts.toList())
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        storefront = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(apiJson)
            }
        }
        partner = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        storefront.close()
        partner.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `storefront places orders and lists receipts as typed objects`() = runBlocking {
        val giftOrder = OrderRequest(
            customerId = "c-17",
            lines = listOf(
                OrderLine(isbn = "978-0134685991", quantity = 1, unitPriceCents = 4_599L),
                OrderLine(isbn = "978-1617293290", quantity = 2, unitPriceCents = 3_950L),
            ),
            giftNote = "Happy birthday!",
        )
        val created: HttpResponse = storefront.post(url("/orders")) {
            contentType(ContentType.Application.Json)
            setBody(giftOrder)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            OrderReceipt(
                orderId = 1,
                customerId = "c-17",
                totalCents = 12_499L,
                itemCount = 3,
                giftNote = "Happy birthday!",
            ),
            created.body<OrderReceipt>(),
        )

        val plainOrder = OrderRequest(
            customerId = "c-42",
            lines = listOf(OrderLine(isbn = "978-1492082798", quantity = 4, unitPriceCents = 2_500L)),
        )
        val secondReceipt: OrderReceipt = storefront.post(url("/orders")) {
            contentType(ContentType.Application.Json)
            setBody(plainOrder)
        }.body()
        assertEquals(2, secondReceipt.orderId)
        assertEquals(null, secondReceipt.giftNote)

        val listed: List<OrderReceipt> = storefront.get(url("/orders")).body()
        assertEquals(listOf("c-17", "c-42"), listed.map { it.customerId })
        assertEquals(listOf(12_499L, 10_000L), listed.map { it.totalCents })
        assertEquals(receipts, listed)
    }

    @Test
    fun `partner posts loose JSON and reads the receipt wire format`() = runBlocking {
        val handWritten: String = """
            {
              "customer_id": "partner-9",
              "lines": [
                {"isbn": "978-0596007126", "quantity": 3, "unit_price_cents": 1999, "shelf": "B4"}
              ],
              "channel": "affiliate",
              "gift_note": null
            }
        """.trimIndent()
        val created: HttpResponse = partner.post(url("/orders")) {
            contentType(ContentType.Application.Json)
            setBody(handWritten)
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val wire: JsonObject = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        assertEquals(
            setOf("order_id", "customer_id", "total_cents", "item_count", "currency"),
            wire.keys,
        )
        assertFalse(wire.containsKey("gift_note"))
        assertEquals("partner-9", wire.getValue("customer_id").jsonPrimitive.content)
        assertEquals(5_997, wire.getValue("total_cents").jsonPrimitive.int)
        assertEquals("EUR", wire.getValue("currency").jsonPrimitive.content)

        val rejected: HttpResponse = partner.post(url("/orders")) {
            contentType(ContentType.Application.Json)
            setBody("""{"customer_id": "partner-9", "lines": []}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, rejected.status)
        assertEquals(
            ApiError(code = "empty_order", message = "An order needs at least one line"),
            apiJson.decodeFromString<ApiError>(rejected.bodyAsText()),
        )

        val listing: JsonArray = Json.parseToJsonElement(partner.get(url("/orders")).bodyAsText()).jsonArray
        assertEquals(1, listing.size)
        assertEquals(1, listing[0].jsonObject.getValue("order_id").jsonPrimitive.int)
    }
}
