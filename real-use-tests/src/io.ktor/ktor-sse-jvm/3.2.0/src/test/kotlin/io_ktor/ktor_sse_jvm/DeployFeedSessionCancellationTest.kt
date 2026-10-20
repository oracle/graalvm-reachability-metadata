/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
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
 * Holds a long-lived client SSE session opened with sseSession and cancels it early.
 *
 * A deployment dashboard opens the deploy feed of a CI server as a session
 * object it keeps, rather than a block that ends with the stream: the request
 * carries a bearer token and a tenant query parameter, which the server reads
 * from the call to scope and authorize the stream. The server keeps sending
 * progress and deploy events until the client goes away; the dashboard takes
 * the first deploys it needs from the session's `incoming` flow, cancels the
 * session, and the server sees its stream close. A request with a wrong token
 * gets a single `denied` event and a stream the server ends itself.
 */
public class DeployFeedSessionCancellationTest {

    /** What the server recorded about one stream once it stopped sending. */
    private data class StreamRecord(
        val tenant: String,
        val eventsSent: Int,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val streamClosed: CompletableDeferred<StreamRecord> = CompletableDeferred()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/deploys") {
                    val tenant: String = call.request.queryParameters["tenant"] ?: "unknown"
                    if (call.request.headers[HttpHeaders.Authorization] != "Bearer $ACCESS_TOKEN") {
                        send(ServerSentEvent(data = "token rejected for $tenant", event = "denied"))
                        return@sse
                    }
                    var sent: Int = 0
                    try {
                        var build: Int = 1
                        while (true) {
                            send(ServerSentEvent(data = "$tenant build $build compiling", event = "progress"))
                            sent++
                            send(ServerSentEvent(data = "$tenant build $build deployed", event = "deploy", id = "$build"))
                            sent++
                            build++
                            delay(SEND_INTERVAL_MILLIS)
                        }
                    } finally {
                        streamClosed.complete(StreamRecord(tenant = tenant, eventsSent = sent))
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

    /** Opens the deploy feed of [tenant] as a session the caller owns and must cancel. */
    private suspend fun openFeed(tenant: String, token: String): ClientSSESession =
        client.sseSession(host = "127.0.0.1", port = port, path = "/deploys") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("tenant", tenant)
        }

    @Test
    fun `the dashboard takes the deploys it needs and cancelling the session closes the server stream`() = runBlocking {
        val session: ClientSSESession = openFeed(tenant = "acme", token = ACCESS_TOKEN)
        val deploys: List<String> = withTimeout(10_000L) {
            session.incoming
                .filter { event: ServerSentEvent -> event.event == "deploy" }
                .map { event: ServerSentEvent -> "#${event.id}: ${event.data}" }
                .take(3)
                .toList()
        }
        assertEquals(
            listOf("#1: acme build 1 deployed", "#2: acme build 2 deployed", "#3: acme build 3 deployed"),
            deploys,
        )

        session.cancel()
        val sessionJob: Job = requireNotNull(session.coroutineContext[Job])
        withTimeout(10_000L) { sessionJob.join() }
        assertTrue(sessionJob.isCancelled)

        val record: StreamRecord = withTimeout(30_000L) { streamClosed.await() }
        assertEquals("acme", record.tenant)
        assertTrue(record.eventsSent >= 6, "server sent ${record.eventsSent} events")
    }

    @Test
    fun `a wrong token gets one denied event and a stream the server ends`() = runBlocking {
        val session: ClientSSESession = openFeed(tenant = "globex", token = "expired-token")
        val events: List<ServerSentEvent> = withTimeout(10_000L) { session.incoming.toList() }
        assertEquals(listOf(ServerSentEvent(data = "token rejected for globex", event = "denied")), events)
        session.cancel()
        assertTrue(!streamClosed.isCompleted)
    }

    private companion object {
        const val ACCESS_TOKEN: String = "ci-dashboard-7f3a"
        const val SEND_INTERVAL_MILLIS: Long = 20L
    }
}
