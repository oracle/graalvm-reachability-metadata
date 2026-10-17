/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
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
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP call logging with the `Logging` plugin on the `Apache5` client engine.
 *
 * An order-service client logs every API call at `LogLevel.ALL` into an application logger,
 * masks its bearer token and an API key, and filters out health probes. The application reads
 * back the log it collected and observes the request line, headers, request and response
 * bodies and status, with no secret and no health probe in it.
 */
class OrderServiceCallLoggingTest {

    /** The application's log sink: it keeps every message the plugin writes. */
    class CollectingLogger : Logger {
        val messages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

        override fun log(message: String) {
            messages.add(message)
        }

        fun text(): String = messages.joinToString("\n")
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/api/orders") {
                    val order: String = call.receiveText()
                    call.response.header("X-Order-Id", "ORD-7731")
                    call.respondText(
                        "accepted: $order",
                        ContentType.Text.Plain,
                        HttpStatusCode.Created,
                    )
                }
                get("/health") {
                    call.respondText("UP", ContentType.Text.Plain)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun loggingClient(sink: CollectingLogger): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        install(Logging) {
            logger = sink
            level = LogLevel.ALL
            format = LoggingFormat.Default
            sanitizeHeader { name -> name == "Authorization" }
            sanitizeHeader(placeholder = "<redacted>") { name -> name == "X-Api-Key" }
            filter { request -> request.url.encodedPath.startsWith("/api") }
        }
    }

    /** Waits, bounded, until the log holds [expected]; the plugin may finish a body log late. */
    private fun awaitLogged(sink: CollectingLogger, expected: String) {
        val deadline: Long = System.nanoTime() + 10_000_000_000L
        while (!sink.text().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(sink.text().contains(expected), "Log is missing '$expected':\n${sink.text()}")
    }

    @Test
    fun orderCallIsLoggedWithMaskedSecretsAndHealthProbesAreFiltered() {
        val sink: CollectingLogger = CollectingLogger()
        loggingClient(sink).use { client ->
            runBlocking {
                val health: HttpResponse = client.get("http://127.0.0.1:$port/health")
                assertEquals("UP", health.bodyAsText())

                val placed: HttpResponse = client.post("http://127.0.0.1:$port/api/orders") {
                    bearerAuth("s3cr3t-token-42")
                    header("X-Api-Key", "key-9f8e7d")
                    header("X-Correlation-Id", "corr-1001")
                    contentType(ContentType.Text.Plain)
                    setBody("2 x espresso beans")
                }
                assertEquals(HttpStatusCode.Created, placed.status)
                assertEquals("ORD-7731", placed.headers["X-Order-Id"])
                assertEquals("accepted: 2 x espresso beans", placed.bodyAsText())
            }
        }

        awaitLogged(sink, "accepted: 2 x espresso beans")
        val log: String = sink.text()
        assertTrue(log.contains("REQUEST: http://127.0.0.1:$port/api/orders"), log)
        assertTrue(log.contains("METHOD: POST"), log)
        assertTrue(log.contains("X-Correlation-Id: corr-1001"), log)
        assertTrue(log.contains("Authorization: ***"), log)
        assertTrue(log.contains("X-Api-Key: <redacted>"), log)
        assertTrue(log.contains("2 x espresso beans"), log)
        assertTrue(log.contains("RESPONSE: 201 Created"), log)
        assertTrue(log.contains("X-Order-Id: ORD-7731"), log)
        assertFalse(log.contains("s3cr3t-token-42"), log)
        assertFalse(log.contains("key-9f8e7d"), log)
        assertFalse(log.contains("/health"), log)
    }
}
