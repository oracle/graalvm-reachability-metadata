/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
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
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Server-Sent Events streaming over the CIO client engine (the client `SSE` plugin).
 *
 * A parcel-tracking dashboard subscribes to the carrier's event stream for one parcel. The
 * carrier pushes typed, numbered events with multi-line payloads and keep-alive comments, then
 * ends the stream once the parcel is delivered. When the dashboard reconnects it sends the last
 * event id it saw, and the carrier replays only the events after it.
 */
class ParcelTrackingServerSentEventsTest {

    /** One tracking update as the dashboard records it. */
    private data class TrackingUpdate(val id: String, val kind: String, val lines: List<String>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** `Last-Event-ID` headers the carrier received, in order; `-` for a fresh subscription. */
    private val resumeHeaders: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/parcels/{parcel}/events") {
                    val parcel: String = call.parameters["parcel"]!!
                    val lastSeen: String? = call.request.headers["Last-Event-ID"]
                    resumeHeaders += lastSeen ?: "-"
                    val skip: Int = lastSeen?.toInt() ?: 0
                    CARRIER_EVENTS.drop(skip).forEachIndexed { index, (kind, data) ->
                        send(ServerSentEvent(comments = "keep-alive"))
                        send(ServerSentEvent(data = "$parcel\n$data", event = kind, id = "${skip + index + 1}"))
                    }
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
    fun dashboardFollowsParcelEventsAndResumesAfterTheLastSeenEvent() {
        val client = HttpClient(ClientCIO) {
            install(ClientSSE)
        }

        val (fullStream: List<TrackingUpdate>, resumedStream: List<TrackingUpdate>) = client.use {
            runBlocking {
                withTimeout(STREAM_TIMEOUT_MILLIS) {
                    val full: List<TrackingUpdate> = follow(it, lastSeen = null)
                    val resumed: List<TrackingUpdate> = follow(it, lastSeen = full[1].id)
                    full to resumed
                }
            }
        }

        val expected: List<TrackingUpdate> = CARRIER_EVENTS.mapIndexed { index, (kind, data) ->
            TrackingUpdate("${index + 1}", kind, listOf(PARCEL) + data.lines())
        }
        assertEquals(expected, fullStream)
        assertEquals(expected.drop(2), resumedStream)
        assertEquals(listOf("-", "2"), resumeHeaders.toList())
    }

    /** Collects the parcel's stream until the carrier ends it. */
    private suspend fun follow(client: HttpClient, lastSeen: String?): List<TrackingUpdate> {
        val updates: MutableList<TrackingUpdate> = mutableListOf()
        client.sse(
            urlString = "http://127.0.0.1:$port/parcels/$PARCEL/events",
            request = { if (lastSeen != null) header("Last-Event-ID", lastSeen) },
        ) {
            incoming.collect { event: ServerSentEvent ->
                // Keep-alive comments carry no payload, so the dashboard has nothing to record.
                val data: String = event.data ?: return@collect
                updates += TrackingUpdate(event.id!!, event.event!!, data.lines())
            }
        }
        return updates
    }

    private companion object {
        const val PARCEL: String = "PX-4471"
        const val STREAM_TIMEOUT_MILLIS: Long = 20_000
        val CARRIER_EVENTS: List<Pair<String, String>> = listOf(
            "picked-up" to "depot=Leeds",
            "in-transit" to "hub=Manchester\neta=2026-10-18",
            "out-for-delivery" to "van=17",
            "delivered" to "signed-by=R. Okafor\nsafe-place=porch",
        )
    }
}
