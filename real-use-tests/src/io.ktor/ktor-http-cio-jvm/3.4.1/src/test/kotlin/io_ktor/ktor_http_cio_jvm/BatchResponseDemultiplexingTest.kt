/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.cio.HttpHeadersMap
import io.ktor.http.cio.MultipartEvent
import io.ktor.http.cio.Response
import io.ktor.http.cio.parseMultipart
import io.ktor.http.cio.parseResponse
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers multipart/mixed event streaming: a batch API response split into its preamble, parts
 * carrying their own headers and embedded HTTP responses, and epilogue, read as parser events.
 */
class BatchResponseDemultiplexingTest {

    /** One sub-response of a batch call, matched back to its request through `Content-ID`. */
    private data class BatchItem(
        val contentId: String,
        val partType: String,
        val status: Int,
        val statusText: String,
        val contentType: String?,
        val etag: String?,
        val body: String,
    )

    /** Everything a batch client keeps after reading one multipart/mixed response body. */
    private data class BatchResult(
        val preamble: String,
        val items: List<BatchItem>,
        val epilogue: String,
    )

    /** Splits a batch response body into sub-responses the way a batch API client does. */
    private class BatchResponseReader(
        private val contentType: String,
        private val contentLength: Long,
    ) {

        suspend fun read(body: ByteReadChannel): BatchResult = coroutineScope {
            var preamble = ""
            var epilogue = ""
            val items: MutableList<BatchItem> = mutableListOf()
            val events: ReceiveChannel<MultipartEvent> = events(this, body)
            for (event in events) {
                when (event) {
                    is MultipartEvent.Preamble -> preamble = event.body.readByteArray().decodeToString()
                    is MultipartEvent.MultipartPart -> items += readItem(event)
                    is MultipartEvent.Epilogue -> epilogue = event.body.readByteArray().decodeToString()
                }
                event.release()
            }
            BatchResult(preamble, items, epilogue)
        }

        private fun events(scope: CoroutineScope, body: ByteReadChannel): ReceiveChannel<MultipartEvent> =
            scope.parseMultipart(body, contentType, contentLength)

        private suspend fun readItem(part: MultipartEvent.MultipartPart): BatchItem {
            val partHeaders: HttpHeadersMap = part.headers.await()
            val embedded: ByteReadChannel = ByteReadChannel(part.body.readRemaining().readByteArray())
            val response: Response = requireNotNull(parseResponse(embedded)) { "empty batch item" }
            return response.use {
                BatchItem(
                    contentId = partHeaders["Content-ID"].toString(),
                    partType = partHeaders[HttpHeaders.ContentType].toString(),
                    status = it.status,
                    statusText = it.statusText.toString(),
                    contentType = it.headers[HttpHeaders.ContentType]?.toString(),
                    etag = it.headers[HttpHeaders.ETag]?.toString(),
                    body = embedded.readRemaining().readByteArray().decodeToString(),
                )
            }
        }
    }

    @Test
    fun clientMatchesBatchSubResponsesToTheirRequests(): Unit = runBlocking {
        val boundary = "batch_7Gq2pXk"
        val wire: String = buildString {
            append("This is a multi-part batch response.\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/http\r\n")
            append("Content-ID: <response-item-1>\r\n")
            append("\r\n")
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n")
            append("ETag: \"v42\"\r\n")
            append("Content-Length: 31\r\n")
            append("\r\n")
            append("{\"id\":\"cal-17\",\"summary\":\"Q4\"}")
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/http\r\n")
            append("Content-ID: <response-item-2>\r\n")
            append("\r\n")
            append("HTTP/1.1 404 Not Found\r\n")
            append("Content-Type: text/plain\r\n")
            append("Content-Length: 19\r\n")
            append("\r\n")
            append("calendar not found.")
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/http\r\n")
            append("Content-ID: <response-item-3>\r\n")
            append("\r\n")
            append("HTTP/1.1 204 No Content\r\n")
            append("\r\n")
            append("\r\n--$boundary--\r\n")
            append("Batch processed in 12 ms.")
        }
        val bytes: ByteArray = wire.encodeToByteArray()
        val reader = BatchResponseReader("multipart/mixed; boundary=$boundary", bytes.size.toLong())

        val result: BatchResult = withTimeout(10_000) { reader.read(ByteReadChannel(bytes)) }

        assertEquals("This is a multi-part batch response.\r\n", result.preamble)
        assertEquals(3, result.items.size)

        val found: BatchItem = result.items[0]
        assertEquals("<response-item-1>", found.contentId)
        assertEquals("application/http", found.partType)
        assertEquals(200, found.status)
        assertEquals("OK", found.statusText)
        assertEquals("application/json; charset=UTF-8", found.contentType)
        assertEquals("\"v42\"", found.etag)
        assertEquals("{\"id\":\"cal-17\",\"summary\":\"Q4\"}", found.body)

        val missing: BatchItem = result.items[1]
        assertEquals("<response-item-2>", missing.contentId)
        assertEquals(404, missing.status)
        assertEquals("Not Found", missing.statusText)
        assertEquals("text/plain", missing.contentType)
        assertEquals(null, missing.etag)
        assertEquals("calendar not found.", missing.body)

        val deleted: BatchItem = result.items[2]
        assertEquals("<response-item-3>", deleted.contentId)
        assertEquals(204, deleted.status)
        assertEquals("No Content", deleted.statusText)
        assertEquals(null, deleted.contentType)
        assertEquals("", deleted.body)

        assertEquals("Batch processed in 12 ms.", result.epilogue.trimStart())
    }
}
