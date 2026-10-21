/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Handles SSE stream establishment failures on the client through SSEClientException.
 *
 * A mail client opens the live inbox stream of a server and falls back to
 * polling when the server cannot stream: a missing stream endpoint and an
 * endpoint that answers with JSON instead of `text/event-stream` both fail
 * with an exception that carries the HTTP response, so the client reads the
 * status and content type it observed. A `204 No Content` answer is not a
 * failure: it tells the client there is nothing to stream, and the stream
 * ends empty.
 */
public class InboxStreamFallbackTest {

    /** How the client obtained the inbox: live over SSE or by polling. */
    private data class InboxView(
        val source: String,
        val messages: List<String>,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/v2/inbox/stream") {
                    send(ServerSentEvent(data = "Welcome aboard", event = "message", id = "m-1"))
                    send(ServerSentEvent(data = "Your invoice is ready", event = "message", id = "m-2"))
                }
                get("/v1/inbox/stream") {
                    call.respondText(
                        text = """{"error":"streaming moved to /v2"}""",
                        contentType = ContentType.Application.Json,
                    )
                }
                get("/v3/inbox/stream") {
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/inbox") {
                    call.respondText(text = "Welcome aboard\nYour invoice is ready", contentType = ContentType.Text.Plain)
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

    /** Streams the inbox from [path], or polls `/inbox` when the server refuses to stream. */
    private suspend fun openInbox(path: String): Pair<InboxView, SSEClientException?> = withTimeout(10_000L) {
        try {
            var events: List<ServerSentEvent> = emptyList()
            client.sse(host = "127.0.0.1", port = port, path = path) {
                events = incoming.toList()
            }
            InboxView(source = "live", messages = events.mapNotNull { it.data }) to null
        } catch (failure: SSEClientException) {
            val polled: String = client.get("http://127.0.0.1:$port/inbox").bodyAsText()
            InboxView(source = "polled", messages = polled.lines()) to failure
        }
    }

    @Test
    fun `the client streams when it can and polls when the server cannot stream`() = runBlocking {
        val (live: InboxView, liveFailure: SSEClientException?) = openInbox("/v2/inbox/stream")
        assertEquals(InboxView(source = "live", messages = listOf("Welcome aboard", "Your invoice is ready")), live)
        assertEquals(null, liveFailure)

        val (missing: InboxView, missingFailure: SSEClientException?) = openInbox("/v4/inbox/stream")
        assertEquals("polled", missing.source)
        assertEquals(live.messages, missing.messages)
        val missingResponse: HttpResponse = requireNotNull(missingFailure?.response)
        assertEquals(HttpStatusCode.NotFound, missingResponse.status)

        val (json: InboxView, jsonFailure: SSEClientException?) = openInbox("/v1/inbox/stream")
        assertEquals("polled", json.source)
        val jsonResponse: HttpResponse = requireNotNull(jsonFailure?.response)
        assertEquals(HttpStatusCode.OK, jsonResponse.status)
        assertTrue(jsonResponse.contentType()!!.match(ContentType.Application.Json))
        assertTrue(jsonFailure.message!!.contains(ContentType.Text.EventStream.toString()), jsonFailure.message)
    }

    @Test
    fun `a no content answer ends the stream without a failure`() = runBlocking {
        val (quiet: InboxView, quietFailure: SSEClientException?) = openInbox("/v3/inbox/stream")
        assertEquals(InboxView(source = "live", messages = emptyList()), quiet)
        assertEquals(null, quietFailure)
    }
}
