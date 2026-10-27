/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.compression.appliedDecoders
import io.ktor.client.plugins.compression.compress
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.appliedDecoders
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/**
 * Gzip and deflate content encoding through the client ContentEncoding plugin, as an operations
 * console downloads compressed daily logs from a log archive and uploads compressed log batches.
 */
public class LogArchiveContentEncodingTest {

    /** The log archive: compresses large responses and decompresses uploaded request bodies. */
    class LogArchive : AutoCloseable {
        /** Every `Accept-Encoding` header a log download carried, in arrival order. */
        val acceptEncodings: MutableList<String> = CopyOnWriteArrayList()

        /** The `Content-Encoding` header of each upload as it arrived, in arrival order. */
        val uploadEncodings: MutableList<String> = CopyOnWriteArrayList()

        /** The decoders the archive applied to each upload, in arrival order. */
        val uploadDecoders: MutableList<List<String>> = CopyOnWriteArrayList()

        /** The text of each upload after the archive decoded it, in arrival order. */
        val uploads: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(Compression) {
                mode = CompressionConfig.Mode.All
                gzip { minimumSize(1_024) }
                deflate { minimumSize(1_024) }
            }
            routing {
                get("/logs/{day}") {
                    acceptEncodings += call.request.headers[HttpHeaders.AcceptEncoding] ?: "<none>"
                    call.respondText(dailyLog(call.parameters["day"]!!), ContentType.Text.Plain)
                }
                get("/health") {
                    call.respondText("archive up", ContentType.Text.Plain)
                }
                post("/logs/batches") {
                    uploadEncodings += call.request.headers[HttpHeaders.ContentEncoding] ?: "<none>"
                    val batch: String = call.receiveText()
                    // The archive records its decoders only once it has decoded the received body.
                    uploadDecoders += call.request.appliedDecoders
                    uploads += batch
                    call.respondText("stored ${batch.lines().size} lines", status = HttpStatusCode.Created)
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun downloadsLogsInTheEncodingTheConsoleAccepts(): Unit = runBlocking {
        LogArchive().use { archive: LogArchive ->
            val gzipConsole: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${archive.port}/") }
                install(ContentEncoding) { gzip() }
            }
            gzipConsole.use {
                val log: HttpResponse = gzipConsole.get("logs/2026-10-01")
                assertThat(log.status).isEqualTo(HttpStatusCode.OK)
                assertThat(log.appliedDecoders).containsExactly("gzip")
                assertThat(log.bodyAsText()).isEqualTo(dailyLog("2026-10-01"))
            }

            val deflateConsole: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${archive.port}/") }
                install(ContentEncoding) { deflate() }
            }
            deflateConsole.use {
                val log: HttpResponse = deflateConsole.get("logs/2026-10-02")
                assertThat(log.status).isEqualTo(HttpStatusCode.OK)
                assertThat(log.appliedDecoders).containsExactly("deflate")
                assertThat(log.bodyAsText()).isEqualTo(dailyLog("2026-10-02"))

                // A body below the archive's minimum size travels uncompressed, so nothing decodes it.
                val health: HttpResponse = deflateConsole.get("health")
                assertThat(health.appliedDecoders).isEmpty()
                assertThat(health.bodyAsText()).isEqualTo("archive up")
            }

            assertThat(archive.acceptEncodings).hasSize(2)
            assertThat(archive.acceptEncodings[0]).contains("gzip").doesNotContain("deflate")
            assertThat(archive.acceptEncodings[1]).contains("deflate").doesNotContain("gzip")
        }
    }

    @Test
    fun receivesTheCompressedBytesWithoutThePlugin(): Unit = runBlocking {
        LogArchive().use { archive: LogArchive ->
            val rawConsole: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${archive.port}/") }
            }
            rawConsole.use {
                val log: HttpResponse = rawConsole.get("logs/2026-10-03") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertThat(log.headers[HttpHeaders.ContentEncoding]).isEqualTo("gzip")
                val compressed: ByteArray = log.readRawBytes()
                assertThat(compressed.take(2)).containsExactly(0x1f.toByte(), 0x8b.toByte())

                val expected: String = dailyLog("2026-10-03")
                assertThat(compressed.size).isLessThan(expected.length)
                val inflated: String = GZIPInputStream(ByteArrayInputStream(compressed))
                    .use { stream -> stream.readBytes().decodeToString() }
                assertThat(inflated).isEqualTo(expected)
            }
        }
    }

    @Test
    fun uploadsGzipCompressedLogBatches(): Unit = runBlocking {
        LogArchive().use { archive: LogArchive ->
            val shipper: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${archive.port}/") }
                install(ContentEncoding) {
                    mode = ContentEncodingConfig.Mode.All
                    gzip()
                }
            }
            shipper.use {
                val batch: String = dailyLog("2026-10-04")
                val compressedUpload: HttpResponse = shipper.post("logs/batches") {
                    compress("gzip")
                    contentType(ContentType.Text.Plain)
                    setBody(batch)
                }
                assertThat(compressedUpload.status).isEqualTo(HttpStatusCode.Created)
                assertThat(compressedUpload.bodyAsText()).isEqualTo("stored 200 lines")

                val plainUpload: HttpResponse = shipper.post("logs/batches") {
                    contentType(ContentType.Text.Plain)
                    setBody("2026-10-04T23:59:59Z INFO rotation complete")
                }
                assertThat(plainUpload.bodyAsText()).isEqualTo("stored 1 lines")
            }

            assertThat(archive.uploadEncodings).containsExactly("gzip", "<none>")
            assertThat(archive.uploadDecoders).containsExactly(listOf("gzip"), emptyList())
            assertThat(archive.uploads).containsExactly(
                dailyLog("2026-10-04"),
                "2026-10-04T23:59:59Z INFO rotation complete",
            )
        }
    }

    companion object {
        /** A repetitive day of access log lines, large enough for the archive to compress. */
        fun dailyLog(day: String): String = (0 until 200).joinToString("\n") { line: Int ->
            "${day}T%02d:%02d:00Z INFO GET /orders/${1_000 + line % 7} 200 ${12 + line % 5}ms"
                .format(line / 60 % 24, line % 60)
        }
    }
}
