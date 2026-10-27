/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * SLF4J MDC propagation into the call log of the `Logging` client plugin.
 *
 * An audit client tags every operation with a `traceId` in the SLF4J MDC and runs it inside
 * `MDCContext()`, as coroutine applications do, and keeps the plugin's default SLF4J logger at
 * `LogLevel.ALL`. The plugin writes the request log once the request body has been streamed,
 * from a background coroutine, and the response log from the calling coroutine. A logback
 * appender, standing in for the application's log shipper, checks that both records of each
 * call carry that call's trace id, and that an untraced health ping logs without one.
 */
class AuditApiMdcPropagationTest {

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
                post("/audit/events") {
                    val event: String = call.receiveText()
                    call.respondText("recorded ${event.length} chars", status = HttpStatusCode.Created)
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
        MDC.clear()
    }

    /** Records [event] under [traceId], or untraced when it is null, and returns the server's reply. */
    private suspend fun record(client: HttpClient, url: String, traceId: String?, event: String): String {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId)
        }
        try {
            return withContext(MDCContext()) {
                val response = client.post(url) {
                    contentType(ContentType.Text.Plain)
                    setBody(event)
                }
                assertEquals(HttpStatusCode.Created, response.status)
                response.bodyAsText()
            }
        } finally {
            MDC.remove(TRACE_ID)
        }
    }

    /** Polls until [count] call-log records arrived, bounded by [timeoutMillis], and returns them. */
    private fun awaitEvents(count: Int, timeoutMillis: Long): List<ILoggingEvent> {
        val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var current: List<ILoggingEvent> = synchronized(sink) { sink.list.toList() }
        while (current.size < count && System.nanoTime() < deadline) {
            Thread.onSpinWait()
            current = synchronized(sink) { sink.list.toList() }
        }
        return current
    }

    @Test
    fun eachCallLogCarriesTheTraceIdOfTheOperationThatMadeIt() {
        val url = "http://127.0.0.1:$port/audit/events"
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }

        val replies: List<String> = client.use {
            runBlocking {
                listOf(
                    record(it, url, "trace-alpha", "user=ada action=login"),
                    record(it, url, "trace-beta", "user=grace action=export-report"),
                    record(it, url, null, "health=ping"),
                )
            }
        }
        assertEquals(listOf("recorded 21 chars", "recorded 31 chars", "recorded 11 chars"), replies)

        val events: List<ILoggingEvent> = awaitEvents(count = 6, timeoutMillis = 10_000)
        val everything: String = events.joinToString("\n----\n") { "mdc=${it.mdcPropertyMap} ${it.formattedMessage}" }
        assertEquals(6, events.size, everything)

        fun requestCarrying(event: String): ILoggingEvent = events.single {
            it.formattedMessage.startsWith("REQUEST: $url") && it.formattedMessage.lines().contains(event)
        }
        fun responseCarrying(reply: String): ILoggingEvent = events.single {
            it.formattedMessage.startsWith("RESPONSE: 201 Created") && it.formattedMessage.lines().contains(reply)
        }

        val alphaRequest: ILoggingEvent = requestCarrying("user=ada action=login")
        val alphaResponse: ILoggingEvent = responseCarrying("recorded 21 chars")
        assertEquals("trace-alpha", alphaRequest.mdcPropertyMap[TRACE_ID], everything)
        assertEquals("trace-alpha", alphaResponse.mdcPropertyMap[TRACE_ID], everything)
        assertTrue(alphaRequest.formattedMessage.lines().contains("BODY START"), everything)

        val betaRequest: ILoggingEvent = requestCarrying("user=grace action=export-report")
        val betaResponse: ILoggingEvent = responseCarrying("recorded 31 chars")
        assertEquals("trace-beta", betaRequest.mdcPropertyMap[TRACE_ID], everything)
        assertEquals("trace-beta", betaResponse.mdcPropertyMap[TRACE_ID], everything)

        val pingRequest: ILoggingEvent = requestCarrying("health=ping")
        val pingResponse: ILoggingEvent = responseCarrying("recorded 11 chars")
        assertFalse(pingRequest.mdcPropertyMap.containsKey(TRACE_ID), "an untraced call logs no trace id:\n$everything")
        assertFalse(pingResponse.mdcPropertyMap.containsKey(TRACE_ID), "an untraced call logs no trace id:\n$everything")
    }

    private companion object {
        const val TRACE_ID: String = "traceId"
    }
}
