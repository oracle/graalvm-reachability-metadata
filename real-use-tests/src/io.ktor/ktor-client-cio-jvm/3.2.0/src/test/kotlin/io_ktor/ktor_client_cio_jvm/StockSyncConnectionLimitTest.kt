/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.endpoint
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Per-route connection limit of the CIO client engine (`endpoint { maxConnectionsPerRoute }`).
 *
 * A stock-sync job fans out one lookup per SKU against the warehouse service at once, but the
 * service only tolerates two concurrent connections from the job. The job caps its CIO engine at
 * two connections per route, so the engine queues the remaining lookups and sends each one only
 * when an earlier lookup has finished; the service never sees more than two lookups in flight and
 * still answers every SKU.
 */
class StockSyncConnectionLimitTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Lookups the service is currently handling. */
    private val inFlight: AtomicInteger = AtomicInteger()

    /** Highest number of lookups the service handled at the same time. */
    private val peakInFlight: AtomicInteger = AtomicInteger()

    /** Completed once the service handles two lookups at the same time. */
    private val bothConnectionsBusy: CompletableDeferred<Unit> = CompletableDeferred()

    /** SKUs in the order the service received their lookups. */
    private val received: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/stock/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    received += sku
                    val current: Int = inFlight.incrementAndGet()
                    peakInFlight.accumulateAndGet(current, ::maxOf)
                    if (current == CONNECTION_LIMIT) {
                        bothConnectionsBusy.complete(Unit)
                    }
                    // Hold the first lookups until the job has filled every allowed connection.
                    withTimeout(GATE_TIMEOUT_MILLIS) { bothConnectionsBusy.await() }
                    // Leave the in-flight set before answering: the client frees its slot only
                    // after it reads the answer.
                    inFlight.decrementAndGet()
                    call.respondText("STOCK:$sku:${sku.length * 10}")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun engineQueuesLookupsBeyondThePerRouteConnectionLimit() {
        val client = HttpClient(ClientCIO) {
            engine {
                endpoint {
                    maxConnectionsPerRoute = CONNECTION_LIMIT
                }
            }
        }

        val answers: List<String> = client.use {
            runBlocking {
                val lookups: List<Deferred<String>> = SKUS.map { sku -> async { stock(it, sku) } }
                lookups.awaitAll()
            }
        }

        assertEquals(SKUS.map { sku -> "STOCK:$sku:${sku.length * 10}" }, answers)
        assertEquals(SKUS.toSet(), received.toSet())
        assertEquals(SKUS.size, received.size)
        assertEquals(CONNECTION_LIMIT, peakInFlight.get())
        assertEquals(0, inFlight.get())
    }

    private suspend fun stock(client: HttpClient, sku: String): String {
        val response: HttpResponse = client.get("http://127.0.0.1:$port/stock/$sku")
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private companion object {
        const val CONNECTION_LIMIT: Int = 2
        const val GATE_TIMEOUT_MILLIS: Long = 10_000
        val SKUS: List<String> = listOf("A-1", "BB-22", "CCC-333", "D-4", "EE-55", "FFF-666")
    }
}
