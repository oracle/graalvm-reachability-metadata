/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryEvent
import io.ktor.client.plugins.HttpRetryEventData
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Retrying failed calls through the client `HttpRequestRetry` plugin over the OkHttp engine.
 *
 * A pricing client fetches quotes from a flaky quote service through the OkHttp engine. The
 * service answers the first lookups with 503, keeps one endpoint permanently broken, and lets
 * its first bulk export hang. The test checks that server errors are retried until a quote
 * arrives, with every retry tagged by `modifyRequest` and reported through
 * `HttpRequestRetryEvent`; that exhausted retries hand back the last error response; that a
 * per-request `noRetry()` returns the first 503 at once; that an attempt hitting its socket
 * timeout is retried when `retryOnTimeout` is enabled; and that a refused connection is retried
 * as configured before the `ConnectException` reaches the caller.
 */
class QuoteServiceRequestRetryTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val exportGate: CompletableDeferred<Unit> = CompletableDeferred()
    private val hits: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    private val retryHeaders: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/quotes/{symbol}") {
                    val symbol: String = call.parameters["symbol"]!!
                    val attempt: Int = hit("quotes/$symbol")
                    if (symbol == "ACME") {
                        retryHeaders.add(call.request.headers[RETRY_HEADER] ?: "none")
                    }
                    if (attempt <= 2) {
                        call.respondText("warming up", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        call.respondText("$symbol 101.25")
                    }
                }
                get("/quotes/history") {
                    hit("history")
                    call.respondText("history store offline", status = HttpStatusCode.InternalServerError)
                }
                get("/exports/daily") {
                    if (hit("export") == 1) {
                        exportGate.await()
                    }
                    call.respondText("ACME,101.25")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        exportGate.complete(Unit)
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun retriesServerErrorsTimeoutsAndRefusedConnections() {
        val events: CopyOnWriteArrayList<HttpRetryEventData> = CopyOnWriteArrayList()
        val client = HttpClient(OkHttp) {
            install(HttpTimeout)
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                retryOnException(maxRetries = 3, retryOnTimeout = true)
                constantDelay(millis = 10, randomizationMs = 0)
                modifyRequest { request -> request.headers.append(RETRY_HEADER, retryCount.toString()) }
            }
        }
        client.monitor.subscribe(HttpRequestRetryEvent) { event: HttpRetryEventData -> events.add(event) }
        client.use {
            runBlocking {
                val quote: HttpResponse = client.get("http://127.0.0.1:$port/quotes/ACME")
                assertEquals(HttpStatusCode.OK, quote.status)
                assertEquals("ACME 101.25", quote.bodyAsText())
                assertEquals(listOf("none", "1", "2"), retryHeaders.toList())
                assertEquals(listOf(1, 2), events.map { it.retryCount })
                assertTrue(events.all { it.response?.status == HttpStatusCode.ServiceUnavailable })
                assertTrue(events.all { it.cause == null })
                events.clear()

                val history: HttpResponse = client.get("http://127.0.0.1:$port/quotes/history")
                assertEquals(HttpStatusCode.InternalServerError, history.status)
                assertEquals("history store offline", history.bodyAsText())
                assertEquals(4, hits["history"]!!.get())
                assertEquals(listOf(1, 2, 3), events.map { it.retryCount })
                events.clear()

                val impatient: HttpResponse = client.get("http://127.0.0.1:$port/quotes/GLOBEX") {
                    retry { noRetry() }
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, impatient.status)
                assertEquals("warming up", impatient.bodyAsText())
                assertEquals(1, hits["quotes/GLOBEX"]!!.get())
                assertTrue(events.isEmpty())

                val export: HttpResponse = client.get("http://127.0.0.1:$port/exports/daily") {
                    timeout { socketTimeoutMillis = EXPORT_LIMIT_MILLIS }
                }
                assertEquals("ACME,101.25", export.bodyAsText())
                assertEquals(2, hits["export"]!!.get())
                assertEquals(1, events.size)
                assertNull(events.single().response)
                assertTrue(events.single().cause is SocketTimeoutException)
                events.clear()

                val closedPort: Int = ServerSocket(0).use { it.localPort }
                assertThrows<ConnectException> {
                    client.get("http://127.0.0.1:$closedPort/quotes/ACME")
                }
                assertEquals(listOf(1, 2, 3), events.map { it.retryCount })
                assertTrue(events.all { it.cause is ConnectException })
            }
        }
    }

    private fun hit(key: String): Int = hits.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()

    private companion object {
        const val RETRY_HEADER: String = "X-Retry-Count"
        const val EXPORT_LIMIT_MILLIS: Long = 500
    }
}
