/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Request timeouts and automatic retries with the `HttpTimeout` and `HttpRequestRetry` plugins
 * on the `Apache5` engine.
 *
 * A checkout service talks to a flaky payment gateway: charges that fail with a transient
 * `503` are retried with an attempt header until the gateway accepts them, a gateway that
 * keeps failing surfaces as a server error once the retry budget runs out, a status call that
 * never answers is cut off by a per-request timeout, and a call whose first attempt hangs is
 * timed out and transparently retried. A hanging handler waits on a gate the test opens only
 * when it stops the gateway, so every timeout is certain rather than a race.
 */
class PaymentGatewayResilienceTest {

    /** The gateway's view of the traffic: how many attempts each charge took and what they carried. */
    class GatewayLedger {
        val attempts: MutableMap<String, AtomicInteger> = ConcurrentHashMap()
        val attemptHeaders: MutableList<String> = CopyOnWriteArrayList()

        fun record(key: String, attemptHeader: String?): Int {
            attemptHeaders.add("$key:${attemptHeader ?: "-"}")
            return attempts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        }

        fun count(key: String): Int = attempts[key]?.get() ?: 0
    }

    private val ledger: GatewayLedger = GatewayLedger()
    private val stalledResponses: CompletableDeferred<Unit> = CompletableDeferred()
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/charges/{id}") {
                    val id: String = call.parameters["id"]!!
                    val failuresBeforeSuccess: Int = call.request.queryParameters["flaky"]!!.toInt()
                    val attempt: Int = ledger.record(id, call.request.headers["X-Attempt"])
                    if (attempt <= failuresBeforeSuccess) {
                        call.respondText("gateway busy", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        call.respondText("charged $id on attempt $attempt", status = HttpStatusCode.Created)
                    }
                }
                get("/status") {
                    ledger.record("status", null)
                    stalledResponses.await()
                    call.respondText("late")
                }
                get("/refunds/{id}") {
                    val id: String = call.parameters["id"]!!
                    val attempt: Int = ledger.record(id, call.request.headers["X-Attempt"])
                    if (attempt == 1) {
                        stalledResponses.await()
                    }
                    call.respondText("refund $id settled")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        stalledResponses.complete(Unit)
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun gatewayClient(): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        // Installed before HttpTimeout so that each attempt, not the whole retry loop, gets the timeout.
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            constantDelay(millis = 10)
            modifyRequest { request -> request.header("X-Attempt", (retryCount + 1).toString()) }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
    }

    @Test
    fun transientGatewayFailuresAreRetriedUntilTheChargeSucceeds() = runBlocking {
        val gateway: String = "http://127.0.0.1:$port"
        gatewayClient().use { client: HttpClient ->
            val response: HttpResponse = client.post("$gateway/charges/order-17?flaky=2")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("charged order-17 on attempt 3", response.bodyAsText())
            assertEquals(3, ledger.count("order-17"))
            assertEquals(
                listOf("order-17:-", "order-17:2", "order-17:3"),
                ledger.attemptHeaders.filter { it.startsWith("order-17:") },
            )
        }
    }

    @Test
    fun exhaustedRetryBudgetSurfacesTheServerError() = runBlocking {
        val gateway: String = "http://127.0.0.1:$port"
        gatewayClient().use { client: HttpClient ->
            val failure: ServerResponseException = assertThrows(ServerResponseException::class.java) {
                runBlocking { client.post("$gateway/charges/order-99?flaky=10") }
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, failure.response.status)
            assertEquals(4, ledger.count("order-99"))
        }
    }

    @Test
    fun perRequestTimeoutCutsOffAGatewayThatNeverAnswers() = runBlocking {
        val gateway: String = "http://127.0.0.1:$port"
        HttpClient(Apache5) {
            install(HttpTimeout)
        }.use { client: HttpClient ->
            val failure: HttpRequestTimeoutException = assertThrows(HttpRequestTimeoutException::class.java) {
                runBlocking {
                    client.get("$gateway/status") {
                        timeout { requestTimeoutMillis = 300 }
                    }
                }
            }

            assertTrue(failure.message!!.contains("/status"), failure.message)
            assertTrue(failure.message!!.contains("300"), failure.message)
            assertEquals(1, ledger.count("status"))
        }
    }

    @Test
    fun timedOutAttemptIsRetriedTransparently() = runBlocking {
        val gateway: String = "http://127.0.0.1:$port"
        gatewayClient().use { client: HttpClient ->
            val response: HttpResponse = client.get("$gateway/refunds/order-23") {
                timeout { requestTimeoutMillis = 300 }
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("refund order-23 settled", response.bodyAsText())
            assertEquals(2, ledger.count("order-23"))
            assertEquals(
                listOf("order-23:-", "order-23:2"),
                ledger.attemptHeaders.filter { it.startsWith("order-23:") },
            )
        }
    }
}
