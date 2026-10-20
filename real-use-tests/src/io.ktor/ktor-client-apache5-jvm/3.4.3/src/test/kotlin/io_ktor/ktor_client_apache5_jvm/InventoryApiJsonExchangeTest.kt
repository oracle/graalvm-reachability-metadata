/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * JSON request and response exchange over the `Apache5` client engine.
 *
 * An inventory client built on the Apache HttpClient 5 engine, with explicit engine timeouts
 * and kotlinx JSON content negotiation, creates, lists, updates and deletes stock items on an
 * embedded CIO server. Every call goes through the engine's request producer and response
 * consumer, and the application observes decoded objects, statuses and a typed failure for a
 * missing item.
 */
class InventoryApiJsonExchangeTest {

    @Serializable
    data class NewItem(val sku: String, val name: String, val quantity: Int)

    @Serializable
    data class StockItem(val id: Int, val sku: String, val name: String, val quantity: Int)

    @Serializable
    data class QuantityChange(val quantity: Int)

    @Serializable
    data class ApiError(val code: String, val message: String)

    /** The server-side store the embedded inventory service keeps its items in. */
    class InventoryStore {
        private val items: MutableMap<Int, StockItem> = ConcurrentHashMap()
        private val nextId: AtomicInteger = AtomicInteger(1)

        fun add(item: NewItem): StockItem {
            val stored = StockItem(nextId.getAndIncrement(), item.sku, item.name, item.quantity)
            items[stored.id] = stored
            return stored
        }

        fun list(minQuantity: Int): List<StockItem> =
            items.values.filter { it.quantity >= minQuantity }.sortedBy { it.id }

        fun update(id: Int, quantity: Int): StockItem? =
            items.computeIfPresent(id) { _, current -> current.copy(quantity = quantity) }

        fun remove(id: Int): Boolean = items.remove(id) != null
    }

    private val store: InventoryStore = InventoryStore()
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                route("/api/items") {
                    get {
                        val minQuantity: Int = call.request.queryParameters["minQuantity"]?.toInt() ?: 0
                        call.respond(store.list(minQuantity))
                    }
                    post {
                        call.respond(HttpStatusCode.Created, store.add(call.receive<NewItem>()))
                    }
                    put("/{id}") {
                        val id: Int = call.parameters["id"]!!.toInt()
                        val updated: StockItem? = store.update(id, call.receive<QuantityChange>().quantity)
                        if (updated == null) {
                            call.respond(HttpStatusCode.NotFound, ApiError("ITEM_NOT_FOUND", "No item $id"))
                        } else {
                            call.respond(updated)
                        }
                    }
                    delete("/{id}") {
                        val removed: Boolean = store.remove(call.parameters["id"]!!.toInt())
                        call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun inventoryClient(): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        engine {
            connectTimeout = 5_000
            socketTimeout = 10_000
            connectionRequestTimeout = 5_000
        }
        install(ClientContentNegotiation) { json() }
    }

    private fun itemsUrl(path: String = ""): String = "http://127.0.0.1:$port/api/items$path"

    @Test
    fun inventoryLifecycleRoundTripsJsonThroughApache5Engine() {
        inventoryClient().use { client ->
            runBlocking {
                val created: HttpResponse = client.post(itemsUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(NewItem("PEN-01", "Fountain pen", 12))
                }
                assertEquals(HttpStatusCode.Created, created.status)
                assertTrue(created.contentType()!!.match(ContentType.Application.Json))
                val pen: StockItem = created.body()
                assertEquals(StockItem(1, "PEN-01", "Fountain pen", 12), pen)

                val ink: StockItem = client.post(itemsUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(NewItem("INK-02", "Blue ink, 50 ml", 3))
                }.body()
                assertEquals(2, ink.id)

                val allItems: List<StockItem> = client.get(itemsUrl()).body()
                assertEquals(listOf(pen, ink), allItems)

                val wellStocked: List<StockItem> = client.get(itemsUrl()) {
                    parameter("minQuantity", 10)
                }.body()
                assertEquals(listOf(pen), wellStocked)

                val restocked: StockItem = client.put(itemsUrl("/${ink.id}")) {
                    contentType(ContentType.Application.Json)
                    setBody(QuantityChange(40))
                }.body()
                assertEquals(ink.copy(quantity = 40), restocked)

                val missing: ClientRequestException = assertThrows {
                    runBlocking {
                        client.put(itemsUrl("/99")) {
                            contentType(ContentType.Application.Json)
                            setBody(QuantityChange(1))
                        }
                    }
                }
                assertEquals(HttpStatusCode.NotFound, missing.response.status)
                assertEquals(ApiError("ITEM_NOT_FOUND", "No item 99"), missing.response.body<ApiError>())

                val deleted: HttpResponse = client.delete(itemsUrl("/${pen.id}"))
                assertEquals(HttpStatusCode.NoContent, deleted.status)

                val remaining: List<StockItem> = client.get(itemsUrl()).body()
                assertEquals(listOf(restocked), remaining)
            }
        }
    }
}
