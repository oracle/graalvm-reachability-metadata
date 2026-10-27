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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.host
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO virtual-host routing through tenant-isolated storefront catalogs on one listener.
 */
class VirtualHostStorefrontTest {
    @Test
    @Timeout(60)
    fun `serves each merchant catalog only through its own host name`(): Unit = runBlocking {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                host("alpine.shop.test") {
                    get("/catalog/{sku}") {
                        if (call.parameters["sku"] == "mug") {
                            call.respondText("Alpine|mug|enamel|1800", ContentType.Text.Plain)
                        } else {
                            call.respondText("No Alpine product", status = HttpStatusCode.NotFound)
                        }
                    }
                }
                host("harbor.shop.test") {
                    get("/catalog/{sku}") {
                        when (call.parameters["sku"]) {
                            "mug" -> call.respondText("Harbor|mug|ceramic|1250", ContentType.Text.Plain)
                            "tea" -> call.respondText("Harbor|tea|earl-grey|800", ContentType.Text.Plain)
                            else -> call.respondText("No Harbor product", status = HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val baseUrl: String = "http://127.0.0.1:$port"
                    val alpineMug: HttpResponse = client.get("$baseUrl/catalog/mug") {
                        header(HttpHeaders.Host, "alpine.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.OK, alpineMug.status)
                    assertEquals("Alpine|mug|enamel|1800", alpineMug.bodyAsText())

                    val harborMug: HttpResponse = client.get("$baseUrl/catalog/mug") {
                        header(HttpHeaders.Host, "harbor.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.OK, harborMug.status)
                    assertEquals("Harbor|mug|ceramic|1250", harborMug.bodyAsText())

                    val harborTea: HttpResponse = client.get("$baseUrl/catalog/tea") {
                        header(HttpHeaders.Host, "harbor.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.OK, harborTea.status)
                    assertEquals("Harbor|tea|earl-grey|800", harborTea.bodyAsText())

                    val alpineTea: HttpResponse = client.get("$baseUrl/catalog/tea") {
                        header(HttpHeaders.Host, "alpine.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.NotFound, alpineTea.status)
                    assertEquals("No Alpine product", alpineTea.bodyAsText())

                    val unknownMerchant: HttpResponse = client.get("$baseUrl/catalog/mug") {
                        header(HttpHeaders.Host, "unknown.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.NotFound, unknownMerchant.status)
                    unknownMerchant.bodyAsText()

                    // An unrecognized host must not replace the next caller's tenant selection.
                    val returningShopper: HttpResponse = client.get("$baseUrl/catalog/mug") {
                        header(HttpHeaders.Host, "alpine.shop.test:$port")
                    }
                    assertEquals(HttpStatusCode.OK, returningShopper.status)
                    assertEquals("Alpine|mug|enamel|1800", returningShopper.bodyAsText())
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }
}
