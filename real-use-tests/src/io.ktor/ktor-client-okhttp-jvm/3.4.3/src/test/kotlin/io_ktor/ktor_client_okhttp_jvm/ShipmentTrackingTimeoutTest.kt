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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.ServerSocket
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The `HttpTimeout` limits that the OkHttp engine applies to its calls, and the exceptions it
 * reports when a call times out or cannot connect.
 *
 * A shipment tracking client polls a carrier service through the OkHttp engine with a generous
 * client-wide socket timeout. The carrier answers status lookups at once, but its manifest
 * export hangs without sending a byte. The test checks that a fast lookup succeeds under the
 * client-wide limits, that a per-request socket timeout surfaces as Ktor's socket timeout
 * exception, that a per-request request timeout surfaces as the plugin's request timeout
 * exception, that the client stays usable after both, and that a carrier that is down is
 * reported as a refused connection.
 */
class ShipmentTrackingTimeoutTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val manifestGate: CompletableDeferred<Unit> = CompletableDeferred()
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/shipments/{id}/status") {
                    call.respondText("${call.parameters["id"]} IN_TRANSIT")
                }
                get("/manifests/export") {
                    manifestGate.await()
                    call.respondText("manifest")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        manifestGate.complete(Unit)
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun reportsTimeoutsAndRefusedConnectionsWhileStayingUsable() {
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                socketTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
        }
        client.use {
            runBlocking {
                val status: HttpResponse = client.get("http://127.0.0.1:$port/shipments/SHP-41/status")
                assertEquals(HttpStatusCode.OK, status.status)
                assertEquals("SHP-41 IN_TRANSIT", status.bodyAsText())

                val socketTimeout: SocketTimeoutException = assertThrows {
                    client.get("http://127.0.0.1:$port/manifests/export") {
                        timeout { socketTimeoutMillis = SHORT_LIMIT_MILLIS }
                    }
                }
                assertTrue(socketTimeout.message!!.contains("/manifests/export"), socketTimeout.message)

                val requestTimeout: HttpRequestTimeoutException = assertThrows {
                    client.get("http://127.0.0.1:$port/manifests/export") {
                        timeout { requestTimeoutMillis = SHORT_LIMIT_MILLIS }
                    }
                }
                assertTrue(requestTimeout.message!!.contains("/manifests/export"), requestTimeout.message)
                assertTrue(requestTimeout.message!!.contains("$SHORT_LIMIT_MILLIS"), requestTimeout.message)

                val retried: HttpResponse = client.get("http://127.0.0.1:$port/shipments/SHP-42/status")
                assertEquals("SHP-42 IN_TRANSIT", retried.bodyAsText())

                val closedPort: Int = ServerSocket(0).use { it.localPort }
                assertThrows<ConnectException> {
                    client.get("http://127.0.0.1:$closedPort/shipments/SHP-43/status")
                }
            }
        }
    }

    private companion object {
        const val SHORT_LIMIT_MILLIS: Long = 300
    }
}
