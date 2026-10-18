/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The `HttpClient` coroutine lifecycle: cancelling in-flight calls, closing the client and sharing a user-supplied engine.
 *
 * A report gateway fans out concurrent report requests from one client, abandons a slow
 * export when the user navigates away by cancelling the coroutine that runs it while the
 * client keeps serving other calls, refuses new calls once the client is closed, and
 * shares one engine between a reporting client and an audit client, so closing either
 * client leaves the engine serving the other and later clients until the engine itself
 * is closed.
 */
class ReportGatewayClientLifecycleTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var serverPort: Int = 0
    private val slowRequestArrived: CompletableDeferred<Unit> = CompletableDeferred()
    private val releaseSlowRequest: CompletableDeferred<String> = CompletableDeferred()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/reports/{id}") {
                    call.respondText("report ${call.parameters["id"]}")
                }
                get("/exports/slow") {
                    slowRequestArrived.complete(Unit)
                    val outcome: String = releaseSlowRequest.await()
                    call.respondText("export $outcome")
                }
                get("/audit") {
                    call.respondText("audit ok")
                }
            }
        }.start(wait = false)
        serverPort = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        releaseSlowRequest.complete("released")
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun concurrentCallsAndCancellationFollowTheCallerScope() {
        val client = HttpClient(CIO) {
            defaultRequest { url("http://127.0.0.1:$serverPort/") }
        }

        client.use {
            runBlocking {
                val reports: List<String> = coroutineScope {
                    val pending: List<Deferred<String>> = (1..5).map { id: Int ->
                        async { it.get("reports/$id").bodyAsText() }
                    }
                    pending.awaitAll()
                }
                assertEquals((1..5).map { id: Int -> "report $id" }, reports)

                var abandoned: Throwable? = null
                val export: Job = launch {
                    try {
                        it.get("exports/slow")
                    } catch (cancelled: CancellationException) {
                        abandoned = cancelled
                        throw cancelled
                    }
                }
                slowRequestArrived.await()
                export.cancelAndJoin()
                assertTrue(export.isCancelled)
                assertTrue(abandoned is CancellationException)

                assertTrue(it.isActive)
                assertEquals("report 9", it.get("reports/9").bodyAsText())
            }
        }
    }

    @Test
    fun aClosedClientRefusesNewCalls() {
        val client = HttpClient(CIO) {
            defaultRequest { url("http://127.0.0.1:$serverPort/") }
        }

        runBlocking {
            val response: HttpResponse = client.get("reports/4")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("report 4", response.bodyAsText())

            client.close()
            assertFalse(client.isActive)
            client.coroutineContext[Job]!!.join()
            assertTrue(client.coroutineContext[Job]!!.isCompleted)

            assertThrows(CancellationException::class.java) {
                runBlocking { client.get("reports/5") }
            }
        }
    }

    @Test
    fun aSharedEngineOutlivesTheClientsBuiltOnIt() {
        val engine: HttpClientEngine = CIO.create()
        val reporting = HttpClient(engine) {
            defaultRequest { url("http://127.0.0.1:$serverPort/") }
        }
        val audit = HttpClient(engine) {
            defaultRequest { url("http://127.0.0.1:$serverPort/") }
        }

        runBlocking {
            assertEquals("report 3", reporting.get("reports/3").bodyAsText())
            assertEquals("audit ok", audit.get("audit").bodyAsText())

            reporting.close()
            assertFalse(reporting.isActive)
            assertTrue(engine.isActive)
            assertEquals("audit ok", audit.get("audit").bodyAsText())

            audit.close()
            assertTrue(engine.isActive)
            val reopened = HttpClient(engine) {
                defaultRequest { url("http://127.0.0.1:$serverPort/") }
            }
            assertEquals("report 8", reopened.get("reports/8").bodyAsText())
            reopened.close()

            engine.close()
            engine.coroutineContext[Job]!!.join()
            assertFalse(engine.isActive)
        }
    }
}
