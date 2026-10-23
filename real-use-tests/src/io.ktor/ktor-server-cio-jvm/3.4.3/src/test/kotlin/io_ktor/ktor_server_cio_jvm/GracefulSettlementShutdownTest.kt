/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO graceful shutdown by draining an in-flight payment settlement before the application stops.
 */
class GracefulSettlementShutdownTest {
    @Test
    @Timeout(60)
    fun `delivers the settlement receipt when shutdown starts during processing`(): Unit = runBlocking {
        val settlementAccepted: CompletableDeferred<Unit> = CompletableDeferred()
        val finishSettlement: CompletableDeferred<Unit> = CompletableDeferred()
        val shutdownPreparing: CompletableDeferred<Unit> = CompletableDeferred()
        val applicationStopped: CompletableDeferred<Unit> = CompletableDeferred()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                post("/settlements/{batch}") {
                    val batch: String = checkNotNull(call.parameters["batch"])
                    val amount: String = call.receiveText()
                    settlementAccepted.complete(Unit)
                    // A settlement already accepted by the application must finish during a rolling restart.
                    withTimeout(30_000) { finishSettlement.await() }
                    call.respondText("settled:$batch:$amount", ContentType.Text.Plain)
                }
            }
        }
        server.monitor.subscribe(ApplicationStopPreparing) { shutdownPreparing.complete(Unit) }
        server.monitor.subscribe(ApplicationStopped) { applicationStopped.complete(Unit) }
        var stopped: Boolean = false
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    expectSuccess = true
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 30_000
                        socketTimeoutMillis = 30_000
                    }
                }.use { client: HttpClient ->
                    val receipt = async {
                        val response: HttpResponse = client.post("http://127.0.0.1:$port/settlements/batch-42") {
                            contentType(ContentType.Text.Plain)
                            header(HttpHeaders.Connection, "close")
                            setBody("1700:USD")
                        }
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
                        response.bodyAsText()
                    }
                    settlementAccepted.await()
                    val shutdown = async { server.stopSuspend(15_000, 20_000) }
                    shutdownPreparing.await()
                    try {
                        assertFalse(applicationStopped.isCompleted, "An accepted settlement is still pending")
                        finishSettlement.complete(Unit)
                        assertEquals("settled:batch-42:1700:USD", receipt.await())
                        shutdown.await()
                        stopped = true
                        assertTrue(applicationStopped.isCompleted, "Shutdown must finish after draining the request")
                    } finally {
                        finishSettlement.complete(Unit)
                    }
                }
            }
        } finally {
            finishSettlement.complete(Unit)
            if (!stopped) server.stop(0, 10_000)
        }
    }
}
