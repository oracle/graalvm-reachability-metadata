/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.CIOHeaders
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.Response
import io.ktor.http.cio.decodeChunked
import io.ktor.http.cio.encodeChunked
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseResponse
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writePacket
import kotlinx.coroutines.Job
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
 * Covers HTTP/1.1 response serialization and client-side response parsing: status lines and headers
 * built with `RequestResponseBuilder`, chunked bodies encoded and decoded, and keep-alive responses.
 */
class ResponseStreamingTest {

    /** One response the application server sends, before it reaches the wire. */
    private data class OutgoingResponse(
        val status: HttpStatusCode,
        val headers: List<Pair<String, String>>,
        val body: ByteArray?,
        val chunked: Boolean,
    )

    /** What the application's HTTP client keeps of one response after the connection moves on. */
    private data class ReceivedResponse(
        val version: HttpProtocolVersion,
        val status: HttpStatusCode,
        val headers: Headers,
        val connection: ConnectionOptions?,
        val body: String,
    )

    /** Writes responses onto one keep-alive connection the way a hand-rolled server would. */
    private class ResponseWriter(private val connection: ByteWriteChannel) {

        suspend fun send(response: OutgoingResponse) {
            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.1", response.status.value, response.status.description)
                for ((name, value) in response.headers) {
                    head.headerLine(name, value)
                }
                if (response.chunked) {
                    head.headerLine(HttpHeaders.TransferEncoding, "chunked")
                } else if (response.body != null) {
                    head.headerLine(HttpHeaders.ContentLength, response.body.size.toString())
                }
                head.emptyLine()
                val packet: Source = head.build()
                connection.writePacket(packet)
            } finally {
                head.release()
            }

            val body: ByteArray = response.body ?: return connection.flush()
            if (response.chunked) {
                encodeChunked(connection, ByteReadChannel(body))
            } else {
                connection.writeFully(body)
                connection.flush()
            }
        }
    }

    /** Reads every response on [connection] until the server announces close or the stream ends. */
    private class ResponseReader(private val connection: ByteReadChannel) {

        suspend fun drain(): List<ReceivedResponse> {
            val received: MutableList<ReceivedResponse> = mutableListOf()
            while (true) {
                val response: Response = parseResponse(connection) ?: break
                val handled: ReceivedResponse = response.use { read(it) }
                received += handled
                if (handled.connection?.close == true) {
                    break
                }
            }
            return received
        }

        private suspend fun read(response: Response): ReceivedResponse {
            val status = HttpStatusCode(response.status, response.statusText.toString())
            val headers: Headers = snapshot(CIOHeaders(response.headers))
            val connectionOptions: ConnectionOptions? =
                ConnectionOptions.parse(response.headers[HttpHeaders.Connection])
            val sink = ByteChannel()
            when {
                status == HttpStatusCode.NoContent || status == HttpStatusCode.NotModified -> Unit
                headers[HttpHeaders.TransferEncoding] == "chunked" -> decodeChunked(connection, sink)
                else -> parseHttpBody(response.headers, connection, sink)
            }
            sink.flushAndClose()
            return ReceivedResponse(
                version = HttpProtocolVersion.parse(response.version),
                status = status,
                headers = headers,
                connection = connectionOptions,
                body = sink.readRemaining().readByteArray().decodeToString(),
            )
        }

        /** Copies the pooled header view so it stays valid after the response is released. */
        private fun snapshot(headers: CIOHeaders): Headers = Headers.build {
            headers.forEach { name: String, values: List<String> -> appendAll(name, values) }
        }
    }

    @Test
    fun clientReadsChunkedEmptyAndFixedLengthResponsesFromOneConnection(): Unit = runBlocking {
        val report: String = (1..40).joinToString(separator = "\n") { "line $it: shipment ok" }
        val outgoing: List<OutgoingResponse> = listOf(
            OutgoingResponse(
                status = HttpStatusCode.OK,
                headers = listOf(
                    HttpHeaders.ContentType to "text/plain; charset=UTF-8",
                    HttpHeaders.CacheControl to "no-cache",
                    HttpHeaders.SetCookie to "session=abc; Path=/",
                    HttpHeaders.SetCookie to "theme=dark; Path=/",
                ),
                body = report.encodeToByteArray(),
                chunked = true,
            ),
            OutgoingResponse(
                status = HttpStatusCode.NotModified,
                headers = listOf(HttpHeaders.ETag to "\"v7\""),
                body = null,
                chunked = false,
            ),
            OutgoingResponse(
                status = HttpStatusCode.Created,
                headers = listOf(
                    HttpHeaders.Location to "/shipments/42",
                    HttpHeaders.ContentType to "application/json",
                    HttpHeaders.Connection to "close",
                ),
                body = "{\"id\":42}".encodeToByteArray(),
                chunked = false,
            ),
        )

        val wire = ByteChannel()
        val received: List<ReceivedResponse> = withTimeout(10_000) {
            val server: Job = launch {
                val writer = ResponseWriter(wire)
                for (response in outgoing) {
                    writer.send(response)
                }
                wire.flushAndClose()
            }
            val responses: List<ReceivedResponse> = ResponseReader(wire).drain()
            server.join()
            responses
        }

        assertEquals(3, received.size)

        val report200: ReceivedResponse = received[0]
        assertEquals(HttpProtocolVersion.HTTP_1_1, report200.version)
        assertEquals(HttpStatusCode.OK, report200.status)
        assertEquals("OK", report200.status.description)
        assertEquals("chunked", report200.headers[HttpHeaders.TransferEncoding])
        assertNull(report200.headers[HttpHeaders.ContentLength])
        assertEquals("text/plain; charset=UTF-8", report200.headers[HttpHeaders.ContentType])
        assertEquals("no-cache", report200.headers["cache-control"])
        assertEquals(
            listOf("session=abc; Path=/", "theme=dark; Path=/"),
            report200.headers.getAll(HttpHeaders.SetCookie),
        )
        assertNull(report200.connection)
        assertEquals(report, report200.body)

        val notModified: ReceivedResponse = received[1]
        assertEquals(HttpStatusCode.NotModified, notModified.status)
        assertEquals("\"v7\"", notModified.headers[HttpHeaders.ETag])
        assertEquals("", notModified.body)

        val created: ReceivedResponse = received[2]
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("Created", created.status.description)
        assertEquals("/shipments/42", created.headers[HttpHeaders.Location])
        assertEquals("9", created.headers[HttpHeaders.ContentLength])
        assertTrue(created.connection!!.close)
        assertEquals("{\"id\":42}", created.body)
    }
}
