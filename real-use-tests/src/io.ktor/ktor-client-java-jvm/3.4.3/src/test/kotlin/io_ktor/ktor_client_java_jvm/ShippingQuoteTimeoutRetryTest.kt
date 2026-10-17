/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryEvent
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Request timeouts and automatic retries through the HttpTimeout and HttpRequestRetry plugins over
 * the Java engine, as a checkout service asks a flaky carrier API for shipping quotes and rates.
 */
public class ShippingQuoteTimeoutRetryTest {

    /** The carrier API: answers quotes, fails on demand, and stalls until it shuts down. */
    class CarrierService : AutoCloseable {
        private val stalled: CompletableDeferred<Unit> = CompletableDeferred()
        private val quoteCalls: AtomicInteger = AtomicInteger()

        /** The `X-Retry-Attempt` header of every quote request, in arrival order. */
        val quoteAttempts: MutableList<String> = CopyOnWriteArrayList()

        /** How many times the always-failing tracking endpoint was called. */
        val trackingCalls: AtomicInteger = AtomicInteger()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/quotes/{zone}") {
                    quoteAttempts += call.request.headers["X-Retry-Attempt"] ?: "first"
                    if (quoteCalls.incrementAndGet() <= 2) {
                        call.respondText("carrier busy", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        call.respondText("zone ${call.parameters["zone"]}: 12.50 EUR")
                    }
                }
                get("/tracking/{parcel}") {
                    trackingCalls.incrementAndGet()
                    call.respondText("tracking database down", status = HttpStatusCode.InternalServerError)
                }
                get("/rates/express") {
                    call.respondText("express: 29.90 EUR")
                }
                get("/rates/freight") {
                    stalled.await()
                    call.respondText("freight: 410.00 EUR")
                }
                get("/health") {
                    call.respondText("ok")
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            stalled.complete(Unit)
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun retriesServerErrorsUntilTheCarrierAnswers(): Unit = runBlocking {
        CarrierService().use { service: CarrierService ->
            val retried: MutableList<Int> = CopyOnWriteArrayList()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${service.port}/") }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    constantDelay(millis = 10, randomizationMs = 0)
                    modifyRequest { request -> request.headers.append("X-Retry-Attempt", retryCount.toString()) }
                }
            }
            client.monitor.subscribe(HttpRequestRetryEvent) { event -> retried += event.retryCount }
            client.use {
                val quote: HttpResponse = client.get("quotes/eu-west")
                assertThat(quote.status).isEqualTo(HttpStatusCode.OK)
                assertThat(quote.bodyAsText()).isEqualTo("zone eu-west: 12.50 EUR")

                // Once retries are exhausted the application sees the carrier's last error response.
                val tracking: HttpResponse = client.get("tracking/PX-77") {
                    retry {
                        retryOnServerErrors(maxRetries = 1)
                        constantDelay(millis = 10, randomizationMs = 0)
                    }
                }
                assertThat(tracking.status).isEqualTo(HttpStatusCode.InternalServerError)
                assertThat(tracking.bodyAsText()).isEqualTo("tracking database down")
            }
            assertThat(service.quoteAttempts).containsExactly("first", "1", "2")
            assertThat(service.trackingCalls.get()).isEqualTo(2)
            assertThat(retried).containsExactly(1, 2, 1)
        }
    }

    @Test
    fun abandonsAStalledRequestAfterItsTimeout(): Unit = runBlocking {
        CarrierService().use { service: CarrierService ->
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${service.port}/") }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                }
            }
            client.use {
                val failure: HttpRequestTimeoutException = assertThrows<HttpRequestTimeoutException> {
                    client.get("rates/freight") {
                        timeout { requestTimeoutMillis = 10_000 }
                    }
                }
                assertThat(failure.message).contains("/rates/freight").contains("10000")

                // The client stays usable for requests that complete within the default timeout.
                val health: HttpResponse = client.get("health")
                assertThat(health.status).isEqualTo(HttpStatusCode.OK)
                assertThat(health.bodyAsText()).isEqualTo("ok")
            }
        }
    }

    @Test
    fun failsOverToTheBackupCarrierWhenThePrimaryRefusesConnections(): Unit = runBlocking {
        CarrierService().use { service: CarrierService ->
            val retiredPort: Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val causes: MutableList<Throwable?> = CopyOnWriteArrayList()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:$retiredPort/") }
                install(HttpRequestRetry) {
                    retryOnExceptionIf(maxRetries = 1) { _, cause: Throwable -> cause is ConnectException }
                    constantDelay(millis = 10, randomizationMs = 0)
                    modifyRequest { request -> request.url.port = service.port }
                }
            }
            client.monitor.subscribe(HttpRequestRetryEvent) { event -> causes += event.cause }
            client.use {
                val express: HttpResponse = client.get("rates/express")
                assertThat(express.status).isEqualTo(HttpStatusCode.OK)
                assertThat(express.bodyAsText()).isEqualTo("express: 29.90 EUR")
                assertThat(express.request.url.port).isEqualTo(service.port)
            }
            assertThat(causes).hasSize(1)
            assertThat(causes[0]).isInstanceOf(ConnectException::class.java)
        }
    }
}
