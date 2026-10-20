/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.url
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Overrides the client SSE plugin's comment and retry event filters per request.
 *
 * A status page streams a banner comment, a reconnection hint and incident
 * updates. One shared HTTP client serves two consumers: the operator console,
 * which installs the SSE plugin to show every comment and retry event, and the
 * public status widget, which only wants incident data and turns both filters
 * off on its own calls. A third client keeps the plugin defaults and opts into
 * comments for a single session. Each override applies to its own request only
 * and leaves the plugin defaults in force for the next call.
 */
public class StatusPageEventFilterOverrideTest {

    /** The fields of one received event that a consumer renders. */
    private data class Observed(
        val event: String?,
        val data: String?,
        val retry: Long?,
        val comments: String?,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    private val banner: Observed = Observed(event = null, data = null, retry = null, comments = "status page v2")
    private val retryHint: Observed = Observed(event = null, data = null, retry = 4_000L, comments = null)
    private val incidents: List<Observed> = listOf(
        Observed(event = "incident", data = "API latency elevated", retry = null, comments = null),
        Observed(event = "incident", data = "API latency resolved", retry = null, comments = null),
    )

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/status") {
                    send(ServerSentEvent(comments = "status page v2"))
                    send(ServerSentEvent(retry = 4_000L))
                    send(ServerSentEvent(data = "API latency elevated", event = "incident", id = "1"))
                    send(ServerSentEvent(data = "API latency resolved", event = "incident", id = "2"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun observe(event: ServerSentEvent): Observed =
        Observed(event = event.event, data = event.data, retry = event.retry, comments = event.comments)

    /** A client whose SSE plugin shows comments and retry hints on every stream by default. */
    private fun operatorClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientSSE) {
            showCommentEvents()
            showRetryEvents()
        }
    }

    @Test
    fun `the widget hides comments and retry hints on its own calls of the operator client`() = runBlocking {
        operatorClient().use { client: HttpClient ->
            val console: MutableList<Observed> = mutableListOf()
            client.sse(host = "127.0.0.1", port = port, path = "/status") {
                incoming.collect { event: ServerSentEvent -> console.add(observe(event)) }
            }

            val widget: MutableList<Observed> = mutableListOf()
            client.sse(
                host = "127.0.0.1",
                port = port,
                path = "/status",
                showCommentEvents = false,
                showRetryEvents = false,
            ) {
                incoming.collect { event: ServerSentEvent -> widget.add(observe(event)) }
            }

            val consoleAgain: MutableList<Observed> = mutableListOf()
            client.sse(host = "127.0.0.1", port = port, path = "/status") {
                incoming.collect { event: ServerSentEvent -> consoleAgain.add(observe(event)) }
            }

            assertEquals(listOf(banner, retryHint) + incidents, console)
            assertEquals(incidents, widget)
            assertEquals(console, consoleAgain)
        }
    }

    @Test
    fun `each filter is overridden on its own`() = runBlocking {
        operatorClient().use { client: HttpClient ->
            val withoutRetry: MutableList<Observed> = mutableListOf()
            client.sse("http://127.0.0.1:$port/status", showRetryEvents = false) {
                incoming.collect { event: ServerSentEvent -> withoutRetry.add(observe(event)) }
            }

            val withoutComments: MutableList<Observed> = mutableListOf()
            client.sse("http://127.0.0.1:$port/status", showCommentEvents = false) {
                incoming.collect { event: ServerSentEvent -> withoutComments.add(observe(event)) }
            }

            assertEquals(listOf(banner) + incidents, withoutRetry)
            assertEquals(listOf(retryHint) + incidents, withoutComments)
        }
    }

    @Test
    fun `a session opts into comments on a client with the plugin defaults`() = runBlocking {
        HttpClient(ClientCIO) {
            install(ClientSSE)
        }.use { client: HttpClient ->
            val session: ClientSSESession = client.sseSession(showCommentEvents = true) {
                url(scheme = "http", host = "127.0.0.1", port = port, path = "/status")
            }
            val withComments: List<Observed> = withTimeout(10_000L) {
                session.incoming.toList().map { event: ServerSentEvent -> observe(event) }
            }
            session.cancel()

            val defaults: MutableList<Observed> = mutableListOf()
            client.sse(host = "127.0.0.1", port = port, path = "/status") {
                incoming.collect { event: ServerSentEvent -> defaults.add(observe(event)) }
            }

            assertEquals(listOf(banner) + incidents, withComments)
            assertEquals(incidents, defaults)
        }
    }
}
