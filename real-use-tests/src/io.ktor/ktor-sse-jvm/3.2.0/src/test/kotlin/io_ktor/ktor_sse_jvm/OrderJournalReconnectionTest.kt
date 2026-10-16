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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Resumes a dropped event stream by reconnecting with the last received event ID.
 *
 * An order service journals order state changes as identified server-sent
 * events and drops each connection after a batch of three. A fulfilment client
 * configured to reconnect picks the stream up again, and the service replays
 * only the entries after the `Last-Event-ID` the client sends back.
 */
public class OrderJournalReconnectionTest {

    /** One journal entry: an order moved into a new state. */
    private data class JournalEntry(
        val sequence: Int,
        val orderId: String,
        val state: String,
    )

    private val journal: List<JournalEntry> = listOf(
        JournalEntry(sequence = 1, orderId = "A-100", state = "placed"),
        JournalEntry(sequence = 2, orderId = "A-100", state = "paid"),
        JournalEntry(sequence = 3, orderId = "B-200", state = "placed"),
        JournalEntry(sequence = 4, orderId = "A-100", state = "shipped"),
        JournalEntry(sequence = 5, orderId = "B-200", state = "cancelled"),
        JournalEntry(sequence = 6, orderId = "A-100", state = "delivered"),
    )

    /** The `Last-Event-ID` each connection to the journal carried, `none` when absent. */
    private val resumePoints: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    private fun toEvent(entry: JournalEntry): ServerSentEvent = ServerSentEvent(
        data = "${entry.orderId}=${entry.state}",
        event = "order-state",
        id = entry.sequence.toString(),
    )

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/journal") {
                    val lastEventId: String? = call.request.headers["Last-Event-ID"]
                    resumePoints.add(lastEventId ?: "none")
                    val after: Int = lastEventId?.toInt() ?: 0
                    journal
                        .filter { entry: JournalEntry -> entry.sequence > after }
                        .take(BATCH_SIZE)
                        .forEach { entry: JournalEntry -> send(toEvent(entry)) }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun readJournal(client: HttpClient, count: Int): List<ServerSentEvent> = runBlocking {
        var events: List<ServerSentEvent> = emptyList()
        client.sse(host = "127.0.0.1", port = port, path = "/journal") {
            events = incoming.take(count).toList()
        }
        events
    }

    @Test
    fun `client reconnects and resumes after the last received event`() {
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientSSE) {
                maxReconnectionAttempts = 3
                reconnectionTime = 10.milliseconds
            }
        }
        val events: List<ServerSentEvent> = client.use { readJournal(it, journal.size) }

        assertEquals(journal.map { entry: JournalEntry -> toEvent(entry) }, events)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), events.map { event: ServerSentEvent -> event.id })
        assertEquals(listOf("none", "3"), resumePoints.toList())

        val finalStates: Map<String, String> = events
            .map { event: ServerSentEvent -> event.data!!.split("=") }
            .associate { parts: List<String> -> parts[0] to parts[1] }
        assertEquals(mapOf("A-100" to "delivered", "B-200" to "cancelled"), finalStates)
    }

    @Test
    fun `client without reconnection stops at the dropped connection`() {
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientSSE)
        }
        val events: List<ServerSentEvent> = client.use { readJournal(it, journal.size) }

        assertEquals(journal.take(BATCH_SIZE).map { entry: JournalEntry -> toEvent(entry) }, events)
        assertEquals(listOf("none"), resumePoints.toList())
    }

    private companion object {
        /** Entries the service sends before it drops a connection. */
        const val BATCH_SIZE: Int = 3
    }
}
