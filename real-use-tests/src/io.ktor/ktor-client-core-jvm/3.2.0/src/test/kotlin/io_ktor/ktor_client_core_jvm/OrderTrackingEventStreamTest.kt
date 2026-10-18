/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * The client `SSE` plugin reading a server-sent event stream.
 *
 * A shop front end follows the status feed of one order. The server publishes typed events
 * with ids and a multi-line delivery note after a keep-alive comment, then closes the stream.
 * The test checks that the client parses event names, ids and multi-line data, hides the
 * comment unless the application asks to see it, and resumes the feed after the last event
 * it saw by sending that id back in the `Last-Event-ID` header.
 */
class OrderTrackingEventStreamTest {

    /** One order status update as the front end models it. */
    data class StatusUpdate(val id: String?, val kind: String?, val lines: List<String>?, val comment: String?)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/orders/{orderId}/events") {
                    val orderId: String = call.parameters["orderId"]!!
                    val resumeAfter: Int = call.request.headers["Last-Event-ID"]?.toInt() ?: 0
                    send(ServerSentEvent(comments = "keep-alive"))
                    FEED.drop(resumeAfter).forEachIndexed { index: Int, (kind: String, text: String) ->
                        val id: Int = resumeAfter + index + 1
                        send(ServerSentEvent(data = "$orderId: $text", event = kind, id = id.toString()))
                    }
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
    fun followsAndResumesAnOrderStatusFeed() {
        val orderA: String = "http://127.0.0.1:$port/orders/A-17/events"
        val orderB: String = "http://127.0.0.1:$port/orders/B-02/events"
        val client = HttpClient(CIO) {
            install(SSE)
        }

        client.use {
            runBlocking {
                withTimeout(30_000) {
                    val full: List<StatusUpdate> = follow(it, orderA, lastSeen = null, comments = false)
                    assertEquals(
                        listOf(
                            StatusUpdate("1", "placed", listOf("A-17: order received"), null),
                            StatusUpdate("2", "packed", listOf("A-17: packed at Rotterdam"), null),
                            StatusUpdate("3", "shipped", SHIPPED_NOTE, null),
                            StatusUpdate("4", "delivered", listOf("A-17: left at reception"), null),
                        ),
                        full,
                    )

                    val resumed: List<StatusUpdate> = follow(it, orderA, lastSeen = "2", comments = false)
                    assertEquals(
                        listOf(
                            StatusUpdate("3", "shipped", SHIPPED_NOTE, null),
                            StatusUpdate("4", "delivered", listOf("A-17: left at reception"), null),
                        ),
                        resumed,
                    )

                    val withComments: List<StatusUpdate> = follow(it, orderB, lastSeen = "1", comments = true)
                    assertEquals(listOf(null, "2", "3", "4"), withComments.map { u: StatusUpdate -> u.id })
                    assertEquals(StatusUpdate(null, null, null, "keep-alive"), withComments[0])
                    assertEquals(listOf("B-02: packed at Rotterdam"), withComments[1].lines)
                }
            }
        }
    }

    /** Collects the whole feed at [url], resuming after [lastSeen] when it is given. */
    private suspend fun follow(
        client: HttpClient,
        url: String,
        lastSeen: String?,
        comments: Boolean,
    ): List<StatusUpdate> {
        var updates: List<StatusUpdate> = emptyList()
        client.sse(url, showCommentEvents = comments, request = {
            if (lastSeen != null) {
                header("Last-Event-ID", lastSeen)
            }
        }) {
            updates = incoming.toList().map { e: ServerSentEvent ->
                StatusUpdate(e.id, e.event, e.data?.lines(), e.comments)
            }
        }
        return updates
    }

    private companion object {
        val SHIPPED_NOTE: List<String> = listOf("A-17: handed to carrier", "tracking NL-88213")

        val FEED: List<Pair<String, String>> = listOf(
            "placed" to "order received",
            "packed" to "packed at Rotterdam",
            "shipped" to "handed to carrier\ntracking NL-88213",
            "delivered" to "left at reception",
        )
    }
}
