/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Request and response body logging at the `ALL` level of the `Logging` client plugin.
 *
 * An order client posts a JSON order through content negotiation to an embedded CIO server
 * and reads back the confirmation. The client logs the whole exchange into a trace log, which
 * must hold the serialized request payload and the server's JSON reply between body markers,
 * together with their content types, while the application still gets the decoded objects.
 */
class OrderApiBodyLoggingTest {

    @Serializable
    data class OrderRequest(val sku: String, val quantity: Int)

    @Serializable
    data class OrderConfirmation(val orderId: String, val sku: String, val quantity: Int, val status: String)

    /** Collects every message the plugin emits, as an application trace log would. */
    class TraceLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun lines(): List<String> = entries.toList().flatMap { it.lines() }.map { it.trim() }

        /** Polls until [count] lines equal [line], bounded by [timeoutMillis]. */
        fun awaitLines(line: String, count: Int, timeoutMillis: Long): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = lines()
            while (current.count { it == line } < count && System.nanoTime() < deadline) {
                Thread.onSpinWait()
                current = lines()
            }
            return current
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                post("/orders") {
                    val order = call.receive<OrderRequest>()
                    call.respond(HttpStatusCode.Created, OrderConfirmation("ord-1001", order.sku, order.quantity, "ACCEPTED"))
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
    fun traceLogHoldsRequestAndResponseBodies() {
        val traceLog = TraceLog()
        val client = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) { json() }
            install(Logging) {
                logger = traceLog
                level = LogLevel.ALL
            }
        }

        val confirmation: OrderConfirmation = client.use {
            runBlocking {
                val response = it.post("http://127.0.0.1:$port/orders") {
                    contentType(ContentType.Application.Json)
                    setBody(OrderRequest("BOOK-7", 3))
                }
                assertEquals(HttpStatusCode.Created, response.status)
                response.body()
            }
        }

        assertEquals(OrderConfirmation("ord-1001", "BOOK-7", 3, "ACCEPTED"), confirmation)

        val lines = traceLog.awaitLines("BODY END", count = 2, timeoutMillis = 10_000)
        val everything = lines.joinToString("\n")
        assertEquals(2, lines.count { it == "BODY START" }, everything)
        assertEquals(2, lines.count { it == "BODY END" }, everything)
        assertTrue(lines.contains("REQUEST: http://127.0.0.1:$port/orders"), everything)
        assertTrue(lines.contains("METHOD: POST"), everything)
        assertTrue(lines.contains("RESPONSE: 201 Created"), everything)
        assertTrue(lines.contains("""{"sku":"BOOK-7","quantity":3}"""), everything)
        assertTrue(
            lines.contains("""{"orderId":"ord-1001","sku":"BOOK-7","quantity":3,"status":"ACCEPTED"}"""),
            everything,
        )
        assertEquals(
            2,
            lines.count { it.startsWith("BODY Content-Type: application/json") },
            "both bodies are logged with their JSON content type:\n$everything",
        )

        val requestBodyStart: Int = lines.indexOf("BODY START")
        assertEquals("""{"sku":"BOOK-7","quantity":3}""", lines[requestBodyStart + 1], everything)
        assertTrue(requestBodyStart < lines.indexOf("RESPONSE: 201 Created"), everything)
    }
}
