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
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
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

/** Exercises HTTP caching by revalidating a catalog's ETag and reusing its unchanged response body. */
class CatalogCacheTest {
    @Test
    @Timeout(60)
    fun readsCatalogAfterNotModifiedRevalidation(): Unit = runBlocking {
        withTimeout(45_000) {
            val catalogRequests = AtomicInteger()
            val notModifiedResponses = AtomicInteger()
            val catalogTag: String = "\"catalog-revision-7\""
            val catalogText: String = "field-notebook: USD 8.50"
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/catalog") {
                        catalogRequests.incrementAndGet()
                        call.response.headers.append(HttpHeaders.ETag, catalogTag)
                        // The catalog can change at any time, so every reuse needs validation.
                        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                        if (call.request.headers[HttpHeaders.IfNoneMatch] == catalogTag) {
                            notModifiedResponses.incrementAndGet()
                            call.respond(HttpStatusCode.NotModified)
                        } else {
                            call.respondText(catalogText, ContentType.Text.Plain)
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val catalogUrl: String = "http://127.0.0.1:$port/catalog"
                HttpClient(CIO) {
                    install(HttpCache)
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val firstCatalog: HttpResponse = client.get(catalogUrl)
                    assertEquals(HttpStatusCode.OK, firstCatalog.status)
                    assertEquals(catalogText, firstCatalog.bodyAsText())
                    assertEquals(catalogTag, firstCatalog.headers[HttpHeaders.ETag])
                    assertEquals(1, catalogRequests.get())
                    assertEquals(0, notModifiedResponses.get())

                    val revalidatedCatalog: HttpResponse = client.get(catalogUrl)
                    assertEquals(HttpStatusCode.OK, revalidatedCatalog.status)
                    assertEquals(catalogText, revalidatedCatalog.bodyAsText())
                    assertEquals(catalogTag, revalidatedCatalog.headers[HttpHeaders.ETag])
                    assertEquals(2, catalogRequests.get())
                    assertEquals(1, notModifiedResponses.get())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
