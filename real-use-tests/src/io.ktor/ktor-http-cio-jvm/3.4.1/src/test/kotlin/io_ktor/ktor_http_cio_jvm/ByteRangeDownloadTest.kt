/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.RangeUnits
import io.ktor.http.RangesSpecifier
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.HttpHeadersMap
import io.ktor.http.cio.MultipartEvent
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.Response
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseMultipart
import io.ktor.http.cio.parseRequest
import io.ktor.http.cio.parseResponse
import io.ktor.http.contentRangeHeaderValue
import io.ktor.http.parseRangesSpecifier
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Source
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers HTTP range requests over TCP: a download client resumes a partial file with a
 * single-range `206 Partial Content` guarded by `If-Range`, fetches several slices as one
 * `multipart/byteranges` body streamed through `parseMultipart`, and handles a `416` refusal.
 */
class ByteRangeDownloadTest {

    /** One slice of a `multipart/byteranges` body. */
    private data class RangePart(val contentRange: String, val contentType: String, val text: String)

    /** What the download client keeps of one response. */
    private data class Download(
        val status: HttpStatusCode,
        val acceptRanges: String?,
        val etag: String?,
        val contentType: String?,
        val contentRange: String?,
        val body: String,
        val parts: List<RangePart>,
    )

    /**
     * A static file origin that honours `Range` and `If-Range` for one document and answers
     * several ranges with a `multipart/byteranges` body.
     */
    private class FileOrigin(private val server: ServerSocket, scope: CoroutineScope) {

        val servedConnections: Channel<List<String>> = Channel(Channel.UNLIMITED)

        val port: Int = (server.localAddress as InetSocketAddress).port

        private val acceptor: Job = scope.launch {
            while (true) {
                val socket: Socket = server.accept()
                launch { serve(socket) }
            }
        }

        fun stop() {
            acceptor.cancel()
            server.close()
        }

        private suspend fun serve(socket: Socket) {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
            val served: MutableList<String> = mutableListOf()
            try {
                while (true) {
                    val request: Request = parseRequest(input) ?: break
                    request.use { served += respond(it, output) }
                }
            } finally {
                output.flushAndClose()
                socket.close()
                servedConnections.send(served)
            }
        }

        /** Writes the response and returns a log line naming the request and the status sent. */
        private suspend fun respond(request: Request, output: ByteWriteChannel): String {
            val path: String = request.uri.toString()
            val rangeHeader: String? = request.headers[HttpHeaders.Range]?.toString()
            val ifRange: String? = request.headers[HttpHeaders.IfRange]?.toString()
            val length: Long = DOCUMENT.size.toLong()

            val head = RequestResponseBuilder()
            val status: HttpStatusCode
            try {
                if (request.method != HttpMethod.Get || path != DOCUMENT_PATH) {
                    status = HttpStatusCode.NotFound
                    writeHead(head, status, "text/plain", 0)
                } else if (rangeHeader == null || (ifRange != null && ifRange != ETAG)) {
                    status = HttpStatusCode.OK
                    writeHead(head, status, "text/plain", DOCUMENT.size)
                    head.bytes(DOCUMENT, 0, DOCUMENT.size)
                } else {
                    val specifier: RangesSpecifier? = parseRangesSpecifier(rangeHeader)
                    val ranges: List<LongRange> =
                        if (specifier != null && specifier.isValid()) specifier.merge(length) else emptyList()
                    status = if (ranges.isEmpty()) {
                        HttpStatusCode.RequestedRangeNotSatisfiable
                    } else {
                        HttpStatusCode.PartialContent
                    }
                    when (ranges.size) {
                        0 -> {
                            head.responseLine("HTTP/1.1", status.value, status.description)
                            head.headerLine(HttpHeaders.ContentRange, "bytes */$length")
                            head.headerLine(HttpHeaders.ContentLength, "0")
                            head.emptyLine()
                        }
                        1 -> {
                            val range: LongRange = ranges.single()
                            val slice: ByteArray = slice(range)
                            head.responseLine("HTTP/1.1", status.value, status.description)
                            head.headerLine(HttpHeaders.AcceptRanges, "bytes")
                            head.headerLine(HttpHeaders.ETag, ETAG)
                            head.headerLine(HttpHeaders.ContentType, "text/plain")
                            val contentRange: String = contentRangeHeaderValue(range, length, RangeUnits.Bytes)
                            head.headerLine(HttpHeaders.ContentRange, contentRange)
                            head.headerLine(HttpHeaders.ContentLength, slice.size.toString())
                            head.emptyLine()
                            head.bytes(slice, 0, slice.size)
                        }
                        else -> {
                            val body: ByteArray = byteRangesBody(ranges, length)
                            head.responseLine("HTTP/1.1", status.value, status.description)
                            head.headerLine(HttpHeaders.AcceptRanges, "bytes")
                            head.headerLine(HttpHeaders.ETag, ETAG)
                            head.headerLine(HttpHeaders.ContentType, "multipart/byteranges; boundary=$BOUNDARY")
                            head.headerLine(HttpHeaders.ContentLength, body.size.toString())
                            head.emptyLine()
                            head.bytes(body, 0, body.size)
                        }
                    }
                }
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()
            return "${request.method.value} $path ${rangeHeader ?: "-"} -> ${status.value}"
        }

        private fun writeHead(head: RequestResponseBuilder, status: HttpStatusCode, type: String, size: Int) {
            head.responseLine("HTTP/1.1", status.value, status.description)
            head.headerLine(HttpHeaders.AcceptRanges, "bytes")
            head.headerLine(HttpHeaders.ETag, ETAG)
            head.headerLine(HttpHeaders.ContentType, type)
            head.headerLine(HttpHeaders.ContentLength, size.toString())
            head.emptyLine()
        }

        private fun slice(range: LongRange): ByteArray =
            DOCUMENT.copyOfRange(range.first.toInt(), range.last.toInt() + 1)

        private fun byteRangesBody(ranges: List<LongRange>, length: Long): ByteArray {
            val body = StringBuilder()
            for (range in ranges) {
                body.append("--$BOUNDARY\r\n")
                body.append("Content-Type: text/plain\r\n")
                body.append("Content-Range: ${contentRangeHeaderValue(range, length, RangeUnits.Bytes)}\r\n")
                body.append("\r\n")
                body.append(slice(range).decodeToString())
                body.append("\r\n")
            }
            body.append("--$BOUNDARY--\r\n")
            return body.toString().encodeToByteArray()
        }
    }

