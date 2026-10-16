/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Broadcasts one live feed of server-sent events to many concurrent subscribers.
 *
 * A live-score service publishes match updates to a single in-process feed,
 * and every open SSE connection relays the updates of the match it follows.
 * Two fans watch the same match from kick-off, a third fan watches another
 * match, and a fourth fan opens the first match at half-time and only sees
 * what happens from then on. Every stream ends with the full-time event.
 */
public class LiveScoreBroadcastTest {

    /** One update on the shared feed, tagged with the match it belongs to. */
    private data class MatchUpdate(
        val match: String,
        val event: ServerSentEvent,
    )

    private val feed: MutableSharedFlow<MatchUpdate> = MutableSharedFlow(extraBufferCapacity = 64)

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/matches/{match}") {
                    val match: String = requireNotNull(call.parameters["match"])
                    feed
                        .filter { update: MatchUpdate -> update.match == match }
                        .map { update: MatchUpdate -> update.event }
                        .transformWhile { event: ServerSentEvent ->
                            emit(event)
                            event.event != FULL_TIME
                        }
                        .collect { event: ServerSentEvent -> send(event) }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO) {
            install(ClientSSE)
        }
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** Follows one match until its full-time event and returns everything the fan saw. */
    private suspend fun follow(match: String): List<ServerSentEvent> {
        var seen: List<ServerSentEvent> = emptyList()
        client.sse(host = "127.0.0.1", port = port, path = "/matches/$match") {
            seen = incoming
                .transformWhile { event: ServerSentEvent ->
                    emit(event)
                    event.event != FULL_TIME
                }
                .toList()
        }
        return seen
    }

    /** Waits until the given number of SSE sessions listen on the feed. */
    private suspend fun awaitSubscribers(count: Int) {
        withTimeout(10_000L) {
            feed.subscriptionCount.first { subscribers: Int -> subscribers == count }
        }
    }

    private suspend fun publish(match: String, event: ServerSentEvent) {
        feed.emit(MatchUpdate(match = match, event = event))
    }

    @Test
    fun `every fan receives the updates of the match they follow`() = runBlocking {
        val homeFan: Deferred<List<ServerSentEvent>> = async { follow(LIONS_TIGERS) }
        val awayFan: Deferred<List<ServerSentEvent>> = async { follow(LIONS_TIGERS) }
        val otherFan: Deferred<List<ServerSentEvent>> = async { follow(BEARS_WOLVES) }
        awaitSubscribers(3)

        publish(LIONS_TIGERS, ServerSentEvent(data = "Lions 1-0 Tigers (12')", event = "goal", id = "lt-1"))
        publish(BEARS_WOLVES, ServerSentEvent(data = "Yellow card, Bears #4 (20')", event = "card", id = "bw-1"))
        publish(LIONS_TIGERS, ServerSentEvent(data = "Half-time: Lions 1-0 Tigers", event = "half-time", id = "lt-2"))

        val lateFan: Deferred<List<ServerSentEvent>> = async { follow(LIONS_TIGERS) }
        awaitSubscribers(4)

        publish(LIONS_TIGERS, ServerSentEvent(data = "Lions 1-1 Tigers (67')", event = "goal", id = "lt-3"))
        publish(BEARS_WOLVES, ServerSentEvent(data = "Bears 0-1 Wolves (81')", event = "goal", id = "bw-2"))
        publish(LIONS_TIGERS, ServerSentEvent(data = "Full-time: Lions 1-1 Tigers", event = FULL_TIME, id = "lt-4"))
        publish(BEARS_WOLVES, ServerSentEvent(data = "Full-time: Bears 0-1 Wolves", event = FULL_TIME, id = "bw-3"))

        val fromKickOff: List<ServerSentEvent> = homeFan.await()
        assertEquals(listOf("lt-1", "lt-2", "lt-3", "lt-4"), fromKickOff.map { it.id })
        assertEquals(listOf("goal", "half-time", "goal", FULL_TIME), fromKickOff.map { it.event })
        assertEquals("Full-time: Lions 1-1 Tigers", fromKickOff.last().data)
        assertEquals(fromKickOff, awayFan.await())

        val otherMatch: List<ServerSentEvent> = otherFan.await()
        assertEquals(
            listOf(
                ServerSentEvent(data = "Yellow card, Bears #4 (20')", event = "card", id = "bw-1"),
                ServerSentEvent(data = "Bears 0-1 Wolves (81')", event = "goal", id = "bw-2"),
                ServerSentEvent(data = "Full-time: Bears 0-1 Wolves", event = FULL_TIME, id = "bw-3"),
            ),
            otherMatch,
        )

        val fromHalfTime: List<ServerSentEvent> = lateFan.await()
        assertEquals(fromKickOff.drop(2), fromHalfTime)
        assertEquals("Lions 1-1 Tigers (67')", fromHalfTime.first().data)

        awaitSubscribers(0)
    }

    private companion object {
        const val FULL_TIME: String = "full-time"
        const val LIONS_TIGERS: String = "lions-tigers"
        const val BEARS_WOLVES: String = "bears-wolves"
    }
}
