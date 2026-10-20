/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ChannelWriterContent
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicLong
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Streaming request and response bodies through the `Apache5` client engine.
 *
 * A backup tool uploads a multi-megabyte archive as a channel-written body of unknown length and
 * downloads a chunked export by reading the response channel incrementally, without buffering
 * either side in memory. The archive passes through the engine's request entity producer and
 * response consumer, and the application checks byte counts, SHA-256 digests and the totals its
 * upload and download progress listeners report.
 */
class ArchiveStreamingTransferTest {

    /** Deterministic archive content: block after block of a repeating byte pattern. */
    object ArchiveContent {
        const val BLOCK_SIZE: Int = 64 * 1024

        fun block(index: Int): ByteArray = ByteArray(BLOCK_SIZE) { offset ->
            ((index * 31 + offset) % 251).toByte()
        }

        fun expectedDigest(blocks: Int): String {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
            repeat(blocks) { digest.update(block(it)) }
            return HexFormat.of().formatHex(digest.digest())
        }

        suspend fun writeTo(channel: ByteWriteChannel, blocks: Int) {
            repeat(blocks) { channel.writeFully(block(it)) }
        }

        /** Drains [channel] chunk by chunk and returns the byte count and the SHA-256 digest. */
        suspend fun drain(channel: ByteReadChannel): Pair<Long, String> {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val read: Int = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                digest.update(buffer, 0, read)
                total += read
            }
            return total to HexFormat.of().formatHex(digest.digest())
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/backups") {
                    val (size: Long, sha256: String) = ArchiveContent.drain(call.receiveChannel())
                    val encoding: String = call.request.headers[HttpHeaders.TransferEncoding] ?: "none"
                    call.respondText("stored $size bytes sha256=$sha256 transfer=$encoding")
                }
                get("/exports/{blocks}") {
                    val blocks: Int = call.parameters["blocks"]!!.toInt()
                    call.respondBytesWriter(ContentType.Application.OctetStream) {
                        ArchiveContent.writeTo(this, blocks)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun backupClient(): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        engine {
            socketTimeout = 30_000
        }
    }

    @Test
    fun uploadsArchiveAsStreamedRequestBody() {
        val blocks = 48
        val archiveSize: Long = blocks.toLong() * ArchiveContent.BLOCK_SIZE
        val uploadedBytes = AtomicLong()
        val announcedLength = AtomicLong(-1)

        backupClient().use { client ->
            runBlocking {
                val response: HttpResponse = client.post("http://127.0.0.1:$port/backups") {
                    setBody(
                        ChannelWriterContent(
                            body = { ArchiveContent.writeTo(this, blocks) },
                            contentType = ContentType.Application.OctetStream,
                        ),
                    )
                    onUpload { bytesSentTotal, contentLength ->
                        uploadedBytes.set(bytesSentTotal)
                        announcedLength.set(contentLength ?: -1)
                    }
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val expected = "stored $archiveSize bytes sha256=${ArchiveContent.expectedDigest(blocks)}"
                assertEquals("$expected transfer=chunked", response.bodyAsText())
            }
        }
        assertEquals(archiveSize, uploadedBytes.get())
        assertEquals(-1, announcedLength.get())
    }

    @Test
    fun downloadsChunkedExportIncrementally() {
        val blocks = 80
        val exportSize: Long = blocks.toLong() * ArchiveContent.BLOCK_SIZE
        val downloadedBytes = AtomicLong()

        backupClient().use { client ->
            runBlocking {
                val statement = client.prepareGet("http://127.0.0.1:$port/exports/$blocks") {
                    onDownload { bytesReceivedTotal, _ -> downloadedBytes.set(bytesReceivedTotal) }
                }
                statement.execute { response: HttpResponse ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertNull(response.headers[HttpHeaders.ContentLength])
                    assertTrue(response.headers[HttpHeaders.TransferEncoding]!!.contains("chunked"))

                    val (size: Long, sha256: String) = ArchiveContent.drain(response.bodyAsChannel())
                    assertEquals(exportSize, size)
                    assertEquals(ArchiveContent.expectedDigest(blocks), sha256)
                }
            }
        }
        assertEquals(exportSize, downloadedBytes.get())
    }
}
