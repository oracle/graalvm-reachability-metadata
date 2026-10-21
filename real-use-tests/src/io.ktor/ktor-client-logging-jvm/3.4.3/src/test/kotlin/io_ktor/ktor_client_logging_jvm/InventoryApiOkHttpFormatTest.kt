/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * OkHttp-style request and response logging through `LoggingFormat.OkHttp` in the `Logging` client plugin.
 *
 * An inventory client restocks an item and then reads its stock level from an embedded CIO
 * server. The client logs at the `BODY` level in the OkHttp interceptor layout that operators
 * already grep for: one `-->` message per request with headers, body and a byte-counted
 * `--> END` line, and one `<--` message per response with status, timing and body size.
 */
class InventoryApiOkHttpFormatTest {

    /** Keeps every message the plugin emits, as an application wire log would. */
    class WireLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun messages(): List<String> = entries.toList()
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/inventory/restock") {
                    val order: String = call.receiveText()
                    call.respondText("restocked: $order", status = HttpStatusCode.Created)
                }
                get("/inventory/SKU-42") {
                    call.respondText("SKU-42 in stock: 17")
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
    fun wireLogUsesOkHttpLayoutForRequestsAndResponses() {
        val wireLog = WireLog()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = wireLog
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }
        val restockUrl = "http://127.0.0.1:$port/inventory/restock"
        val stockUrl = "http://127.0.0.1:$port/inventory/SKU-42"
        val restockOrder = "SKU-42 qty=5"
        val restockReply = "restocked: $restockOrder"
        val stockReply = "SKU-42 in stock: 17"

        client.use {
            runBlocking {
                val restocked = it.post(restockUrl) {
                    header("X-Request-Id", "req-77")
                    setBody(restockOrder)
                }
                assertEquals(HttpStatusCode.Created, restocked.status)
                assertEquals(restockReply, restocked.bodyAsText())

                val stock = it.get(stockUrl) { header("X-Request-Id", "req-78") }
                assertEquals(HttpStatusCode.OK, stock.status)
                assertEquals(stockReply, stock.bodyAsText())
            }
        }

        val messages: List<String> = wireLog.messages()
        val everything: String = messages.joinToString("\n----\n")
        assertEquals(4, messages.size, everything)
        assertFalse(everything.contains("REQUEST: "), "default layout must not be used:\n$everything")
        val (restockRequest, restockResponse, stockRequest, stockResponse) = messages.map { it.lines() }

        assertEquals("--> POST $restockUrl", restockRequest.first(), everything)
        assertTrue(restockRequest.contains("X-Request-Id: req-77"), everything)
        assertTrue(restockRequest.any { it.startsWith("Content-Type: text/plain") }, everything)
        assertTrue(restockRequest.contains("Content-Length: ${restockOrder.length}"), everything)
        assertEquals(
            listOf("", restockOrder, "--> END POST (${restockOrder.length}-byte body)"),
            restockRequest.takeLast(3),
            everything,
        )

        assertTrue(Regex("""<-- 201 Created \Q$restockUrl\E \(\d+ms\)""").matches(restockResponse.first()), everything)
        assertTrue(restockResponse.contains("Content-Length: ${restockReply.length}"), everything)
        assertEquals(restockReply, restockResponse[restockResponse.size - 2], everything)
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${restockReply.length}-byte body\)""").matches(restockResponse.last()),
            everything,
        )

        assertEquals("--> GET $stockUrl", stockRequest.first(), everything)
        assertTrue(stockRequest.contains("X-Request-Id: req-78"), everything)
        assertEquals("--> END GET", stockRequest.last(), everything)

        assertTrue(Regex("""<-- 200 OK \Q$stockUrl\E \(\d+ms\)""").matches(stockResponse.first()), everything)
        assertEquals(stockReply, stockResponse[stockResponse.size - 2], everything)
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${stockReply.length}-byte body\)""").matches(stockResponse.last()),
            everything,
        )
    }
}
