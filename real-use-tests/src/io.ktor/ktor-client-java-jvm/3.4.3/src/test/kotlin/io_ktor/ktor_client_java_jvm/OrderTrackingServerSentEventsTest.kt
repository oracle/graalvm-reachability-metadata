/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Server-sent event streams through the client SSE plugin over the Java engine, as a storefront
 * follows an order's shipping progress pushed by a tracking service.
 */
public class OrderTrackingServerSentEventsTest {

    /** One step in an order's journey, as the tracking service records it. */
    data class Milestone(val id: String, val kind: String, val detail: String)

    /** The tracking service: streams each order's milestones and resumes after `Last-Event-ID`. */
    class TrackingService : AutoCloseable {
        private val milestones: List<Milestone> = listOf(
            Milestone("1", "placed", "order A-17 placed"),
            Milestone("2", "packed", "2 items packed\nbox 40x30x20"),
            Milestone("3", "shipped", "carrier PostNord"),
            Milestone("4", "delivered", "left at reception"),
        )

        /** The `Last-Event-ID` header of every stream request, `<none>` when absent, in arrival order. */
        val resumeHeaders: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/orders/{number}/events") {
                    val lastSeen: String? = call.request.headers["Last-Event-ID"]
                    resumeHeaders += lastSeen ?: "<none>"
                    val order: String = call.parameters["number"]!!
                    send(ServerSentEvent(comments = "tracking $order"))
                    send(ServerSentEvent(retry = 5_000))
                    milestones
                        .dropWhile { lastSeen != null && it.id.toInt() <= lastSeen.toInt() }
                        .forEach { send(ServerSentEvent(data = it.detail, event = it.kind, id = it.id)) }
                }
                get("/orders/{number}/summary") {
                    call.respondText("order ${call.parameters["number"]}: delivered")
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun trackingClient(service: TrackingService, showControlEvents: Boolean = false): HttpClient =
        HttpClient(Java) {
            defaultRequest { url("http://127.0.0.1:${service.port}/") }
            install(ClientSSE) {
                maxReconnectionAttempts = 0
                if (showControlEvents) {
                    showCommentEvents()
                    showRetryEvents()
                }
            }
        }

    @Test
    fun followsAnOrderUntilTheServiceEndsTheStream(): Unit = runBlocking {
        TrackingService().use { service: TrackingService ->
            val received: MutableList<ServerSentEvent> = mutableListOf()
            trackingClient(service).use { client: HttpClient ->
                client.sse("orders/A-17/events") {
                    incoming.collect { event: ServerSentEvent -> received += event }
                }
            }

            // A multi-line payload arrives as several `data:` fields; the storefront reads it line by line.
            val milestones: List<Milestone> = received.map { event: ServerSentEvent ->
                Milestone(event.id!!, event.event!!, event.data!!.lines().joinToString("\n"))
            }
            assertThat(milestones).containsExactly(
                Milestone("1", "placed", "order A-17 placed"),
                Milestone("2", "packed", "2 items packed\nbox 40x30x20"),
                Milestone("3", "shipped", "carrier PostNord"),
                Milestone("4", "delivered", "left at reception"),
            )
            assertThat(service.resumeHeaders).containsExactly("<none>")
        }
    }

    @Test
    fun surfacesCommentAndRetryFieldsWhenTheClientAsksForThem(): Unit = runBlocking {
        TrackingService().use { service: TrackingService ->
            trackingClient(service, showControlEvents = true).use { client: HttpClient ->
                val session: ClientSSESession = client.sseSession("orders/B-02/events")
                val firstThree: List<ServerSentEvent> = session.incoming.take(3).toList()

                assertThat(firstThree[0].comments).isEqualTo("tracking B-02")
                assertThat(firstThree[0].data).isNull()
                assertThat(firstThree[1].retry).isEqualTo(5_000L)
                assertThat(firstThree[2].event).isEqualTo("placed")
                assertThat(firstThree[2].data).isEqualTo("order A-17 placed")
            }
        }
    }

    @Test
    fun resumesAfterTheLastEventTheStorefrontProcessed(): Unit = runBlocking {
        TrackingService().use { service: TrackingService ->
            val resumed: MutableList<String> = mutableListOf()
            trackingClient(service).use { client: HttpClient ->
                client.sse("orders/A-17/events", request = { header("Last-Event-ID", "2") }) {
                    incoming.collect { event: ServerSentEvent -> resumed += "${event.id}:${event.event}" }
                }
            }

            assertThat(resumed).containsExactly("3:shipped", "4:delivered")
            assertThat(service.resumeHeaders).containsExactly("2")
        }
    }

    @Test
    fun rejectsAnEndpointThatDoesNotStreamEvents(): Unit = runBlocking {
        TrackingService().use { service: TrackingService ->
            trackingClient(service).use { client: HttpClient ->
                assertThatThrownBy {
                    runBlocking { client.sse("orders/A-17/summary") { incoming.collect { } } }
                }.isInstanceOfSatisfying(SSEClientException::class.java) { failure: SSEClientException ->
                    assertThat(failure.response?.status).isEqualTo(HttpStatusCode.OK)
                    assertThat(failure.message).contains("text/event-stream")
                }

                assertThatThrownBy {
                    runBlocking { client.sse("tracking/unknown") { incoming.collect { } } }
                }.isInstanceOfSatisfying(SSEClientException::class.java) { failure: SSEClientException ->
                    assertThat(failure.response?.status).isEqualTo(HttpStatusCode.NotFound)
                }
            }
            assertThat(service.resumeHeaders).isEmpty()
        }
    }
}
