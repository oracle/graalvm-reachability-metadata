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
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.HttpHeadersMap
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.Response
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.http.cio.parseResponse
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
 * Covers HTTP/1.0 exchanges over a real TCP connection: requests and responses framed with
 * `RequestResponseBuilder`, response bodies delimited by the server closing the socket, and
 * connections reused only when the client opts in with `Connection: keep-alive`.
 */
class LegacyHttp10ExchangeTest {

    /** What the legacy client keeps of one response. */
    private data class Received(
        val version: HttpProtocolVersion,
        val status: HttpStatusCode,
        val connection: ConnectionOptions?,
        val contentLength: String?,
        val body: String,
    )

    /**
     * An HTTP/1.0 origin server: it answers with a close-delimited body unless the client asked
     * to keep the connection alive, and reports the paths it served on each connection.
     */
    private class LegacyOrigin(private val server: ServerSocket, scope: CoroutineScope) {

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
            val paths: MutableList<String> = mutableListOf()
            try {
                while (true) {
                    val request: Request = parseRequest(input) ?: break
                    val keepAlive: Boolean = request.use { handle(it, input, output, paths) }
                    if (!keepAlive) {
                        break
                    }
                }
            } finally {
                output.flushAndClose()
                socket.close()
                servedConnections.send(paths)
            }
        }

