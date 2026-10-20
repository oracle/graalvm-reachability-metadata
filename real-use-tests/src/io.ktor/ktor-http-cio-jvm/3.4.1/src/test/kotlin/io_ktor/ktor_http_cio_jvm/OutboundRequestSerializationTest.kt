/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.Request
import io.ktor.http.cio.encodeChunked
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writePacket
import io.ktor.utils.io.writeStringUtf8
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers HTTP/1.1 request serialization: request lines, headers, and fixed-length bodies built with
 * `RequestResponseBuilder`, and an upload streamed through the `encodeChunked` writer job.
 */
class OutboundRequestSerializationTest {

    /** One row of the nightly export the client uploads while it is still being produced. */
    private data class ExportRow(val sku: String, val quantity: Int)

    /** What the receiving end makes of one request after parsing it off the wire. */
    private data class ReceivedRequest(
        val method: HttpMethod,
        val uri: String,
        val version: String,
        val host: String?,
        val contentType: String?,
        val transferEncoding: String?,
        val hasBody: Boolean,
        val body: ByteArray,
    ) {
        val text: String get() = body.decodeToString()
    }

    /** A hand-rolled HTTP/1.1 client that writes its requests onto one keep-alive connection. */
    private class InventoryClient(private val connection: ByteWriteChannel, private val host: String) {

        suspend fun checkStatus() {
            writeHead(HttpMethod.Get, "/status?verbose=1", listOf(HttpHeaders.Accept to "application/json"))
            connection.flush()
        }

        suspend fun postWebhook(json: String) {
            val payload: ByteArray = json.encodeToByteArray()
            val message = RequestResponseBuilder()
            try {
                message.requestLine(HttpMethod.Post, "/hooks/orders", "HTTP/1.1")
                message.headerLine(HttpHeaders.Host, host)
                message.headerLine(HttpHeaders.ContentType, "application/json")
                message.headerLine(HttpHeaders.ContentLength, payload.size.toString())
                message.line("X-Signature: sha256=feedface")
                message.emptyLine()
                message.bytes(payload)
                connection.writePacket(message.build())
            } finally {
                message.release()
            }
            connection.flush()
        }

        suspend fun putBinary(path: String, blob: ByteBuffer) {
            val length: Int = blob.remaining()
            val message = RequestResponseBuilder()
            try {
                message.requestLine(HttpMethod.Put, path, "HTTP/1.1")
                message.headerLine(HttpHeaders.Host, host)
                message.headerLine(HttpHeaders.ContentType, "application/octet-stream")
                message.headerLine(HttpHeaders.ContentLength, length.toString())
                message.emptyLine()
                message.bytes(blob)
                connection.writePacket(message.build())
            } finally {
                message.release()
            }
            connection.flush()
        }

        /** Streams [rows] as CSV through a chunked body without knowing its length up front. */
        suspend fun uploadExport(rows: Sequence<ExportRow>) {
            writeHead(
                HttpMethod.Put,
                "/exports/nightly.csv",
                listOf(
                    HttpHeaders.ContentType to "text/csv",
                    HttpHeaders.TransferEncoding to "chunked",
                    HttpHeaders.Connection to "close",
                ),
            )
            connection.flush()
            val upload: ReaderJob = encodeChunked(connection, currentCoroutineContext())
            upload.channel.writeStringUtf8("sku,quantity\r\n")
            for (row in rows) {
                upload.channel.writeStringUtf8("${row.sku},${row.quantity}\r\n")
                upload.channel.flush()
            }
            upload.channel.flushAndClose()
            upload.job.join()
        }

        private suspend fun writeHead(method: HttpMethod, uri: String, headers: List<Pair<String, String>>) {
            val head = RequestResponseBuilder()
            try {
                head.requestLine(method, uri, "HTTP/1.1")
                head.headerLine(HttpHeaders.Host, host)
                for ((name, value) in headers) {
                    head.headerLine(name, value)
                }
                head.emptyLine()
                connection.writePacket(head.build())
            } finally {
                head.release()
            }
        }
    }

