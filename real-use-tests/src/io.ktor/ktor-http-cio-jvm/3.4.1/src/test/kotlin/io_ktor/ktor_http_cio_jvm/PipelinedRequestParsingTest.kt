/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.Request
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers HTTP/1.1 request parsing: a keep-alive connection carrying pipelined requests whose
 * heads, headers, connection options, and Content-Length or chunked bodies are decoded in order.
 */
class PipelinedRequestParsingTest {

    /** What the application's dispatcher keeps of one request after the connection moves on. */
    private data class ReceivedRequest(
        val method: HttpMethod,
        val uri: String,
        val version: HttpProtocolVersion,
        val host: String?,
        val userAgents: List<String>,
        val connection: ConnectionOptions?,
        val body: String,
    )

    /** Reads every request on [connection] until the client asks to close or the stream ends. */
    private class RequestDispatcher(private val connection: ByteReadChannel) {

        suspend fun drain(): List<ReceivedRequest> {
            val received: MutableList<ReceivedRequest> = mutableListOf()
            while (true) {
                val request: Request = parseRequest(connection) ?: break
                val handled: ReceivedRequest = request.use { handle(it) }
                received += handled
                if (handled.connection?.close == true) {
                    break
                }
            }
            return received
        }

        private suspend fun handle(request: Request): ReceivedRequest {
            val version: HttpProtocolVersion = HttpProtocolVersion.parse(request.version)
            val contentLength: Long = request.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
            val transferEncoding: CharSequence? = request.headers[HttpHeaders.TransferEncoding]
            val connectionOptions: ConnectionOptions? =
                ConnectionOptions.parse(request.headers[HttpHeaders.Connection])
            val body: String = if (expectHttpBody(request)) {
                val sink = ByteChannel()
                parseHttpBody(version, contentLength, transferEncoding, connectionOptions, connection, sink)
                sink.flushAndClose()
                sink.readRemaining().readByteArray().decodeToString()
            } else {
                ""
            }
            return ReceivedRequest(
                method = request.method,
                uri = request.uri.toString(),
                version = version,
                host = request.headers[HttpHeaders.Host]?.toString(),
                userAgents = request.headers.getAll(HttpHeaders.UserAgent).map { it.toString() }.toList(),
                connection = connectionOptions,
                body = body,
            )
        }
    }

    @Test
    fun dispatcherReadsPipelinedRequestsWithFixedAndChunkedBodies(): Unit = runBlocking {
        val wire: String = buildString {
            append("GET /orders?status=open HTTP/1.1\r\n")
            append("Host: shop.example\r\n")
            append("User-Agent: mobile-app/4.2\r\n")
            append("User-Agent: proxy/1.0\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")

            append("POST /orders HTTP/1.1\r\n")
            append("Host: shop.example\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: 27\r\n")
            append("\r\n")
            append("{\"sku\":\"A-17\",\"quantity\":3}")

            append("PUT /orders/42/notes HTTP/1.1\r\n")
            append("Host: shop.example\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append("7\r\ndeliver\r\n")
            append("a\r\n after 5pm\r\n")
            append("0\r\n\r\n")

            append("GET /never-read HTTP/1.1\r\n\r\n")
        }

        val received: List<ReceivedRequest> = withTimeout(10_000) {
            RequestDispatcher(ByteReadChannel(wire.encodeToByteArray())).drain()
        }

        assertEquals(3, received.size)

        val list: ReceivedRequest = received[0]
        assertEquals(HttpMethod.Get, list.method)
        assertEquals("/orders?status=open", list.uri)
        assertEquals(HttpProtocolVersion.HTTP_1_1, list.version)
        assertEquals("shop.example", list.host)
        assertEquals(listOf("mobile-app/4.2", "proxy/1.0"), list.userAgents)
        assertEquals(ConnectionOptions.KeepAlive, list.connection)
        assertTrue(list.connection!!.keepAlive)
        assertEquals("", list.body)

        val create: ReceivedRequest = received[1]
        assertEquals(HttpMethod.Post, create.method)
        assertEquals("/orders", create.uri)
        assertEquals(null, create.connection)
        assertEquals("{\"sku\":\"A-17\",\"quantity\":3}", create.body)

        val note: ReceivedRequest = received[2]
        assertEquals(HttpMethod.Put, note.method)
        assertEquals("/orders/42/notes", note.uri)
        assertTrue(note.connection!!.close)
        assertFalse(note.connection.keepAlive)
        assertEquals("deliver after 5pm", note.body)
    }
}
