/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises custom client plugins by propagating receipt request correlation IDs into an audit log. */
class ReceiptCorrelationPluginTest {
    @Test
    @Timeout(60)
    fun correlatesSuccessfulAndMissingReceiptRequests(): Unit = runBlocking {
        withTimeout(45_000) {
            val correlationId: AttributeKey<String> = AttributeKey("ReceiptCorrelationId")
            val auditLog: MutableList<String> = CopyOnWriteArrayList()
            val correlationPlugin = createClientPlugin("ReceiptCorrelation") {
                onRequest { request, _ ->
                    request.headers.append("X-Correlation-ID", request.attributes[correlationId])
                }
                onResponse { response ->
                    val requestId: String? = response.call.request.headers["X-Correlation-ID"]
                    auditLog.add("$requestId: HTTP ${response.status.value}")
                }
            }
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/receipts/{id}") {
                        val requestId: String? = call.request.headers["X-Correlation-ID"]
                        if (requestId.isNullOrBlank()) {
                            call.respondText("Correlation ID required", status = HttpStatusCode.BadRequest)
                        } else {
                            call.response.headers.append("X-Correlation-ID", requestId)
                            if (call.parameters["id"] == "order-42") {
                                call.respondText("Receipt order-42: USD 17.00")
                            } else {
                                call.respondText("Receipt not found", status = HttpStatusCode.NotFound)
                            }
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(correlationPlugin)
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val receipt: HttpResponse = client.get("http://127.0.0.1:$port/receipts/order-42") {
                        attributes.put(correlationId, "checkout-42")
                    }
                    assertEquals(HttpStatusCode.OK, receipt.status)
                    assertEquals("Receipt order-42: USD 17.00", receipt.bodyAsText())
                    assertEquals("checkout-42", receipt.headers["X-Correlation-ID"])

                    val missing: HttpResponse = client.get("http://127.0.0.1:$port/receipts/order-99") {
                        attributes.put(correlationId, "support-99")
                    }
                    assertEquals(HttpStatusCode.NotFound, missing.status)
                    assertEquals("Receipt not found", missing.bodyAsText())
                    assertEquals("support-99", missing.headers["X-Correlation-ID"])
                    assertEquals(listOf("checkout-42: HTTP 200", "support-99: HTTP 404"), auditLog)
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
