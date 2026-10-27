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
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises default request configuration for a shipping API's base URL and shared request headers. */
class ShippingDefaultRequestTest {
    @Test
    @Timeout(60)
    fun retrievesShipmentInDefaultAndRequestedLanguages(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/api/shipments/{id}") {
                        if (call.request.headers["X-Shipping-Account"] != "merchant-42") {
                            call.respondText("Shipping account required", status = HttpStatusCode.Unauthorized)
                        } else {
                            val status: String = when (call.request.headers[HttpHeaders.AcceptLanguage]) {
                                "en" -> "Ready for collection"
                                "fr" -> "Pret pour le retrait"
                                else -> "Unsupported language"
                            }
                            call.respondText("${call.parameters["id"]}: $status")
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    defaultRequest {
                        url("http://127.0.0.1:$port/api/")
                        header("X-Shipping-Account", "merchant-42")
                        headers.appendIfNameAbsent(HttpHeaders.AcceptLanguage, "en")
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val shipment: HttpResponse = client.get("shipments/parcel-17")
                    assertEquals(HttpStatusCode.OK, shipment.status)
                    assertEquals("parcel-17: Ready for collection", shipment.bodyAsText())

                    // A customer's language overrides the service default for just this request.
                    val translatedShipment: HttpResponse = client.get("shipments/parcel-17") {
                        header(HttpHeaders.AcceptLanguage, "fr")
                    }
                    assertEquals(HttpStatusCode.OK, translatedShipment.status)
                    assertEquals("parcel-17: Pret pour le retrait", translatedShipment.bodyAsText())

                    val nextShipment: HttpResponse = client.get("shipments/parcel-18")
                    assertEquals(HttpStatusCode.OK, nextShipment.status)
                    assertEquals("parcel-18: Ready for collection", nextShipment.bodyAsText())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
