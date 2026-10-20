/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Streamed request and response bodies that the OkHttp engine pipes between Ktor byte channels
 * and OkHttp's request and response bodies.
 *
 * A media archive client uploads a recording as a body it writes chunk by chunk, of unknown
 * length; the archive service digests the stream as it arrives and reports how it was framed.
 * The client then exports the archive catalog, which the service writes line by line, and reads
 * it back incrementally from the response channel instead of buffering it. The test checks that
 * the service saw every byte the client wrote, chunk-encoded, and that the catalog lines
 * arrive in order.
 */
class MediaArchiveStreamingTest {

    /** A recording body of unknown length, written in fixed-size chunks as it is produced. */
    class RecordingBody(private val chunks: List<ByteArray>) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Application.OctetStream

        override suspend fun writeTo(channel: ByteWriteChannel) {
            for (chunk in chunks) {
                channel.writeFully(chunk)
                channel.flush()
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/recordings/{name}") {
                    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
                    val upload: ByteReadChannel = call.receiveChannel()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read: Int = upload.readAvailable(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        total += read
                    }
                    val name: String = call.parameters["name"]!!
                    val transfer: String = call.request.headers["Transfer-Encoding"] ?: "none"
                    call.respondText(
                        "$name $total ${HexFormat.of().formatHex(digest.digest())} $transfer",
                        status = HttpStatusCode.Created,
                    )
                }
                get("/catalog") {
                    call.respondBytesWriter(contentType = ContentType.Text.Plain) {
                        for (index in 1..CATALOG_SIZE) {
                            writeStringUtf8("recording-$index.wav\n")
                            flush()
                        }
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(0, 0)
    }

    @Test
    fun uploadsAStreamedRecordingAndReadsTheCatalogIncrementally() {
        val chunks: List<ByteArray> = (0 until CHUNK_COUNT).map { index ->
            ByteArray(CHUNK_SIZE) { offset -> ((index * 31 + offset) % 251).toByte() }
        }
        val expectedSize: Long = CHUNK_COUNT.toLong() * CHUNK_SIZE
        val expectedDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
        chunks.forEach { expectedDigest.update(it) }
        val expectedHex: String = HexFormat.of().formatHex(expectedDigest.digest())

        val client = HttpClient(OkHttp)
        client.use {
            runBlocking {
                val receipt: String = client.post("http://127.0.0.1:$port/recordings/session-7") {
                    setBody(RecordingBody(chunks))
                }.bodyAsText()
                val parts: List<String> = receipt.split(" ")
                assertEquals("session-7", parts[0])
                assertEquals(expectedSize, parts[1].toLong())
                assertEquals(expectedHex, parts[2])
                assertEquals("chunked", parts[3])

                val lines: MutableList<String> = ArrayList()
                client.prepareGet("http://127.0.0.1:$port/catalog").execute { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertNull(response.headers["Content-Length"])
                    val catalog: ByteReadChannel = response.bodyAsChannel()
                    while (true) {
                        val line: String = catalog.readLine() ?: break
                        lines.add(line)
                    }
                }
                assertEquals(CATALOG_SIZE, lines.size)
                assertEquals("recording-1.wav", lines.first())
                assertEquals("recording-$CATALOG_SIZE.wav", lines.last())
                assertTrue(lines.indices.all { lines[it] == "recording-${it + 1}.wav" })
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE: Int = 16 * 1024
        const val CHUNK_COUNT: Int = 24
        const val BUFFER_SIZE: Int = 8 * 1024
        const val CATALOG_SIZE: Int = 500
    }
}
