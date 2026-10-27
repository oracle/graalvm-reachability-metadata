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
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Logging of Server-Sent Events (`text/event-stream`) subscriptions in the `Logging` client plugin.
 *
 * A trading dashboard subscribes to a quote feed that an embedded CIO server streams as
 * Server-Sent Events, through the client `SSE` plugin, while its wire log runs at the `ALL`
 * level. The log must record the subscription request and the event-stream response with
 * their headers, but must leave the stream to the dashboard: the body section stays empty in
 * the default layout and the OkHttp layout marks the response as streaming, no quote reaches
 * the log, and the dashboard still receives every event in order.
 */
class QuoteFeedServerSentEventsLoggingTest {

    /** A wire log that keeps every call-log message, as an application log appender would. */
    class FeedLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        /** Polls until a message contains [marker], bounded by [timeoutMillis], and returns all messages. */
        fun awaitMessage(marker: String, timeoutMillis: Long): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = entries.toList()
            while (current.none { it.contains(marker) } && System.nanoTime() < deadline) {
                Thread.onSpinWait()
                current = entries.toList()
            }
            return current
        }
    }

    /** One event of the quote feed, as the dashboard keeps it. */
    data class FeedEvent(val id: String?, val type: String?, val data: String?)

    private val feed: List<FeedEvent> = listOf(
        FeedEvent(id = "1", type = "quote", data = "ACME 101.25"),
        FeedEvent(id = "2", type = "quote", data = "INITECH 55.10"),
        FeedEvent(id = "3", type = "market", data = "closed"),
    )

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var feedUrl: String

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/quotes") {
                    for (event in feed) {
                        send(ServerSentEvent(data = event.data, event = event.type, id = event.id))
                    }
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        feedUrl = "http://127.0.0.1:$port/quotes"
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    /** Subscribes to the quote feed through a client logging into [log] and returns the events received. */
    private fun subscribe(log: FeedLog, layout: LoggingFormat): List<FeedEvent> {
        val client = HttpClient(ClientCIO) {
            install(SSE)
            install(Logging) {
                logger = log
                level = LogLevel.ALL
                format = layout
            }
        }
        return client.use {
            runBlocking {
                var received: List<FeedEvent> = emptyList()
                it.sse(feedUrl) {
                    assertEquals(HttpStatusCode.OK, call.response.status)
                    assertEquals(ContentType.Text.EventStream, call.response.contentType()?.withoutParameters())
                    received = incoming.toList().map { event -> FeedEvent(event.id, event.event, event.data) }
                }
                received
            }
        }
    }

    @Test
    fun defaultLayoutLogsTheSubscriptionWithoutReadingTheStream() {
        val log = FeedLog()

        val received: List<FeedEvent> = subscribe(log, LoggingFormat.Default)
        assertEquals(feed, received, "the dashboard receives every event in order")

        val messages: List<String> = log.awaitMessage("RESPONSE: 200 OK", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val request: List<String> = messages.first { it.startsWith("REQUEST: ") }.lines()
        assertEquals("REQUEST: $feedUrl", request.first(), everything)
        assertTrue(request.contains("METHOD: GET"), everything)
        assertTrue(request.contains("-> Accept: text/event-stream"), "the subscription asks for a stream:\n$everything")
        assertTrue(request.contains("-> Cache-Control: no-store"), "the subscription bypasses caches:\n$everything")

        val response: List<String> = messages.first { it.startsWith("RESPONSE: ") }.lines()
        assertEquals("RESPONSE: 200 OK", response.first(), everything)
        assertTrue(response.contains("FROM: $feedUrl"), everything)
        assertTrue(response.contains("-> Content-Type: text/event-stream"), everything)
        assertTrue(response.contains("BODY Content-Type: text/event-stream"), everything)
        val bodyStart: Int = response.indexOf("BODY START")
        val bodyEnd: Int = response.indexOf("BODY END")
        assertTrue(bodyStart in 0 until bodyEnd, "the response has a body section:\n$everything")
        assertTrue(
            response.subList(bodyStart + 1, bodyEnd).all { it.isBlank() },
            "the body section stays empty for an event stream:\n$everything",
        )
        assertNoQuoteLogged(everything)
    }

    @Test
    fun okHttpLayoutMarksTheResponseAsStreaming() {
        val log = FeedLog()

        val received: List<FeedEvent> = subscribe(log, LoggingFormat.OkHttp)
        assertEquals(feed, received, "the dashboard receives every event in order")

        val messages: List<String> = log.awaitMessage("<-- END HTTP", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val request: List<String> = messages.first { it.startsWith("--> GET") }.lines()
        assertEquals("--> GET $feedUrl", request.first(), everything)
        assertEquals("--> END GET", request.last(), "a subscription carries no request body:\n$everything")

        val response: List<String> = messages.first { it.startsWith("<-- 200") }.lines()
        assertTrue(Regex("""<-- 200 OK \Q$feedUrl\E \(\d+ms\)""").matches(response.first()), everything)
        assertTrue(response.contains("Content-Type: text/event-stream"), everything)
        assertTrue(response.contains("Cache-Control: no-store"), everything)
        assertEquals("<-- END HTTP (streaming)", response.last(), "the stream is not byte-counted:\n$everything")
        assertNoQuoteLogged(everything)
    }

    private fun assertNoQuoteLogged(everything: String) {
        for (event in feed) {
            assertFalse(everything.contains(event.data!!), "event ${event.id} stays out of the log:\n$everything")
        }
        assertFalse(everything.contains("event: quote"), "no raw event frame is logged:\n$everything")
    }
}
