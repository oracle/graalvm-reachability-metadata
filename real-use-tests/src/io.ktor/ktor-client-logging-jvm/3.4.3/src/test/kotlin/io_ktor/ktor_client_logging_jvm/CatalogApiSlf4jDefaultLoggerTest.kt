/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
import org.slf4j.LoggerFactory
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * The default SLF4J-backed logger (`Logger.DEFAULT`) of the `Logging` client plugin.
 *
 * A catalog client keeps the plugin's default logger, so its call log goes through SLF4J into
 * the application's logback setup under the `io.ktor.client.HttpClient` logger. The scenario
 * attaches a logback appender there, as an application ships call logs to its own sink, and
 * checks that both a found and a missing book are logged at `INFO` with URL, method and status.
 */
class CatalogApiSlf4jDefaultLoggerTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val clientLogger: LogbackLogger = LoggerFactory.getLogger(HttpClient::class.java) as LogbackLogger
    private val sink: ListAppender<ILoggingEvent> = ListAppender()

    @BeforeEach
    fun start() {
        sink.context = clientLogger.loggerContext
        sink.start()
        clientLogger.addAppender(sink)
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/catalog/books/978-0134685991") {
                    call.respondText("Effective Java")
                }
                get("/catalog/books/000-0000000000") {
                    call.respondText("no such book", status = HttpStatusCode.NotFound)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        clientLogger.detachAppender(sink)
        sink.stop()
    }

    @Test
    fun defaultLoggerRoutesCallLogThroughSlf4jIntoLogback() {
        val foundUrl = "http://127.0.0.1:$port/catalog/books/978-0134685991"
        val missingUrl = "http://127.0.0.1:$port/catalog/books/000-0000000000"
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }

        client.use {
            runBlocking {
                val found = it.get(foundUrl)
                assertEquals(HttpStatusCode.OK, found.status)
                assertEquals("Effective Java", found.bodyAsText())

                val missing = it.get(missingUrl)
                assertEquals(HttpStatusCode.NotFound, missing.status)
                assertEquals("no such book", missing.bodyAsText())
            }
        }

        val events: List<ILoggingEvent> = sink.list.toList()
        val everything: String = events.joinToString("\n----\n") { "${it.level} ${it.loggerName}: ${it.formattedMessage}" }
        assertEquals(4, events.size, everything)
        assertTrue(events.all { it.loggerName == "io.ktor.client.HttpClient" }, everything)
        assertTrue(events.all { it.level == Level.INFO }, everything)

        val (foundRequest, foundResponse, missingRequest, missingResponse) = events.map { it.formattedMessage.lines() }
        assertTrue(foundRequest.contains("REQUEST: $foundUrl"), everything)
        assertTrue(foundRequest.contains("METHOD: GET"), everything)
        assertTrue(foundResponse.contains("RESPONSE: 200 OK"), everything)
        assertTrue(foundResponse.contains("FROM: $foundUrl"), everything)
        assertTrue(missingRequest.contains("REQUEST: $missingUrl"), everything)
        assertTrue(missingResponse.contains("RESPONSE: 404 Not Found"), everything)
        assertTrue(missingResponse.contains("FROM: $missingUrl"), everything)
        assertFalse(everything.contains("Effective Java"), "INFO level must not log bodies:\n$everything")
        assertFalse(everything.contains("-> "), "INFO level must not log headers:\n$everything")
    }
}
