/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Exception handlers chosen by the thrown type's class hierarchy, where a CIO
 * inventory service maps a sealed domain exception family to JSON error bodies.
 *
 * `ItemNotFound` has its own handler, every other `InventoryException` falls back
 * to the family handler, and anything else reaches the `Throwable` safety net.
 */
public class InventoryExceptionMappingTest {
    /** The error document every failing inventory call answers with. */
    @Serializable
    data class ApiError(val code: String, val message: String, val handler: String)

    /** A stocked item as the inventory reports it. */
    @Serializable
    data class StockLevel(val sku: String, val quantity: Int)

    /** Every failure the inventory domain raises on purpose. */
    sealed class InventoryException(message: String) : RuntimeException(message) {
        class ItemNotFound(val sku: String) : InventoryException("No item with SKU $sku")

        class OutOfStock(val sku: String, val requested: Int, val available: Int) :
            InventoryException("Requested $requested of $sku but only $available left")

        class WarehouseClosed(val warehouse: String) : InventoryException("Warehouse $warehouse is closed")
    }

    private val stock: MutableMap<String, Int> = mutableMapOf("BOLT-10" to 40, "NUT-6" to 2)

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startInventoryService() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ContentNegotiation) {
                json(ERROR_JSON)
            }
            install(StatusPages) {
                exception<InventoryException.ItemNotFound> { call, cause ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError("item-not-found", cause.message.orEmpty(), "item-not-found"),
                    )
                }
                exception<InventoryException> { call, cause ->
                    val status: HttpStatusCode = when (cause) {
                        is InventoryException.OutOfStock -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.ServiceUnavailable
                    }
                    call.respond(status, ApiError("inventory", cause.message.orEmpty(), "inventory-family"))
                }
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError("internal", cause::class.simpleName.orEmpty(), "safety-net"),
                    )
                }
            }
            routing {
                get("/stock/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    val quantity: Int = stock[sku] ?: throw InventoryException.ItemNotFound(sku)
                    call.respond(StockLevel(sku, quantity))
                }
                post("/reserve/{sku}/{count}") {
                    val sku: String = call.parameters["sku"]!!
                    val count: Int = call.parameters["count"]!!.toInt()
                    val available: Int = stock[sku] ?: throw InventoryException.ItemNotFound(sku)
                    if (count > available) {
                        throw InventoryException.OutOfStock(sku, count, available)
                    }
                    stock[sku] = available - count
                    call.respond(StockLevel(sku, available - count))
                }
                post("/warehouses/{name}/restock") {
                    throw InventoryException.WarehouseClosed(call.parameters["name"]!!)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopInventoryService() {
        client.close()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    @Test
    fun successfulReservationIsNotIntercepted() = runBlocking {
        val response: HttpResponse = client.post("http://$HOST:$port/reserve/BOLT-10/15")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(StockLevel("BOLT-10", 25), ERROR_JSON.decodeFromString<StockLevel>(response.bodyAsText()))
        assertEquals(25, stock["BOLT-10"])
    }

    @Test
    fun mostSpecificHandlerWinsForUnknownItem() = runBlocking {
        val response: HttpResponse = client.get("http://$HOST:$port/stock/WASHER-3")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            ApiError("item-not-found", "No item with SKU WASHER-3", "item-not-found"),
            ERROR_JSON.decodeFromString<ApiError>(response.bodyAsText()),
        )
    }

    @Test
    fun familyHandlerCatchesSubtypesWithoutTheirOwnHandler() = runBlocking {
        val shortage: HttpResponse = client.post("http://$HOST:$port/reserve/NUT-6/5")
        val closed: HttpResponse = client.post("http://$HOST:$port/warehouses/north/restock")

        assertEquals(HttpStatusCode.Conflict, shortage.status)
        assertEquals(
            ApiError("inventory", "Requested 5 of NUT-6 but only 2 left", "inventory-family"),
            ERROR_JSON.decodeFromString<ApiError>(shortage.bodyAsText()),
        )
        assertEquals(2, stock["NUT-6"])
        assertEquals(HttpStatusCode.ServiceUnavailable, closed.status)
        assertEquals(
            ApiError("inventory", "Warehouse north is closed", "inventory-family"),
            ERROR_JSON.decodeFromString<ApiError>(closed.bodyAsText()),
        )
    }

    @Test
    fun unrelatedFailureReachesTheSafetyNet() = runBlocking {
        val response: HttpResponse = client.post("http://$HOST:$port/reserve/BOLT-10/plenty")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(
            ApiError("internal", "NumberFormatException", "safety-net"),
            ERROR_JSON.decodeFromString<ApiError>(response.bodyAsText()),
        )
        assertEquals(40, stock["BOLT-10"])
    }

    private companion object {
        const val HOST: String = "127.0.0.1"

        val ERROR_JSON: Json = Json { encodeDefaults = true }
    }
}
