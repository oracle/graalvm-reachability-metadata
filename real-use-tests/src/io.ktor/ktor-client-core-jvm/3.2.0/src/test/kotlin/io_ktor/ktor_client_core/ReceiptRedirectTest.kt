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
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises HTTP redirects by retrieving a receipt through a migrated URL with a relative location. */
class ReceiptRedirectTest {
    @Test
    @Timeout(60)
    fun retrievesReceiptFromAnOldBookmark(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/checkout/orders/order-42") {
                        call.response.headers.append(HttpHeaders.Location, "/checkout/receipts/order-42?view=plain")
                        call.respondText("Receipt moved", status = HttpStatusCode.MovedPermanently)
                    }
                    get("/checkout/receipts/order-42") {
                        if (call.request.queryParameters["view"] == "plain") {
                            call.respondText("Order order-42: 2 field notebooks", ContentType.Text.Plain)
                        } else {
                            call.respondText("Unknown receipt format", status = HttpStatusCode.BadRequest)
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val bookmark: String = "http://127.0.0.1:$port/checkout/orders/order-42"
                HttpClient(CIO) {
                    followRedirects = false
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val moved: HttpResponse = client.get(bookmark)
                    assertEquals(HttpStatusCode.MovedPermanently, moved.status)
                    assertEquals("/checkout/receipts/order-42?view=plain", moved.headers[HttpHeaders.Location])
                    assertEquals("Receipt moved", moved.bodyAsText())
                }
                HttpClient(CIO) {
                    followRedirects = true
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val receipt: HttpResponse = client.get(bookmark)
                    assertEquals(HttpStatusCode.OK, receipt.status)
                    assertEquals("Order order-42: 2 field notebooks", receipt.bodyAsText())
                    assertEquals(HttpMethod.Get, receipt.call.request.method)
                    assertEquals("/checkout/receipts/order-42", receipt.call.request.url.encodedPath)
                    assertEquals("plain", receipt.call.request.url.parameters["view"])
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
