/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.retry
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpRequestRetry` plugin working together with response validation.
 *
 * A warehouse dashboard reads stock levels from an inventory service that answers
 * `503 Service Unavailable` while it warms up. The test checks that the client retries
 * server errors up to its limit and marks each retry in a header, that a per-request
 * `retry { noRetry() }` turns retrying off, that client errors are never retried and are
 * translated into the application's own exception, and that a server error which outlasts
 * every retry surfaces as a `ServerResponseException`.
 */
class InventoryRetryingClientTest {

    /** Raised by the dashboard when the inventory service does not know a SKU. */
    class UnknownSkuException(val sku: String) : RuntimeException("unknown SKU $sku")

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val attempts: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()

    private fun attempt(path: String): Int = attempts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()

    private fun attemptsOf(path: String): Int = attempts[path]?.get() ?: 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/stock/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    val count: Int = attempt("/stock/$sku")
                    if (sku == "UNKNOWN-1") {
                        call.respondText("no such sku", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    if (count <= WARM_UP_FAILURES) {
                        call.respondText("warming up", status = HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val retry: String = call.request.headers[RETRY_HEADER] ?: "none"
                    call.respondText("sku=$sku qty=12 retry=$retry")
                }
                get("/reports/nightly") {
                    attempt("/reports/nightly")
                    call.respondText("report store offline", status = HttpStatusCode.InternalServerError)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun serverErrorsAreRetriedAndClientErrorsAreTranslated() {
        val base: String = "http://127.0.0.1:$port"
        val client = HttpClient(CIO) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = MAX_RETRIES)
                constantDelay(millis = 5, randomizationMs = 0)
                modifyRequest { request -> request.headers.append(RETRY_HEADER, retryCount.toString()) }
            }
            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause: Throwable, _ ->
                    if (cause is ClientRequestException && cause.response.status == HttpStatusCode.NotFound) {
                        throw UnknownSkuException(cause.response.call.request.url.segments.last())
                    }
                }
            }
        }

        client.use {
            runBlocking {
                val stock: HttpResponse = it.get("$base/stock/BOLT-7")
                assertEquals(HttpStatusCode.OK, stock.status)
                assertEquals("sku=BOLT-7 qty=12 retry=$WARM_UP_FAILURES", stock.bodyAsText())
                assertEquals(WARM_UP_FAILURES + 1, attemptsOf("/stock/BOLT-7"))

                val once: ServerResponseException = assertThrows(ServerResponseException::class.java) {
                    runBlocking { it.get("$base/stock/NUT-3") { retry { noRetry() } } }
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, once.response.status)
                assertEquals(1, attemptsOf("/stock/NUT-3"))

                val unknown: UnknownSkuException = assertThrows(UnknownSkuException::class.java) {
                    runBlocking { it.get("$base/stock/UNKNOWN-1") }
                }
                assertEquals("UNKNOWN-1", unknown.sku)
                assertEquals(1, attemptsOf("/stock/UNKNOWN-1"))

                val exhausted: ServerResponseException = assertThrows(ServerResponseException::class.java) {
                    runBlocking { it.get("$base/reports/nightly") }
                }
                assertEquals(HttpStatusCode.InternalServerError, exhausted.response.status)
                assertEquals("report store offline", exhausted.response.bodyAsText())
                assertEquals(MAX_RETRIES + 1, attemptsOf("/reports/nightly"))
            }
        }
    }

    private companion object {
        const val RETRY_HEADER: String = "X-Retry-Count"
        const val WARM_UP_FAILURES: Int = 2
        const val MAX_RETRIES: Int = 3
    }
}
