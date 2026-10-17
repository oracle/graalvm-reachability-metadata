/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.HttpResponseInterceptor
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.util.Timeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Apache HttpAsyncClient tuning through the `Apache5` engine's `customizeClient`,
 * `customizeRequest` and `configureConnectionManager` hooks.
 *
 * A warehouse sync job talks to a stock service through a hand-tuned Apache client: native
 * request interceptors and default headers stamp every call with a tenant and a sequence
 * number while a response interceptor audits status codes, a single-connection pool makes
 * concurrent stock updates queue on one keep-alive connection, and a per-request response
 * timeout set on the Apache request config cuts off a reconciliation the service never answers.
 */
class WarehouseSyncApacheClientTuningTest {

    /** The stock service's view of the traffic it receives. */
    class StockServiceLog {
        val inFlight: AtomicInteger = AtomicInteger()
        val maxInFlight: AtomicInteger = AtomicInteger()
        val clientPorts: MutableList<Int> = CopyOnWriteArrayList()

        fun enter(clientPort: Int) {
            clientPorts.add(clientPort)
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
        }

        fun leave() {
            inFlight.decrementAndGet()
        }
    }

    private val log: StockServiceLog = StockServiceLog()
    private val holdArrived: CompletableDeferred<Unit> = CompletableDeferred()
    private val holdReleased: CompletableDeferred<Unit> = CompletableDeferred()
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var stockService: String

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/stock/{sku}") {
                    val headers: Headers = call.request.headers
                    call.respondText(
                        "${call.parameters["sku"]} tenant=${headers["X-Tenant"]} seq=${headers["X-Request-Sequence"]}",
                    )
                }
                post("/stock/{sku}") {
                    log.enter(call.request.local.remotePort)
                    try {
                        val quantity: String = call.receiveText()
                        call.respondText(
                            "${call.parameters["sku"]}=$quantity seq=${call.request.headers["X-Request-Sequence"]}",
                            status = HttpStatusCode.Accepted,
                        )
                    } finally {
                        log.leave()
                    }
                }
                get("/reconcile") {
                    holdArrived.complete(Unit)
                    holdReleased.await()
                    call.respondText("reconciled")
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        stockService = "http://127.0.0.1:$port"
    }

    @AfterEach
    fun stopServer() {
        holdReleased.complete(Unit)
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun nativeInterceptorsAndDefaultHeadersApplyToEveryCall() = runBlocking {
        val sequence: AtomicInteger = AtomicInteger()
        val auditedStatuses: MutableList<Int> = CopyOnWriteArrayList()
        HttpClient(Apache5) {
            engine {
                socketTimeout = 10_000
                customizeClient {
                    setDefaultHeaders(listOf(BasicHeader("X-Tenant", "acme-west")))
                    addRequestInterceptorLast(
                        HttpRequestInterceptor { request, _, _ ->
                            request.setHeader("X-Request-Sequence", sequence.incrementAndGet().toString())
                        },
                    )
                    addResponseInterceptorFirst(
                        HttpResponseInterceptor { response, _, _ -> auditedStatuses.add(response.code) },
                    )
                }
            }
        }.use { client: HttpClient ->
            val lookup: HttpResponse = client.get("$stockService/stock/sku-100")
            val update: HttpResponse = client.post("$stockService/stock/sku-200") { setBody("42") }

            assertEquals("sku-100 tenant=acme-west seq=1", lookup.bodyAsText())
            assertEquals(HttpStatusCode.Accepted, update.status)
            assertEquals("sku-200=42 seq=2", update.bodyAsText())
            assertEquals(listOf(200, 202), auditedStatuses)
        }
    }

    @Test
    fun singleConnectionPoolQueuesConcurrentUpdatesOnOneConnection() = runBlocking {
        HttpClient(Apache5) {
            engine {
                socketTimeout = 10_000
                configureConnectionManager {
                    setMaxConnTotal(1)
                    setMaxConnPerRoute(1)
                }
            }
        }.use { client: HttpClient ->
            val updates: List<Deferred<String>> = (1..5).map { index: Int ->
                async { client.post("$stockService/stock/sku-$index") { setBody("${index * 10}") }.bodyAsText() }
            }
            val bodies: List<String> = updates.awaitAll()

            assertEquals((1..5).map { index: Int -> "sku-$index=${index * 10} seq=null" }, bodies)
            assertEquals(5, log.clientPorts.size)
            assertEquals(1, log.clientPorts.toSet().size, "all updates share one pooled connection")
            assertEquals(1, log.maxInFlight.get(), "the pool never lets two updates run at once")
        }
    }

    @Test
    fun perRequestResponseTimeoutCutsOffAStalledReconciliation() = runBlocking {
        HttpClient(Apache5) {
            engine {
                socketTimeout = 30_000
                customizeRequest {
                    setResponseTimeout(Timeout.ofMilliseconds(300))
                }
            }
        }.use { client: HttpClient ->
            val failure: SocketTimeoutException = assertThrows(SocketTimeoutException::class.java) {
                runBlocking { client.get("$stockService/reconcile") }
            }
            assertTrue(failure.message!!.contains("/reconcile"), failure.message)
            assertTrue(holdArrived.isCompleted, "the reconciliation reached the stock service")

            val lookup: HttpResponse = client.get("$stockService/stock/sku-300")
            assertEquals("sku-300 tenant=null seq=null", lookup.bodyAsText())
        }
    }
}