    /** A download client that keeps one HTTP/1.1 connection to the origin. */
    private class DownloadClient(private val socket: Socket) {

        private val input: ByteReadChannel = socket.openReadChannel()

        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)

        suspend fun get(path: String, range: String? = null, ifRange: String? = null): Download {
            val head = RequestResponseBuilder()
            try {
                head.requestLine(HttpMethod.Get, path, "HTTP/1.1")
                head.headerLine(HttpHeaders.Host, "downloads.example")
                head.headerLine(HttpHeaders.UserAgent, "resumable-downloader/2.0")
                if (range != null) {
                    head.headerLine(HttpHeaders.Range, range)
                }
                if (ifRange != null) {
                    head.headerLine(HttpHeaders.IfRange, ifRange)
                }
                head.emptyLine()
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()

            val response: Response = requireNotNull(parseResponse(input)) { "connection closed before a response" }
            return response.use { read(it) }
        }

        fun close() {
            socket.close()
        }

        private suspend fun read(response: Response): Download = coroutineScope {
            val headers: HttpHeadersMap = response.headers
            val contentType: String? = headers[HttpHeaders.ContentType]?.toString()
            val contentLength: Long = requireNotNull(headers[HttpHeaders.ContentLength]).toString().toLong()
            val body = ByteChannel()
            val pump: Job = launch {
                parseHttpBody(
                    HttpProtocolVersion.parse(response.version),
                    contentLength,
                    headers[HttpHeaders.TransferEncoding],
                    ConnectionOptions.parse(headers[HttpHeaders.Connection]),
                    input,
                    body,
                )
                body.flushAndClose()
            }

            val byteRanges: Boolean =
                contentType != null && ContentType.parse(contentType).match(ContentType.MultiPart.ByteRanges)
            var text = ""
            val parts: MutableList<RangePart> = mutableListOf()
            if (byteRanges) {
                val events: ReceiveChannel<MultipartEvent> =
                    parseMultipart(body, requireNotNull(contentType), contentLength)
                for (event in events) {
                    if (event is MultipartEvent.MultipartPart) {
                        val partHeaders: HttpHeadersMap = event.headers.await()
                        parts += RangePart(
                            contentRange = requireNotNull(partHeaders[HttpHeaders.ContentRange]).toString(),
                            contentType = requireNotNull(partHeaders[HttpHeaders.ContentType]).toString(),
                            text = event.body.readRemaining().readByteArray().decodeToString(),
                        )
                    }
                    event.release()
                }
            } else {
                text = body.readRemaining().readByteArray().decodeToString()
            }
            pump.join()

            Download(
                status = HttpStatusCode.fromValue(response.status),
                acceptRanges = headers[HttpHeaders.AcceptRanges]?.toString(),
                etag = headers[HttpHeaders.ETag]?.toString(),
                contentType = contentType,
                contentRange = headers[HttpHeaders.ContentRange]?.toString(),
                body = text,
                parts = parts,
            )
        }
    }

