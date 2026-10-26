/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO response compression negotiation through a downloadable sales report.
 */
class CompressedSalesExportTest {
    @Test
    @Timeout(60)
    fun `delivers the same complete export to gzip and identity clients`(): Unit = runBlocking {
        val report: String = buildString {
            append("order,region,total_cents\n")
            for (order: Int in 1..200) {
                append("order-$order,north,${order * 125}\n")
            }
        }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(Compression) {
                gzip {
                    minimumSize(1024)
                }
            }
            routing {
                get("/reports/sales.csv") {
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=sales.csv")
                    call.respondText(report, ContentType.parse("text/csv"))
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
                    val url: String = "http://127.0.0.1:$port/reports/sales.csv"
                    val compressed: HttpResponse = client.get(url) {
                        header(HttpHeaders.AcceptEncoding, "gzip")
                    }
                    assertEquals(HttpStatusCode.OK, compressed.status)
                    assertEquals("gzip", compressed.headers[HttpHeaders.ContentEncoding])
                    assertEquals(
                        "attachment; filename=sales.csv",
                        compressed.headers[HttpHeaders.ContentDisposition],
                    )
                    assertTrue(compressed.headers[HttpHeaders.ContentType]!!.startsWith("text/csv"))
                    val compressedBytes: ByteArray = compressed.body()
                    val unpacked: ByteArray = GZIPInputStream(compressedBytes.inputStream()).use { input ->
                        input.readBytes()
                    }
                    val expected: ByteArray = report.toByteArray(Charsets.UTF_8)
                    assertArrayEquals(expected, unpacked)
                    assertTrue(compressedBytes.size < expected.size, "The export should benefit from gzip")

                    val identity: HttpResponse = client.get(url) {
                        header(HttpHeaders.AcceptEncoding, "identity")
                    }
                    assertEquals(HttpStatusCode.OK, identity.status)
                    assertNull(identity.headers[HttpHeaders.ContentEncoding])
                    assertEquals(
                        "attachment; filename=sales.csv",
                        identity.headers[HttpHeaders.ContentDisposition],
                    )
                    val identityBytes: ByteArray = identity.body()
                    assertArrayEquals(expected, identityBytes)
                    assertArrayEquals(identityBytes, unpacked)
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }
}
