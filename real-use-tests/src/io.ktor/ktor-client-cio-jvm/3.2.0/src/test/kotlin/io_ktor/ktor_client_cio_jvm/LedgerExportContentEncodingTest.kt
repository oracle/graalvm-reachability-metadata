/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.compression.UnsupportedContentEncodingException
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
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.compression.ContentEncoding as ClientContentEncoding
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.compression.appliedDecoders as serverAppliedDecoders

/**
 * HTTP content coding over the CIO client engine (the client `ContentEncoding` plugin with gzip and deflate).
 *
 * A bookkeeping tool downloads a large ledger export that the reporting service gzips, the same
 * export from a legacy endpoint that only speaks deflate, and a short summary the service sends
 * uncompressed; it also uploads a gzip-compressed batch of journal entries. The tool reads plain
 * text throughout: the plugin advertises its codings in `Accept-Encoding`, undoes whichever coding
 * the service applied, compresses the upload on request, and refuses a coding it does not know.
 */
class LedgerExportContentEncodingTest {

    /** Upload the reporting service received: the codings it undid and the decoded text. */
    data class ReceivedUpload(val decoders: List<String>, val text: String)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** `Accept-Encoding` header of every download the service received. */
    private val receivedAcceptEncodings: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private val receivedUploads: ConcurrentLinkedQueue<ReceivedUpload> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                route("/reports") {
                    install(Compression) {
                        mode = CompressionConfig.Mode.All
                        gzip {
                            minimumSize(MINIMUM_COMPRESSED_SIZE)
                        }
                    }
                    get("/ledger.csv") {
                        receivedAcceptEncodings += call.request.headers[HttpHeaders.AcceptEncoding].orEmpty()
                        call.respondText(LEDGER_CSV, ContentType.Text.CSV)
                    }
                    get("/summary") {
                        receivedAcceptEncodings += call.request.headers[HttpHeaders.AcceptEncoding].orEmpty()
                        call.respondText(SUMMARY, ContentType.Text.Plain)
                    }
                    post("/journal") {
                        val text: String = call.receiveText()
                        receivedUploads += ReceivedUpload(call.request.serverAppliedDecoders, text)
                        call.respondText("accepted ${text.lines().size} entries", ContentType.Text.Plain)
                    }
                }
                route("/legacy") {
                    install(Compression) {
                        deflate {
                            minimumSize(MINIMUM_COMPRESSED_SIZE)
                        }
                    }
                    get("/ledger.csv") {
                        receivedAcceptEncodings += call.request.headers[HttpHeaders.AcceptEncoding].orEmpty()
                        call.respondText(LEDGER_CSV, ContentType.Text.CSV)
                    }
                }
                get("/archive/snapshot") {
                    call.response.header(HttpHeaders.ContentEncoding, "br")
                    call.respondBytes(byteArrayOf(0x1b, 0x2a, 0x00, 0x7f), ContentType.Application.OctetStream)
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
    fun bookkeepingToolReadsAndSendsCompressedLedgers() {
        val reportingUrl: String = "http://127.0.0.1:$port/"
        val client = HttpClient(ClientCIO) {
            install(ClientContentEncoding) {
                mode = ContentEncodingConfig.Mode.All
                gzip(0.9f)
                deflate(0.8f)
            }
            defaultRequest {
                url(reportingUrl)
            }
        }

        client.use {
            runBlocking {
                val gzipped: HttpResponse = client.get("reports/ledger.csv")
                assertEquals(HttpStatusCode.OK, gzipped.status)
                assertEquals(listOf("gzip"), gzipped.appliedDecoders)
                assertEquals(LEDGER_CSV, gzipped.bodyAsText())

                val deflated: HttpResponse = client.get("legacy/ledger.csv")
                assertEquals(HttpStatusCode.OK, deflated.status)
                assertEquals(listOf("deflate"), deflated.appliedDecoders)
                assertEquals(LEDGER_CSV, deflated.bodyAsText())

                val summary: HttpResponse = client.get("reports/summary")
                assertEquals(HttpStatusCode.OK, summary.status)
                assertEquals(emptyList<String>(), summary.appliedDecoders)
                assertEquals(SUMMARY, summary.bodyAsText())

                val upload: HttpResponse = client.post("reports/journal") {
                    compress("gzip")
                    contentType(ContentType.Text.Plain)
                    setBody(JOURNAL_ENTRIES)
                }
                assertEquals(HttpStatusCode.OK, upload.status)
                assertEquals("accepted $JOURNAL_ENTRY_COUNT entries", upload.bodyAsText())

                assertThrows(UnsupportedContentEncodingException::class.java) {
                    runBlocking { client.get("archive/snapshot").bodyAsText() }
                }
            }
        }

        assertEquals(listOf(ReceivedUpload(listOf("gzip"), JOURNAL_ENTRIES)), receivedUploads.toList())
        assertEquals(3, receivedAcceptEncodings.size)
        receivedAcceptEncodings.forEach { acceptEncoding ->
            assertEquals(listOf("gzip;q=0.9", "deflate;q=0.8"), acceptEncoding.split(",").map { it.trim() })
        }
    }

    @Test
    fun ledgerExportTravelsGzippedOnTheWire() {
        val plainClient = HttpClient(ClientCIO)

        plainClient.use {
            runBlocking {
                val response: HttpResponse = plainClient.get("http://127.0.0.1:$port/reports/ledger.csv") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
                val wireBytes: ByteArray = response.readRawBytes()
                val plainBytes: ByteArray = LEDGER_CSV.toByteArray(Charsets.UTF_8)
                assertTrue(wireBytes.size < plainBytes.size / 4, "export is ${wireBytes.size} bytes on the wire")
                val decoded: String = GZIPInputStream(ByteArrayInputStream(wireBytes)).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
                assertEquals(LEDGER_CSV, decoded)
            }
        }
    }

    private companion object {
        const val MINIMUM_COMPRESSED_SIZE: Long = 512

        const val SUMMARY: String = "balance=12840.55 EUR"

        const val JOURNAL_ENTRY_COUNT: Int = 200

        /** Ledger export large and repetitive enough that gzip and deflate shrink it well. */
        val LEDGER_CSV: String = buildString {
            append("date,account,description,debit,credit\n")
            for (day in 1..28) {
                for (entry in 1..12) {
                    val account: Int = 4000 + entry * 10
                    append("2026-02-${day.toString().padStart(2, '0')},$account,Invoice INV-$day-$entry,")
                    append("${entry * 125}.00,0.00\n")
                }
            }
        }

        val JOURNAL_ENTRIES: String = (1..JOURNAL_ENTRY_COUNT).joinToString("\n") { entry ->
            "JE-$entry;account=${1000 + entry % 7};amount=${entry * 3}.50;memo=Monthly accrual"
        }
    }
}
