/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Streaming request and response bodies with the client's `onDownload` and `onUpload` progress.
 *
 * An accounting tool pulls a large ledger export as CSV and pushes a batch of journal lines
 * back to the bookkeeping service. The test checks that a prepared statement executed with
 * `execute { }` lets the application read the response line by line from its body channel,
 * that download progress climbs monotonically to the announced `Content-Length`, that a
 * streamed `WriteChannelContent` upload reaches the server byte for byte with its declared
 * content type and length, and that upload progress ends at the number of bytes written.
 */
class LedgerExportStreamingTest {

    /** One booked line of the ledger as the accounting tool models it. */
    data class LedgerEntry(val id: Int, val account: String, val cents: Long)

    /** Streams journal lines to the service without building the whole body in memory. */
    class JournalUpload(private val lines: List<String>) : OutgoingContent.WriteChannelContent() {
        private val bytes: Int = lines.sumOf { it.toByteArray(Charsets.UTF_8).size }

        override val contentType: ContentType = ContentType.Text.CSV

        override val contentLength: Long = bytes.toLong()

        override suspend fun writeTo(channel: ByteWriteChannel) {
            for (line: String in lines) {
                channel.writeStringUtf8(line)
                channel.flush()
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val ledger: ByteArray = buildLedger()
    private val receivedUploads: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    private val uploadTypes: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/exports/ledger.csv") {
                    call.respondBytes(ledger, ContentType.Text.CSV)
                }
                post("/journal") {
                    uploadTypes.add(call.request.headers[HttpHeaders.ContentType] ?: "none")
                    val channel: ByteReadChannel = call.receiveChannel()
                    val body: StringBuilder = StringBuilder()
                    var lines: Int = 0
                    while (true) {
                        val line: String = channel.readUTF8Line() ?: break
                        body.append(line).append('\n')
                        lines++
                    }
                    receivedUploads.add(body.toString())
                    call.respondText("accepted $lines lines", status = HttpStatusCode.Accepted)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun ledgerIsStreamedDownAndJournalIsStreamedUp() {
        val base: String = "http://127.0.0.1:$port"
        val downloadProgress: CopyOnWriteArrayList<Pair<Long, Long?>> = CopyOnWriteArrayList()
        val uploadProgress: CopyOnWriteArrayList<Pair<Long, Long?>> = CopyOnWriteArrayList()

        HttpClient(CIO).use { client: HttpClient ->
            runBlocking {
                val entries: List<LedgerEntry> = client.prepareGet("$base/exports/ledger.csv") {
                    onDownload { sent: Long, total: Long? -> downloadProgress.add(sent to total) }
                }.execute { response: HttpResponse ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(ContentType.Text.CSV, response.contentType()?.withoutParameters())
                    assertEquals(ledger.size.toLong(), response.contentLength())
                    readLedger(response.bodyAsChannel())
                }

                assertEquals(ENTRY_COUNT, entries.size)
                assertEquals(LedgerEntry(1, "4000-sales", 101L), entries.first())
                val sales: Long = (ENTRY_COUNT - 1L) * ENTRY_COUNT / 2 * 101L
                assertEquals(LedgerEntry(ENTRY_COUNT, "5000-costs", -sales), entries.last())
                assertEquals(0L, entries.sumOf { it.cents })

                assertTrue(downloadProgress.isNotEmpty())
                assertEquals(ledger.size.toLong() to ledger.size.toLong(), downloadProgress.last())
                assertTrue(downloadProgress.zipWithNext().all { (a, b) -> a.first <= b.first })

                val journal: List<String> = entries.take(JOURNAL_LINES).map { entry: LedgerEntry ->
                    "${entry.id};${entry.account};${-entry.cents}\n"
                }
                val upload: JournalUpload = JournalUpload(journal)
                val accepted: HttpResponse = client.post("$base/journal") {
                    setBody(upload)
                    onUpload { sent: Long, total: Long? -> uploadProgress.add(sent to total) }
                }

                assertEquals(HttpStatusCode.Accepted, accepted.status)
                assertEquals("accepted $JOURNAL_LINES lines", accepted.bodyAsText())
                assertEquals(listOf(journal.joinToString("")), receivedUploads)
                assertEquals(ContentType.Text.CSV, ContentType.parse(uploadTypes.single()).withoutParameters())
                assertTrue(uploadProgress.isNotEmpty())
                assertEquals(upload.contentLength to upload.contentLength, uploadProgress.last())
                assertTrue(uploadProgress.zipWithNext().all { (a, b) -> a.first <= b.first })
            }
        }
    }

    private suspend fun readLedger(channel: ByteReadChannel): List<LedgerEntry> {
        val header: String? = channel.readUTF8Line()
        assertEquals(LEDGER_HEADER, header)
        val entries: MutableList<LedgerEntry> = mutableListOf()
        while (true) {
            val line: String = channel.readUTF8Line() ?: break
            val (id: String, account: String, cents: String) = line.split(',')
            entries.add(LedgerEntry(id.toInt(), account, cents.toLong()))
        }
        return entries
    }

    private companion object {
        const val LEDGER_HEADER: String = "id,account,cents"
        const val ENTRY_COUNT: Int = 4_000
        const val JOURNAL_LINES: Int = 500

        /** Builds a balanced ledger large enough to arrive in several network chunks. */
        fun buildLedger(): ByteArray {
            val csv: StringBuilder = StringBuilder(LEDGER_HEADER).append('\n')
            var balance: Long = 0
            for (id: Int in 1 until ENTRY_COUNT) {
                val cents: Long = id * 101L
                balance += cents
                csv.append(id).append(",4000-sales,").append(cents).append('\n')
            }
            csv.append(ENTRY_COUNT).append(",5000-costs,").append(-balance).append('\n')
            return csv.toString().toByteArray(Charsets.UTF_8)
        }
    }
}
