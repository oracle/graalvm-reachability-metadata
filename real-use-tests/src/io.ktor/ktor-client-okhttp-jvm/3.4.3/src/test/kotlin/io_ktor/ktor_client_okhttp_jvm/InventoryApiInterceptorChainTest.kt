/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * The OkHttp interceptor chain that the OkHttp engine builds from `addInterceptor` and
 * `addNetworkInterceptor`.
 *
 * An inventory client talks JSON to a Ktor CIO service through the OkHttp engine. Its
 * application interceptor stamps the API key and a per-call request id onto every request,
 * as an application's auth layer would; its network interceptor audits what actually goes
 * on the wire. The test checks that the service authorizes the stamped calls, that the
 * network interceptor sees both the stamped headers and the headers OkHttp itself adds
 * below the application layer, and that the JSON bodies round-trip.
 */
class InventoryApiInterceptorChainTest {

    @Serializable
    data class StockItem(val sku: String, val quantity: Int)

    @Serializable
    data class StockReceipt(val sku: String, val total: Int, val requestId: String)

    /** One request as seen by an interceptor: method, path and the headers it carried. */
    data class SeenRequest(val method: String, val path: String, val headers: Map<String, String>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val stock: MutableMap<String, Int> = LinkedHashMap()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                post("/stock") {
                    if (call.request.headers["X-Api-Key"] != API_KEY) {
                        call.respond(HttpStatusCode.Unauthorized, "missing api key")
                        return@post
                    }
                    val item: StockItem = call.receive()
                    val total: Int = synchronized(stock) {
                        val updated: Int = (stock[item.sku] ?: 0) + item.quantity
                        stock[item.sku] = updated
                        updated
                    }
                    val requestId: String = call.request.headers["X-Request-Id"] ?: "none"
                    call.respond(HttpStatusCode.Created, StockReceipt(item.sku, total, requestId))
                }
                get("/stock") {
                    if (call.request.headers["X-Api-Key"] != API_KEY) {
                        call.respond(HttpStatusCode.Unauthorized, "missing api key")
                        return@get
                    }
                    val items: List<StockItem> = synchronized(stock) { stock.map { StockItem(it.key, it.value) } }
                    call.respond(items)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun interceptorsStampAndAuditEveryCallTheEngineMakes() {
        val applicationSeen: MutableList<SeenRequest> = CopyOnWriteArrayList()
        val networkSeen: MutableList<SeenRequest> = CopyOnWriteArrayList()
        val requestCounter = AtomicInteger()

        val stampCredentials = Interceptor { chain: Interceptor.Chain ->
            val original = chain.request()
            applicationSeen += SeenRequest(original.method, original.url.encodedPath, original.headers.toMap())
            val stamped = original.newBuilder()
                .header("X-Api-Key", API_KEY)
                .header("X-Request-Id", "inv-${requestCounter.incrementAndGet()}")
                .build()
            chain.proceed(stamped)
        }
        val auditWire = Interceptor { chain: Interceptor.Chain ->
            val wire = chain.request()
            networkSeen += SeenRequest(wire.method, wire.url.encodedPath, wire.headers.toMap())
            val response: Response = chain.proceed(wire)
            response.newBuilder().header("X-Audited", "true").build()
        }

        val client = HttpClient(OkHttp) {
            engine {
                addInterceptor(stampCredentials)
                addNetworkInterceptor(auditWire)
            }
            install(ClientContentNegotiation) { json() }
        }

        client.use {
            runBlocking {
                val first: StockReceipt = it.post("http://127.0.0.1:$port/stock") {
                    contentType(ContentType.Application.Json)
                    setBody(StockItem("bolt-m6", 40))
                }.body()
                assertEquals(StockReceipt("bolt-m6", 40, "inv-1"), first)

                val createdResponse: HttpResponse = it.post("http://127.0.0.1:$port/stock") {
                    contentType(ContentType.Application.Json)
                    setBody(StockItem("bolt-m6", 15))
                }
                assertEquals(HttpStatusCode.Created, createdResponse.status)
                assertEquals("true", createdResponse.headers["X-Audited"])
                assertEquals(StockReceipt("bolt-m6", 55, "inv-2"), createdResponse.body<StockReceipt>())

                it.post("http://127.0.0.1:$port/stock") {
                    contentType(ContentType.Application.Json)
                    setBody(StockItem("washer-m6", 200))
                }

                val listing: List<StockItem> = it.get("http://127.0.0.1:$port/stock").body()
                assertEquals(listOf(StockItem("bolt-m6", 55), StockItem("washer-m6", 200)), listing)
            }
        }

        assertEquals(4, applicationSeen.size)
        assertEquals(4, networkSeen.size)
        assertEquals(
            listOf("POST /stock", "POST /stock", "POST /stock", "GET /stock"),
            networkSeen.map { "${it.method} ${it.path}" },
        )
        applicationSeen.forEach { seen: SeenRequest ->
            assertNull(seen.header("X-Api-Key"), "the application layer sees the request before stamping")
            assertNull(seen.header("Accept-Encoding"), "OkHttp adds transport headers below the application layer")
        }
        assertEquals(listOf("inv-1", "inv-2", "inv-3", "inv-4"), networkSeen.map { it.header("X-Request-Id") })
        networkSeen.forEach { seen: SeenRequest ->
            assertEquals(API_KEY, seen.header("X-Api-Key"))
            assertEquals("gzip", seen.header("Accept-Encoding"))
            assertEquals("127.0.0.1:$port", seen.header("Host"))
            assertTrue(seen.header("Accept").orEmpty().contains("application/json"), "Accept: ${seen.headers}")
        }
        networkSeen.filter { it.method == "POST" }.forEach { seen: SeenRequest ->
            assertTrue(seen.header("Content-Type").orEmpty().startsWith("application/json"), "${seen.headers}")
        }
    }

    private fun SeenRequest.header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val API_KEY: String = "inventory-key-7f3a"
    }
}
