/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Failed-call logging, where the `Logging` client plugin records the exception that ended a call.
 *
 * A shipping client validates responses (`expectSuccess`) and decodes JSON quotes through content
 * negotiation. One carrier's host refuses connections, one answers 503, and one answers with a
 * quote the client cannot decode. The application sees all three as exceptions; its incident log
 * must name the URL and exception of the refused request and the undecodable response, record the
 * 503 as an ordinary response, and log the healthy carrier's call without any failure.
 */
class ShippingApiFailureLoggingTest {

    @Serializable
    data class Quote(val carrier: String, val priceCents: Long, val days: Int)

    /** Collects every message the plugin emits, as an application incident log would. */
    class IncidentLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun lines(): List<String> = entries.toList().flatMap { it.lines() }.map { it.trim() }

        /** Polls until a line satisfies [predicate], bounded by [timeoutMillis]. */
        fun awaitLine(timeoutMillis: Long, predicate: (String) -> Boolean): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = lines()
            while (current.none(predicate) && System.nanoTime() < deadline) {
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
            routing {
                get("/carriers/northwind/quote") {
                    call.respondText(
                        """{"error":"carrier offline"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.ServiceUnavailable,
                    )
                }
                get("/carriers/bluefin/quote") {
                    call.respondText("""{"carrier":"bluefin","priceCents":"cheap"}""", ContentType.Application.Json)
                }
                get("/carriers/eastline/quote") {
                    call.respondText("""{"carrier":"eastline","priceCents":1250,"days":3}""", ContentType.Application.Json)
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
    fun incidentLogNamesTheUrlAndExceptionOfEachFailedCall() {
        val incidentLog = IncidentLog()
        val client = HttpClient(ClientCIO) {
            expectSuccess = true
            install(ClientContentNegotiation) { json() }
            install(Logging) {
                logger = incidentLog
                level = LogLevel.INFO
            }
        }

        val refusedPort: Int = ServerSocket(0).use { it.localPort }
        val refusedUrl = "http://127.0.0.1:$refusedPort/carriers/southport/quote"
        val offlineUrl = "http://127.0.0.1:$port/carriers/northwind/quote"
        val malformedUrl = "http://127.0.0.1:$port/carriers/bluefin/quote"
        val healthyUrl = "http://127.0.0.1:$port/carriers/eastline/quote"

        val refused: Throwable = client.use {
            val refused: Throwable = assertThrows(Throwable::class.java) {
                runBlocking { it.get(refusedUrl) }
            }

            val offline = assertThrows(ServerResponseException::class.java) {
                runBlocking { it.get(offlineUrl) }
            }
            assertEquals(HttpStatusCode.ServiceUnavailable, offline.response.status)

            assertThrows(JsonConvertException::class.java) {
                runBlocking { it.get(malformedUrl).body<Quote>() }
            }

            assertEquals(Quote("eastline", 1250, 3), runBlocking { it.get(healthyUrl).body<Quote>() })
            refused
        }

        val lines = incidentLog.awaitLine(10_000) { line ->
            line.contains(malformedUrl) && line.contains("failed with exception")
        }
        val everything = lines.joinToString("\n")

        val refusedRequest: Int = lines.indexOf("REQUEST: $refusedUrl")
        assertTrue(refusedRequest >= 0, everything)
        val refusedFailure: String = lines[refusedRequest + 2]
        assertTrue(refusedFailure.startsWith("REQUEST $refusedUrl failed with exception: "), everything)
        assertTrue(refusedFailure.contains(refused::class.java.name), everything)
        assertTrue(lines.none { it.startsWith("RESPONSE") && it.contains(refusedUrl) }, everything)

        val offlineRequest: Int = lines.indexOf("REQUEST: $offlineUrl")
        assertTrue(offlineRequest >= 0, everything)
        assertEquals("RESPONSE: 503 Service Unavailable", lines[offlineRequest + 2], everything)
        assertTrue(lines.none { it.contains(offlineUrl) && it.contains("failed") }, everything)

        val malformedFailure: String? = lines.singleOrNull {
            it.startsWith("RESPONSE $malformedUrl failed with exception: ")
        }
        assertTrue(malformedFailure != null, everything)
        assertTrue(malformedFailure!!.contains(JsonConvertException::class.java.name), everything)
        assertTrue(malformedFailure.contains("priceCents"), everything)

        assertTrue(lines.none { it.contains(healthyUrl) && it.contains("failed") }, everything)
        assertTrue(lines.contains("RESPONSE: 200 OK"), everything)
    }
}