    @Test
    fun clientResumesSlicesAndIsRefusedOverOneConnection(): Unit = runBlocking {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            withTimeout(30_000) {
                val origin = FileOrigin(aSocket(selector).tcp().bind("127.0.0.1", 0), this)
                try {
                    val client = DownloadClient(aSocket(selector).tcp().connect("127.0.0.1", origin.port))

                    val probe: Download = client.get(DOCUMENT_PATH, range = "bytes=0-7")
                    assertEquals(HttpStatusCode.PartialContent, probe.status)
                    assertEquals("bytes", probe.acceptRanges)
                    assertEquals(ETAG, probe.etag)
                    assertEquals("bytes 0-7/105", probe.contentRange)
                    assertEquals("ktor-cio", probe.body)

                    val partialFile: ByteArray = DOCUMENT.copyOfRange(0, 40)
                    val resumed: Download = client.get(DOCUMENT_PATH, range = "bytes=40-", ifRange = ETAG)
                    assertEquals(HttpStatusCode.PartialContent, resumed.status)
                    assertEquals("bytes 40-104/105", resumed.contentRange)
                    assertEquals("ader parsing\n- chunked trailers fixed\n- multipart limits honored\n", resumed.body)
                    assertEquals(DOCUMENT.decodeToString(), partialFile.decodeToString() + resumed.body)

                    val staleTag = "\"release-4.2.0-rc1\""
                    val stale: Download = client.get(DOCUMENT_PATH, range = "bytes=40-", ifRange = staleTag)
                    assertEquals(HttpStatusCode.OK, stale.status)
                    assertNull(stale.contentRange)
                    assertEquals(DOCUMENT.decodeToString(), stale.body)

                    val slices: Download = client.get(DOCUMENT_PATH, range = "bytes=0-7,29-52,-10")
                    assertEquals(HttpStatusCode.PartialContent, slices.status)
                    assertEquals("multipart/byteranges; boundary=$BOUNDARY", slices.contentType)
                    assertNull(slices.contentRange)
                    assertEquals("", slices.body)
                    assertEquals(
                        listOf(
                            RangePart("bytes 0-7/105", "text/plain", "ktor-cio"),
                            RangePart("bytes 29-52/105", "text/plain", "- faster header parsing\n"),
                            RangePart("bytes 95-104/105", "text/plain", "s honored\n"),
                        ),
                        slices.parts,
                    )

                    val beyond: Download = client.get(DOCUMENT_PATH, range = "bytes=500-")
                    assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, beyond.status)
                    assertEquals("bytes */105", beyond.contentRange)
                    assertEquals("", beyond.body)
                    assertTrue(beyond.parts.isEmpty())

                    val after: Download = client.get(DOCUMENT_PATH)
                    assertEquals(HttpStatusCode.OK, after.status)
                    assertEquals(DOCUMENT.size, after.body.length)
                    client.close()

                    assertEquals(
                        listOf(
                            "GET $DOCUMENT_PATH bytes=0-7 -> 206",
                            "GET $DOCUMENT_PATH bytes=40- -> 206",
                            "GET $DOCUMENT_PATH bytes=40- -> 200",
                            "GET $DOCUMENT_PATH bytes=0-7,29-52,-10 -> 206",
                            "GET $DOCUMENT_PATH bytes=500- -> 416",
                            "GET $DOCUMENT_PATH - -> 200",
                        ),
                        origin.servedConnections.receive(),
                    )
                } finally {
                    origin.stop()
                }
            }
        } finally {
            selector.close()
        }
    }

    private companion object {

        const val DOCUMENT_PATH: String = "/releases/4.2.0/NOTES.txt"

        const val ETAG: String = "\"release-4.2.0\""

        const val BOUNDARY: String = "3d6b6a416f9b5"

        val DOCUMENT: ByteArray = (
            "ktor-cio 3.4.1 release notes\n" +
                "- faster header parsing\n" +
                "- chunked trailers fixed\n" +
                "- multipart limits honored\n"
            ).encodeToByteArray()
    }
}
