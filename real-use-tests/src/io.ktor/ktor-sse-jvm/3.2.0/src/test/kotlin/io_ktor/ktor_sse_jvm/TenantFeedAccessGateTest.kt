/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Serves SSE routes nested under a route that a route-scoped server plugin gates.
 *
 * A multi-tenant activity service nests each tenant's SSE feeds under
 * `/tenants/{tenant}` and installs an access-key plugin on that route only:
 * a subscriber without the tenant's key is rejected before the stream starts,
 * while the public status feed outside the route needs no key. The legacy feed
 * URL redirects to the nested feed, and the client follows it. Clients take
 * the base URL and their access key from `defaultRequest`, so application code
 * opens feeds by relative path.
 */
public class TenantFeedAccessGateTest {

    /** Configuration of the access-key plugin: the key each tenant accepts. */
    private class AccessKeyConfig {
        var keys: Map<String, String> = emptyMap()
    }

    private lateinit var server: EmbeddedServer<*, *>
    private val handledStreams: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        val accessKey = createRouteScopedPlugin("TenantAccessKey", ::AccessKeyConfig) {
            val keys: Map<String, String> = pluginConfig.keys
            onCall { call ->
                val tenant: String? = call.parameters["tenant"]
                val presented: String? = call.request.headers["X-Access-Key"]
                if (tenant == null || keys[tenant] != presented) {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/status") {
                    handledStreams.add("status")
                    send(ServerSentEvent(data = "all systems operational", event = "status"))
                }
                get("/legacy/{tenant}/activity") {
                    call.respondRedirect("/tenants/${call.parameters["tenant"]}/activity")
                }
                route("/tenants/{tenant}") {
                    install(accessKey) {
                        keys = mapOf("acme" to "acme-key", "globex" to "globex-key")
                    }
                    sse("/activity") {
                        val tenant: String = call.parameters["tenant"]!!
                        handledStreams.add("activity:$tenant")
                        send(ServerSentEvent(data = "$tenant: invoice paid", event = "activity", id = "1"))
                        send(ServerSentEvent(data = "$tenant: user invited", event = "activity", id = "2"))
                    }
                    route("/audit") {
                        sse {
                            val tenant: String = call.parameters["tenant"]!!
                            handledStreams.add("audit:$tenant")
                            send(ServerSentEvent(data = "$tenant: key rotated", event = "audit", id = "a-1"))
                        }
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** A tenant's client: base URL and access key come from `defaultRequest`. */
    private fun tenantClient(accessKey: String?): HttpClient {
        // Captured here: inside `defaultRequest`, `port` names the builder's own URL port.
        val baseUrl: String = "http://127.0.0.1:$port/"
        return HttpClient(ClientCIO) {
            install(ClientSSE)
            defaultRequest {
                url(baseUrl)
                if (accessKey != null) {
                    header("X-Access-Key", accessKey)
                }
            }
        }
    }

    /** Opens the feed at the relative [path] and returns the data of every event it delivers. */
    private suspend fun HttpClient.readFeed(path: String): List<String> = withTimeout(10_000L) {
        var events: List<ServerSentEvent> = emptyList()
        sse(path) {
            events = incoming.toList()
        }
        events.map { it.data!! }
    }

    /** Opens the feed at [path] expecting the server to refuse it, and returns the refusal status. */
    private suspend fun HttpClient.refusedStatus(path: String): HttpStatusCode = withTimeout(10_000L) {
        val refusal: SSEClientException = try {
            sse(path) { incoming.toList() }
            throw AssertionError("the feed at $path was not refused")
        } catch (failure: SSEClientException) {
            failure
        }
        requireNotNull(refusal.response).status
    }

    @Test
    fun `nested tenant feeds stream only to subscribers presenting the tenant key`() = runBlocking {
        val acme: HttpClient = tenantClient(accessKey = "acme-key")
        val anonymous: HttpClient = tenantClient(accessKey = null)
        try {
            assertEquals(listOf("acme: invoice paid", "acme: user invited"), acme.readFeed("tenants/acme/activity"))
            assertEquals(listOf("acme: key rotated"), acme.readFeed("tenants/acme/audit"))

            assertEquals(HttpStatusCode.Unauthorized, acme.refusedStatus("tenants/globex/activity"))
            assertEquals(HttpStatusCode.Unauthorized, anonymous.refusedStatus("tenants/acme/audit"))

            assertEquals(listOf("all systems operational"), anonymous.readFeed("status"))
        } finally {
            acme.close()
            anonymous.close()
        }
        assertEquals(listOf("activity:acme", "audit:acme", "status"), handledStreams.toList())
    }

    @Test
    fun `the legacy feed URL redirects into the gated tenant feed`() = runBlocking {
        val globex: HttpClient = tenantClient(accessKey = "globex-key")
        val acme: HttpClient = tenantClient(accessKey = "acme-key")
        try {
            assertEquals(
                listOf("globex: invoice paid", "globex: user invited"),
                globex.readFeed("legacy/globex/activity"),
            )
            assertEquals(HttpStatusCode.Unauthorized, acme.refusedStatus("legacy/globex/activity"))
        } finally {
            globex.close()
            acme.close()
        }
        assertEquals(listOf("activity:globex"), handledStreams.toList())
    }
}
