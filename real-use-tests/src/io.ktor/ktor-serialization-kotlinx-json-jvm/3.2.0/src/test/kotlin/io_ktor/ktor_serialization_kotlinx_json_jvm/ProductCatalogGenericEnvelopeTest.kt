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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Resolves serializers for parameterized body types, such as generic envelopes, pages, maps,
 * pairs and value classes, from the full type information `json()` receives on each call.
 *
 * A product catalog API wraps every answer in a generic `Envelope<T>`, pages its listings as
 * `Page<T>`, keys its stock report by a value-class SKU and accepts a cart as an envelope of
 * SKUs; a typed shop client reads every shape back through the same converter.
 */
public class ProductCatalogGenericEnvelopeTest {

    /** A stock keeping unit, carried on the wire as its bare code. */
    @JvmInline
    @Serializable
    private value class Sku(val code: String)

    /** One catalog entry. */
    @Serializable
    private data class Product(
        val sku: Sku,
        val name: String,
        @SerialName("price_cents") val priceCents: Long,
        val tags: Set<String> = emptySet(),
    )

    /** One customer review of a product. */
    @Serializable
    private data class Review(val sku: Sku, val stars: Int, val text: String)

    /** One page of a listing. */
    @Serializable
    private data class Page<T>(
        val items: List<T>,
        val page: Int,
        val total: Int,
        val next: Int? = null,
    )

    /** The wrapper every catalog answer travels in. */
    @Serializable
    private data class Envelope<T>(
        val data: T,
        val meta: Map<String, String> = emptyMap(),
    )

    private val catalog: List<Product> = listOf(
        Product(Sku("KB-101"), "Mechanical keyboard", 8_900L, setOf("input", "usb")),
        Product(Sku("MS-202"), "Wireless mouse", 2_900L, setOf("input")),
        Product(Sku("MN-303"), "27 inch monitor", 24_900L),
        Product(Sku("HD-404"), "USB hub", 1_900L, setOf("usb")),
        Product(Sku("CB-505"), "Braided cable", 900L),
    )

    private val reviews: List<Review> = listOf(
        Review(Sku("KB-101"), 5, "Clicky and solid"),
        Review(Sku("MN-303"), 4, "Sharp picture"),
    )

    private val stock: Map<Sku, Int> = mapOf(Sku("KB-101") to 12, Sku("MS-202") to 0, Sku("MN-303") to 3)

    private val pageSize: Int = 2

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var shop: HttpClient
    private lateinit var raw: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                get("/products") {
                    val page: Int = call.request.queryParameters["page"]?.toInt() ?: 1
                    val items: List<Product> = catalog.drop((page - 1) * pageSize).take(pageSize)
                    val next: Int? = if (page * pageSize < catalog.size) page + 1 else null
                    val body: Envelope<Page<Product>> = Envelope(
                        data = Page(items = items, page = page, total = catalog.size, next = next),
                        meta = mapOf("currency" to "EUR"),
                    )
                    call.respond(body)
                }
                get("/products/{sku}") {
                    val sku = Sku(call.parameters["sku"]!!)
                    val body: Envelope<Product?> = Envelope(data = catalog.firstOrNull { it.sku == sku })
                    call.respond(body)
                }
                get("/reviews") {
                    call.respond(Page(items = reviews, page = 1, total = reviews.size))
                }
                get("/stock") {
                    call.respond(stock)
                }
                post("/cart") {
                    val cart: Envelope<List<Sku>> = call.receive()
                    val priced: List<Pair<Sku, Long>> = cart.data.map { sku ->
                        sku to catalog.first { it.sku == sku }.priceCents
                    }
                    val total: Pair<Int, Long> = priced.size to priced.sumOf { it.second }
                    call.respond(HttpStatusCode.Created, Envelope(data = total, meta = cart.meta))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        shop = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
        }
        raw = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        shop.close()
        raw.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `shop walks the paged catalog through nested generic envelopes`() = runBlocking {
        val seen: MutableList<Product> = mutableListOf()
        var page: Int? = 1
        var pages: Int = 0
        while (page != null) {
            val answer: Envelope<Page<Product>> = shop.get(url("/products?page=$page")).body()
            assertEquals(mapOf("currency" to "EUR"), answer.meta)
            assertEquals(catalog.size, answer.data.total)
            assertEquals(page, answer.data.page)
            seen += answer.data.items
            page = answer.data.next
            pages++
        }
        assertEquals(3, pages)
        assertEquals(catalog, seen)

        val keyboard: Envelope<Product?> = shop.get(url("/products/KB-101")).body()
        assertEquals(catalog[0], keyboard.data)
        val missing: Envelope<Product?> = shop.get(url("/products/XX-000")).body()
        assertNull(missing.data)

        val reviewPage: Page<Review> = shop.get(url("/reviews")).body()
        assertEquals(reviews, reviewPage.items)
        assertNull(reviewPage.next)

        val report: Map<Sku, Int> = shop.get(url("/stock")).body()
        assertEquals(stock, report)
    }

    @Test
    fun `shop posts a generic cart envelope and reads a priced pair back`() = runBlocking {
        val cart: Envelope<List<Sku>> = Envelope(
            data = listOf(Sku("KB-101"), Sku("HD-404"), Sku("CB-505")),
            meta = mapOf("coupon" to "WELCOME"),
        )
        val created: HttpResponse = shop.post(url("/cart")) {
            contentType(ContentType.Application.Json)
            setBody(cart)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val priced: Envelope<Pair<Int, Long>> = created.body()
        assertEquals(3 to 11_700L, priced.data)
        assertEquals(mapOf("coupon" to "WELCOME"), priced.meta)
    }

    @Test
    fun `generic bodies keep a flat wire format for other clients`() = runBlocking {
        val listing: JsonObject = Json.parseToJsonElement(raw.get(url("/products?page=3")).bodyAsText()).jsonObject
        val data: JsonObject = listing.getValue("data").jsonObject
        assertEquals(setOf("items", "page", "total", "next"), data.keys)
        assertEquals(JsonNull, data.getValue("next"))
        val onlyItem: JsonObject = data.getValue("items").jsonArray.single().jsonObject
        assertEquals("CB-505", onlyItem.getValue("sku").jsonPrimitive.content)
        assertEquals(900, onlyItem.getValue("price_cents").jsonPrimitive.int)

        val stockWire: JsonObject = Json.parseToJsonElement(raw.get(url("/stock")).bodyAsText()).jsonObject
        assertEquals(setOf("KB-101", "MS-202", "MN-303"), stockWire.keys)
        assertEquals(3, stockWire.getValue("MN-303").jsonPrimitive.int)

        val handWritten: HttpResponse = raw.post(url("/cart")) {
            contentType(ContentType.Application.Json)
            setBody("""{"data": ["MS-202", "MN-303"]}""")
        }
        assertEquals(HttpStatusCode.Created, handWritten.status)
        val pair: JsonObject = Json.parseToJsonElement(handWritten.bodyAsText()).jsonObject
            .getValue("data").jsonObject
        assertEquals(2, pair.getValue("first").jsonPrimitive.int)
        assertEquals(27_800, pair.getValue("second").jsonPrimitive.int)
    }
}
