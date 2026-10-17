/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JSON content negotiation through the client ContentNegotiation plugin with the kotlinx.serialization
 * converter, as a warehouse application reads its product catalog, registers products, and posts
 * polymorphic stock movements to an inventory service as typed Kotlin objects.
 */
public class WarehouseInventoryJsonNegotiationTest {

    @Serializable
    data class Dimensions(val widthMm: Int, val heightMm: Int, val depthMm: Int)

    @Serializable
    data class Product(
        val sku: String,
        val name: String,
        val quantity: Int,
        val tags: List<String> = emptyList(),
        val dimensions: Dimensions? = null,
    )

    @Serializable
    data class NewProduct(val sku: String, val name: String, val dimensions: Dimensions)

    /** A stock movement the warehouse records; the JSON carries its kind in the `kind` field. */
    @Serializable
    sealed class StockMovement {
        abstract val sku: String

        @Serializable
        @SerialName("receipt")
        data class Receipt(override val sku: String, val units: Int, val supplier: String) : StockMovement()

        @Serializable
        @SerialName("pick")
        data class Pick(override val sku: String, val units: Int, val orderId: String) : StockMovement()
    }

    @Serializable
    data class MovementBatch(val movements: List<StockMovement>)

    @Serializable
    data class MovementResult(val accepted: Int, val balances: Map<String, Int>)

    /** What the inventory service saw of one incoming request. */
    data class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /** The inventory service; it answers with fixed JSON so the client's decoding is what is checked. */
    class InventoryService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/products") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(
                        """
                        [
                          {"sku":"BOLT-M8","name":"Hex bolt M8","quantity":1200,"tags":["fastener","steel"],
                           "dimensions":{"widthMm":13,"heightMm":40,"depthMm":13},"supplierNote":"bulk only"},
                          {"sku":"TAPE-50","name":"Packing tape","quantity":85}
                        ]
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                post("/products") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText(
                        """{"sku":"CRATE-L","name":"Large crate","quantity":0,"dimensions":{"widthMm":800,"heightMm":600,"depthMm":400}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                put("/movements") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText(
                        """{"accepted":2,"balances":{"BOLT-M8":1700,"TAPE-50":73}}""",
                        ContentType.Application.Json.withParameter("charset", "utf-8"),
                    )
                }
                get("/health") {
                    call.respondText("inventory up", ContentType.Text.Plain)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, contentType: String?, body: String) {
            received += ReceivedRequest(path, accept, contentType, body)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The JSON format the warehouse shares with the inventory service. */
    private val inventoryJson: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
        encodeDefaults = false
    }

    private fun inventoryClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(inventoryJson)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun theCatalogIsDecodedIntoTypedProducts(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val catalog: List<Product> = client.get("products").body()

                assertThat(catalog).containsExactly(
                    Product("BOLT-M8", "Hex bolt M8", 1200, listOf("fastener", "steel"), Dimensions(13, 40, 13)),
                    Product("TAPE-50", "Packing tape", 85),
                )
                assertThat(service.received.single().accept).contains("application/json")
            }
        }
    }

    @Test
    fun aNewProductIsEncodedAsJsonAndTheCreatedOneDecoded(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.post("products") {
                    contentType(ContentType.Application.Json)
                    setBody(NewProduct("CRATE-L", "Large crate", Dimensions(800, 600, 400)))
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val created: Product = response.body()
                assertThat(created).isEqualTo(Product("CRATE-L", "Large crate", 0, dimensions = Dimensions(800, 600, 400)))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(inventoryJson.decodeFromString(NewProduct.serializer(), seen.body))
                    .isEqualTo(NewProduct("CRATE-L", "Large crate", Dimensions(800, 600, 400)))
                assertThat(seen.body).contains("\"widthMm\":800")
            }
        }
    }

    @Test
    fun polymorphicMovementsCarryTheirKindOnTheWire(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val batch = MovementBatch(
                    listOf(
                        StockMovement.Receipt("BOLT-M8", 500, "Fasteners Ltd"),
                        StockMovement.Pick("TAPE-50", 12, "ORD-7781"),
                    ),
                )
                val result: MovementResult = client.put("movements") {
                    contentType(ContentType.Application.Json)
                    setBody(batch)
                }.body()

                assertThat(result.accepted).isEqualTo(2)
                assertThat(result.balances).containsEntry("BOLT-M8", 1700).containsEntry("TAPE-50", 73)

                val body: String = service.received.single().body
                assertThat(body).contains("\"kind\":\"receipt\"").contains("\"kind\":\"pick\"")
                val echoed: MovementBatch = inventoryJson.decodeFromString(MovementBatch.serializer(), body)
                assertThat(echoed).isEqualTo(batch)
                assertThat(echoed.movements.map { movement: StockMovement -> movement.sku })
                    .containsExactly("BOLT-M8", "TAPE-50")
            }
        }
    }

    @Test
    fun aPlainTextReplyBypassesTheJsonConverter(): Unit = runBlocking {
        InventoryService().use { service: InventoryService ->
            inventoryClient(service.baseUrl).use { client: HttpClient ->
                val status: String = client.get("health").body()
                assertThat(status).isEqualTo("inventory up")
            }
        }
    }
}
