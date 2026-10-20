/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/**
 * Server-Sent Events streams consumed through the `Apache5` client engine.
 *
 * A release dashboard follows a deployment by subscribing to the service's `text/event-stream`
 * of progress events. It reads typed, identified events with multi-line payloads until the
 * service ends the stream, and when it reconnects it sends the last event id it saw so the
 * service resumes after that event instead of replaying the whole deployment.
 */
class DeploymentProgressEventStreamTest {

    /** A progress step the dashboard renders, decoded from one received event. */
    data class ProgressStep(val id: String, val kind: String, val lines: List<String>)

    private val deploymentSteps: List<ServerSentEvent> = listOf(
        ServerSentEvent(data = "pulling image registry.local/billing:7.2", event = "stage", id = "1"),
        ServerSentEvent(data = "replica 1 of 2 ready\nreplica 2 of 2 ready", event = "stage", id = "2"),
        ServerSentEvent(data = "health checks passed", event = "stage", id = "3"),
        ServerSentEvent(data = "billing 7.2 is live", event = "completed", id = "4"),
    )

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerSSE)
            routing {
                sse("/deployments/billing/progress") {
                    val lastSeen: Int = call.request.headers["Last-Event-ID"]?.toInt() ?: 0
                    deploymentSteps.filter { it.id!!.toInt() > lastSeen }.forEach { send(it) }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun dashboardClient(): HttpClient = HttpClient(Apache5) {
        engine {
            connectTimeout = 5_000
            socketTimeout = 10_000
        }
        install(ClientSSE)
    }

    private fun progressUrl(): String = "http://127.0.0.1:$port/deployments/billing/progress"

    private fun ServerSentEvent.toStep(): ProgressStep = ProgressStep(id!!, event!!, data!!.lines())

    @Test
    fun dashboardFollowsWholeDeploymentStream() {
        dashboardClient().use { client ->
            runBlocking {
                var status: HttpStatusCode? = null
                var contentType: ContentType? = null
                val steps: MutableList<ProgressStep> = mutableListOf()
                client.sse(progressUrl()) {
                    status = call.response.status
                    contentType = ContentType.parse(call.response.headers[HttpHeaders.ContentType]!!)
                    incoming.toList().mapTo(steps) { it.toStep() }
                }

                assertEquals(HttpStatusCode.OK, status)
                assertTrue(contentType!!.match(ContentType.Text.EventStream))
                assertEquals(
                    listOf(
                        ProgressStep("1", "stage", listOf("pulling image registry.local/billing:7.2")),
                        ProgressStep("2", "stage", listOf("replica 1 of 2 ready", "replica 2 of 2 ready")),
                        ProgressStep("3", "stage", listOf("health checks passed")),
                        ProgressStep("4", "completed", listOf("billing 7.2 is live")),
                    ),
                    steps,
                )
            }
        }
    }

    @Test
    fun dashboardResumesAfterLastSeenEvent() {
        dashboardClient().use { client ->
            runBlocking {
                val resumed: MutableList<ProgressStep> = mutableListOf()
                client.sse(progressUrl(), request = { header("Last-Event-ID", "2") }) {
                    incoming.toList().mapTo(resumed) { it.toStep() }
                }

                assertEquals(
                    listOf(
                        ProgressStep("3", "stage", listOf("health checks passed")),
                        ProgressStep("4", "completed", listOf("billing 7.2 is live")),
                    ),
                    resumed,
                )
            }
        }
    }
}
