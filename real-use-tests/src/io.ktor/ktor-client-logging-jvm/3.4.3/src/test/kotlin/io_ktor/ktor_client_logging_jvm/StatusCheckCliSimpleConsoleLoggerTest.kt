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
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The console logger `Logger.SIMPLE` of the `Logging` client plugin, which prints to standard output.
 *
 * A command-line status checker probes two service endpoints and, with `--verbose`, wants the
 * HTTP trace on the terminal without configuring any logging backend. It installs the plugin
 * with `Logger.SIMPLE` at the `INFO` level, so every request and response summary is printed
 * to `System.out` with the `HttpClient: ` prefix, next to the tool's own output.
 */
class StatusCheckCliSimpleConsoleLoggerTest {

    /** Captures what the tool writes to standard output, as a terminal would show it. */
    class Terminal {
        private val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true, StandardCharsets.UTF_8)

        fun text(): String = synchronized(buffer) { buffer.toString(StandardCharsets.UTF_8) }

        fun lines(): List<String> = text().lines().map { it.trimEnd('\r') }
    }

    /** The status checker: probes each endpoint and prints one verdict line per service. */
    class StatusChecker(private val baseUrl: String) {
        fun run(services: List<String>) {
            val client = HttpClient(ClientCIO) {
                expectSuccess = false
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.INFO
                }
            }
            client.use {
                runBlocking {
                    for (service in services) {
                        val response = it.get("$baseUrl/status/$service")
                        val verdict = if (response.status == HttpStatusCode.OK) "UP" else "DOWN"
                        println("status-check $service: $verdict (${response.bodyAsText()})")
                    }
                }
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/status/billing") {
                    call.respondText("billing healthy")
                }
                get("/status/search") {
                    call.respondText("search index rebuilding", status = HttpStatusCode.ServiceUnavailable)
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
    fun verboseTraceIsPrintedToStandardOutputWithClientPrefix() {
        val baseUrl = "http://127.0.0.1:$port"
        val billingUrl = "$baseUrl/status/billing"
        val searchUrl = "$baseUrl/status/search"
        val terminal = Terminal()
        val originalOut: PrintStream = System.out
        System.setOut(terminal.stream)
        try {
            StatusChecker(baseUrl).run(listOf("billing", "search"))
            waitForLine(terminal, "FROM: $searchUrl")
        } finally {
            System.out.flush()
            System.setOut(originalOut)
        }

        val lines: List<String> = terminal.lines()
        val everything: String = lines.joinToString("\n")

        assertTrue(lines.contains("status-check billing: UP (billing healthy)"), everything)
        assertTrue(lines.contains("status-check search: DOWN (search index rebuilding)"), everything)

        assertEquals(
            listOf(
                "HttpClient: REQUEST: $billingUrl",
                "HttpClient: RESPONSE: 200 OK",
                "HttpClient: REQUEST: $searchUrl",
                "HttpClient: RESPONSE: 503 Service Unavailable",
            ),
            lines.filter { it.startsWith("HttpClient: ") },
            everything,
        )
        assertEquals(4, lines.count { it == "METHOD: GET" }, everything)
        assertTrue(lines.contains("FROM: $billingUrl"), everything)
        assertTrue(lines.contains("FROM: $searchUrl"), everything)

        val requestIndex: Int = lines.indexOf("HttpClient: REQUEST: $billingUrl")
        assertEquals("METHOD: GET", lines[requestIndex + 1], everything)
        val responseIndex: Int = lines.indexOf("HttpClient: RESPONSE: 503 Service Unavailable")
        assertEquals("METHOD: GET", lines[responseIndex + 1], everything)
        assertEquals("FROM: $searchUrl", lines[responseIndex + 2], everything)

        assertFalse(everything.contains("COMMON HEADERS"), "INFO level must not print headers:\n$everything")
        assertFalse(everything.contains("BODY START"), "INFO level must not print bodies:\n$everything")
    }

    /** Polls the terminal until [line] appears, for at most ten seconds. */
    private fun waitForLine(terminal: Terminal, line: String) {
        val deadline: Long = System.nanoTime() + 10_000_000_000L
        while (!terminal.lines().contains(line)) {
            assertTrue(System.nanoTime() < deadline, "missing '$line' in:\n${terminal.text()}")
            Thread.sleep(20)
        }
    }
}
