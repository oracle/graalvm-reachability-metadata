/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.compression.appliedDecoders
import io.ktor.client.plugins.compression.compress
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compressed request and response bodies over the OkHttp engine, decoded and encoded by the
 * client `ContentEncoding` plugin or, without it, by OkHttp's transparent gzip.
 *
 * An invoice-archive client downloads large invoice exports and uploads batches of ledger lines.
 * With `ContentEncoding` installed the client advertises gzip and deflate, Ktor decodes whichever
 * encoding the archive picked and reports it through `appliedDecoders`, and uploads marked with
 * `compress("gzip")` reach the archive gzip-encoded. A plain client that installs nothing still
 * gets gzip from OkHttp itself, which asks for it and strips the encoding before Ktor sees the
 * body. The test checks the text the application reads and the bytes and headers the archive saw.
 */
class InvoiceArchiveCompressionTest {

    private lateinit var archive: MockWebServer

    @BeforeEach
    fun start() {
        archive = MockWebServer()
        archive.start()
    }

    @AfterEach
    fun stop() {
        archive.close()
    }

    @Test
    fun gzipAndDeflateExportsAreDecodedByTheContentEncodingPlugin() {
        archive.enqueue(encodedExport("gzip", gzip(INVOICE_EXPORT)))
        archive.enqueue(encodedExport("deflate", deflate(CREDIT_NOTE_EXPORT)))

        lateinit var invoices: String
        lateinit var invoiceDecoders: List<String>
        lateinit var creditNotes: String
        lateinit var creditNoteDecoders: List<String>
        archiveClient().use { client: HttpClient ->
            runBlocking {
                val invoiceResponse: HttpResponse = client.get(archive.url("/exports/invoices").toString())
                assertEquals(HttpStatusCode.OK, invoiceResponse.status)
                invoices = invoiceResponse.bodyAsText()
                invoiceDecoders = invoiceResponse.appliedDecoders

                val creditNoteResponse: HttpResponse =
                    client.get(archive.url("/exports/credit-notes").toString())
                creditNotes = creditNoteResponse.bodyAsText()
                creditNoteDecoders = creditNoteResponse.appliedDecoders
            }
        }

        assertEquals(INVOICE_EXPORT, invoices)
        assertEquals(listOf("gzip"), invoiceDecoders)
        assertEquals(CREDIT_NOTE_EXPORT, creditNotes)
        assertEquals(listOf("deflate"), creditNoteDecoders)

        val acceptedEncodings: Set<String> = acceptedEncodings(takeRecorded())
        assertEquals(setOf("gzip", "deflate"), acceptedEncodings)
        assertEquals(setOf("gzip", "deflate"), acceptedEncodings(takeRecorded()))
    }

    @Test
    fun ledgerUploadsMarkedForCompressionReachTheArchiveGzipped() {
        archive.enqueue(MockResponse.Builder().code(202).body("batch accepted").build())
        archive.enqueue(MockResponse.Builder().code(202).body("batch accepted").build())

        lateinit var compressedReply: String
        lateinit var plainReply: String
        archiveClient().use { client: HttpClient ->
            runBlocking {
                val compressedResponse: HttpResponse = client.post(archive.url("/ledger/batches").toString()) {
                    contentType(ContentType.Text.CSV)
                    setBody(LEDGER_BATCH)
                    compress("gzip")
                }
                assertEquals(HttpStatusCode.Accepted, compressedResponse.status)
                compressedReply = compressedResponse.bodyAsText()

                val plainResponse: HttpResponse = client.post(archive.url("/ledger/batches").toString()) {
                    contentType(ContentType.Text.CSV)
                    setBody(LEDGER_BATCH)
                }
                plainReply = plainResponse.bodyAsText()
            }
        }

        assertEquals("batch accepted", compressedReply)
        assertEquals("batch accepted", plainReply)

        val compressedUpload: RecordedRequest = takeRecorded()
        assertEquals("gzip", compressedUpload.headers[HttpHeaders.ContentEncoding])
        assertEquals(LEDGER_BATCH, gunzip(compressedUpload.body!!.toByteArray()))

        val plainUpload: RecordedRequest = takeRecorded()
        assertNull(plainUpload.headers[HttpHeaders.ContentEncoding])
        assertEquals(LEDGER_BATCH, plainUpload.body!!.utf8())
    }

    @Test
    fun plainClientReceivesGzipThroughOkHttpTransparentDecompression() {
        archive.enqueue(encodedExport("gzip", gzip(INVOICE_EXPORT)))

        lateinit var invoices: String
        var contentEncoding: String? = "unread"
        HttpClient(OkHttp).use { client: HttpClient ->
            runBlocking {
                val response: HttpResponse = client.get(archive.url("/exports/invoices").toString())
                assertEquals(HttpStatusCode.OK, response.status)
                invoices = response.bodyAsText()
                contentEncoding = response.headers[HttpHeaders.ContentEncoding]
            }
        }

        assertEquals(INVOICE_EXPORT, invoices)
        assertNull(contentEncoding)
        assertEquals("gzip", takeRecorded().headers[HttpHeaders.AcceptEncoding])
    }

    /** Builds the archive client the way the application does: OkHttp under Ktor's ContentEncoding. */
    private fun archiveClient(): HttpClient = HttpClient(OkHttp) {
        ContentEncoding(ContentEncodingConfig.Mode.All) {
            gzip()
            deflate(0.9f)
        }
    }

    private fun encodedExport(encoding: String, body: ByteArray): MockResponse = MockResponse.Builder()
        .addHeader(HttpHeaders.ContentType, "text/csv")
        .addHeader(HttpHeaders.ContentEncoding, encoding)
        .body(Buffer().write(body))
        .build()

    /** The coding names of an `Accept-Encoding` header, without their quality values. */
    private fun acceptedEncodings(request: RecordedRequest): Set<String> =
        checkNotNull(request.headers[HttpHeaders.AcceptEncoding]) { "no Accept-Encoding was sent" }
            .split(',')
            .map { coding: String -> coding.substringBefore(';').trim() }
            .toSet()

    private fun takeRecorded(): RecordedRequest =
        checkNotNull(archive.takeRequest(10, TimeUnit.SECONDS)) { "the archive received no request" }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }

    /** Raw DEFLATE without a zlib wrapper, as Ktor servers send and Ktor clients decode it. */
    private fun deflate(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        DeflaterOutputStream(bytes, deflater).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        deflater.end()
        return bytes.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        val INVOICE_EXPORT: String = buildString {
            append("invoice,customer,amount,currency\n")
            repeat(200) { index: Int -> append("INV-${1000 + index},ACME-${index % 7},${index * 3}.50,EUR\n") }
        }
        val CREDIT_NOTE_EXPORT: String = buildString {
            append("credit_note,invoice,amount\n")
            repeat(80) { index: Int -> append("CN-${500 + index},INV-${1000 + index},${index}.25\n") }
        }
        val LEDGER_BATCH: String = buildString {
            append("account,debit,credit\n")
            repeat(150) { index: Int -> append("${4000 + index % 9},${index}.00,0.00\n") }
        }
    }
}