    /** Parses every request the client wrote, the way the receiving server would. */
    private suspend fun receiveAll(wire: ByteReadChannel): List<ReceivedRequest> {
        val received: MutableList<ReceivedRequest> = mutableListOf()
        while (true) {
            val request: Request = parseRequest(wire) ?: break
            received += request.use { read(it, wire) }
        }
        return received
    }

    private suspend fun read(request: Request, wire: ByteReadChannel): ReceivedRequest {
        val hasBody: Boolean = expectHttpBody(request)
        val sink = ByteChannel()
        if (hasBody) {
            parseHttpBody(request.headers, wire, sink)
        }
        sink.flushAndClose()
        return ReceivedRequest(
            method = request.method,
            uri = request.uri.toString(),
            version = request.version.toString(),
            host = request.headers[HttpHeaders.Host]?.toString(),
            contentType = request.headers[HttpHeaders.ContentType]?.toString(),
            transferEncoding = request.headers[HttpHeaders.TransferEncoding]?.toString(),
            hasBody = hasBody,
            body = sink.readRemaining().readByteArray(),
        )
    }

    @Test
    fun clientSerializesRequestHeadsFixedBodiesAndChunkedUploads(): Unit = runBlocking {
        val rows: List<ExportRow> = (1..25).map { ExportRow("SKU-%03d".format(it), it * 7) }
        val webhook = "{\"order\":1017,\"state\":\"paid\"}"
        val blob: ByteArray = ByteArray(300) { (it % 251).toByte() }

        val wire = ByteChannel()
        withTimeout(10_000) {
            val client = InventoryClient(wire, "inventory.internal:8080")
            client.checkStatus()
            client.postWebhook(webhook)
            client.putBinary("/blobs/thumb.bin", ByteBuffer.wrap(blob))
            client.uploadExport(rows.asSequence())
            wire.flushAndClose()
        }
        val written: ByteArray = wire.readRemaining().readByteArray()
        val text: String = written.decodeToString()

        assertTrue(
            text.startsWith(
                "GET /status?verbose=1 HTTP/1.1\r\n" +
                    "Host: inventory.internal:8080\r\n" +
                    "Accept: application/json\r\n" +
                    "\r\n" +
                    "POST /hooks/orders HTTP/1.1\r\n",
            ),
            text.take(200),
        )
        assertTrue(text.contains("X-Signature: sha256=feedface\r\n\r\n$webhook"))
        assertTrue(text.endsWith("\r\n0\r\n\r\n"), text.takeLast(40))

        val received: List<ReceivedRequest> = withTimeout(10_000) { receiveAll(ByteReadChannel(written)) }
        assertEquals(4, received.size)

        val status: ReceivedRequest = received[0]
        assertEquals(HttpMethod.Get, status.method)
        assertEquals("/status?verbose=1", status.uri)
        assertEquals("HTTP/1.1", status.version)
        assertEquals("inventory.internal:8080", status.host)
        assertFalse(status.hasBody)
        assertEquals(0, status.body.size)

        val hook: ReceivedRequest = received[1]
        assertEquals(HttpMethod.Post, hook.method)
        assertEquals("/hooks/orders", hook.uri)
        assertEquals("application/json", hook.contentType)
        assertTrue(hook.hasBody)
        assertEquals(webhook, hook.text)

        val binary: ReceivedRequest = received[2]
        assertEquals(HttpMethod.Put, binary.method)
        assertEquals("/blobs/thumb.bin", binary.uri)
        assertEquals("application/octet-stream", binary.contentType)
        assertArrayEquals(blob, binary.body)

        val export: ReceivedRequest = received[3]
        assertEquals(HttpMethod.Put, export.method)
        assertEquals("/exports/nightly.csv", export.uri)
        assertEquals("chunked", export.transferEncoding)
        assertTrue(export.hasBody)
        val expectedCsv: String = "sku,quantity\r\n" +
            rows.joinToString(separator = "") { "${it.sku},${it.quantity}\r\n" }
        assertEquals(expectedCsv, export.text)
    }
}
