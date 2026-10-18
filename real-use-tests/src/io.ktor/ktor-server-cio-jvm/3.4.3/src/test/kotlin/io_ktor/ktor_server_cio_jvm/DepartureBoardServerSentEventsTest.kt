/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * The CIO engine's Server-Sent Events streaming: long-lived text/event-stream responses
 * that carry comments, retry hints, typed events with ids, and multi-line data.
 *
 * A railway station runs its departure board on an embedded CIO server. A platform
 * display subscribes to the feed and receives a greeting comment with a reconnection
 * hint, then every departure and gate change as a typed event whose multi-line body
 * lists the train, destination, and platform, and finally an end-of-feed marker. A
 * second display that lost its connection resubscribes with the id of the last event
 * it saw and is sent only what it missed. The test checks the stream content type,
 * every event field the displays decoded, and the resume points the server honoured.
 */
class DepartureBoardServerSentEventsTest {

    data class Departure(val id: Long, val kind: String, val train: String, val destination: String, val platform: Int)

    data class Seen(val event: String?, val id: String?, val data: String?, val retry: Long?, val comments: String?)

    private val board: List<Departure> = listOf(
        Departure(1, "departure", "IC 512", "Zagreb Glavni", 3),
        Departure(2, "departure", "RE 7", "Ljubljana", 1),
        Departure(3, "gate-change", "IC 512", "Zagreb Glavni", 5),
        Departure(4, "departure", "EC 158", "Wien Hbf", 2),
        Departure(5, "gate-change", "RE 7", "Ljubljana", 4),
    )

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val resumePoints: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/departures") {
                    streamBoard(this)
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
    fun displaysFollowAndResumeTheDepartureFeedOverCio() {
        HttpClient(ClientCIO) { install(ClientSSE) }.use { client: HttpClient ->
            runBlocking {
                withTimeout(30_000) {
                    val fullFeed: List<Seen> = subscribe(client, lastEventId = null)
                    assertEquals(
                        listOf(Seen(null, null, null, 1_500, "Central Station departures")) +
                            board.map { departure: Departure -> expected(departure) } +
                            Seen("end-of-feed", "5", "5 updates", null, null),
                        fullFeed,
                    )

                    val resumedFeed: List<Seen> = subscribe(client, lastEventId = "3")
                    assertEquals(
                        listOf(Seen(null, null, null, 1_500, "Central Station departures")) +
                            board.drop(3).map { departure: Departure -> expected(departure) } +
                            Seen("end-of-feed", "5", "2 updates", null, null),
                        resumedFeed,
                    )
                }
            }
        }

        assertEquals(listOf("start", "3"), resumePoints)
    }

    /** Sends the greeting, every update after the client's last seen id, and the end marker. */
    private suspend fun streamBoard(session: ServerSSESession) {
        val lastSeen: Long = session.call.request.headers["Last-Event-ID"]?.toLong() ?: 0
        resumePoints += if (lastSeen == 0L) "start" else lastSeen.toString()
        session.send(ServerSentEvent(comments = "Central Station departures", retry = 1_500))
        val pending: List<Departure> = board.filter { departure: Departure -> departure.id > lastSeen }
        pending.forEach { departure: Departure ->
            session.send(data = body(departure), event = departure.kind, id = departure.id.toString())
        }
        session.send(data = "${pending.size} updates", event = "end-of-feed", id = board.last().id.toString())
    }

    /** Reads the whole feed until the server closes it, keeping comment and retry events and data lines. */
    private suspend fun subscribe(client: HttpClient, lastEventId: String?): List<Seen> {
        val seen: MutableList<Seen> = mutableListOf()
        client.sse(
            urlString = "http://127.0.0.1:$port/departures",
            request = { if (lastEventId != null) header("Last-Event-ID", lastEventId) },
            showCommentEvents = true,
            showRetryEvents = true,
        ) {
            val session: ClientSSESession = this
            assertEquals(HttpStatusCode.OK, session.call.response.status)
            assertEquals(ContentType.Text.EventStream, session.call.response.contentType()?.withoutParameters())
            session.incoming.collect { event: ServerSentEvent ->
                val lines: String? = event.data?.lines()?.joinToString("\n")
                seen += Seen(event.event, event.id, lines, event.retry, event.comments)
            }
        }
        return seen
    }

    private fun body(departure: Departure): String =
        "train=${departure.train}\ndestination=${departure.destination}\nplatform=${departure.platform}"

    private fun expected(departure: Departure): Seen =
        Seen(departure.kind, departure.id.toString(), body(departure), null, null)
}
