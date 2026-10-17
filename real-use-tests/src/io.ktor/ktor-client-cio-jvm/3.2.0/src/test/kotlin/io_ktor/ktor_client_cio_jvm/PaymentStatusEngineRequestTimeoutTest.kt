/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Engine-wide request timeout of the CIO client engine and its per-request override.
 *
 * A payment-status client caps every call through the CIO engine's `requestTimeout`. Settled
 * payments answer at once; a payment still clearing at the acquirer holds its answer for far
 * longer than the cap, so the engine abandons that call with a request timeout while the client
 * keeps serving later calls. A reconciliation job that must wait for the acquirer lifts the cap
 * for its own call through the `HttpTimeout` plugin and receives the delayed answer.
 */
class PaymentStatusEngineRequestTimeoutTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Payment IDs in the order the server started answering them. */
    private val received: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/payments/{id}/status") {
                    val id: String = call.parameters["id"]!!
                    received += id
                    if (id.startsWith("clearing-")) {
                        delay(ACQUIRER_DELAY_MILLIS)
                        call.respondText("CLEARED:$id")
                    } else {
                        call.respondText("SETTLED:$id")
                    }
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
    fun engineTimeoutAbandonsSlowCallsUnlessTheRequestLiftsIt() {
        val client = HttpClient(ClientCIO) {
            engine {
                requestTimeout = ENGINE_TIMEOUT_MILLIS
            }
            install(HttpTimeout)
        }

        client.use {
            runBlocking {
                assertEquals("SETTLED:pay-1001", status(it, "pay-1001"))

                val failure: Throwable? = runCatching { status(it, "clearing-2002") }.exceptionOrNull()
                val timeout: HttpRequestTimeoutException = assertInstanceOf(HttpRequestTimeoutException::class.java, failure)
                assertTrue(
                    timeout.message!!.contains("/payments/clearing-2002/status"),
                    "the timeout names the abandoned call: ${timeout.message}",
                )

                assertEquals("SETTLED:pay-1003", status(it, "pay-1003"))

                val reconciled: HttpResponse = it.get(statusUrl("clearing-2004")) {
                    timeout { requestTimeoutMillis = RECONCILIATION_TIMEOUT_MILLIS }
                }
                assertEquals(HttpStatusCode.OK, reconciled.status)
                assertEquals("CLEARED:clearing-2004", reconciled.bodyAsText())
            }
        }

        assertEquals(listOf("pay-1001", "clearing-2002", "pay-1003", "clearing-2004"), received.toList())
    }

    private suspend fun status(client: HttpClient, id: String): String {
        val response: HttpResponse = client.get(statusUrl(id))
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private fun statusUrl(id: String): String = "http://127.0.0.1:$port/payments/$id/status"

    private companion object {
        const val ENGINE_TIMEOUT_MILLIS: Long = 2_000
        const val ACQUIRER_DELAY_MILLIS: Long = 5_000
        const val RECONCILIATION_TIMEOUT_MILLIS: Long = 60_000
    }
}
