/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Server-Sent Events streams that the OkHttp engine consumes through Ktor's client SSE plugin.
 *
 * A trading dashboard subscribes to a market feed that pushes named events: JSON quotes,
 * a multi-line halt notice and a closing summary, each with an event id. The dashboard resumes
 * a feed by sending the id of the last event it processed and expects the service to replay only
 * what came after it. The test checks the event names, ids and decoded payloads the dashboard
 * observes, in order, and that the resumed feed starts after the given id.
 */
class MarketFeedServerSentEventsTest {

    /** One price quote, as the feed serializes it into an event's data field. */
    @Serializable
    data class Quote(val symbol: String, val bid: Double, val ask: Double)

    /** What the dashboard keeps from one received event. */
    data class FeedEntry(val event: String?, val id: String?, val data: String?)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/feed") {
                    val lastSeen: Int = call.request.headers["Last-Event-ID"]?.toInt() ?: 0
                    FEED.withIndex().filter { it.index + 1 > lastSeen }.forEach { (index, entry) ->
                        send(ServerSentEvent(data = entry.data, event = entry.event, id = "${index + 1}"))
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(0, 0)
    }

    @Test
    fun receivesTheWholeFeedAndResumesAfterTheLastSeenEvent() {
        val client = HttpClient(OkHttp) {
            install(SSE)
        }
        client.use {
            runBlocking {
                val full: List<FeedEntry> = readFeed(client, lastEventId = null)
                assertEquals(FEED.size, full.size)
                assertEquals(listOf("quote", "quote", "halt", "quote", "summary"), full.map { it.event })
                assertEquals(listOf("1", "2", "3", "4", "5"), full.map { it.id })

                val quotes: List<Quote> = full.filter { it.event == "quote" }
                    .map { Json.decodeFromString<Quote>(it.data!!) }
                assertEquals(
                    listOf(
                        Quote("ACME", 101.25, 101.5),
                        Quote("GLOBEX", 47.1, 47.3),
                        Quote("ACME", 100.75, 101.0),
                    ),
                    quotes,
                )
                assertEquals(listOf("GLOBEX halted", "reason: pending news"), full[2].data!!.lines())
                assertEquals("quotes=3 halts=1", full[4].data)

                val resumed: List<FeedEntry> = readFeed(client, lastEventId = "3")
                assertEquals(listOf("4", "5"), resumed.map { it.id })
                assertEquals(full.drop(3), resumed)
            }
        }
    }

    /** Subscribes to the feed, optionally resuming after [lastEventId], and drains it until it ends. */
    private suspend fun readFeed(client: HttpClient, lastEventId: String?): List<FeedEntry> {
        val received: MutableList<FeedEntry> = ArrayList()
        client.sse(
            urlString = "http://127.0.0.1:$port/feed",
            request = { if (lastEventId != null) header("Last-Event-ID", lastEventId) },
        ) {
            incoming.collect { event -> received.add(FeedEntry(event.event, event.id, event.data)) }
        }
        return received
    }

    private companion object {
        val FEED: List<FeedEntry> = listOf(
            FeedEntry("quote", null, """{"symbol":"ACME","bid":101.25,"ask":101.5}"""),
            FeedEntry("quote", null, """{"symbol":"GLOBEX","bid":47.1,"ask":47.3}"""),
            FeedEntry("halt", null, "GLOBEX halted\nreason: pending news"),
            FeedEntry("quote", null, """{"symbol":"ACME","bid":100.75,"ask":101.0}"""),
            FeedEntry("summary", null, "quotes=3 halts=1"),
        )
    }
}
