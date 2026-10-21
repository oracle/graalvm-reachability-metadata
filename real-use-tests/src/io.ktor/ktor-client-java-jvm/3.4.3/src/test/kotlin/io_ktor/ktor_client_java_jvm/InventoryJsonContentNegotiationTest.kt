/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Typed JSON request and response bodies through the client ContentNegotiation plugin, as a
 * warehouse application calls an inventory REST service over the Java engine.
 */
public class InventoryJsonContentNegotiationTest {

    @Serializable
    enum class Category { HARDWARE, TOOLS, GARDEN }

    @Serializable
    data class NewItem(
        val sku: String,
        val name: String,
        val category: Category,
        val quantity: Int,
        val tags: List<String> = emptyList(),
    )

    @Serializable
    data class Item(
        val id: Long,
        val sku: String,
        val name: String,
        val category: Category,
        val quantity: Int,
        val tags: List<String>,
        val note: String? = null,
    )

    @Serializable
    data class StockAdjustment(val delta: Int, val reason: String)

    @Serializable
    data class ApiError(val code: String, val message: String)

    /** The inventory service the application talks to, backed by an in-memory store. */
    class InventoryService : AutoCloseable {
        private val catalog: ConcurrentHashMap<Long, Item> = ConcurrentHashMap()
        private val ids: AtomicLong = AtomicLong()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                get("/items") {
                    call.respond(catalog.values.sortedBy { item: Item -> item.id })
                }
                post("/items") {
                    val request: NewItem = call.receive()
                    val item = Item(
                        id = ids.incrementAndGet(),
                        sku = request.sku,
                        name = request.name,
                        category = request.category,
                        quantity = request.quantity,
                        tags = request.tags,
                    )
                    catalog[item.id] = item
                    call.respond(HttpStatusCode.Created, item)
                }
                get("/items/{id}") {
                    val id: Long? = call.parameters["id"]?.toLongOrNull()
                    val item: Item? = id?.let { key: Long -> catalog[key] }
                    if (item == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("ITEM_NOT_FOUND", "No item ${call.parameters["id"]}"))
                    } else {
                        call.respond(item)
                    }
                }
                put("/items/{id}/stock") {
                    val id: Long = call.parameters["id"]!!.toLong()
                    val adjustment: StockAdjustment = call.receive()
                    val current: Item? = catalog[id]
                    val remaining: Int = (current?.quantity ?: 0) + adjustment.delta
                    when {
                        current == null -> call.respond(HttpStatusCode.NotFound, ApiError("ITEM_NOT_FOUND", "No item $id"))
                        remaining < 0 -> call.respond(
                            HttpStatusCode.Conflict,
                            ApiError("INSUFFICIENT_STOCK", "Only ${current.quantity} of ${current.sku} in stock"),
                        )
                        else -> {
                            val updated: Item = current.copy(quantity = remaining, note = adjustment.reason)
                            catalog[id] = updated
                            call.respond(updated)
                        }
                    }
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun inventoryClient(baseUrl: String): HttpClient = HttpClient(Java) {
        expectSuccess = false
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            url("$baseUrl/")
        }
    }

    @Test
    fun createsItemsAndReadsTheCatalogBackAsTypedObjects(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val created: HttpResponse = client.post("items") {
                    contentType(ContentType.Application.Json)
                    setBody(NewItem("HAM-01", "Claw hammer", Category.TOOLS, 12, listOf("steel", "16oz")))
                }
                assertThat(created.status).isEqualTo(HttpStatusCode.Created)
                assertThat(created.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.Json)
                val hammer: Item = created.body()
                assertThat(hammer.id).isEqualTo(1L)
                assertThat(hammer.tags).containsExactly("steel", "16oz")
                assertThat(hammer.note).isNull()

                client.post("items") {
                    contentType(ContentType.Application.Json)
                    setBody(NewItem("SEED-9", "Tomato seeds", Category.GARDEN, 40))
                }

                val catalog: List<Item> = client.get("items").body()
                assertThat(catalog.map { it.sku }).containsExactly("HAM-01", "SEED-9")
                assertThat(catalog.map { it.category }).containsExactly(Category.TOOLS, Category.GARDEN)
                assertThat(catalog.last().tags).isEmpty()

                val fetched: Item = client.get("items/1").body()
                assertThat(fetched).isEqualTo(hammer)
            }
        }
    }

    @Test
    fun mapsServiceErrorsToTypedErrorBodies(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val missing: HttpResponse = client.get("items/404")
                assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
                assertThat(missing.body<ApiError>()).isEqualTo(ApiError("ITEM_NOT_FOUND", "No item 404"))

                val drill: Item = client.post("items") {
                    contentType(ContentType.Application.Json)
                    setBody(NewItem("DRL-7", "Cordless drill", Category.HARDWARE, 3))
                }.body()

                val restocked: HttpResponse = client.put("items/${drill.id}/stock") {
                    contentType(ContentType.Application.Json)
                    setBody(StockAdjustment(delta = 5, reason = "supplier delivery"))
                }
                assertThat(restocked.status).isEqualTo(HttpStatusCode.OK)
                val afterDelivery: Item = restocked.body()
                assertThat(afterDelivery.quantity).isEqualTo(8)
                assertThat(afterDelivery.note).isEqualTo("supplier delivery")

                val oversold: HttpResponse = client.put("items/${drill.id}/stock") {
                    contentType(ContentType.Application.Json)
                    setBody(StockAdjustment(delta = -20, reason = "bulk order"))
                }
                assertThat(oversold.status).isEqualTo(HttpStatusCode.Conflict)
                val error: ApiError = oversold.body()
                assertThat(error.code).isEqualTo("INSUFFICIENT_STOCK")
                assertThat(error.message).isEqualTo("Only 8 of DRL-7 in stock")
            }
        }
    }
}
