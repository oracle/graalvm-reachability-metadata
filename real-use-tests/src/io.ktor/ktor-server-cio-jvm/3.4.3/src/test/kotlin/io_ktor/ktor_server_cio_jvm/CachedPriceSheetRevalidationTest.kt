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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.TextContent
import io.ktor.http.content.versions
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO conditional HTTP responses through ETag revalidation of a cached wholesale price sheet.
 */
class CachedPriceSheetRevalidationTest {
    @Test
    @Timeout(60)
    fun `avoids resending an unchanged sheet and refreshes the cache after a price update`(): Unit = runBlocking {
        val original: String = "sku,price_cents\nnotebook,450\npen,125\n"
        val revised: String = "sku,price_cents\nnotebook,475\npen,150\n"
        val sheet: AtomicReference<PriceSheet> = AtomicReference(PriceSheet(1, original))
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(ConditionalHeaders)
            routing {
                get("/wholesale/prices.csv") {
                    val snapshot: PriceSheet = sheet.get()
                    val content: TextContent = TextContent(snapshot.csv, ContentType.parse("text/csv")).apply {
                        versions = listOf(EntityTagVersion("prices-${snapshot.revision}"))
                    }
                    call.respond(content)
                }
                put("/wholesale/prices.csv") {
                    val csv: String = call.receiveText()
                    sheet.updateAndGet { previous: PriceSheet -> PriceSheet(previous.revision + 1, csv) }
                    call.respond(HttpStatusCode.NoContent)
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
                    val url: String = "http://127.0.0.1:$port/wholesale/prices.csv"
                    val downloaded: HttpResponse = client.get(url)
                    assertEquals(HttpStatusCode.OK, downloaded.status)
                    assertEquals(ContentType.parse("text/csv"), downloaded.contentType()?.withoutParameters())
                    val cachedBody: String = downloaded.bodyAsText()
                    assertEquals(original, cachedBody)
                    val cachedTag: String? = downloaded.headers[HttpHeaders.ETag]
                    assertNotNull(cachedTag, "The client needs an ETag to revalidate its local copy")
                    assertEquals("\"prices-1\"", cachedTag)

                    val unchanged: HttpResponse = client.get(url) {
                        header(HttpHeaders.IfNoneMatch, cachedTag!!)
                    }
                    assertEquals(HttpStatusCode.NotModified, unchanged.status)
                    assertEquals("", unchanged.bodyAsText(), "A cache hit must not transfer the price sheet again")

                    val published: HttpResponse = client.put(url) {
                        contentType(ContentType.parse("text/csv"))
                        setBody(revised)
                    }
                    assertEquals(HttpStatusCode.NoContent, published.status)
                    assertEquals("", published.bodyAsText())

                    // Revalidating the old cache must now deliver the new representation and validator.
                    val refreshed: HttpResponse = client.get(url) {
                        header(HttpHeaders.IfNoneMatch, cachedTag!!)
                    }
                    assertEquals(HttpStatusCode.OK, refreshed.status)
                    assertEquals(revised, refreshed.bodyAsText())
                    val refreshedTag: String? = refreshed.headers[HttpHeaders.ETag]
                    assertEquals("\"prices-2\"", refreshedTag)
                    assertNotEquals(cachedTag, refreshedTag)

                    val current: HttpResponse = client.get(url) {
                        header(HttpHeaders.IfNoneMatch, refreshedTag!!)
                    }
                    assertEquals(HttpStatusCode.NotModified, current.status)
                    assertEquals("", current.bodyAsText())
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private data class PriceSheet(val revision: Int, val csv: String)
}
