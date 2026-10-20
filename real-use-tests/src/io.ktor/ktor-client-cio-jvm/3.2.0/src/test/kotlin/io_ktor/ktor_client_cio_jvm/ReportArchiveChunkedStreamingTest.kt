/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Chunked transfer-encoding streaming through the CIO client engine's HTTP/1.1 codec.
 *
 * A reporting job archives its daily sales rows: it streams the rows to the archive service as a
 * body of unknown length, which the CIO engine sends with `Transfer-Encoding: chunked`, and later
 * reads the archived rows back through `prepareGet { execute }` as a channel, line by line, while
 * the service is still producing them. The service holds back the rest of the export until the
 * job confirms it has read the header row, which only succeeds if the engine hands the body over
 * incrementally instead of buffering the whole response.
 */
class ReportArchiveChunkedStreamingTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Rows the archive service stored, in the order they arrived. */
    private val archivedRows: MutableList<String> = mutableListOf()

    /** `Transfer-Encoding` header of the upload as the archive service saw it. */
    private val uploadTransferEncoding: CompletableDeferred<String?> = CompletableDeferred()

    /** `Content-Length` header of the upload as the archive service saw it. */
    private val uploadContentLength: CompletableDeferred<String?> = CompletableDeferred()

    /** Completed by the job once it has read the export's header row. */
    private val headerRead: CompletableDeferred<Unit> = CompletableDeferred()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/archive/sales") {
                    uploadTransferEncoding.complete(call.request.headers[HttpHeaders.TransferEncoding])
                    uploadContentLength.complete(call.request.headers[HttpHeaders.ContentLength])
                    val upload: ByteReadChannel = call.receiveChannel()
                    var stored: Int = 0
                    while (true) {
                        val row: String = upload.readUTF8Line() ?: break
                        synchronized(archivedRows) { archivedRows += row }
                        stored++
                    }
                    call.respondText("ARCHIVED:$stored")
                }
                get("/archive/sales") {
                    call.respondBytesWriter(contentType = ContentType.Text.CSV) {
                        writeStringUtf8("$CSV_HEADER\n")
                        flush()
                        // The rest of the export waits until the job has read the header row.
                        withTimeout(GATE_TIMEOUT_MILLIS) { headerRead.await() }
                        val rows: List<String> = synchronized(archivedRows) { archivedRows.toList() }
                        for (row in rows) {
                            writeStringUtf8("$row\n")
                            flush()
                        }
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

    @Test
    fun streamsRowsUpAndDownWithChunkedTransferEncoding() {
        val client = HttpClient(ClientCIO)

        client.use {
            runBlocking {
                val upload: HttpResponse = client.post("http://127.0.0.1:$port/archive/sales") {
                    setBody(SalesRowsContent(SALES_ROWS))
                }
                assertEquals(HttpStatusCode.OK, upload.status)
                assertEquals("ARCHIVED:${SALES_ROWS.size}", upload.bodyAsText())

                val (header: String, rows: List<String>, encoding: String?) =
                    client.prepareGet("http://127.0.0.1:$port/archive/sales").execute { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        val export: ByteReadChannel = response.bodyAsChannel()
                        val firstLine: String = export.readUTF8Line()!!
                        // Only reachable if the header row arrived before the rest was written.
                        headerRead.complete(Unit)
                        val received: MutableList<String> = mutableListOf()
                        while (true) {
                            received += export.readUTF8Line() ?: break
                        }
                        Triple(firstLine, received, response.headers[HttpHeaders.TransferEncoding])
                    }

                assertEquals(CSV_HEADER, header)
                assertEquals(SALES_ROWS, rows)
                assertEquals("chunked", encoding)
            }
        }

        assertEquals("chunked", runBlocking { uploadTransferEncoding.await() })
        assertNull(runBlocking { uploadContentLength.await() })
        assertEquals(SALES_ROWS, synchronized(archivedRows) { archivedRows.toList() })
    }

    /** Upload body that writes one sales row at a time without announcing its length. */
    private class SalesRowsContent(private val rows: List<String>) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Text.CSV

        override suspend fun writeTo(channel: ByteWriteChannel) {
            for (row in rows) {
                channel.writeStringUtf8("$row\n")
                channel.flush()
            }
        }
    }

    private companion object {
        const val GATE_TIMEOUT_MILLIS: Long = 10_000
        const val CSV_HEADER: String = "store,sku,quantity,total"
        val SALES_ROWS: List<String> = listOf(
            "berlin,A-1,3,29.97",
            "berlin,BB-22,1,149.00",
            "lisbon,A-1,7,69.93",
            "oslo,CCC-333,2,18.50",
            "oslo,BB-22,4,596.00",
        )
    }
}
