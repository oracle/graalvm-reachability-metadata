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
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * The platform-detecting `Logger.ANDROID` logger of the `Logging` client plugin, off Android.
 *
 * A field-service app shares its HTTP client setup between the Android app and a JVM back
 * office, so the client always installs `Logger.ANDROID`. On the JVM there is no Logcat: the
 * logger falls back to the SLF4J default under `io.ktor.client.HttpClient`, still limiting
 * every record to Logcat's line budget. The scenario fetches a work order whose technician
 * notes are longer than that budget and checks that the call log reaches logback in records
 * no longer than 4000 characters that, rejoined, hold every note line intact.
 */
class WorkOrderApiAndroidLoggerFallbackTest {

    private val logcatLineBudget: Int = 4000
    private val noteLines: List<String> = (1..120).map {
        "note %03d: replaced filter on unit %03d, pressure nominal".format(it, it)
    }
    private val workOrderNotes: String = noteLines.joinToString("\n")

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
                get("/work-orders/WO-4711/notes") {
                    call.respondText(workOrderNotes, ContentType.Text.Plain, HttpStatusCode.OK)
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

    /** Copies the appender's events under the lock logback appends under. */
    private fun events(): List<ILoggingEvent> = synchronized(sink) { sink.list.toList() }

    /** Polls until an event's message contains [marker], bounded by [timeoutMillis]. */
    private fun awaitEvents(marker: String, timeoutMillis: Long): List<ILoggingEvent> {
        val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var current: List<ILoggingEvent> = events()
        while (current.none { it.formattedMessage.contains(marker) } && System.nanoTime() < deadline) {
            Thread.onSpinWait()
            current = events()
        }
        return current
    }

    @Test
    fun androidLoggerFallsBackToSlf4jWithLogcatSizedRecords() {
        val sharedLogger: Logger = Logger.ANDROID
        assertSame(sharedLogger, Logger.ANDROID, "the platform logger is resolved once and reused")

        val notesUrl = "http://127.0.0.1:$port/work-orders/WO-4711/notes"
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = sharedLogger
                level = LogLevel.BODY
            }
        }

        client.use {
            runBlocking {
                val notes = it.get(notesUrl)
                assertEquals(HttpStatusCode.OK, notes.status)
                assertEquals(workOrderNotes, notes.bodyAsText())
            }
        }

        val events: List<ILoggingEvent> = awaitEvents(noteLines.last(), timeoutMillis = 10_000)
        val everything: String = events.joinToString("\n----\n") { "${it.level} ${it.loggerName}: ${it.formattedMessage}" }
        assertTrue(events.all { it.loggerName == "io.ktor.client.HttpClient" }, everything)
        assertTrue(events.all { it.level == Level.INFO }, everything)
        assertTrue(
            events.all { it.formattedMessage.length <= logcatLineBudget },
            "a record exceeds $logcatLineBudget chars:\n$everything",
        )

        val messages: List<String> = events.map { it.formattedMessage }
        assertTrue(messages.any { it.lines().contains("REQUEST: $notesUrl") }, everything)
        assertTrue(messages.any { it.lines().contains("RESPONSE: 200 OK") }, everything)
        val noteRecords: Int = messages.count { message -> noteLines.any { message.contains(it) } }
        assertTrue(noteRecords >= 2, "notes longer than the budget are split across records:\n$everything")
        val loggedLines: Set<String> = messages.flatMap { it.lines() }.toSet()
        assertTrue(loggedLines.containsAll(noteLines), "every note line stays whole in one record:\n$everything")
    }
}
