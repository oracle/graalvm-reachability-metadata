/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `DefaultRequest` and `UserAgent` plugins shaping every request a client sends.
 *
 * A billing API client is built once with a versioned base URL, a tenant header, an API key
 * query parameter and a product user agent. The test checks how relative and absolute paths
 * resolve against the base URL, that a request's own query parameters and headers win over
 * the defaults, that a fully qualified URL bypasses the base path, and that a client derived
 * with `HttpClient.config` keeps the inherited defaults while adding its own.
 */
class BillingApiDefaultRequestTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var billingPort: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/{segments...}") {
                    val query: String = call.request.queryParameters.entries()
                        .sortedBy { entry: Map.Entry<String, List<String>> -> entry.key }
                        .joinToString("&") { entry: Map.Entry<String, List<String>> ->
                            "${entry.key}=${entry.value.joinToString(",")}"
                        }
                    val tenant: String = call.request.headers.getAll(TENANT_HEADER)?.joinToString(",") ?: "-"
                    val role: String = call.request.headers[ROLE_HEADER] ?: "-"
                    val agent: String = call.request.headers[HttpHeaders.UserAgent] ?: "-"
                    call.respondText("path=${call.request.path()} query=$query tenant=$tenant role=$role agent=$agent")
                }
            }
        }.start(wait = false)
        billingPort = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun requestsResolveAgainstTheBillingDefaults() {
        billingClient().use { client: HttpClient ->
            runBlocking {
                val invoices: HttpResponse = client.get("invoices")
                assertEquals(HttpStatusCode.OK, invoices.status)
                assertEquals(
                    "path=/billing/v2/invoices query=apiKey=$API_KEY tenant=acme role=- agent=$AGENT",
                    invoices.bodyAsText(),
                )

                val page: String = client.get("invoices/INV-7") {
                    parameter("expand", "lines")
                }.bodyAsText()
                assertEquals(
                    "path=/billing/v2/invoices/INV-7 query=apiKey=$API_KEY&expand=lines tenant=acme role=- agent=$AGENT",
                    page,
                )

                val health: String = client.get("/health").bodyAsText()
                assertEquals("path=/health query=apiKey=$API_KEY tenant=acme role=- agent=$AGENT", health)

                val rotated: String = client.get("invoices") {
                    parameter("apiKey", "k-rotated")
                    header(TENANT_HEADER, "globex")
                }.bodyAsText()
                assertEquals(
                    "path=/billing/v2/invoices query=apiKey=k-rotated tenant=globex role=- agent=$AGENT",
                    rotated,
                )

                val direct: String = client.get("http://localhost:$billingPort/status").bodyAsText()
                assertEquals("path=/status query= tenant=acme role=- agent=$AGENT", direct)
            }
        }
    }

    @Test
    fun derivedClientAddsItsOwnDefaultsOnTopOfTheInheritedOnes() {
        val billing: HttpClient = billingClient()
        val admin: HttpClient = billing.config {
            defaultRequest {
                headers.appendIfNameAbsent(ROLE_HEADER, "admin")
            }
        }

        admin.use { adminClient: HttpClient ->
            billing.use { billingClient: HttpClient ->
                runBlocking {
                    val audit: String = adminClient.get("audit-log").bodyAsText()
                    assertEquals(
                        "path=/billing/v2/audit-log query=apiKey=$API_KEY tenant=acme role=admin agent=$AGENT",
                        audit,
                    )

                    val plain: String = billingClient.get("audit-log").bodyAsText()
                    assertEquals(
                        "path=/billing/v2/audit-log query=apiKey=$API_KEY tenant=acme role=- agent=$AGENT",
                        plain,
                    )
                }
            }
        }
    }

    private fun billingClient(): HttpClient = HttpClient(CIO) {
        install(UserAgent) {
            agent = AGENT
        }
        defaultRequest {
            url("http://127.0.0.1:$billingPort/billing/v2/")
            url {
                parameters.append("apiKey", API_KEY)
            }
            headers.appendIfNameAbsent(TENANT_HEADER, "acme")
        }
    }

    private companion object {
        const val TENANT_HEADER: String = "X-Tenant"
        const val ROLE_HEADER: String = "X-Role"
        const val API_KEY: String = "k-live-01"
        const val AGENT: String = "BillingSync/4.2"
    }
}
