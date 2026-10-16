/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Parses a raw `text/event-stream` body into `ServerSentEvent` objects on the client.
 *
 * A CI service streams a build log it writes by hand, mixing comments, a
 * reconnection hint, multi-line data, compact `field:value` lines, CRLF line
 * endings and a vendor field, and a build dashboard reads the log back through
 * the Ktor SSE client. Multi-line data is joined with CRLF, and an event
 * without an `id` field keeps the last event ID seen on the stream.
 */
public class BuildLogStreamParsingTest {

    /** The fields of one parsed event that the dashboard looks at. */
    private data class Observed(
        val event: String?,
        val id: String?,
        val data: String?,
        val retry: Long?,
        val comments: String?,
    )

    private val buildLog: String =
        ": build 512 log stream\n" +
            "\n" +
            "retry: 3000\n" +
            "\n" +
            "event: step\n" +
            "id: 1\n" +
            "data: Compiling module core\n" +
            "data: 42 files\n" +
            "\n" +
            "event:step\n" +
            "id:2\n" +
            "data:Running tests\n" +
            "\n" +
            "event: step\r\n" +
            "x-runner: linux-amd64\r\n" +
            "data: Packaging\r\n" +
            "id: 3\r\n" +
            "\r\n" +
            "event: done\n" +
            "data: SUCCESS\n" +
            "\n"

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/builds/512/log") {
                    call.respondText(buildLog, ContentType.Text.EventStream)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private suspend fun readLog(client: HttpClient): List<Observed> {
        val observed: MutableList<Observed> = mutableListOf()
        client.sse(host = "127.0.0.1", port = port, path = "/builds/512/log") {
            incoming.collect { event: ServerSentEvent ->
                observed.add(
                    Observed(
                        event = event.event,
                        id = event.id,
                        data = event.data,
                        retry = event.retry,
                        comments = event.comments,
                    ),
                )
            }
        }
        return observed
    }

    private val steps: List<Observed> = listOf(
        Observed(
            event = "step",
            id = "1",
            data = "Compiling module core\r\n42 files",
            retry = null,
            comments = null,
        ),
        Observed(event = "step", id = "2", data = "Running tests", retry = null, comments = null),
        Observed(event = "step", id = "3", data = "Packaging", retry = null, comments = null),
        Observed(event = "done", id = "3", data = "SUCCESS", retry = null, comments = null),
    )

    @Test
    fun `dashboard receives only data events by default`() = runBlocking {
        val observed: List<Observed> = HttpClient(ClientCIO) {
            install(ClientSSE)
        }.use { client: HttpClient -> readLog(client) }

        assertEquals(steps, observed)
    }

    @Test
    fun `dashboard can also observe comments and the retry hint`() = runBlocking {
        val observed: List<Observed> = HttpClient(ClientCIO) {
            install(ClientSSE) {
                showCommentEvents()
                showRetryEvents()
            }
        }.use { client: HttpClient -> readLog(client) }

        val preamble: List<Observed> = listOf(
            Observed(
                event = null,
                id = null,
                data = null,
                retry = null,
                comments = "build 512 log stream",
            ),
            Observed(event = null, id = null, data = null, retry = 3_000L, comments = null),
        )
        assertEquals(preamble + steps, observed)
        assertEquals("SUCCESS", observed.last { it.event == "done" }.data)
    }
}
