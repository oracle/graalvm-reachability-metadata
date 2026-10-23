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
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.appliedDecoders
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises HTTP content decoding by retrieving a supplier manifest with gzip and deflate compression. */
class SupplierManifestCompressionTest {
    @Test
    @Timeout(60)
    fun readsTheSameManifestFromCompressedAndUncompressedResponses(): Unit = runBlocking {
        withTimeout(45_000) {
            val manifest: String = buildString {
                append("sku,description,quantity\n")
                for (item: Int in 1..200) {
                    append("coffee-$item,Caf\u00e9 blend $item,12\n")
                }
            }
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                install(Compression) {
                    gzip()
                    deflate()
                    minimumSize(128)
                }
                routing {
                    get("/suppliers/42/manifest.csv") {
                        call.response.headers.append(HttpHeaders.ETag, "\"manifest-42-revision-7\"")
                        call.respondText(manifest, ContentType.Text.Plain)
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val manifestUrl: String = "http://127.0.0.1:$port/suppliers/42/manifest.csv"
                // Suppliers deploy both encodings; either must yield the same importable document.
                for (encoding: String in listOf("gzip", "deflate")) {
                    HttpClient(CIO) {
                        install(ContentEncoding) {
                            if (encoding == "gzip") gzip() else deflate()
                        }
                        install(HttpTimeout) {
                            requestTimeoutMillis = 15_000
                            connectTimeoutMillis = 15_000
                            socketTimeoutMillis = 15_000
                        }
                    }.use { client: HttpClient ->
                        val compressed: HttpResponse = client.get(manifestUrl)
                        assertEquals(HttpStatusCode.OK, compressed.status)
                        assertEquals(listOf(encoding), compressed.appliedDecoders)
                        assertEquals("\"manifest-42-revision-7\"", compressed.headers[HttpHeaders.ETag])
                        assertEquals(manifest, compressed.bodyAsText())

                        // A request opting out of compression still works through the configured client.
                        val uncompressed: HttpResponse = client.get(manifestUrl) {
                            header(HttpHeaders.AcceptEncoding, "identity")
                        }
                        assertEquals(HttpStatusCode.OK, uncompressed.status)
                        assertEquals(emptyList<String>(), uncompressed.appliedDecoders)
                        assertEquals(manifest, uncompressed.bodyAsText())
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
