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
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises streaming HTTP responses by saving a binary account export to disk in bounded chunks. */
class ExportStreamingDownloadTest {
    @Test
    @Timeout(60)
    fun savesAnExportWithoutChangingItsBinaryContent(): Unit = runBlocking {
        withTimeout(45_000) {
            val export: ByteArray = ByteArray(131_113) { index: Int -> (index * 31 + 7).toByte() }
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/accounts/customer-42/export") {
                        call.response.headers.append(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=account-export.bin"
                        )
                        call.respondBytes(export, ContentType.Application.OctetStream)
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val destination: Path = Files.createTempFile("account-export-", ".bin")
                    try {
                        client.prepareGet("http://127.0.0.1:$port/accounts/customer-42/export")
                            .execute { response ->
                                assertEquals(HttpStatusCode.OK, response.status)
                                assertEquals(
                                    "attachment; filename=account-export.bin",
                                    response.headers[HttpHeaders.ContentDisposition]
                                )
                                assertEquals(export.size.toString(), response.headers[HttpHeaders.ContentLength])
                                val channel: ByteReadChannel = response.bodyAsChannel()
                                val buffer: ByteArray = ByteArray(4096)
                                var downloaded: Long = 0
                                Files.newOutputStream(destination).use { output ->
                                    while (true) {
                                        val count: Int = channel.readAvailable(buffer, 0, buffer.size)
                                        if (count == -1) break
                                        output.write(buffer, 0, count)
                                        downloaded += count
                                    }
                                }
                                assertEquals(export.size.toLong(), downloaded)
                            }
                        assertEquals(export.size.toLong(), Files.size(destination))
                        assertArrayEquals(export, Files.readAllBytes(destination))
                    } finally {
                        Files.deleteIfExists(destination)
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
