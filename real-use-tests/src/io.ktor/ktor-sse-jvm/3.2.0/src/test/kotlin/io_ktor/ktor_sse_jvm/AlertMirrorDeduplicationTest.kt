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
import io.ktor.sse.ServerSentEventMetadata
import io.ktor.sse.TypedServerSentEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Relies on the value semantics of server-sent events: equality, hashing, `copy` and destructuring.
 *
 * An on-call pager subscribes to two mirrored alert feeds that overlap, merges
 * what both deliver into one set so a duplicated alert pages only once, and
 * re-tags escalated alerts with `copy` before routing them, while the feeds
 * themselves stamp every alert from a shared event template.
 */
public class AlertMirrorDeduplicationTest {

    /** An alert raised by monitoring, before it becomes an event. */
    private data class Alert(
        val id: String,
        val text: String,
    )

    /** A parsed alert, as the pager routes it. */
    private data class Page(
        val severity: String,
        val host: String,
        val message: String,
    )

    /** The fields every alert event shares, filled in per alert with `copy`. */
    private val alertTemplate: ServerSentEvent = ServerSentEvent(event = "alert", retry = 5_000L)

    private val primaryFeed: List<Alert> = listOf(
        Alert(id = "a-1", text = "warning|db-1|disk 85% full"),
        Alert(id = "a-2", text = "critical|web-3|health check failing"),
        Alert(id = "a-3", text = "warning|cache-2|evictions rising"),
    )

    /** Overlaps the primary feed on `a-2` verbatim and on `a-3` with a revised text. */
    private val replicaFeed: List<Alert> = listOf(
        Alert(id = "a-2", text = "critical|web-3|health check failing"),
        Alert(id = "a-3", text = "critical|cache-2|evictions rising"),
        Alert(id = "a-4", text = "warning|queue-1|consumer lag 2m"),
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/alerts/primary") {
                    for (alert: Alert in primaryFeed) {
                        send(alertTemplate.copy(data = alert.text, id = alert.id))
                    }
                }
                sse("/alerts/replica") {
                    for (alert: Alert in replicaFeed) {
                        send(alertTemplate.copy(data = alert.text, id = alert.id))
                    }
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

    private suspend fun collect(path: String): List<ServerSentEvent> {
        val received: MutableList<ServerSentEvent> = mutableListOf()
        client.sse(host = "127.0.0.1", port = port, path = path) {
            incoming.collect { event: ServerSentEvent -> received.add(event) }
        }
        return received
    }

    private fun parse(event: ServerSentEvent): Page {
        val (data: String?, _, id: String?) = event
        val parts: List<String> = requireNotNull(data) { "Alert $id carries no data" }.split("|")
        return Page(severity = parts[0], host = parts[1], message = parts[2])
    }

    @Test
    fun `alerts delivered by both mirrors page only once`() = runBlocking {
        val primary: List<ServerSentEvent> = collect("/alerts/primary")
        val replica: List<ServerSentEvent> = collect("/alerts/replica")

        val expectedPrimary: List<ServerSentEvent> = primaryFeed.map { alert: Alert ->
            ServerSentEvent(data = alert.text, event = "alert", id = alert.id, retry = 5_000L)
        }
        assertEquals(expectedPrimary, primary)

        val duplicateOnPrimary: ServerSentEvent = primary.single { it.id == "a-2" }
        val duplicateOnReplica: ServerSentEvent = replica.single { it.id == "a-2" }
        assertEquals(duplicateOnPrimary, duplicateOnReplica)
        assertEquals(duplicateOnPrimary.hashCode(), duplicateOnReplica.hashCode())

        val revisedOnPrimary: ServerSentEvent = primary.single { it.id == "a-3" }
        val revisedOnReplica: ServerSentEvent = replica.single { it.id == "a-3" }
        assertNotEquals(revisedOnPrimary, revisedOnReplica)

        val paged: Set<ServerSentEvent> = LinkedHashSet<ServerSentEvent>().apply {
            addAll(primary)
            addAll(replica)
        }
        assertEquals(listOf("a-1", "a-2", "a-3", "a-3", "a-4"), paged.map { it.id })
        assertEquals(
            listOf("warning", "critical", "warning", "critical", "warning"),
            paged.map { parse(it).severity },
        )

        val latestById: Map<String?, ServerSentEvent> = paged.associateBy { it.id }
        assertEquals(
            Page(severity = "critical", host = "cache-2", message = "evictions rising"),
            parse(latestById.getValue("a-3")),
        )
    }

    @Test
    fun `escalated alerts are re-tagged with copy and routed by event name`() = runBlocking {
        val replica: List<ServerSentEvent> = collect("/alerts/replica")

        val routed: List<ServerSentEvent> = replica.map { event: ServerSentEvent ->
            if (parse(event).severity == "critical") {
                event.copy(event = "escalation", comments = "routed to on-call")
            } else {
                event
            }
        }

        val (data: String?, name: String?, id: String?, retry: Long?, comments: String?) = routed[0]
        assertEquals("critical|web-3|health check failing", data)
        assertEquals("escalation", name)
        assertEquals("a-2", id)
        assertEquals(5_000L, retry)
        assertEquals("routed to on-call", comments)

        assertEquals("alert", replica[0].event)
        assertEquals(null, replica[0].comments)
        assertEquals(replica[0], routed[0].copy(event = "alert", comments = null))
        assertEquals(replica[2], routed[2])

        val byRoute: Map<String?, List<String?>> = routed.groupBy(
            keySelector = { it.event },
            valueTransform = { it.id },
        )
        assertEquals(mapOf("escalation" to listOf("a-2", "a-3"), "alert" to listOf("a-4")), byRoute)

        val pages: List<TypedServerSentEvent<Page>> = routed.map { event: ServerSentEvent ->
            TypedServerSentEvent(data = parse(event), event = event.event, id = event.id)
        }
        val metadata: List<ServerSentEventMetadata<*>> = pages
        assertEquals(listOf("a-2", "a-3", "a-4"), metadata.map { it.id })
        assertEquals(
            TypedServerSentEvent(
                data = Page(severity = "warning", host = "queue-1", message = "consumer lag 2m"),
                event = "alert",
                id = "a-4",
            ),
            pages[2],
        )
        assertEquals(
            pages[0].copy(data = pages[0].data!!.copy(severity = "acknowledged")).data,
            Page(severity = "acknowledged", host = "web-3", message = "health check failing"),
        )
    }
}
