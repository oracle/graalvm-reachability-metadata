/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises HTTP request retries by retrieving inventory after transient service failures. */
class InventoryRetryTest {
    @Test
    @Timeout(60)
    fun retrievesInventoryWithoutRetryingInvalidWarehouseRequests(): Unit = runBlocking {
        withTimeout(45_000) {
            val inventoryAttempts = AtomicInteger()
            val invalidAttempts = AtomicInteger()
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/inventory") {
                        if (call.request.headers["X-Store"] != "store-42"
                            || call.request.queryParameters["warehouse"] != "east") {
                            invalidAttempts.incrementAndGet()
                            call.respondText("Unknown warehouse", status = HttpStatusCode.BadRequest)
                        } else if (inventoryAttempts.incrementAndGet() <= 2) {
                            call.respondText("Inventory updating", status = HttpStatusCode.ServiceUnavailable)
                        } else {
                            call.respondText("field-notebook: 18 available")
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val inventoryUrl: String = "http://127.0.0.1:$port/inventory"
                HttpClient(CIO) {
                    install(HttpRequestRetry) {
                        retryOnServerErrors(maxRetries = 2)
                        delayMillis { 0L }
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val inventory: HttpResponse = client.get(inventoryUrl) {
                        header("X-Store", "store-42")
                        parameter("warehouse", "east")
                    }
                    assertEquals(HttpStatusCode.OK, inventory.status)
                    assertEquals("field-notebook: 18 available", inventory.bodyAsText())
                    assertEquals(3, inventoryAttempts.get())
                    assertEquals(0, invalidAttempts.get())

                    val invalidWarehouse: HttpResponse = client.get(inventoryUrl) {
                        header("X-Store", "store-42")
                        parameter("warehouse", "unknown")
                    }
                    assertEquals(HttpStatusCode.BadRequest, invalidWarehouse.status)
                    assertEquals("Unknown warehouse", invalidWarehouse.bodyAsText())
                    assertEquals(1, invalidAttempts.get())
                    assertEquals(3, inventoryAttempts.get())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
