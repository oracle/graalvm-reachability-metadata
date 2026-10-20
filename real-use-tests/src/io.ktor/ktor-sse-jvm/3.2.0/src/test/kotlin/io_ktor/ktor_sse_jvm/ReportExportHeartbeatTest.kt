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
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Keeps an idle event stream alive with periodic server heartbeat events.
 *
 * A reporting service accepts an export request and holds the stream open
 * while the export runs, sending heartbeats so that proxies do not drop the
 * idle connection. Once the export job finishes, the service sends the
 * download link and ends the stream, which stops the heartbeats.
 */
public class ReportExportHeartbeatTest {

    /** What the export client observed for one event. */
    private data class Observed(
        val event: String?,
        val id: String?,
        val data: String?,
        val comments: String?,
    )

    private val pingEvent: ServerSentEvent = ServerSentEvent(event = "ping", data = "keep-alive")

    private val readyEvent: ServerSentEvent = ServerSentEvent(
        event = "export-ready",
        id = "export-7",
        data = "/downloads/report-7.csv",
    )

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    /** Completed by the scenario once the export job counts as finished. */
    private lateinit var exportFinished: CompletableDeferred<Unit>

    @BeforeEach
    fun startServer() {
        exportFinished = CompletableDeferred()
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/exports/7") {
                    heartbeat {
                        period = 50.milliseconds
                        event = pingEvent
                    }
                    exportFinished.await()
                    send(readyEvent)
                }
                sse("/exports/7/default-heartbeat") {
                    heartbeat {
                        period = 50.milliseconds
                    }
                    exportFinished.await()
                    send(readyEvent)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        exportFinished.complete(Unit)
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /**
     * Follows the export stream up to the ready event, finishing the job after [heartbeatsBeforeFinish] heartbeats.
     *
     * Reading stops at the ready event: the server cancels its heartbeat only after the handler
     * returns, so one heartbeat can still race onto the wire behind the ready event.
     */
    private suspend fun followExport(
        client: HttpClient,
        path: String,
        isHeartbeat: (ServerSentEvent) -> Boolean,
        heartbeatsBeforeFinish: Int,
    ): List<Observed> {
        val observed: MutableList<Observed> = mutableListOf()
        var heartbeats: Int = 0
        withTimeout(30.seconds) {
            client.sse(host = "127.0.0.1", port = port, path = path) {
                incoming.transformWhile { event: ServerSentEvent ->
                    emit(event)
                    event.event != readyEvent.event
                }.collect { event: ServerSentEvent ->
                    observed.add(
                        Observed(event = event.event, id = event.id, data = event.data, comments = event.comments),
                    )
                    if (isHeartbeat(event)) {
                        heartbeats++
                        if (heartbeats == heartbeatsBeforeFinish) {
                            exportFinished.complete(Unit)
                        }
                    }
                }
            }
        }
        return observed
    }

    private val ready: Observed = Observed(
        event = "export-ready",
        id = "export-7",
        data = "/downloads/report-7.csv",
        comments = null,
    )

    @Test
    fun `client sees named heartbeat events until the export is ready`() = runBlocking {
        val observed: List<Observed> = HttpClient(ClientCIO) {
            install(ClientSSE)
        }.use { client: HttpClient ->
            followExport(
                client = client,
                path = "/exports/7",
                isHeartbeat = { event: ServerSentEvent -> event.event == "ping" },
                heartbeatsBeforeFinish = 3,
            )
        }

        val ping: Observed = Observed(event = "ping", id = null, data = "keep-alive", comments = null)
        assertEquals(ready, observed.last())
        val beforeReady: List<Observed> = observed.dropLast(1)
        assertTrue(beforeReady.size >= 3, "expected at least three heartbeats, got $beforeReady")
        assertEquals(List(beforeReady.size) { ping }, beforeReady)
    }

    @Test
    fun `default heartbeat is a comment the client can opt in to see`() = runBlocking {
        val observed: List<Observed> = HttpClient(ClientCIO) {
            install(ClientSSE) {
                showCommentEvents()
            }
        }.use { client: HttpClient ->
            followExport(
                client = client,
                path = "/exports/7/default-heartbeat",
                isHeartbeat = { event: ServerSentEvent -> event.comments != null },
                heartbeatsBeforeFinish = 2,
            )
        }

        val heartbeat: Observed = Observed(event = null, id = null, data = null, comments = "heartbeat")
        assertEquals(ready, observed.last())
        val beforeReady: List<Observed> = observed.dropLast(1)
        assertTrue(beforeReady.size >= 2, "expected at least two heartbeats, got $beforeReady")
        assertEquals(List(beforeReady.size) { heartbeat }, beforeReady)
    }
}
