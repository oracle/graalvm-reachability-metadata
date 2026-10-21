/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

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
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Exercises HTTP content negotiation backed by a shared `Json` instance, with Ktor's
 * kotlinx-json converter turning request and response bodies into `@Serializable` types.
 *
 * An inventory service on a CIO server accepts stock items as JSON, ignores fields that newer
 * clients send, rejects malformed bodies with a serialized error, and lists stock as a JSON
 * array that a CIO client reads back as typed objects and as raw text.
 */
@OptIn(ExperimentalSerializationApi::class)
public class InventoryApiContentNegotiationTest {

    @Serializable
    public data class NewStockItem(
        val sku: String,
        val name: String,
        val quantity: Int,
        @SerialName("unit_price_cents") val unitPriceCents: Long,
        val location: String? = null,
    )

    @Serializable
    public data class StockItem(
        val id: Int,
        val sku: String,
        val name: String,
        val quantity: Int,
        @SerialName("unit_price_cents") val unitPriceCents: Long,
        val location: String? = null,
    )

    @Serializable
    public data class StockReport(
        val items: List<StockItem>,
        @SerialName("total_value_cents") val totalValueCents: Long,
    )

    @Serializable
    public data class ApiError(val code: String, val status: Int)

    /** In-memory stock store shared by the request handlers. */
    public class Inventory {
        private val nextId: AtomicInteger = AtomicInteger(1)
        private val items: MutableMap<Int, StockItem> = ConcurrentHashMap()

        public fun add(item: NewStockItem): StockItem {
            val stored = StockItem(
                id = nextId.getAndIncrement(),
                sku = item.sku,
                name = item.name,
                quantity = item.quantity,
                unitPriceCents = item.unitPriceCents,
                location = item.location,
            )
            items[stored.id] = stored
            return stored
        }

        public fun report(): StockReport {
            val sorted: List<StockItem> = items.values.sortedBy { it.id }
            return StockReport(sorted, sorted.sumOf { it.quantity * it.unitPriceCents })
        }
    }

    private val appJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun startServer(inventory: Inventory): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(appJson)
            }
            routing {
                post("/stock") {
                    val item: NewStockItem = try {
                        call.receive<NewStockItem>()
                    } catch (e: BadRequestException) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_body", 400))
                        return@post
                    }
                    call.respond(HttpStatusCode.Created, inventory.add(item))
                }
                get("/stock") {
                    call.respond(inventory.report())
                }
                get("/stock/items") {
                    call.respond(inventory.report().items)
                }
            }
        }.start(wait = false)

    private fun newClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientContentNegotiation) {
            json(appJson)
        }
        expectSuccess = false
    }

    @Test
    fun clientAndServerExchangeSerializableBodiesThroughTheSharedJson(): Unit = runBlocking {
        val inventory = Inventory()
        val server = startServer(inventory)
        val client: HttpClient = newClient()
        try {
            val port: Int = server.engine.resolvedConnectors().first().port
            val base = "http://127.0.0.1:$port"

            val created: HttpResponse = client.post("$base/stock") {
                contentType(ContentType.Application.Json)
                setBody(NewStockItem("BOLT-M8", "Hex bolt M8", 120, 15, "A-12"))
            }
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(
                StockItem(1, "BOLT-M8", "Hex bolt M8", 120, 15, "A-12"),
                created.body<StockItem>(),
            )

            val noLocation: StockItem = client.post("$base/stock") {
                contentType(ContentType.Application.Json)
                setBody(NewStockItem("WASHER-8", "Flat washer 8mm", 300, 2))
            }.body()
            assertEquals(2, noLocation.id)
            assertEquals(null, noLocation.location)

            val report: StockReport = client.get("$base/stock").body()
            assertEquals(listOf("BOLT-M8", "WASHER-8"), report.items.map { it.sku })
            assertEquals(120L * 15 + 300L * 2, report.totalValueCents)

            val items: List<StockItem> = client.get("$base/stock/items").body()
            assertEquals(report.items, items)
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }

    @Test
    fun serverIgnoresUnknownFieldsAndOmitsNullsOnTheWire(): Unit = runBlocking {
        val inventory = Inventory()
        val server = startServer(inventory)
        val client: HttpClient = newClient()
        try {
            val port: Int = server.engine.resolvedConnectors().first().port
            val base = "http://127.0.0.1:$port"

            val newerClientBody = """
                {"sku":"NUT-M8","name":"Hex nut M8","quantity":75,"unit_price_cents":4,
                 "supplier":{"id":"S-9","country":"DE"},"tags":["metric","zinc"]}
            """.trimIndent()
            val created: HttpResponse = client.post("$base/stock") {
                contentType(ContentType.Application.Json)
                setBody(newerClientBody)
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val rawCreated: String = created.bodyAsText()
            assertFalse(rawCreated.contains("supplier"), rawCreated)
            assertFalse(rawCreated.contains("location"), rawCreated)
            assertEquals(StockItem(1, "NUT-M8", "Hex nut M8", 75, 4), appJson.decodeFromString<StockItem>(rawCreated))

            val rawReport: String = client.get("$base/stock").bodyAsText()
            val reportTree: JsonObject = appJson.parseToJsonElement(rawReport).jsonObject
            assertEquals(300, reportTree.getValue("total_value_cents").jsonPrimitive.int)
            val firstItem: JsonObject = reportTree.getValue("items").jsonArray[0].jsonObject
            assertEquals(setOf("id", "sku", "name", "quantity", "unit_price_cents"), firstItem.keys)
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }

    @Test
    fun malformedBodyIsRejectedWithASerializedError(): Unit = runBlocking {
        val inventory = Inventory()
        val server = startServer(inventory)
        val client: HttpClient = newClient()
        try {
            val port: Int = server.engine.resolvedConnectors().first().port
            val base = "http://127.0.0.1:$port"

            val missingField: HttpResponse = client.post("$base/stock") {
                contentType(ContentType.Application.Json)
                setBody("""{"sku":"PIN-3","name":"Split pin"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, missingField.status)
            assertEquals(ApiError("invalid_body", 400), missingField.body<ApiError>())

            val wrongType: HttpResponse = client.post("$base/stock") {
                contentType(ContentType.Application.Json)
                setBody("""{"sku":"PIN-3","name":"Split pin","quantity":"many","unit_price_cents":1}""")
            }
            assertEquals(HttpStatusCode.BadRequest, wrongType.status)
            assertEquals("invalid_body", wrongType.body<ApiError>().code)

            val report: StockReport = client.get("$base/stock").body()
            assertTrue(report.items.isEmpty())
            assertEquals(0L, report.totalValueCents)
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }
}