        private suspend fun handle(
            request: Request,
            input: ByteReadChannel,
            output: ByteWriteChannel,
            paths: MutableList<String>,
        ): Boolean {
            val version: HttpProtocolVersion = HttpProtocolVersion.parse(request.version)
            val options: ConnectionOptions? = ConnectionOptions.parse(request.headers[HttpHeaders.Connection])
            val path: String = request.uri.toString()
            paths += "${request.method.value} $path"
            val requestBody: String = if (expectHttpBody(request)) readBody(version, request.headers, input) else ""

            val (status: HttpStatusCode, body: String) = when {
                request.method == HttpMethod.Get && path == "/motd" ->
                    HttpStatusCode.OK to "Welcome to the legacy gateway.\nMaintenance window: Sunday 02:00 UTC.\n"
                request.method == HttpMethod.Get && path == "/status" -> HttpStatusCode.OK to "ok"
                request.method == HttpMethod.Post && path == "/echo" -> HttpStatusCode.OK to requestBody.uppercase()
                else -> HttpStatusCode.NotFound to "no such resource: $path"
            }
            val keepAlive: Boolean = version == HttpProtocolVersion.HTTP_1_0 && options?.keepAlive == true
            val bytes: ByteArray = body.encodeToByteArray()

            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.0", status.value, status.description)
                head.headerLine(HttpHeaders.Server, "legacy-origin/1.0")
                head.headerLine(HttpHeaders.ContentType, "text/plain")
                if (keepAlive) {
                    head.headerLine(HttpHeaders.Connection, "keep-alive")
                    head.headerLine(HttpHeaders.ContentLength, bytes.size.toString())
                }
                head.emptyLine()
                head.bytes(bytes, 0, bytes.size)
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()
            return keepAlive
        }
    }

    /** An HTTP/1.0 client that speaks to the origin over one TCP connection. */
    private class LegacyClient(private val socket: Socket) {

        val input: ByteReadChannel = socket.openReadChannel()

        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)

        suspend fun exchange(method: HttpMethod, path: String, body: String?, keepAlive: Boolean): Received {
            val head = RequestResponseBuilder()
            try {
                head.requestLine(method, path, "HTTP/1.0")
                head.headerLine(HttpHeaders.Host, "legacy.example")
                head.headerLine(HttpHeaders.UserAgent, "legacy-client/1.0")
                if (keepAlive) {
                    head.headerLine(HttpHeaders.Connection, "keep-alive")
                }
                val bytes: ByteArray? = body?.encodeToByteArray()
                if (bytes != null) {
                    head.headerLine(HttpHeaders.ContentType, "text/plain")
                    head.headerLine(HttpHeaders.ContentLength, bytes.size.toString())
                }
                head.emptyLine()
                if (bytes != null) {
                    head.bytes(bytes, 0, bytes.size)
                }
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

        private suspend fun read(response: Response): Received {
            val version: HttpProtocolVersion = HttpProtocolVersion.parse(response.version)
            return Received(
                version = version,
                status = HttpStatusCode(response.status, response.statusText.toString()),
                connection = ConnectionOptions.parse(response.headers[HttpHeaders.Connection]),
                contentLength = response.headers[HttpHeaders.ContentLength]?.toString(),
                body = readBody(version, response.headers, input),
            )
        }
    }

    @Test
    fun closeDelimitedResponsesEndWhenTheOriginClosesTheConnection(): Unit = runBlocking {
        withLegacyOrigin { origin: LegacyOrigin, selector: SelectorManager ->
            val motdClient: LegacyClient = connect(selector, origin)
            val motd: Received = motdClient.exchange(HttpMethod.Get, "/motd", body = null, keepAlive = false)
            assertEquals(HttpProtocolVersion.HTTP_1_0, motd.version)
            assertEquals(HttpStatusCode.OK, motd.status)
            assertNull(motd.connection)
            assertNull(motd.contentLength)
            assertEquals("Welcome to the legacy gateway.\nMaintenance window: Sunday 02:00 UTC.\n", motd.body)
            assertTrue(motdClient.input.isClosedForRead)
            motdClient.close()
            assertEquals(listOf("GET /motd"), origin.servedConnections.receive())

            val echoClient: LegacyClient = connect(selector, origin)
            val echo: Received = echoClient.exchange(HttpMethod.Post, "/echo", "order 1187: ship", keepAlive = false)
            assertEquals(HttpStatusCode.OK, echo.status)
            assertEquals("ORDER 1187: SHIP", echo.body)
            assertTrue(echoClient.input.isClosedForRead)
            echoClient.close()
            assertEquals(listOf("POST /echo"), origin.servedConnections.receive())
        }
    }

    @Test
    fun keepAliveOptInReusesOneConnectionUntilTheClientStopsAsking(): Unit = runBlocking {
        withLegacyOrigin { origin: LegacyOrigin, selector: SelectorManager ->
            val client: LegacyClient = connect(selector, origin)

            val status: Received = client.exchange(HttpMethod.Get, "/status", body = null, keepAlive = true)
            assertEquals(HttpStatusCode.OK, status.status)
            assertEquals(ConnectionOptions.KeepAlive, status.connection)
            assertEquals("2", status.contentLength)
            assertEquals("ok", status.body)

            val missing: Received = client.exchange(HttpMethod.Get, "/missing", body = null, keepAlive = true)
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertEquals(ConnectionOptions.KeepAlive, missing.connection)
            assertEquals("no such resource: /missing", missing.body)

            val last: Received = client.exchange(HttpMethod.Post, "/echo", "final batch", keepAlive = false)
            assertEquals(HttpStatusCode.OK, last.status)
            assertNull(last.connection)
            assertNull(last.contentLength)
            assertEquals("FINAL BATCH", last.body)
            assertTrue(client.input.isClosedForRead)
            client.close()

            assertEquals(listOf("GET /status", "GET /missing", "POST /echo"), origin.servedConnections.receive())
        }
    }

    private companion object {

        /** Reads one message body; with no length and no `Connection` header, HTTP/1.0 reads until EOF. */
        suspend fun readBody(version: HttpProtocolVersion, headers: HttpHeadersMap, input: ByteReadChannel): String {
            val sink = ByteChannel()
            parseHttpBody(
                version,
                headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L,
                headers[HttpHeaders.TransferEncoding],
                ConnectionOptions.parse(headers[HttpHeaders.Connection]),
                input,
                sink,
            )
            sink.flushAndClose()
            return sink.readRemaining().readByteArray().decodeToString()
        }

        suspend fun connect(selector: SelectorManager, origin: LegacyOrigin): LegacyClient =
            LegacyClient(aSocket(selector).tcp().connect("127.0.0.1", origin.port))

        suspend fun withLegacyOrigin(block: suspend (LegacyOrigin, SelectorManager) -> Unit) {
            val selector = SelectorManager(Dispatchers.IO)
            try {
                withTimeout(30_000) {
                    val origin = LegacyOrigin(aSocket(selector).tcp().bind("127.0.0.1", 0), this)
                    try {
                        block(origin, selector)
                    } finally {
                        origin.stop()
                    }
                }
            } finally {
                selector.close()
            }
        }
    }
}
