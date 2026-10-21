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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts request and response bodies through the kotlinx-io backed converter that `jsonIo()`
 * registers with server and client content negotiation.
 *
 * A warehouse inventory service stores bins of stock keyed by location; handheld scanners
 * replace and read bins as typed objects through the kotlinx-io converter, an auditing tool
 * reads the raw JSON the converter writes, and a scanner with corrupted firmware sends a
 * malformed body that the service reports back as a structured bad request.
 */
@OptIn(ExperimentalSerializationApi::class)
public class WarehouseInventoryJsonIoTest {

    /** How a stocked item has to be stored. */
    @Serializable
    private enum class Handling {
        @SerialName("ambient") AMBIENT,
        @SerialName("chilled") CHILLED,
        @SerialName("fragile") FRAGILE,
    }

    /** One stock keeping unit held in a bin. */
    @Serializable
    private data class StockItem(
        val sku: String,
        val quantity: Int,
        val handling: Handling = Handling.AMBIENT,
    )

    /** A storage bin and everything in it. */
    @Serializable
    private data class Bin(
        val location: String,
        val items: List<StockItem>,
        val attributes: Map<String, String> = emptyMap(),
        @SerialName("last_counted_by") val lastCountedBy: String? = null,
    )

    /** The service's answer to a bin replacement. */
    @Serializable
    private data class BinUpdate(
        val location: String,
        @SerialName("total_units") val totalUnits: Int,
        val created: Boolean,
    )

    /** A rejected request. */
    @Serializable
    private data class Problem(val status: Int, val title: String)

    /** The wire format scanners and the service share. */
    private val inventoryJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val bins: MutableMap<String, Bin> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var scanner: HttpClient
    private lateinit var auditor: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                jsonIo(inventoryJson)
            }
            install(StatusPages) {
                exception<BadRequestException> { call, _ ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Problem(status = 400, title = "Malformed bin document"),
                    )
                }
            }
            routing {
                put("/bins/{location}") {
                    val location: String = call.parameters["location"]!!
                    val bin: Bin = call.receive()
                    val stored: Bin = bin.copy(location = location)
                    val previous: Bin? = bins.put(location, stored)
                    call.respond(
                        if (previous == null) HttpStatusCode.Created else HttpStatusCode.OK,
                        BinUpdate(location, stored.items.sumOf { it.quantity }, created = previous == null),
                    )
                }
                get("/bins/{location}") {
                    val bin: Bin? = bins[call.parameters["location"]]
                    if (bin == null) {
                        call.respond(HttpStatusCode.NotFound, Problem(status = 404, title = "Unknown bin"))
                    } else {
                        call.respond(bin)
                    }
                }
                get("/bins") {
                    call.respond(bins.values.sortedBy { it.location })
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        scanner = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                jsonIo(inventoryJson)
            }
        }
        auditor = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        scanner.close()
        auditor.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `scanners replace and read bins as typed objects`() = runBlocking {
        val chilled = Bin(
            location = "ignored",
            items = listOf(
                StockItem(sku = "MILK-1L", quantity = 24, handling = Handling.CHILLED),
                StockItem(sku = "YOGURT-6", quantity = 12, handling = Handling.CHILLED),
            ),
            attributes = mapOf("zone" to "cold-room", "max_kg" to "300"),
            lastCountedBy = "scanner-07",
        )
        val created: HttpResponse = scanner.put(url("/bins/C-01-03")) {
            contentType(ContentType.Application.Json)
            setBody(chilled)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.contentType()!!.match(ContentType.Application.Json))
        assertEquals(BinUpdate("C-01-03", totalUnits = 36, created = true), created.body<BinUpdate>())

        val recount: BinUpdate = scanner.put(url("/bins/C-01-03")) {
            contentType(ContentType.Application.Json)
            setBody(chilled.copy(items = chilled.items.take(1)))
        }.body()
        assertEquals(BinUpdate("C-01-03", totalUnits = 24, created = false), recount)

        scanner.put(url("/bins/A-02-01")) {
            contentType(ContentType.Application.Json)
            setBody(Bin(location = "A-02-01", items = listOf(StockItem("GLASS-SET", 5, Handling.FRAGILE))))
        }

        val fetched: Bin = scanner.get(url("/bins/C-01-03")).body()
        assertEquals(listOf(StockItem("MILK-1L", 24, Handling.CHILLED)), fetched.items)
        assertEquals("cold-room", fetched.attributes.getValue("zone"))
        assertEquals("scanner-07", fetched.lastCountedBy)

        val all: List<Bin> = scanner.get(url("/bins")).body()
        assertEquals(listOf("A-02-01", "C-01-03"), all.map { it.location })
        assertEquals(Handling.FRAGILE, all[0].items.single().handling)
        assertEquals(null, all[0].lastCountedBy)

        val missing: HttpResponse = scanner.get(url("/bins/Z-99-99"))
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(Problem(status = 404, title = "Unknown bin"), missing.body<Problem>())
    }

    @Test
    fun `auditors read the raw documents and malformed uploads are rejected`() = runBlocking {
        val handWritten: String = """
            {
              "location": "B-07-02",
              "items": [
                {"sku": "FLOUR-5KG", "quantity": 40, "pallet": "P-118"},
                {"sku": "EGGS-30", "quantity": 8, "handling": "fragile"}
              ],
              "last_counted_by": null,
              "temperature": 18.5
            }
        """.trimIndent()
        val created: HttpResponse = auditor.put(url("/bins/B-07-02")) {
            contentType(ContentType.Application.Json)
            setBody(handWritten)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val update: JsonObject = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        assertEquals(48, update.getValue("total_units").jsonPrimitive.int)

        val raw: HttpResponse = auditor.get(url("/bins/B-07-02"))
        assertTrue(raw.contentType()!!.match(ContentType.Application.Json))
        val wire: JsonObject = Json.parseToJsonElement(raw.bodyAsText()).jsonObject
        assertEquals(setOf("location", "items"), wire.keys)
        val items = wire.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(setOf("sku", "quantity"), items[0].keys)
        assertEquals("fragile", items[1].getValue("handling").jsonPrimitive.content)

        val corrupted: HttpResponse = auditor.put(url("/bins/B-07-03")) {
            contentType(ContentType.Application.Json)
            setBody("""{"location": "B-07-03", "items": [{"sku": "SALT-1KG", "quantity": "many"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, corrupted.status)
        assertEquals(
            Problem(status = 400, title = "Malformed bin document"),
            inventoryJson.decodeFromString<Problem>(corrupted.bodyAsText()),
        )
        assertEquals(setOf("B-07-02"), bins.keys)
    }
}
