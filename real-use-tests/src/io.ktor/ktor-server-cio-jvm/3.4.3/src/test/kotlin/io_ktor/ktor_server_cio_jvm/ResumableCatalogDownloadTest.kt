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
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises CIO file transfer and byte-range responses through a resumable product catalog download.
 */
class ResumableCatalogDownloadTest {
    @Test
    @Timeout(60)
    fun `resumes a catalog download and rejects a range past the end`(@TempDir directory: Path): Unit = runBlocking {
        val catalog: ByteArray = buildString {
            append("sku,description,price\n")
            for (index: Int in 1..256) {
                append("sku-$index,Product $index,${index * 100}\n")
            }
        }.toByteArray(Charsets.UTF_8)
        val file: Path = Files.write(directory.resolve("catalog.csv"), catalog)
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(PartialContent)
            routing {
                get("/exports/catalog.csv") {
                    call.respondFile(file.toFile())
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
                    val url: String = "http://127.0.0.1:$port/exports/catalog.csv"
                    val first: HttpResponse = client.get(url) {
                        header(HttpHeaders.Range, "bytes=0-1023")
                    }
                    assertEquals(HttpStatusCode.PartialContent, first.status)
                    assertEquals("bytes 0-1023/${catalog.size}", first.headers[HttpHeaders.ContentRange])
                    assertEquals("1024", first.headers[HttpHeaders.ContentLength])
                    val downloaded: ByteArray = first.bodyAsBytes()
                    assertArrayEquals(catalog.copyOfRange(0, 1024), downloaded)

                    // A downloader persists the prefix and requests only the missing suffix.
                    val resumed: HttpResponse = client.get(url) {
                        header(HttpHeaders.Range, "bytes=${downloaded.size}-")
                    }
                    assertEquals(HttpStatusCode.PartialContent, resumed.status)
                    assertEquals(
                        "bytes 1024-${catalog.lastIndex}/${catalog.size}",
                        resumed.headers[HttpHeaders.ContentRange],
                    )
                    assertArrayEquals(catalog, downloaded + resumed.bodyAsBytes())

                    val outOfBounds: HttpResponse = client.get(url) {
                        header(HttpHeaders.Range, "bytes=${catalog.size}-")
                    }
                    assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, outOfBounds.status)
                    assertEquals("bytes */${catalog.size}", outOfBounds.headers[HttpHeaders.ContentRange])
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }
}
