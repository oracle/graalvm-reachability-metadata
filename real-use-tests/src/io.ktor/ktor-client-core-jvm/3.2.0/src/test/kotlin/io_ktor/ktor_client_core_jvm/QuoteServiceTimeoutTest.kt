/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpTimeout` plugin bounding how long a request and a socket read may take.
 *
 * A trading desk client talks to a quote service with a tight client-wide request timeout:
 * a live quote arrives in time, a stalled quote fails with `HttpRequestTimeoutException`
 * that the application can handle as an `IOException`, an end-of-day batch request relaxes
 * the limit for itself and completes once the service releases it, and a price feed that
 * goes silent fails the read with the client's socket timeout well before the request limit.
 */
class QuoteServiceTimeoutTest {

    private lateinit var service: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var servicePort: Int = 0

    /** Holds every stalled handler until the test or teardown lets it finish. */
    private val release: CompletableDeferred<Unit> = CompletableDeferred()

    /** Completed by the batch handler once the batch request has reached the service. */
    private val batchArrived: CompletableDeferred<Unit> = CompletableDeferred()

    @BeforeEach
    fun start() {
        service = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/quotes/{symbol}") {
                    call.respondText("${call.parameters["symbol"]} 101.25")
                }
                get("/stalled/{symbol}") {
                    release.await()
                    call.respondText("${call.parameters["symbol"]} late")
                }
                get("/batch/eod") {
                    batchArrived.complete(Unit)
                    release.await()
                    call.respondText("EOD AAPL=101.25 MSFT=402.10")
                }
                get("/feed/{symbol}") {
                    release.await()
                    call.respondText("${call.parameters["symbol"]} 101.30")
                }
            }
        }.start(wait = false)
        servicePort = runBlocking { service.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        release.complete(Unit)
        service.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun requestTimeoutFailsStalledQuotesButNotLiveOnes() {
        quoteClient().use { client: HttpClient ->
            runBlocking {
                val live: HttpResponse = client.get("quotes/AAPL")
                assertEquals(HttpStatusCode.OK, live.status)
                assertEquals("AAPL 101.25", live.bodyAsText())

                val stalled: HttpRequestTimeoutException = assertThrows(HttpRequestTimeoutException::class.java) {
                    runBlocking { client.get("stalled/MSFT") }
                }
                val message: String = stalled.message.orEmpty()
                assertTrue(message.startsWith("Request timeout has expired"), message)
                assertTrue(message.contains("/stalled/MSFT"), message)
                assertTrue(message.contains("request_timeout=$REQUEST_TIMEOUT_MS ms"), message)

                val fallback: String = try {
                    client.get("stalled/TSLA").bodyAsText()
                } catch (failure: IOException) {
                    "TSLA unavailable: ${failure::class.simpleName}"
                }
                assertEquals("TSLA unavailable: HttpRequestTimeoutException", fallback)

                val afterwards: HttpResponse = client.get("quotes/MSFT")
                assertEquals("MSFT 101.25", afterwards.bodyAsText())
            }
        }
    }

    @Test
    fun batchRequestRelaxesTheTimeoutForItself() {
        quoteClient().use { client: HttpClient ->
            runBlocking {
                val batch = async {
                    client.get("batch/eod") {
                        timeout { requestTimeoutMillis = RELAXED_TIMEOUT_MS }
                    }
                }
                withTimeout(RELAXED_TIMEOUT_MS) { batchArrived.await() }
                release.complete(Unit)

                val response: HttpResponse = batch.await()
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("EOD AAPL=101.25 MSFT=402.10", response.bodyAsText())
            }
        }
    }

    @Test
    fun silentFeedFailsTheReadWithSocketTimeout() {
        val client = HttpClient(CIO) {
            defaultRequest { url("http://127.0.0.1:$servicePort/") }
            install(HttpTimeout) {
                requestTimeoutMillis = RELAXED_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
        }

        client.use {
            runBlocking {
                val silence: SocketTimeoutException = assertThrows(SocketTimeoutException::class.java) {
                    runBlocking { it.get("feed/AAPL") }
                }
                val message: String = silence.message.orEmpty()
                assertTrue(message.startsWith("Socket timeout has expired"), message)
                assertTrue(message.contains("/feed/AAPL"), message)
                assertTrue(message.contains("socket_timeout=$SOCKET_TIMEOUT_MS"), message)
                assertTrue(silence is IOException)

                val live: HttpResponse = it.get("quotes/AAPL")
                assertEquals("AAPL 101.25", live.bodyAsText())
            }
        }
    }

    private fun quoteClient(): HttpClient = HttpClient(CIO) {
        defaultRequest { url("http://127.0.0.1:$servicePort/") }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS: Long = 300
        const val SOCKET_TIMEOUT_MS: Long = 300
        const val RELAXED_TIMEOUT_MS: Long = 30_000
    }
}
